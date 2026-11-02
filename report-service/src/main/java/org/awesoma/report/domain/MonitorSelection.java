package org.awesoma.report.domain;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Either every monitor of the project or an explicit, ordered and de-duplicated set. */
public record MonitorSelection(boolean all, List<Long> monitorIds) {

    public MonitorSelection {
        monitorIds = List.copyOf(monitorIds);
        if (all && !monitorIds.isEmpty()) {
            throw new IllegalArgumentException("A selection of all monitors lists no IDs");
        }
    }

    public static MonitorSelection allMonitors() {
        return new MonitorSelection(true, List.of());
    }

    /** No list means every monitor; an explicit empty list is a mistake rather than "all". */
    public static MonitorSelection requested(Collection<Long> ids) {
        if (ids == null) return allMonitors();
        if (ids.isEmpty()) {
            throw new InvalidReportRequestException("monitor_ids", "Monitor list cannot be empty");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new InvalidReportRequestException("monitor_ids", "Monitor IDs must be positive");
        }
        return new MonitorSelection(false, List.copyOf(new TreeSet<>(ids)));
    }

    public int size() {
        return monitorIds.size();
    }

    /** A stable text form: equal selections give equal strings regardless of input order. */
    public String canonical() {
        return all ? "all" : monitorIds.stream().map(Objects::toString).collect(Collectors.joining(",", "ids:", ""));
    }
}
