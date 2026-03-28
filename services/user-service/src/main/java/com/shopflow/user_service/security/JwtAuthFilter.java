package com.shopflow.user_service.security;

import com.shopflow.user_service.config.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Runs once per request. Extracts and validates the JWT from the
 * Authorization header, then:
 *   1. Populates Spring SecurityContext (so @PreAuthorize works)
 *   2. Sets TenantContext (so Hibernate tenant filter works)
 *
 * TenantContext is always cleared in the finally block to prevent
 * thread-pool leaks between requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Add correlation ID for distributed tracing (populated by API Gateway later)
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null) {
            org.slf4j.MDC.put("correlationId", correlationId);
        }

        try {
            String token = extractToken(request);

            if (token != null && jwtUtil.validateToken(token) && jwtUtil.isAccessToken(token)) {
                UUID userId   = jwtUtil.extractUserId(token);
                UUID tenantId = jwtUtil.extractTenantId(token);
                List<String> roles = jwtUtil.extractRoles(token);

                // 1. Set tenant context for Hibernate filter
                TenantContext.setTenantId(tenantId);
                org.slf4j.MDC.put("tenantId", tenantId.toString());

                // 2. Build authorities from roles
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                // 3. Set Spring Security context
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId.toString(),  // principal = userId string
                                null,
                                authorities
                        );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user {} for tenant {}", userId, tenantId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // Always clear to prevent leaks between requests on the same thread
            TenantContext.clear();
            org.slf4j.MDC.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}