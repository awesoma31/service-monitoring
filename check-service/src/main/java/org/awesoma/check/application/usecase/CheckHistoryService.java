package org.awesoma.check.application.usecase;

import java.util.List;
import org.awesoma.check.application.port.CheckHistory;
import org.awesoma.check.application.port.MonitorDirectory;
import org.awesoma.check.application.exception.NotFoundException;
import org.awesoma.check.domain.CheckResult;
import reactor.core.publisher.Mono;

/** Reads one bounded slice without requiring an expensive total-count query. */
public class CheckHistoryService {
    private final CheckHistory results;
    private final MonitorDirectory monitors;
    public CheckHistoryService(CheckHistory results, MonitorDirectory monitors) {
        this.results = results;
        this.monitors = monitors;
    }
    public record Slice(List<CheckResult> items, boolean hasNext) { }
    public Mono<Slice> list(Long monitorId, int page, int size) {
        return monitors.exists(monitorId).flatMap(exists -> exists
                ? results.findPage(monitorId, size + 1, (long) page * size).collectList().map(rows -> {
                    boolean hasNext = rows.size() > size;
                    return new Slice(hasNext ? rows.subList(0, size) : rows, hasNext);
                }) : Mono.error(new NotFoundException("Monitor %d not found".formatted(monitorId))));
    }
}
