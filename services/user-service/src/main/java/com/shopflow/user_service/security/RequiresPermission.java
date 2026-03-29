package com.shopflow.user_service.security;

import java.lang.annotation.*;

/**
 * Type-safe alternative to @PreAuthorize.
 *
 * Usage:
 *   @RequiresPermission(Permission.USER_READ)
 *   public ResponseEntity<Page<UserResponse>> getUsers(...) { ... }
 *
 * Instead of the brittle string-based:
 *   @PreAuthorize("hasRole('TENANT_ADMIN')")
 *
 * Processed by PermissionEvaluator which checks the current user's
 * roles against RolePermissions.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    Permission[] value();
    boolean requireAll() default false; // false = any one permission is enough
}