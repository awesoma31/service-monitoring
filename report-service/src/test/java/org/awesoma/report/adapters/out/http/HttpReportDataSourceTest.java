package org.awesoma.report.adapters.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.awesoma.integration.reportdata.ReportDataResponses.IncidentItem;
import org.awesoma.integration.reportdata.ReportDataResponses.MonitorItem;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.awesoma.integration.reportdata.ReportDataResponses.ProjectItem;
import org.awesoma.report.application.port.ProjectNotFoundException;
import org.awesoma.report.application.port.SourceRejectedException;
import org.awesoma.report.application.port.SourceUnavailableException;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.source.CheckOutcome;
import org.awesoma.report.domain.source.IncidentSeverity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;

class HttpReportDataSourceTest {
    private static final Instant CAPTURED = Instant.parse("2026-11-02T12:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-10-02T00:00:00Z"));

    private final ReportDataClients.MonitorData monitors = mock(ReportDataClients.MonitorData.class);
    private final ReportDataClients.CheckData checks = mock(ReportDataClients.CheckData.class);
    private final HttpReportDataSource source = new HttpReportDataSource(monitors, checks, new ObjectMapper());

    @Test
    void convertsPagesAndSendsTheSelection() {
        when(monitors.selection(anyLong(), any())).thenReturn(new Page<>(List.of(new MonitorItem(11, "Сайт", "u")),
                "next", true, CAPTURED, 50L, new ProjectItem(42, "Проект")));
        when(checks.checks(any())).thenReturn(new Page<>(List.of(new CheckItem(1, 11, "TIMEOUT", null, CAPTURED)),
                null, false, CAPTURED, 9L, null));
        when(monitors.incidents(anyLong(), any())).thenReturn(new Page<>(List.of(new IncidentItem(5, 11, "HIGH",
                CAPTURED, null)), null, false, CAPTURED, 5L, null));

        var page = source.monitors(42, MonitorSelection.allMonitors(), null, 50);
        var checkPage = source.checks(42, List.of(11L), PERIOD, "c", 50);
        var incidentPage = source.incidents(42, List.of(11L), PERIOD, null, 50);

        assertThat(page.project().name()).isEqualTo("Проект");
        assertThat(page.monitors().items().get(0).name()).isEqualTo("Сайт");
        assertThat(page.monitors().nextCursor()).isEqualTo("next");
        assertThat(checkPage.items().get(0).outcome()).isEqualTo(CheckOutcome.TIMEOUT);
        assertThat(checkPage.items().get(0).responseMs()).isNull();
        assertThat(checkPage.upperId()).isEqualTo(9L);
        assertThat(incidentPage.items().get(0).severity()).isEqualTo(IncidentSeverity.HIGH);

        var selection = ArgumentCaptor.forClass(MonitorSelectionRequest.class);
        verify(monitors).selection(eq(42L), selection.capture());
        assertThat(selection.getValue().monitorIds()).isNull();
        var search = ArgumentCaptor.forClass(PeriodSearchRequest.class);
        verify(checks).checks(search.capture());
        assertThat(search.getValue().projectId()).isEqualTo(42L);
        assertThat(search.getValue().cursor()).isEqualTo("c");
        assertThat(search.getValue().from()).isEqualTo(PERIOD.from());
    }

    @Test
    void actsOnlyOnRefusalsThatNameTheirReason() {
        when(monitors.selection(anyLong(), any()))
                .thenThrow(new NoFallbackAvailableException("open", status(404, "{\"code\":\"PROJECT_NOT_FOUND\"}")))
                .thenThrow(status(422, "{\"code\":\"MONITOR_NOT_IN_PROJECT\"}"))
                .thenThrow(status(404, "{\"title\":\"Not Found\"}"))
                .thenThrow(status(404, "not json"))
                .thenThrow(status(500, "{\"code\":\"PROJECT_NOT_FOUND\"}"))
                .thenThrow(new IllegalStateException("circuit open"));

        assertThatThrownBy(() -> source.monitors(42, MonitorSelection.allMonitors(), null, 1))
                .isInstanceOf(ProjectNotFoundException.class);
        assertThatThrownBy(() -> source.monitors(42, MonitorSelection.allMonitors(), null, 1))
                .isInstanceOf(SourceRejectedException.class)
                .extracting("reason").isEqualTo(FailureCode.MONITOR_NOT_IN_PROJECT);
        for (int outage = 0; outage < 4; outage++) {
            assertThatThrownBy(() -> source.monitors(42, MonitorSelection.allMonitors(), null, 1))
                    .as("a bare 404 or a server error is an outage")
                    .isInstanceOf(SourceUnavailableException.class);
        }
    }

    private static FeignException status(int status, String body) {
        var request = Request.create(Request.HttpMethod.POST, "/internal", Map.of(), new byte[0],
                StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("call", Response.builder().status(status).reason("x")
                .request(request).headers(Map.of()).body(body, StandardCharsets.UTF_8).build());
    }
}
