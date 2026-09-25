package org.awesoma.check.service;

import lombok.RequiredArgsConstructor;
import org.awesoma.check.client.ReactiveMonitorClient;
import org.awesoma.check.repository.CheckResultRepository;
import org.awesoma.check.web.dto.CheckResultResponse;
import org.awesoma.check.web.exception.NotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CheckHistoryService {

    private final CheckResultRepository results;
    private final ReactiveMonitorClient monitors;

    /**
     * Check history for infinite scrolling.
     *
     * <p>A Slice rather than a Page on purpose: check_results grows without bound, and counting
     * the rows of a busy monitor on every scroll would cost more than the page itself. The
     * client only needs to know whether more rows follow.
     */
    public Mono<Slice<CheckResultResponse>> list(Long monitorId, Pageable pageable) {
        return monitors.exists(monitorId).flatMap(exists -> exists
                ? page(monitorId, pageable)
                : Mono.error(new NotFoundException("Monitor %d not found".formatted(monitorId))));
    }

    private Mono<Slice<CheckResultResponse>> page(Long monitorId, Pageable pageable) {
        int size = pageable.getPageSize();
        return results.findPage(monitorId, size + 1, pageable.getOffset())
                .map(CheckResultResponse::of)
                .collectList()
                .map(rows -> {
                    boolean hasNext = rows.size() > size;
                    return new SliceImpl<>(hasNext ? rows.subList(0, size) : rows, pageable, hasNext);
                });
    }
}
