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
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CheckHistoryController {

    private final CheckHistoryService history;

    @GetMapping("/monitors/{monitorId}/results")
    @Operation(
            summary = "Scroll monitor check history",
            description = "Returns a Slice ordered from newest to oldest. The response tells whether "
                    + "more rows follow but carries no total count, so infinite scrolling costs "
                    + "no count query.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Check-result slice returned"),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters"),
        @ApiResponse(responseCode = "404", description = "Monitor not found")
    })
    public Mono<ResponseEntity<Slice<CheckResultResponse>>> listResults(
            @PathVariable @Positive Long monitorId, @Valid PageParams page) {
        return history.list(monitorId, page.toPageable()).map(ResponseEntity::ok);
    }
}
