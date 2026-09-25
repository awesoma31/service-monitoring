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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping("/projects/{projectId}/channels")
    @Operation(summary = "List notification channels of a project")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel page returned"),
        @ApiResponse(responseCode = "400", description = "Invalid paging parameters"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public Mono<ResponseEntity<Page<ChannelResponse>>> listByProject(
            @PathVariable @Positive Long projectId, @Valid PageParams page) {
        return channelService.listByProject(projectId, page.toPageable()).map(ResponseEntity::ok);
    }

    @PostMapping("/projects/{projectId}/channels")
    @Operation(summary = "Add a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Channel created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Project not found"),
        @ApiResponse(responseCode = "409", description = "The project already notifies this target")
    })
    public Mono<ResponseEntity<ChannelResponse>> create(
            @PathVariable @Positive Long projectId, @Valid @RequestBody ChannelCreateRequest request) {
        return channelService.create(projectId, request).map(created -> ResponseEntity
                .created(URI.create("/api/v1/channels/" + created.id()))
                .body(created));
    }

    @GetMapping("/channels/{id}")
    @Operation(summary = "Get a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel returned"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
    })
    public Mono<ResponseEntity<ChannelResponse>> get(@PathVariable @Positive Long id) {
        return channelService.get(id).map(ResponseEntity::ok);
    }

    @PutMapping("/channels/{id}")
    @Operation(summary = "Retarget or silence a channel")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
    })
    public Mono<ResponseEntity<ChannelResponse>> update(
            @PathVariable @Positive Long id, @Valid @RequestBody ChannelUpdateRequest request) {
        return channelService.update(id, request).map(ResponseEntity::ok);
    }

    @DeleteMapping("/channels/{id}")
    @Operation(summary = "Delete a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Channel deleted"),
        @ApiResponse(responseCode = "404", description = "Channel not found")
    })
    public Mono<ResponseEntity<Void>> delete(@PathVariable @Positive Long id) {
        return channelService.delete(id).thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
