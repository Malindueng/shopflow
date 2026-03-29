package com.shopflow.user_service.security;

/**
 * Every permission in the system defined as an enum.
 * Grouped by resource for readability.
 *
 * Convention: RESOURCE_ACTION
 */
public enum Permission {

    // ── User management ───────────────────────────────────────────
    USER_READ,
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,
    USER_ASSIGN_ROLE,

    // ── Tenant management ─────────────────────────────────────────
    TENANT_READ,
    TENANT_UPDATE,
    TENANT_SUSPEND,

    // ── Product management ────────────────────────────────────────
    PRODUCT_READ,
    PRODUCT_CREATE,
    PRODUCT_UPDATE,
    PRODUCT_DELETE,

    // ── Order management ──────────────────────────────────────────
    ORDER_READ_OWN,       // customer: read their own orders
    ORDER_READ_ALL,       // admin/seller: read all orders in tenant
    ORDER_UPDATE_STATUS,

    // ── Analytics ─────────────────────────────────────────────────
    ANALYTICS_READ
}