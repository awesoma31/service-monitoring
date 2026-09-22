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
import org.awesoma.monitoring.service.UserService;
import org.awesoma.monitoring.web.dto.common.PageParams;
import org.awesoma.monitoring.web.dto.user.UserCreateRequest;
import org.awesoma.monitoring.web.dto.user.UserResponse;
import org.awesoma.monitoring.web.dto.user.UserUpdateRequest;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService users;

    @GetMapping
    @Operation(summary = "List users", description = "Returns one page of users, at most 50 records.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest")
    })
    public Page<UserResponse> list(@Valid PageParams page) {
        return users.list(page.toPageable());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User returned"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public UserResponse get(@PathVariable @Positive Long id) {
        return users.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a user", description = "Hashes the supplied password before storage.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "User created",
                headers = @Header(
                        name = "Location",
                        description = "URI of the created user",
                        schema = @Schema(type = "string", format = "uri"))),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse created = users.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user", description = "Changes the display name and account status.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User updated"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public UserResponse update(
            @PathVariable @Positive Long id, @Valid @RequestBody UserUpdateRequest request) {
        return users.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User deleted"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public void delete(@PathVariable @Positive Long id) {
        users.delete(id);
    }
}
