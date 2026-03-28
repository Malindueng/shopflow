package com.shopflow.user_service.service;

import com.shopflow.user_service.domain.*;
import com.shopflow.user_service.dto.AuthDto;
import com.shopflow.user_service.exception.DuplicateEmailException;
import com.shopflow.user_service.exception.InvalidTokenException;
import com.shopflow.user_service.exception.TenantNotFoundException;
import com.shopflow.user_service.repository.RoleRepository;
import com.shopflow.user_service.repository.TenantRepository;
import com.shopflow.user_service.repository.UserRepository;
import com.shopflow.user_service.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository       userRepository;
    private final TenantRepository     tenantRepository;
    private final RoleRepository       roleRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtUtil              jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    // ── Register ─────────────────────────────────────────────────────

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        // 1. Create tenant from tenantName
        String slug = generateSlug(request.getTenantName());
        Tenant tenant = Tenant.builder()
                .name(request.getTenantName())
                .slug(slug)
                .build();
        tenant = tenantRepository.save(tenant);

        // 2. Check duplicate email within tenant
        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenant.getId())) {
            throw new DuplicateEmailException("Email already registered in this tenant");
        }

        // 3. Create user
        User user = User.builder()
                .tenantId(tenant.getId())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();
        user = userRepository.save(user);

        // 4. Assign TENANT_ADMIN role (first user in a tenant is always admin)
        Role adminRole = roleRepository.findByName(Role.RoleName.TENANT_ADMIN)
                .orElseThrow(() -> new IllegalStateException("TENANT_ADMIN role not seeded"));

        UserRole userRole = UserRole.builder()
                .id(new UserRole.UserRoleId(user.getId(), adminRole.getId()))
                .user(user)
                .role(adminRole)
                .tenantId(tenant.getId())
                .build();
        user.getUserRoles().add(userRole);
        user = userRepository.save(user);

        // 5. Generate tokens
        return buildAuthResponse(user, tenant);
    }

    // ── Login ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        // 1. Find tenant
        Tenant tenant = tenantRepository.findBySlug(request.getTenantSlug())
                .orElseThrow(() -> new TenantNotFoundException(
                        "Tenant not found: " + request.getTenantSlug()));

        // 2. Authenticate (throws BadCredentialsException if wrong password)
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail() + ":" + tenant.getId(), // namespaced principal
                        request.getPassword()
                )
        );

        // 3. Load user with roles
        User user = userRepository.findByEmailWithRoles(request.getEmail(), tenant.getId())
                .orElseThrow(() -> new TenantNotFoundException("User not found"));

        return buildAuthResponse(user, tenant);
    }

    // ── Refresh token ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthDto.AuthResponse refreshToken(AuthDto.RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        // 1. Validate JWT structure
        if (!jwtUtil.validateToken(token) || !jwtUtil.isRefreshToken(token)) {
            throw new InvalidTokenException("Invalid refresh token");
        }

        UUID userId = jwtUtil.extractUserId(token);

        // 2. Check token exists in Redis (not revoked)
        String redisKey = REFRESH_TOKEN_PREFIX + userId + ":" + token.substring(token.length() - 10);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))) {
            throw new InvalidTokenException("Refresh token not found or already used");
        }

        // 3. Load user
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));

        // 4. Rotate: delete old token, issue new pair
        redisTemplate.delete(redisKey);
        return buildAuthResponse(user, tenant);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private AuthDto.AuthResponse buildAuthResponse(User user, Tenant tenant) {
        String accessToken  = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        // Store refresh token in Redis with TTL
        String redisKey = REFRESH_TOKEN_PREFIX + user.getId()
                + ":" + refreshToken.substring(refreshToken.length() - 10);
        redisTemplate.opsForValue().set(
                redisKey,
                user.getId().toString(),
                Duration.ofMillis(jwtUtil.getRefreshTokenExpiryMs())
        );

        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getName().name())
                .toList();

        return AuthDto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900)   // 15 minutes in seconds
                .user(AuthDto.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .tenantId(tenant.getId())
                        .tenantSlug(tenant.getSlug())
                        .roles(roles)
                        .build())
                .build();
    }

    private String generateSlug(String tenantName) {
        String base = tenantName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        // Append short UUID to guarantee uniqueness
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}