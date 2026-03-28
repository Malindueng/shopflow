# ShopFlow — Database Schema Design

## Design Principles

1. **Row-level multi-tenancy** — `tenant_id` on every tenant-scoped table; enforced by Hibernate filter
2. **Soft deletes** — `deleted_at TIMESTAMP NULL`; never `DELETE` records that affect audit trails
3. **JPA auditing** — `created_at`, `updated_at`, `created_by` managed by `@EntityListeners(AuditingEntityListener.class)`
4. **Flyway migrations** — all DDL lives in versioned SQL files under `src/main/resources/db/migration/`
5. **Separate databases per service** — each microservice owns its schema; no cross-service joins

---

## Services → Databases

| Service | Database |
|---|---|
| user-service | `shopflow_users` |
| product-service | `shopflow_products` |
| order-service | `shopflow_orders` |
| payment-service | `shopflow_payments` |
| notification-service | `shopflow_notifications` |

---

## shopflow_users

### tenants

```sql
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,   -- used in subdomains / URLs
    plan        VARCHAR(50)  NOT NULL DEFAULT 'FREE',  -- FREE | PRO | ENTERPRISE
    status      VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | SUSPENDED | DELETED
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP    NULL
);

CREATE INDEX idx_tenants_slug   ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status);
```

### users

```sql
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    email        VARCHAR(255) NOT NULL,
    password     VARCHAR(255) NOT NULL,          -- bcrypt hash
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    status       VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by   UUID         NULL,
    deleted_at   TIMESTAMP    NULL,

    CONSTRAINT uq_users_email_tenant UNIQUE (email, tenant_id)
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email     ON users(email);
CREATE INDEX idx_users_status    ON users(status);
```

### roles

```sql
CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL UNIQUE,    -- TENANT_ADMIN | SELLER | CUSTOMER
    description VARCHAR(255) NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

INSERT INTO roles (name, description) VALUES
    ('TENANT_ADMIN', 'Full administrative access within a tenant'),
    ('SELLER',       'Can manage own products and view own orders'),
    ('CUSTOMER',     'Can browse and place orders');
```

### user_roles

```sql
CREATE TABLE user_roles (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    tenant_id  UUID NOT NULL REFERENCES tenants(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    assigned_by UUID NULL,

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id   ON user_roles(user_id);
CREATE INDEX idx_user_roles_tenant_id ON user_roles(tenant_id);
```

### refresh_tokens

```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,    -- SHA-256 hash of the actual token
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

---

## shopflow_products

### categories

```sql
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    parent_id   UUID         NULL REFERENCES categories(id),
    name        VARCHAR(255) NOT NULL,
    slug        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP    NULL,

    CONSTRAINT uq_category_slug_tenant UNIQUE (slug, tenant_id)
);

CREATE INDEX idx_categories_tenant_id ON categories(tenant_id);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
```

### products

```sql
CREATE TABLE products (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID           NOT NULL,
    seller_id    UUID           NOT NULL,           -- references users.id (cross-service, no FK)
    category_id  UUID           NULL REFERENCES categories(id),
    name         VARCHAR(500)   NOT NULL,
    slug         VARCHAR(500)   NOT NULL,
    description  TEXT           NULL,
    base_price   NUMERIC(12, 2) NOT NULL,
    status       VARCHAR(50)    NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | ARCHIVED
    search_vector TSVECTOR      NULL,               -- PostgreSQL full-text search
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by   UUID           NULL,
    deleted_at   TIMESTAMP      NULL,

    CONSTRAINT uq_product_slug_tenant UNIQUE (slug, tenant_id)
);

CREATE INDEX idx_products_tenant_id    ON products(tenant_id);
CREATE INDEX idx_products_seller_id    ON products(seller_id);
CREATE INDEX idx_products_category_id  ON products(category_id);
CREATE INDEX idx_products_status       ON products(status);
CREATE INDEX idx_products_search       ON products USING GIN(search_vector);
```

### product_variants

```sql
CREATE TABLE product_variants (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID           NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tenant_id  UUID           NOT NULL,
    sku        VARCHAR(100)   NOT NULL,
    name       VARCHAR(255)   NOT NULL,    -- e.g. "Red / Large"
    price      NUMERIC(12, 2) NOT NULL,
    attributes JSONB          NULL,        -- {"color":"red","size":"L"}
    created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_variant_sku_tenant UNIQUE (sku, tenant_id)
);

