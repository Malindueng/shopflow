package com.shopflow.user_service.security;

import com.shopflow.user_service.domain.Role;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for which permissions each role has.
 *
 * Used by @PreAuthorize expressions and the custom @RequiresPermission
 * annotation processor.
 */
public final class RolePermissions {

    private RolePermissions() {}

    private static final Map<Role.RoleName, Set<Permission>> ROLE_PERMISSION_MAP = Map.of(

            Role.RoleName.TENANT_ADMIN, EnumSet.of(
                    Permission.USER_READ,
                    Permission.USER_CREATE,
                    Permission.USER_UPDATE,
                    Permission.USER_DELETE,
                    Permission.USER_ASSIGN_ROLE,
                    Permission.TENANT_READ,
                    Permission.TENANT_UPDATE,
                    Permission.PRODUCT_READ,
                    Permission.PRODUCT_CREATE,
                    Permission.PRODUCT_UPDATE,
                    Permission.PRODUCT_DELETE,
                    Permission.ORDER_READ_ALL,
                    Permission.ORDER_UPDATE_STATUS,
                    Permission.ANALYTICS_READ
            ),

            Role.RoleName.SELLER, EnumSet.of(
                    Permission.PRODUCT_READ,
                    Permission.PRODUCT_CREATE,
                    Permission.PRODUCT_UPDATE,
                    Permission.PRODUCT_DELETE,
                    Permission.ORDER_READ_ALL,
                    Permission.ORDER_UPDATE_STATUS
            ),

            Role.RoleName.CUSTOMER, EnumSet.of(
                    Permission.PRODUCT_READ,
                    Permission.ORDER_READ_OWN
            )
    );

    public static Set<Permission> getPermissions(Role.RoleName role) {
        return ROLE_PERMISSION_MAP.getOrDefault(role, EnumSet.noneOf(Permission.class));
    }

    public static boolean hasPermission(Role.RoleName role, Permission permission) {
        return getPermissions(role).contains(permission);
    }
}