package com.shopflow.user_service.config;

import java.util.UUID;

/**
 * Holds the current tenant ID for the duration of a request.
 * Populated by JwtAuthFilter after validating the JWT.
 * Cleared at the end of every request to prevent thread-pool leaks.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}