CREATE INDEX idx_variants_product_id ON product_variants(product_id);
```

### inventory

```sql
CREATE TABLE inventory (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id         UUID    NOT NULL UNIQUE REFERENCES product_variants(id),
    tenant_id          UUID    NOT NULL,
    quantity_available INTEGER NOT NULL DEFAULT 0,
    quantity_reserved  INTEGER NOT NULL DEFAULT 0,
    low_stock_threshold INTEGER NOT NULL DEFAULT 10,
    updated_at         TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_inventory_qty CHECK (quantity_available >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (quantity_reserved >= 0)
);

CREATE INDEX idx_inventory_tenant_id ON inventory(tenant_id);
```

### product_images

```sql
CREATE TABLE product_images (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    s3_key      VARCHAR(500) NOT NULL,
    cdn_url     VARCHAR(1000) NOT NULL,
    position    INTEGER      NOT NULL DEFAULT 0,
    is_primary  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);
```

---

## shopflow_orders

### addresses

```sql
CREATE TABLE addresses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    user_id      UUID         NOT NULL,
    line1        VARCHAR(255) NOT NULL,
    line2        VARCHAR(255) NULL,
    city         VARCHAR(100) NOT NULL,
    state        VARCHAR(100) NOT NULL,
    country      CHAR(2)      NOT NULL,   -- ISO 3166-1 alpha-2
    postal_code  VARCHAR(20)  NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_addresses_user_id   ON addresses(user_id);
CREATE INDEX idx_addresses_tenant_id ON addresses(tenant_id);
```

### orders

```sql
CREATE TABLE orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID           NOT NULL,
    customer_id      UUID           NOT NULL,
    address_id       UUID           NOT NULL REFERENCES addresses(id),
    status           VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    -- PENDING | CONFIRMED | PROCESSING | SHIPPED | DELIVERED | CANCELLED | REFUNDED
    subtotal         NUMERIC(12, 2) NOT NULL,
    tax              NUMERIC(12, 2) NOT NULL DEFAULT 0,
    shipping         NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total            NUMERIC(12, 2) NOT NULL,
    idempotency_key  VARCHAR(255)   NOT NULL UNIQUE,  -- prevents duplicate orders
    step_fn_execution_arn VARCHAR(500) NULL,           -- AWS Step Functions ARN
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_tenant_id   ON orders(tenant_id);
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status      ON orders(status);
```

### order_items

```sql
CREATE TABLE order_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   UUID           NOT NULL,
    variant_id   UUID           NOT NULL,
    seller_id    UUID           NOT NULL,
    product_name VARCHAR(500)   NOT NULL,   -- snapshot at time of order
    variant_name VARCHAR(255)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    subtotal     NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_order_item_qty CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id   ON order_items(order_id);
CREATE INDEX idx_order_items_seller_id  ON order_items(seller_id);
```

### order_status_history

```sql
CREATE TABLE order_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    status      VARCHAR(50)  NOT NULL,
    note        TEXT         NULL,
    changed_by  UUID         NULL,
    changed_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);
```

---

## shopflow_payments

### payments

```sql
CREATE TABLE payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID           NOT NULL,
    order_id             UUID           NOT NULL UNIQUE,
    stripe_payment_intent_id VARCHAR(255) NOT NULL UNIQUE,
    amount               NUMERIC(12, 2) NOT NULL,
    currency             CHAR(3)        NOT NULL DEFAULT 'USD',
    status               VARCHAR(50)    NOT NULL DEFAULT 'INITIATED',
    -- INITIATED | PROCESSING | SUCCEEDED | FAILED | REFUNDED
    failure_code         VARCHAR(100)   NULL,
    failure_message      TEXT           NULL,
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_tenant_id ON payments(tenant_id);
CREATE INDEX idx_payments_order_id  ON payments(order_id);
CREATE INDEX idx_payments_status    ON payments(status);
```

### refunds

```sql
CREATE TABLE refunds (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          UUID           NOT NULL REFERENCES payments(id),
    stripe_refund_id    VARCHAR(255)   NOT NULL UNIQUE,
    amount              NUMERIC(12, 2) NOT NULL,
    reason              VARCHAR(255)   NULL,
    status              VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
```

---

## shopflow_notifications

### notifications

```sql
CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    type        VARCHAR(100) NOT NULL,
    -- ORDER_CONFIRMED | PAYMENT_SUCCEEDED | ORDER_SHIPPED | etc.
    title       VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    read_at     TIMESTAMP    NULL,
    metadata    JSONB        NULL,    -- e.g. {"orderId":"...", "trackingNumber":"..."}
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_id   ON notifications(user_id);
CREATE INDEX idx_notifications_tenant_id ON notifications(tenant_id);
CREATE INDEX idx_notifications_read_at   ON notifications(read_at);
```

---

## ERD (Text Representation)

```
tenants ──< users ──< user_roles >── roles
   │
   └── (tenant_id on all tables below)

products >── product_variants ──< inventory
   │    └── product_images
   └── categories (self-referencing tree)

orders ──< order_items
  │    ──< order_status_history
  └── addresses

payments ──< refunds

notifications
```

---

## Cross-Service References (No Foreign Keys)

Since each service has its own database, cross-service references are stored as plain UUIDs with **no database-level foreign key**. Consistency is enforced at the application layer:

| Column | Owned By | Referenced In |
|---|---|---|
| `users.id` | user-service | order-service, payment-service, notification-service |
| `products.id` | product-service | order-service |
| `orders.id` | order-service | payment-service, notification-service |