package org.awesoma.report.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.awesoma.integration.reportdata.ReportDataResponses.IncidentItem;
import org.awesoma.integration.reportdata.ReportDataResponses.MonitorItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.awesoma.integration.reportdata.ReportDataResponses.ProjectItem;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.usecase.GenerationWorker;
import org.awesoma.report.domain.ObjectKeys;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.awesoma.security.jwt.JwtTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The whole path of a generated report: request, queue, worker, storage and download. */
@AutoConfigureMockMvc
class GenerationApiIntegrationTest extends AbstractIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(7000);
    private static final Instant CAPTURED = Instant.parse("2026-11-02T12:00:00Z");

    @Autowired private MockMvc http;
    @Autowired private JwtTokens tokens;
    @Autowired private ObjectMapper json;
    @Autowired private GenerationWorker worker;
    @Autowired private ObjectStorage storage;

    private long project;
    private long operator;
    private long viewer;

    @BeforeEach
    void sources() {
        project = IDS.incrementAndGet();
        operator = IDS.incrementAndGet();
        viewer = IDS.incrementAndGet();
        grantWrite(project, operator);
        grantRead(project, viewer);
        when(monitorData.selection(anyLong(), any())).thenReturn(new Page<>(List.of(
                new MonitorItem(11, "Главный сайт", "https://example.com"),
                new MonitorItem(12, "=API", "https://api.example.com")), null, false, CAPTURED, 12L,
                new ProjectItem(project, "Проект")));
        when(checkData.checks(any())).thenReturn(new Page<>(List.of(
                new CheckItem(1, 11, "SUCCESS", 120, Instant.parse("2026-10-01T01:00:00Z")),
                new CheckItem(2, 11, "TIMEOUT", null, Instant.parse("2026-10-01T02:00:00Z"))),
                null, false, CAPTURED, 2L, null));
        when(monitorData.incidents(anyLong(), any())).thenReturn(new Page<>(List.of(
                new IncidentItem(5, 11, "HIGH", Instant.parse("2026-09-30T00:00:00Z"), null)),
                null, false, CAPTURED, 5L, null));
    }

    @Test
    void queuesGeneratesAndServesACsvReport() throws Exception {
        String key = UUID.randomUUID().toString();
        var accepted = generate(operator, "OPERATOR", body("CSV", "[12, 11]"), key)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.source").value("GENERATED"))
                .andExpect(jsonPath("$.file_name").isEmpty())
                .andExpect(jsonPath("$.generation.from").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.generation.to").value("2026-10-02T00:00:00Z"))
                .andExpect(jsonPath("$.generation.monitor_ids[0]").value(11));
        String id = read(accepted).get("id").asText();
        assertThat(accepted.andReturn().getResponse().getHeader("Location")).isEqualTo("/api/v1/reports/" + id);

        generate(operator, "OPERATOR", body("CSV", "[11, 12]"), key)
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(id));
        generate(operator, "OPERATOR", body("PDF", "[11, 12]"), key)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        perform(get("/api/v1/reports/" + id + "/content"), viewer, "VIEWER").andExpect(status().isConflict());

        drain();

        perform(get("/api/v1/reports/" + id), viewer, "VIEWER")
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.file_name").value("report-" + id + ".csv"))
                .andExpect(jsonPath("$.generation.attempts").value(1))
                .andExpect(jsonPath("$.generation.source_bounds.checks_upper_id").value(2))
                .andExpect(jsonPath("$.generation.generated_at").isNotEmpty());
        String csv = perform(get("/api/v1/reports/" + id + "/content"), viewer, "VIEWER")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).contains(project + ",Проект,11,Главный сайт,https://example.com,2026-10-01T00:00:00Z,"
                + "2026-10-02T00:00:00Z")
                .contains(",2,1,0,1,0,50.00,120,120.00,120,1,0,0,1,0,1")
                .contains(",12,'=API,");
    }

    @Test
    void generatesAPdfForEveryMonitorOfTheProject() throws Exception {
        String id = read(generate(operator, "OPERATOR", body("PDF", null), null)).get("id").asText();

        drain();

        byte[] pdf = perform(get("/api/v1/reports/" + id + "/content"), operator, "OPERATOR")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void refusesInvalidRequestsAndReadersWithoutTheRightToWrite() throws Exception {
        generate(viewer, "VIEWER", body("CSV", null), null).andExpect(status().isForbidden());
        generate(operator, "OPERATOR", "{\"from\":\"2026-10-01T00:00:00\",\"to\":\"2026-10-02T00:00:00Z\","
                + "\"format\":\"CSV\"}", null).andExpect(status().isBadRequest());
        generate(operator, "OPERATOR", body("CSV", "[]"), null).andExpect(status().isBadRequest());
        generate(operator, "OPERATOR", body("XLSX", null), null).andExpect(status().isBadRequest());
        generate(operator, "OPERATOR", "{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-12-01T00:00:00Z\","
                + "\"format\":\"CSV\"}", null).andExpect(status().isBadRequest());
        perform(get("/api/v1/projects/" + project + "/reports?source=GENERATED"), operator, "OPERATOR")
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void deletingAJobRemovesWhatItsAttemptsStoredAndKeepsTheKeyTaken() throws Exception {
        String key = UUID.randomUUID().toString();
        String id = read(generate(operator, "OPERATOR", body("CSV", null), key)).get("id").asText();
        drain();
        String prefix = ObjectKeys.upload(project, UUID.fromString(id));
        assertThat(storage.list(prefix)).hasSize(1);

        perform(delete("/api/v1/reports/" + id), operator, "OPERATOR").andExpect(status().isAccepted());

        assertThat(storage.list(prefix)).isEmpty();
        perform(get("/api/v1/reports/" + id), operator, "OPERATOR").andExpect(status().isNotFound());
        generate(operator, "OPERATOR", body("CSV", null), key)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_DELETED"));
    }

    /** Other test classes may have left jobs in the shared database; run them all. */
    private void drain() {
        while (worker.runNext()) {
            // Next job.
        }
    }

    private static String body(String format, String monitorIds) {
        return "{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-10-02T03:00:00+03:00\",\"format\":\"" + format
                + "\"" + (monitorIds == null ? "" : ",\"monitor_ids\":" + monitorIds) + "}";
    }

    private ResultActions generate(long user, String role, String body, String key) throws Exception {
        var request = post("/api/v1/projects/" + project + "/reports/generations")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request.header("Idempotency-Key", key);
        return perform(request, user, role);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, long user, String role) throws Exception {
        return http.perform(request.header("Authorization",
                "Bearer " + tokens.issue(Long.toString(user), role).getTokenValue()));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
