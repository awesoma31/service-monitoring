package org.awesoma.check.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.service.CheckHistoryService;
import org.awesoma.check.web.dto.CheckResultResponse;
import org.awesoma.check.web.dto.PageParams;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CheckHistoryController {

    private final CheckHistoryService history;

    @GetMapping(value = "/monitors/{monitorId}/results",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_NDJSON_VALUE})
    @Operation(
            summary = "Scroll monitor check history",
            description = "Returns check results ordered from newest to oldest. X-Has-Next tells "
                    + "whether more rows follow; no total count query is performed.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Check-result slice returned"),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters"),
        @ApiResponse(responseCode = "404", description = "Monitor not found")
    })
    public Flux<CheckResultResponse> listResults(
            @PathVariable @Positive Long monitorId, @Valid PageParams page,
            ServerHttpResponse response) {
        return history.list(monitorId, page.toPageable())
                .doOnNext(slice -> response.getHeaders().set("X-Has-Next", Boolean.toString(slice.hasNext())))
                .flatMapIterable(slice -> slice.getContent());
    }
}
