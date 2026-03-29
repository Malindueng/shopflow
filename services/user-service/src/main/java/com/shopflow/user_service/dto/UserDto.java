package com.shopflow.user_service.dto;

import com.shopflow.user_service.domain.Role;
import com.shopflow.user_service.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserDto {

    // ── Requests ──────────────────────────────────────────────────

    @Data
    public static class CreateUserRequest {
        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;

        @NotNull(message = "Role is required")
        private Role.RoleName role;
    }

    @Data
    public static class UpdateUserRequest {
        private String firstName;
        private String lastName;
        private User.UserStatus status;
    }

    @Data
    public static class AssignRoleRequest {
        @NotNull(message = "Role is required")
        private Role.RoleName role;
    }

    // ── Responses ─────────────────────────────────────────────────

    @Data
    @Builder
    public static class UserResponse {
        private UUID id;
        private String email;
        private String firstName;
        private String lastName;
        private String fullName;
        private User.UserStatus status;
        private UUID tenantId;
        private List<String> roles;
        private Instant createdAt;
        private Instant updatedAt;
    }

    // ── Filter params (for GET /users query params) ───────────────

    @Data
    public static class UserFilterParams {
        private String search;           // searches email, firstName, lastName
        private User.UserStatus status;
        private Role.RoleName role;
        private int page = 0;
        private int size = 20;
        private String sortBy = "createdAt";
        private String sortDir = "desc";
    }
}