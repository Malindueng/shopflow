# ADR-001 — Multi-Tenancy Strategy

**Date:** Day 2  
**Status:** Accepted

---

## Context

ShopFlow is a multi-tenant B2B platform. Multiple businesses (tenants) share
the same infrastructure. We need to ensure complete data isolation — tenant A
must never see tenant B's data under any circumstance.

Three strategies were considered:

| Strategy | Description |
|---|---|
| Separate database | Each tenant gets their own PostgreSQL database |
| Schema per tenant | Each tenant gets their own schema within one database |
| Row-level (discriminator) | All tenants share tables; every row has a `tenant_id` column |

---

## Decision

**Row-level tenancy with Hibernate filters.**

Every tenant-scoped table has a `tenant_id UUID NOT NULL` column.
A Hibernate `@Filter` is applied to every entity so that all queries
automatically include `WHERE tenant_id = :tenantId` without requiring
developers to remember to add it manually.

The current tenant is stored in a `ThreadLocal` via `TenantContext`,
populated by `JwtAuthFilter` on every request.

---

## Consequences

**Good:**
- Simple to implement and reason about
- Single database, single schema — easy migrations with Flyway
- No connection pool explosion (separate-DB approach requires N pools)
- Works well at our current scale

**Bad / risks:**
- A bug in the Hibernate filter could leak cross-tenant data — mitigated
  by integration tests that explicitly verify tenant isolation
- Harder to give tenants their own backup/restore — acceptable for now
- Noisy-neighbour problem at extreme scale — acceptable until Series A

**Migration path:** If we need stronger isolation later, the row-level
approach can be migrated to schema-per-tenant by partitioning on `tenant_id`.

---

## Implementation

- `TenantContext` — `ThreadLocal<UUID>` holding current tenant ID
- `@TenantFilter` — Hibernate filter defined on every entity
- `TenantFilterAspect` — enables the filter on every JPA session
- `JwtAuthFilter` — extracts `tenantId` from JWT claims, sets `TenantContext`