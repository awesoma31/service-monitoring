package org.awesoma.monitoring.web.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.service.ChannelService;
import org.awesoma.monitoring.web.dto.channel.ChannelCreateRequest;
import org.awesoma.monitoring.web.dto.channel.ChannelResponse;
import org.awesoma.monitoring.web.dto.channel.ChannelUpdateRequest;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Channels")
public class ChannelController {

    private final ChannelService channels;

    @GetMapping("/projects/{projectId}/channels")
    @Operation(summary = "List project notification channels")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channels returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public Page<ChannelResponse> listByProject(
            @PathVariable @Positive Long projectId, @Valid PageParams page) {
        return channels.listByProject(projectId, page.toPageable());
    }

    @PostMapping("/projects/{projectId}/channels")
    @Operation(summary = "Create a notification channel")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Channel created",
                headers = @Header(
                        name = "Location",
                        description = "URI of the created channel",
                        schema = @Schema(type = "string", format = "uri"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<ChannelResponse> create(
            @PathVariable @Positive Long projectId,
            @Valid @RequestBody ChannelCreateRequest request) {
        ChannelResponse created = channels.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/v1/channels/" + created.id())).body(created);
    }

    @GetMapping("/channels/{id}")
    @Operation(summary = "Get a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ChannelResponse get(@PathVariable @Positive Long id) {
        return channels.get(id);
    }

    @PutMapping("/channels/{id}")
    @Operation(summary = "Update a notification channel", description = "Changes its destination and enabled state.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel updated"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ChannelResponse update(
            @PathVariable @Positive Long id, @Valid @RequestBody ChannelUpdateRequest request) {
        return channels.update(id, request);
    }

    @DeleteMapping("/channels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a notification channel")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Channel deleted"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public void delete(@PathVariable @Positive Long id) {
        channels.delete(id);
    }
}
