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
import org.awesoma.monitoring.service.TagService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.tag.TagCreateRequest;
import org.awesoma.monitoring.web.dto.tag.TagResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Tags")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "List tags", description = "Returns one page of reusable monitor tags.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tags returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    })
    public Page<TagResponse> list(@Valid PageParams page) {
        return tagService.list(page.toPageable());
    }

    @PostMapping
    @Operation(summary = "Create a tag")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Tag created",
                headers = @Header(
                        name = "Location",
                        description = "URI of the created tag",
                        schema = @Schema(type = "string", format = "uri"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        TagResponse created = tagService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tags/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a tag")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Tag deleted"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public void delete(@PathVariable @Positive Long id) {
        tagService.delete(id);
    }
}
