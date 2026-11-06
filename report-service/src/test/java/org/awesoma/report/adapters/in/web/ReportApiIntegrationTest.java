package org.awesoma.report.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.RemoveObjectArgs;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.awesoma.security.jwt.JwtTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class ReportApiIntegrationTest extends AbstractIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(5000);
    private static final byte[] CSV = "monitor,total\nНаш сайт,42\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired private MockMvc http;
    @Autowired private JwtTokens tokens;
    @Autowired private ObjectMapper json;
    @Autowired private ReportRepository reports;
    @Autowired private ObjectStorage storage;

    private long project;
    private long operator;
    private long viewer;
    private long outsider;

    @BeforeEach
    void users() {
        project = IDS.incrementAndGet();
        operator = IDS.incrementAndGet();
        viewer = IDS.incrementAndGet();
        outsider = IDS.incrementAndGet();
        grantWrite(project, operator);
        grantRead(project, viewer);
    }

    @Test
    void uploadsListsAndDownloadsTheSameBytes() throws Exception {
        var created = upload(operator, "OPERATOR", file("Отчёт за октябрь.csv", "text/csv", CSV))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.source").value("UPLOADED"))
                .andExpect(jsonPath("$.format").value("CSV"))
                .andExpect(jsonPath("$.project_id").value(project))
                .andExpect(jsonPath("$.created_by").value(operator))
                .andExpect(jsonPath("$.file_name").value("Отчёт за октябрь.csv"))
                .andExpect(jsonPath("$.content_type").value("text/csv; charset=UTF-8"))
                .andExpect(jsonPath("$.size_bytes").value(CSV.length))
                .andExpect(jsonPath("$.sha256").value(sha256(CSV)))
                .andExpect(jsonPath("$.generation").doesNotExist())
                .andReturn();
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String id = body.get("id").asText();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/reports/" + id);
        assertThat(body.toString()).doesNotContain("projects/").doesNotContain("object_key");

        perform(get("/api/v1/reports/" + id), viewer, "VIEWER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        perform(get("/api/v1/projects/" + project + "/reports"), viewer, "VIEWER")
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(header().string("X-Has-Next", "false"))
                .andExpect(jsonPath("$[0].id").value(id));

        var download = perform(get("/api/v1/reports/" + id + "/content"), viewer, "VIEWER")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().longValue("Content-Length", CSV.length))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        assertThat(download.getContentAsByteArray()).isEqualTo(CSV);
        assertThat(download.getHeader("Content-Disposition"))
                .startsWith("attachment")
                .contains("filename*=UTF-8''%D0%9E%D1%82%D1%87%D1%91%D1%82");
    }

    @Test
    void acceptsAPdfSentWithoutAMeaningfulType() throws Exception {
        upload(operator, "OPERATOR", file("uptime.PDF", "application/octet-stream", PDF))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.content_type").value("application/pdf"));
        upload(operator, "OPERATOR", file("uptime.pdf", null, PDF)).andExpect(status().isCreated());
        upload(operator, "OPERATOR", file("uptime.csv", "TEXT/CSV; Charset=utf-8", CSV)).andExpect(status().isCreated());
    }

    @Test
    void rejectsFilesItDoesNotAccept() throws Exception {
        upload(operator, "OPERATOR", file("empty.csv", "text/csv", new byte[0]))
                .andExpect(status().isBadRequest());
        upload(operator, "OPERATOR", file("notes.txt", "text/plain", CSV))
                .andExpect(status().isUnsupportedMediaType());
        upload(operator, "OPERATOR", file("uptime.csv", "application/pdf", CSV))
                .andExpect(status().isUnsupportedMediaType());
        upload(operator, "OPERATOR", file("uptime.csv", "text/csv; charset=windows-1251", CSV))
                .andExpect(status().isUnsupportedMediaType());
        upload(operator, "OPERATOR", file("latin1.csv", "text/csv", new byte[] {'a', (byte) 0xE9, '\n'}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("CSV file is not valid UTF-8 text"));
        upload(operator, "OPERATOR", file("fake.pdf", "application/pdf", CSV))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("File does not start with the PDF signature"));
        upload(operator, "OPERATOR", file("bad\u0007name.csv", "text/csv", CSV))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("file"));
        perform(multipart("/api/v1/projects/" + project + "/reports"), operator, "OPERATOR")
                .andExpect(status().isBadRequest());

        perform(get("/api/v1/projects/" + project + "/reports"), operator, "OPERATOR")
                .andExpect(header().string("X-Total-Count", "0"));
    }

    @Test
    void checksTheProjectRightsOfEveryCaller() throws Exception {
        var csv = file("uptime.csv", "text/csv", CSV);
        http.perform(multipart("/api/v1/projects/" + project + "/reports").file(csv))
                .andExpect(status().isUnauthorized());
        upload(viewer, "VIEWER", csv).andExpect(status().isForbidden());
        upload(outsider, "OPERATOR", csv).andExpect(status().isForbidden());

        String id = idOf(upload(operator, "OPERATOR", csv).andExpect(status().isCreated()));
        perform(get("/api/v1/reports/" + id), outsider, "OPERATOR").andExpect(status().isForbidden());
        perform(get("/api/v1/reports/" + id + "/content"), outsider, "OPERATOR").andExpect(status().isForbidden());
        perform(get("/api/v1/projects/" + project + "/reports"), outsider, "OPERATOR")
                .andExpect(status().isForbidden());
        perform(delete("/api/v1/reports/" + id), viewer, "VIEWER").andExpect(status().isForbidden());

        missingProjects.add(project);
        perform(get("/api/v1/reports/" + id), operator, "OPERATOR").andExpect(status().isNotFound());
        perform(get("/api/v1/projects/" + project + "/reports"), operator, "SUPERVISOR")
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnverifiableRightClosesAccess() throws Exception {
        when(projectLookup.projectExists(anyLong())).thenThrow(new IllegalStateException("monitor-service down"));

        perform(get("/api/v1/projects/" + project + "/reports"), operator, "OPERATOR")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Access permissions cannot currently be verified"));
    }

    @Test
    void reportsAJobBeforeItIsReadyButNotItsContent() throws Exception {
        var queued = reports.save(Report.queueGeneration(UUID.randomUUID(), project, operator, ReportFormat.PDF,
                new GenerationParameters(new ReportPeriod(Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-02T00:00:00Z")), MonitorSelection.allMonitors(),
                        Instant.parse("2026-11-01T00:00:00Z"))));

        perform(get("/api/v1/reports/" + queued.id()), viewer, "VIEWER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.file_name").isEmpty())
                .andExpect(jsonPath("$.sha256").isEmpty())
                .andExpect(jsonPath("$.generation.from").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.generation.monitor_ids").isEmpty());
        perform(get("/api/v1/reports/" + queued.id() + "/content"), viewer, "VIEWER")
                .andExpect(status().isConflict());
    }

    @Test
    void deletionHidesTheReportAndRemovesItsObject() throws Exception {
        String id = idOf(upload(operator, "OPERATOR", file("uptime.csv", "text/csv", CSV)));
        String key = reports.findById(UUID.fromString(id)).orElseThrow().objectKey();

        perform(delete("/api/v1/reports/" + id), operator, "OPERATOR").andExpect(status().isAccepted());

        perform(get("/api/v1/reports/" + id), operator, "OPERATOR").andExpect(status().isNotFound());
        perform(get("/api/v1/projects/" + project + "/reports"), operator, "OPERATOR")
                .andExpect(header().string("X-Total-Count", "0"));
        assertThat(storage.exists(key)).isFalse();
        perform(delete("/api/v1/reports/" + id), operator, "OPERATOR").andExpect(status().isNotFound());
    }

    @Test
    void aReadyReportWithoutItsObjectFailsInsteadOfServingNothing() throws Exception {
        String id = idOf(upload(operator, "OPERATOR", file("uptime.csv", "text/csv", CSV)));
        String key = reports.findById(UUID.fromString(id)).orElseThrow().objectKey();
        rootClient().removeObject(RemoveObjectArgs.builder().bucket(BUCKET).object(key).build());

        perform(get("/api/v1/reports/" + id + "/content"), operator, "OPERATOR")
                .andExpect(status().isServiceUnavailable());

        perform(get("/api/v1/reports/" + id), operator, "OPERATOR")
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure_code").value("OBJECT_MISSING"));
        perform(get("/api/v1/reports/" + id + "/content"), operator, "OPERATOR")
                .andExpect(status().isConflict());
    }

    @Test
    void pagesAndFiltersTheList() throws Exception {
        for (int i = 0; i < 3; i++) upload(operator, "OPERATOR", file("r" + i + ".csv", "text/csv", CSV));

        perform(get("/api/v1/projects/" + project + "/reports?size=2"), viewer, "VIEWER")
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(header().string("X-Has-Next", "true"));
        perform(get("/api/v1/projects/" + project + "/reports?page=1&size=2"), viewer, "VIEWER")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(header().string("X-Has-Next", "false"));
        perform(get("/api/v1/projects/" + project + "/reports?source=GENERATED"), viewer, "VIEWER")
                .andExpect(header().string("X-Total-Count", "0"));
        perform(get("/api/v1/projects/" + project + "/reports?size=51"), viewer, "VIEWER")
                .andExpect(status().isBadRequest());
        perform(get("/api/v1/projects/" + project + "/reports?page=-1"), viewer, "VIEWER")
                .andExpect(status().isBadRequest());
        perform(get("/api/v1/projects/" + project + "/reports?status=UNKNOWN"), viewer, "VIEWER")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAndMalformedIdsAreRejected() throws Exception {
        perform(get("/api/v1/reports/" + UUID.randomUUID()), operator, "OPERATOR").andExpect(status().isNotFound());
        perform(get("/api/v1/reports/not-a-uuid"), operator, "OPERATOR").andExpect(status().isBadRequest());
    }

    @Test
    void documentsTheApiInSnakeCase() throws Exception {
        http.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/projects/{projectId}/reports'].post").exists())
                .andExpect(jsonPath("$.components.schemas.Report.properties.sha256").exists())
                .andExpect(jsonPath("$.components.schemas.Report.properties.file_name").exists());
    }

    private ResultActions upload(long user, String role, MockMultipartFile file) throws Exception {
        return perform(multipart("/api/v1/projects/" + project + "/reports").file(file), user, role);
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, long user, String role) throws Exception {
        return http.perform(request.header("Authorization",
                "Bearer " + tokens.issue(Long.toString(user), role).getTokenValue()));
    }

    private String idOf(ResultActions created) throws Exception {
        return json.readTree(created.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
