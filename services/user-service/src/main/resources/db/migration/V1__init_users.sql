-- V1__init_users.sql
-- ShopFlow User Service — initial schema
-- Flyway runs this automatically on first startup

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── tenants ──────────────────────────────────────────────────────────
CREATE TABLE tenants (
                         id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                         name       VARCHAR(255) NOT NULL,
                         slug       VARCHAR(100) NOT NULL,
                         plan       VARCHAR(50)  NOT NULL DEFAULT 'FREE',
                         status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
                         created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                         updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                         deleted_at TIMESTAMP    NULL,

                         CONSTRAINT uq_tenants_slug UNIQUE (slug)
);

CREATE INDEX idx_tenants_slug   ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status);

-- ── users ─────────────────────────────────────────────────────────────
CREATE TABLE users (
                       id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       tenant_id  UUID         NOT NULL REFERENCES tenants(id),
                       email      VARCHAR(255) NOT NULL,
                       password   VARCHAR(255) NOT NULL,
                       first_name VARCHAR(100) NOT NULL,
                       last_name  VARCHAR(100) NOT NULL,
                       status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
                       created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                       created_by UUID         NULL,
                       deleted_at TIMESTAMP    NULL,

                       CONSTRAINT uq_users_email_tenant UNIQUE (email, tenant_id)
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_email     ON users(email);
CREATE INDEX idx_users_status    ON users(status);

-- ── roles ─────────────────────────────────────────────────────────────
CREATE TABLE roles (
                       id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       name        VARCHAR(50)  NOT NULL,
                       description VARCHAR(255) NULL,
                       created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

                       CONSTRAINT uq_roles_name UNIQUE (name)
);

INSERT INTO roles (name, description) VALUES
                                          ('TENANT_ADMIN', 'Full administrative access within a tenant'),
                                          ('SELLER',       'Can manage own products and view own orders'),
                                          ('CUSTOMER',     'Can browse and place orders');

-- ── user_roles ────────────────────────────────────────────────────────
CREATE TABLE user_roles (
                            user_id     UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id     UUID      NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                            tenant_id   UUID      NOT NULL REFERENCES tenants(id),
                            assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
                            assigned_by UUID      NULL,

                            PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id   ON user_roles(user_id);
CREATE INDEX idx_user_roles_tenant_id ON user_roles(tenant_id);

-- ── refresh_tokens ────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
                                id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token_hash VARCHAR(255) NOT NULL,
                                expires_at TIMESTAMP    NOT NULL,
                                revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP    NOT NULL DEFAULT NOW(),

                                CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);