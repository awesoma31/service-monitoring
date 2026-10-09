package org.awesoma.notification.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.notification.service.ChannelService;
import org.awesoma.notification.web.dto.ChannelCreateRequest;
import org.awesoma.notification.web.dto.ChannelResponse;
import org.awesoma.notification.web.dto.ChannelUpdateRequest;
import org.awesoma.notification.web.dto.PageParams;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping(value = "/projects/{projectId}/channels", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "List notification channels of a project")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel page returned"),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public Flux<Page<ChannelResponse>> listByProject(
            @PathVariable @Positive Long projectId, @Valid PageParams page) {
        return channelService.listByProject(projectId, page.toPageable()).flux();
    }

    @PostMapping(value = "/projects/{projectId}/channels", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Add a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Channel created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Project not found"),
        @ApiResponse(responseCode = "409", description = "The project already notifies this target")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public Flux<ChannelResponse> create(
            @PathVariable @Positive Long projectId,
            @Valid @RequestBody ChannelCreateRequest request,
            ServerHttpResponse response) {
        return channelService
                .create(projectId, request)
                .doOnNext(created -> response.getHeaders()
                        .setLocation(URI.create("/api/v1/channels/" + created.id())))
                .flux();
    }

    @GetMapping(value = "/channels/{id}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Get a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel returned"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
    })
    public Flux<ChannelResponse> get(@PathVariable @Positive Long id) {
        return channelService.get(id).flux();
    }

    @PutMapping(value = "/channels/{id}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Operation(summary = "Retarget or silence a channel")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
    })
    public Flux<ChannelResponse> update(
            @PathVariable @Positive Long id, @Valid @RequestBody ChannelUpdateRequest request) {
        return channelService.update(id, request).flux();
    }

    @DeleteMapping("/channels/{id}")
    @Operation(summary = "Delete a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Channel deleted"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Flux<Void> delete(@PathVariable @Positive Long id) {
        return channelService.delete(id).flux();
    }
}
