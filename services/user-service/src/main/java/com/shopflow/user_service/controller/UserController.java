package com.shopflow.user_service.controller;

import com.shopflow.user_service.dto.UserDto;
import com.shopflow.user_service.security.Permission;
import com.shopflow.user_service.security.RequiresPermission;
import com.shopflow.user_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management — requires authentication")
@SecurityRequirement(name = "bearerAuth")   // tells Swagger all endpoints need JWT
public class UserController {

    private final UserService userService;

    // ── GET /users ────────────────────────────────────────────────────

    @GetMapping
    @RequiresPermission(Permission.USER_READ)
    @Operation(summary = "List all users in current tenant (paginated)")
    public ResponseEntity<Page<UserDto.UserResponse>> getUsers(
            @ModelAttribute UserDto.UserFilterParams params) {
        return ResponseEntity.ok(userService.getUsers(params));
    }

    // ── GET /users/me ─────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public ResponseEntity<UserDto.UserResponse> getMe(Authentication auth) {
        UUID currentUserId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(userService.getCurrentUser(currentUserId));
    }

    // ── GET /users/{id} ───────────────────────────────────────────────

    @GetMapping("/{id}")
    @RequiresPermission(Permission.USER_READ)
    @Operation(summary = "Get a user by ID")
    public ResponseEntity<UserDto.UserResponse> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    // ── POST /users ───────────────────────────────────────────────────

    @PostMapping
    @RequiresPermission(Permission.USER_CREATE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new user in current tenant")
    public ResponseEntity<UserDto.UserResponse> createUser(
            @Valid @RequestBody UserDto.CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.createUser(request));
    }

    // ── PUT /users/{id} ───────────────────────────────────────────────

    @PutMapping("/{id}")
    @RequiresPermission(Permission.USER_UPDATE)
    @Operation(summary = "Update a user's details")
    public ResponseEntity<UserDto.UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserDto.UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    // ── DELETE /users/{id} ────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @RequiresPermission(Permission.USER_DELETE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft delete a user")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    // ── POST /users/{id}/assign-role ──────────────────────────────────

    @PostMapping("/{id}/assign-role")
    @RequiresPermission(Permission.USER_ASSIGN_ROLE)
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<UserDto.UserResponse> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody UserDto.AssignRoleRequest request) {
        return ResponseEntity.ok(userService.assignRole(id, request));
    }
}