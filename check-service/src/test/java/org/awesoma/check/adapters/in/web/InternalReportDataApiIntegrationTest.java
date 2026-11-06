package org.awesoma.check.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.check.adapters.out.http.ReportDataMonitorClient;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.CheckResultType;
import org.awesoma.check.adapters.out.persistence.repository.CheckResultRepository;
import org.awesoma.check.support.AbstractIntegrationTest;
import org.awesoma.integration.reportdata.ReportDataResponses.Ownership;
import org.awesoma.integration.reportdata.ReportDataResponses.OwnershipResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

class InternalReportDataApiIntegrationTest extends AbstractIntegrationTest {
    private static final AtomicLong MONITORS = new AtomicLong(900_000);
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-10-02T00:00:00Z");
    private static final long PROJECT = 42;

    @Autowired private CheckResultRepository results;
    @MockitoBean private ReportDataMonitorClient monitorService;

    private long a;
    private long b;
    private long notSelected;

    @BeforeEach
    void monitors() {
        a = MONITORS.incrementAndGet();
        b = MONITORS.incrementAndGet();
        notSelected = MONITORS.incrementAndGet();
        when(monitorService.ownership(anyLong(), any())).thenReturn(new OwnershipResponse(Ownership.OWNED));
    }

    @Test
    void returnsTheChecksOfTheSelectedMonitorsInsideTheHalfOpenPeriod() {
        long atFrom = save(a, FROM, CheckResultType.SUCCESS, 120);
        save(a, FROM.minusNanos(1000), CheckResultType.SUCCESS, 100);
        long timedOut = save(b, TO.minusSeconds(1), CheckResultType.TIMEOUT, null);
        save(b, TO, CheckResultType.SUCCESS, 90);
        save(notSelected, FROM.plusHours(1), CheckResultType.SUCCESS, 80);

        JsonNode page = search(body(50)).expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(ids(page)).containsExactly(atFrom, timedOut);
        assertThat(page.get("items").get(0).get("response_ms").asInt()).isEqualTo(120);
        assertThat(page.get("items").get(1).get("result").asText()).isEqualTo("TIMEOUT");
        assertThat(page.get("items").get(1).get("response_ms").isNull()).isTrue();
        assertThat(page.get("items").get(1).get("checked_at").asText()).isEqualTo("2026-10-01T23:59:59Z");
        assertThat(page.get("has_next").asBoolean()).isFalse();
        assertThat(page.get("next_cursor").isNull()).isTrue();
    }

    @Test
    void leavesOutChecksWrittenAfterTheWalkStarted() {
        long first = save(a, FROM.plusHours(1), CheckResultType.SUCCESS, 10);
        long second = save(b, FROM.plusHours(2), CheckResultType.BAD_STATUS, 11);
        long third = save(a, FROM.plusHours(3), CheckResultType.CONNECTION_ERROR, null);
        var request = body(2);

        JsonNode firstPage = search(request).expectBody(JsonNode.class).returnResult().getResponseBody();
        save(a, FROM.plusHours(4), CheckResultType.SUCCESS, 12);
        request.put("cursor", firstPage.get("next_cursor").asText());
        JsonNode secondPage = search(request).expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(ids(firstPage)).containsExactly(first, second);
        assertThat(firstPage.get("has_next").asBoolean()).isTrue();
        assertThat(ids(secondPage)).containsExactly(third);
        assertThat(secondPage.get("upper_id").asLong()).isEqualTo(firstPage.get("upper_id").asLong()).isEqualTo(third);
        assertThat(secondPage.get("source_captured_at").asText())
                .isEqualTo(firstPage.get("source_captured_at").asText());
    }

    @Test
    void asksMonitorServiceWhetherTheMonitorsBelongToTheProject() {
        when(monitorService.ownership(anyLong(), any()))
                .thenReturn(new OwnershipResponse(Ownership.MONITOR_NOT_IN_PROJECT))
                .thenReturn(new OwnershipResponse(Ownership.PROJECT_NOT_FOUND))
                .thenThrow(new IllegalStateException("circuit open"));

        search(body(10)).expectStatus().isEqualTo(422)
                .expectBody().jsonPath("$.code").isEqualTo("MONITOR_NOT_IN_PROJECT");
        search(body(10)).expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("PROJECT_NOT_FOUND");
        search(body(10)).expectStatus().isEqualTo(503);
    }

    @Test
    void rejectsInvalidRequestsAndForeignCursors() {
        save(a, FROM.plusHours(1), CheckResultType.SUCCESS, 10);
        save(a, FROM.plusHours(2), CheckResultType.SUCCESS, 10);
        search(body(51)).expectStatus().isBadRequest();
        var noProject = body(10);
        noProject.remove("project_id");
        search(noProject).expectStatus().isBadRequest();
        var reversed = body(10);
        reversed.put("from", TO.toInstant().toString());
        reversed.put("to", FROM.toInstant().toString());
        search(reversed).expectStatus().isBadRequest();

        JsonNode page = search(body(1)).expectBody(JsonNode.class).returnResult().getResponseBody();
        var otherPeriod = body(1);
        otherPeriod.put("to", TO.plusSeconds(1).toInstant().toString());
        otherPeriod.put("cursor", page.get("next_cursor").asText());
        search(otherPeriod).expectStatus().isBadRequest();
    }

    @Test
    void isForServicesOnly() {
        http.post().uri("/internal/report-data/checks/search").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body(10)).exchange().expectStatus().isForbidden();
    }

    private ResponseSpec search(Map<String, Object> body) {
        return http.post().uri("/internal/report-data/checks/search")
                .header("Authorization", "Bearer " + tokens.issue("report-service", "SERVICE").getTokenValue())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    private Map<String, Object> body(int limit) {
        return new HashMap<>(Map.of("project_id", PROJECT, "monitor_ids", List.of(b, a), "from", FROM.toInstant().toString(),
                "to", TO.toInstant().toString(), "limit", limit));
    }

    private long save(long monitorId, OffsetDateTime checkedAt, CheckResultType type, Integer responseMs) {
        return results.save(new CheckResult(null, monitorId, checkedAt, type, responseMs, null, null)).block().id();
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }
}
