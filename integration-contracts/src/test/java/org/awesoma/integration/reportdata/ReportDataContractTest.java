package org.awesoma.integration.reportdata;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorSelectionRequest;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.Page;
import org.junit.jupiter.api.Test;

class ReportDataContractTest {
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant CAPTURED = Instant.parse("2026-11-02T12:00:00.123Z");

    @Test
    void aCursorRoundTripsWithinItsOwnQuery() {
        var cursor = ReportDataCursor.start(900, CAPTURED).after(40);
        String scope = new PeriodSearchRequest(42L, List.of(12L, 11L), FROM, TO, 50, null).scope("checks", 42);

        assertEquals(cursor, ReportDataCursor.decode(cursor.encode(scope), scope));
    }

    @Test
    void everyPageReportsTheSameCaptureTime() {
        var start = ReportDataCursor.start(1, Instant.parse("2026-11-02T12:00:00.123456789Z"));
        String scope = new MonitorSelectionRequest(null, 50, null).scope(42);

        assertEquals(Instant.parse("2026-11-02T12:00:00.123Z"), start.capturedAt());
        assertEquals(start.capturedAt(), ReportDataCursor.decode(start.encode(scope), scope).capturedAt());
    }

    @Test
    void theScopeDoesNotDependOnIdOrder() {
        assertEquals(new MonitorSelectionRequest(List.of(12L, 11L), 50, null).scope(42),
                new MonitorSelectionRequest(List.of(11L, 12L, 11L), 50, null).scope(42));
        assertNotEquals(new MonitorSelectionRequest(null, 50, null).scope(42),
                new MonitorSelectionRequest(List.of(11L), 50, null).scope(42));
    }

    @Test
    void aCursorCannotBeUsedForAnotherQuery() {
        var request = new PeriodSearchRequest(42L, List.of(11L), FROM, TO, 50, null);
        String token = ReportDataCursor.start(900, CAPTURED).encode(request.scope("checks", 42));

        for (String other : List.of(request.scope("checks", 43), request.scope("incidents", 42),
                new PeriodSearchRequest(42L, List.of(11L, 12L), FROM, TO, 50, null).scope("checks", 42),
                new PeriodSearchRequest(42L, List.of(11L), FROM, TO.plusSeconds(1), 50, null).scope("checks", 42))) {
            assertThrows(IllegalArgumentException.class, () -> ReportDataCursor.decode(token, other));
        }
    }

    @Test
    void rejectsMalformedCursors() {
        String scope = new MonitorSelectionRequest(null, 50, null).scope(42);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        List<String> tokens = new ArrayList<>(Arrays.asList("not base64 !", encoder.encodeToString("v2|0|1|0|x".getBytes()),
                encoder.encodeToString("v1|x|1|0|x".getBytes())));
        String valid = ReportDataCursor.start(5, CAPTURED).encode(scope);
        tokens.add(valid.substring(0, valid.length() - 2));
        String hash = new String(Base64.getUrlDecoder().decode(valid)).split("\\|")[4];
        tokens.add(encoder.encodeToString(("v1|9|5|0|" + hash).getBytes()));

        for (String token : tokens) {
            assertThrows(IllegalArgumentException.class, () -> ReportDataCursor.decode(token, scope), token);
        }
    }

    @Test
    void validatesRequests() {
        assertDoesNotThrow(() -> new MonitorSelectionRequest(null, 50, null).validate());
        assertDoesNotThrow(() -> new MonitorOwnershipRequest(List.of(1L)).validate());
        assertThrows(IllegalArgumentException.class, () -> new MonitorSelectionRequest(List.of(), 10, null).validate());
        assertThrows(IllegalArgumentException.class, () -> new MonitorSelectionRequest(null, 51, null).validate());
        assertThrows(IllegalArgumentException.class, () -> new MonitorSelectionRequest(null, 0, null).validate());
        assertThrows(IllegalArgumentException.class, () -> new MonitorOwnershipRequest(Arrays.asList(1L, null)).validate());
        assertThrows(IllegalArgumentException.class, () -> new MonitorOwnershipRequest(null).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodSearchRequest(42L, List.of(1L), TO, FROM, 10, null).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodSearchRequest(0L, List.of(1L), FROM, TO, 10, null).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodSearchRequest(42L, List.of(1L), null, TO, 10, null).validate());
        assertDoesNotThrow(() -> new PeriodSearchRequest(null, List.of(1L), FROM, TO, 10, null).validate());
    }

    @Test
    void aPageOfLimitPlusOneRowsHasACursorAfterItsLastItem() {
        var cursor = ReportDataCursor.start(9, CAPTURED);
        String scope = new MonitorSelectionRequest(null, 2, null).scope(42);

        var full = Page.of(List.of(4L, 6L, 8L), 2, cursor, scope, Long::longValue, String::valueOf, null);
        var last = Page.of(List.of(4L), 2, cursor, scope, Long::longValue, String::valueOf, null);

        assertEquals(List.of("4", "6"), full.items());
        assertEquals(6, ReportDataCursor.decode(full.nextCursor(), scope).lastId());
        assertEquals(9L, full.upperId());
        assertEquals(List.of("4"), last.items());
        assertEquals(null, last.nextCursor());
        assertEquals(false, last.hasNext());
    }

    @Test
    void aPageKeepsItsOwnCopyOfTheItems() {
        var items = new ArrayList<>(List.of("a"));
        var page = new Page<>(items, null, false, CAPTURED, 1L, null);
        items.add("b");

        assertEquals(List.of("a"), page.items());
    }
}
