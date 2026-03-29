package com.shopflow.user_service.service;

import com.shopflow.user_service.config.TenantContext;
import com.shopflow.user_service.domain.Role;
import com.shopflow.user_service.domain.User;
import com.shopflow.user_service.domain.UserRole;
import com.shopflow.user_service.dto.UserDto;
import com.shopflow.user_service.exception.DuplicateEmailException;
import com.shopflow.user_service.exception.UserNotFoundException;
import com.shopflow.user_service.mapper.UserMapper;
import com.shopflow.user_service.repository.RoleRepository;
import com.shopflow.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final RoleRepository  roleRepository;
    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;

    // ── GET /users (paginated + filtered) ────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserDto.UserResponse> getUsers(UserDto.UserFilterParams params) {
        UUID tenantId = TenantContext.getTenantId();

        // Build sort
        Sort sort = params.getSortDir().equalsIgnoreCase("asc")
                ? Sort.by(params.getSortBy()).ascending()
                : Sort.by(params.getSortBy()).descending();

        Pageable pageable = PageRequest.of(params.getPage(), params.getSize(), sort);

        // Fetch all users for this tenant (Hibernate filter handles tenant isolation)
        List<User> users = userRepository.findAllWithRoles(tenantId);

        // Apply in-memory filters (search, status, role)
        List<User> filtered = users.stream()
                .filter(u -> matchesSearch(u, params.getSearch()))
                .filter(u -> params.getStatus() == null || u.getStatus() == params.getStatus())
                .filter(u -> params.getRole() == null || hasRole(u, params.getRole()))
                .toList();

        // Manual pagination on filtered results
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), filtered.size());

        List<UserDto.UserResponse> content = start >= filtered.size()
                ? List.of()
                : userMapper.toResponseList(filtered.subList(start, end));

        return new PageImpl<>(content, pageable, filtered.size());
    }

    // ── GET /users/{id} ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserDto.UserResponse getUserById(UUID id) {
        User user = findUserInCurrentTenant(id);
        return userMapper.toResponse(user);
    }

    // ── GET /users/me ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserDto.UserResponse getCurrentUser(UUID currentUserId) {
        User user = userRepository.findByIdWithRoles(currentUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    // ── POST /users ───────────────────────────────────────────────────

    @Transactional
    public UserDto.UserResponse createUser(UserDto.CreateUserRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new DuplicateEmailException(
                    "Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .tenantId(tenantId)
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        user = userRepository.save(user);

        // Assign the requested role
        assignRoleToUser(user, request.getRole());

        return userMapper.toResponse(userRepository.findByIdWithRoles(user.getId()).orElseThrow());
    }

    // ── PUT /users/{id} ───────────────────────────────────────────────

    @Transactional
    public UserDto.UserResponse updateUser(UUID id, UserDto.UpdateUserRequest request) {
        User user = findUserInCurrentTenant(id);

        if (StringUtils.hasText(request.getFirstName())) {
            user.setFirstName(request.getFirstName());
        }
        if (StringUtils.hasText(request.getLastName())) {
            user.setLastName(request.getLastName());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    // ── DELETE /users/{id} (soft delete) ──────────────────────────────

    @Transactional
    public void deleteUser(UUID id) {
        User user = findUserInCurrentTenant(id);
        user.softDelete();
        userRepository.save(user);
        log.info("Soft deleted user {} in tenant {}", id, TenantContext.getTenantId());
    }

    // ── POST /users/{id}/assign-role ──────────────────────────────────

    @Transactional
    public UserDto.UserResponse assignRole(UUID userId, UserDto.AssignRoleRequest request) {
        User user = findUserInCurrentTenant(userId);
        assignRoleToUser(user, request.getRole());
        return userMapper.toResponse(
                userRepository.findByIdWithRoles(userId).orElseThrow());
    }

    // ── Private helpers ───────────────────────────────────────────────

    private User findUserInCurrentTenant(UUID userId) {
        UUID tenantId = TenantContext.getTenantId();
        // findByIdWithRoles uses JPQL — Hibernate @Filter still applies
        return userRepository.findByIdWithRoles(userId)
                .filter(u -> u.getTenantId().equals(tenantId))
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found with id: " + userId));
    }

    private void assignRoleToUser(User user, Role.RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(
                        "Role not found: " + roleName));

        // Remove existing role if present (one role per user for now)
        user.getUserRoles().removeIf(ur ->
                ur.getRole().getName() == roleName);

        UserRole userRole = UserRole.builder()
                .id(new UserRole.UserRoleId(user.getId(), role.getId()))
                .user(user)
                .role(role)
                .tenantId(user.getTenantId())
                .build();

        user.getUserRoles().add(userRole);
        userRepository.save(user);
    }

    private boolean matchesSearch(User user, String search) {
        if (!StringUtils.hasText(search)) return true;
        String q = search.toLowerCase();
        return user.getEmail().toLowerCase().contains(q)
                || user.getFirstName().toLowerCase().contains(q)
                || user.getLastName().toLowerCase().contains(q);
    }

    private boolean hasRole(User user, Role.RoleName roleName) {
        return user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName() == roleName);
    }
}