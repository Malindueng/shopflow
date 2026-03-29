package com.shopflow.user_service.security;

import com.shopflow.user_service.domain.Role;
import com.shopflow.user_service.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AOP aspect that intercepts methods annotated with @RequiresPermission
 * and checks whether the current user has the required permission(s).
 *
 * Runs BEFORE the method executes — throws UnauthorizedException if
 * the check fails, preventing the method body from ever running.
 */
@Slf4j
@Aspect
@Component
public class PermissionEvaluator {

    @Before("@annotation(requiresPermission)")
    public void checkPermission(JoinPoint joinPoint,
                                RequiresPermission requiresPermission) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Not authenticated");
        }

        // Extract role names from Spring Security authorities
        Set<Role.RoleName> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))  // strip "ROLE_" prefix
                .map(Role.RoleName::valueOf)
                .collect(Collectors.toSet());

        Permission[] required = requiresPermission.value();
        boolean requireAll    = requiresPermission.requireAll();

        boolean hasPermission;
        if (requireAll) {
            // User must have ALL listed permissions
            hasPermission = Arrays.stream(required)
                    .allMatch(p -> userRoles.stream()
                            .anyMatch(role -> RolePermissions.hasPermission(role, p)));
        } else {
            // User must have AT LEAST ONE of the listed permissions
            hasPermission = Arrays.stream(required)
                    .anyMatch(p -> userRoles.stream()
                            .anyMatch(role -> RolePermissions.hasPermission(role, p)));
        }

        if (!hasPermission) {
            log.warn("Access denied for user {} — required: {} requireAll: {}",
                    auth.getName(), Arrays.toString(required), requireAll);
            throw new UnauthorizedException(
                    "You do not have permission to perform this action");
        }
    }
}