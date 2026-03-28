# ShopFlow — Multi-Tenant B2B E-Commerce Platform

A production-grade, multi-tenant B2B e-commerce platform built with Spring Boot microservices and React.

![CI/CD](https://github.com/yourname/shopflow/actions/workflows/backend-ci.yml/badge.svg)

## Live Demo
> Coming in Week 7 after AWS deployment.

---

## Architecture Overview

```
                          ┌─────────────────────────────────────┐
                          │         React + TypeScript          │
                          │    (Storefront / Seller / Admin)    │
                          └──────────────┬──────────────────────┘
                                         │ HTTPS
                          ┌──────────────▼──────────────────────┐
                          │        AWS CloudFront (CDN)          │
                          └──────────────┬──────────────────────┘
                                         │
                          ┌──────────────▼──────────────────────┐
                          │     Application Load Balancer        │
                          └──────────────┬──────────────────────┘
                                         │
                          ┌──────────────▼──────────────────────┐
                          │   Spring Cloud Gateway (Port 8080)   │
                          │  JWT Validation · Rate Limiting · CORS│
                          └──┬───────┬───────┬───────┬──────────┘
                             │       │       │       │
               ┌─────────────▼─┐ ┌───▼───┐ ┌▼──────┐ ┌▼──────────────┐
               │ user-service  │ │product│ │ order │ │   payment     │
               │    :8081      │ │ :8082 │ │ :8083 │ │   service     │
               │ Auth·RBAC     │ │Catalog│ │ Cart  │ │   :8084       │
               │ Multi-tenant  │ │Search │ │Checkout│ │   Stripe      │
               └───────────────┘ └───────┘ └───────┘ └───────────────┘
                                                              │
                                               ┌─────────────▼──────────┐
                                               │  notification-service  │
                                               │        :8085           │
                                               │  SQS · Kafka · WS      │
                                               └────────────────────────┘

Data Layer:  PostgreSQL (RDS)  ·  Redis (ElastiCache)  ·  S3 + CloudFront
Events:      SQS (order/payment)  ·  Kafka/MSK (high-volume streams)
Workflow:    AWS Step Functions (order saga)
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, Spring Data JPA |
| Frontend | React 18, TypeScript, Redux Toolkit, RTK Query, TailwindCSS |
| Database | PostgreSQL 15 (RDS), Redis (ElastiCache) |
| Messaging | AWS SQS, Apache Kafka (MSK) |
| Infrastructure | AWS ECS Fargate, RDS, ElastiCache, S3, CloudFront, Lambda |
| IaC | Terraform |
| CI/CD | GitHub Actions → ECR → ECS |
| Observability | AWS X-Ray, CloudWatch, Micrometer, Zipkin |

---

## Key Features

- **Multi-tenancy** — row-level tenant isolation with Hibernate filters; every table carries `tenant_id`
- **RBAC** — three roles: `TENANT_ADMIN`, `SELLER`, `CUSTOMER` with `@PreAuthorize` enforcement
- **Real-time dashboard** — live revenue, live order count, live notifications via WebSocket (STOMP)
- **Event-driven** — SQS for order/payment events; Kafka for high-volume inventory/status streams
- **Order saga** — AWS Step Functions orchestrates distributed transaction across 3 services
- **Stripe payments** — PaymentIntent flow, webhook handling, refunds, invoices
- **CI/CD** — GitHub Actions: test → build → push to ECR → rolling ECS deploy, Lighthouse CI gate on frontend
- **IaC** — entire AWS stack in Terraform, repeatable across `dev` / `staging` / `prod` workspaces

---

## Monorepo Structure

```
shopflow/
├── services/
│   ├── api-gateway/          Spring Cloud Gateway
│   ├── user-service/         Auth, RBAC, multi-tenancy
│   ├── product-service/      Catalog, inventory, search
│   ├── order-service/        Cart, checkout, Step Functions
│   ├── payment-service/      Stripe integration, invoices
│   └── notification-service/ SQS, Kafka consumer, WebSocket
├── frontend/                 React + TypeScript + Redux Toolkit
├── infra/                    Terraform (VPC, ECS, RDS, etc.)
└── docs/                     ADRs, schema, API contracts, runbooks
```

---

## Local Setup (3 commands)

> Docker required. See [docs/local-setup.md](docs/local-setup.md) for prerequisites.

```bash
git clone https://github.com/yourname/shopflow.git
cd shopflow
docker compose up
```

All services start on their respective ports. API Gateway is available at `http://localhost:8080`.

---

## API Documentation

Each service exposes Swagger UI:

| Service | URL |
|---|---|
| API Gateway | http://localhost:8080/swagger-ui.html |
| User Service | http://localhost:8081/swagger-ui.html |
| Product Service | http://localhost:8082/swagger-ui.html |
| Order Service | http://localhost:8083/swagger-ui.html |
| Payment Service | http://localhost:8084/swagger-ui.html |
| Notification Service | http://localhost:8085/swagger-ui.html |

---

## Documentation

- [Database Schema](docs/database-schema.md)
- [Local Setup Guide](docs/local-setup.md)
- [API Contracts](docs/api-contracts.md)
- [Architecture Decision Records](docs/adr/)
- [Performance Results](docs/performance-results.md)
- [Runbooks](docs/runbooks/)

---

## Monthly AWS Cost Estimate

> To be added after Week 5 deployment.

---

## Author

Built as a portfolio project demonstrating production-grade full-stack engineering.