# Market Tool — Tunisian Stock Market Data Platform

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.13-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.0.0-brightgreen.svg)](https://spring.io/projects/spring-cloud)
[![Angular](https://img.shields.io/badge/Angular-22-red.svg)](https://angular.dev)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](#license)

**Author:** Fares Ben Slama

## Overview

Market Tool is a microservices platform that scrapes, enriches, and serves Tunisian Stock Exchange (BVMT) data, wrapped in a full SaaS shell — authentication, user accounts, billing/subscriptions, and notifications (email/SMS). It is built as 10 independent Spring Boot services registered with a Netflix Eureka discovery server, plus an Angular frontend that is currently scaffolded but not yet implemented.

> This document reflects the actual state of the codebase as inspected, not just the original plan. It calls out work-in-progress and known issues explicitly (see [Known Issues](#known-issues--architectural-debt)) rather than presenting the system as finished.

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Backend Modules](#backend-modules)
- [Inter-Service Communication](#inter-service-communication)
- [Frontend](#frontend-angular)
- [Testing](#testing)
- [Setup & Installation](#setup--installation)
- [API Usage Examples](#api-usage-examples)
- [Known Issues / Architectural Debt](#known-issues--architectural-debt)
- [Roadmap](#roadmap)
- [License](#license)

## Architecture

```
┌───────────────────────────────┐
│   Angular Frontend (scaffold  │
│   only, not implemented)      │
│   tunindex-market-tool :4200  │
└───────────────┬────────────────┘
                 │ HTTP (planned — no calls wired up yet)
                 ▼
┌──────────────────────────────────────────────────────┐
│                     api  — :8082                       │
│  Security gateway: Google OAuth2 login + opaque token   │
│  resource server, user/account management,              │
│  stock-data proxy to collector                          │
└───────┬────────────────────────────────────────────────┘
        │ load-balanced WebClient (Eureka service names)
        │ + shared "X-API-Key" header on every internal call
        │
        ├──► collector :8081 ─────────────► stockanalysis.com (scraping)
        ├──► mailing-service :8085 ───────► Gmail SMTP
        ├──► sms-service :8086 ───────────► Twilio API
        ├──► recaptcha-service :8087 ─────► Google reCAPTCHA v3
        ├──► payment-service :8088* ──────► Konnect payment gateway (Tunisia)
        ├──► billing-service :8088* ──────► Konnect + invoicing/subscriptions
        └──► user-subscription-service :8089 (incomplete, see Known Issues)

  * payment-service and billing-service both hardcode server.port=8088 —
    they cannot run at the same time with the checked-in config.

              ┌───────────────────────────────┐
              │   discovery-server (Eureka)    │
              │            :8761                │
              │  All 9 business services         │
              │  register here                   │
              └───────────────────────────────┘

Each business service owns its own PostgreSQL database (localhost:5432,
ddl-auto=update, no Flyway/Liquibase):
tunindex-api · tunindex-collector · tunindex-mailing · tunindex-sms ·
tunindex-recaptcha · tunindex-payment (shared by payment- & billing-service)
· tunindex-user-subscription
```

`common` is a shared library jar (no port, no controllers) that every service depends on.

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.5.13 |
| Microservices | Spring Cloud (Eureka, LoadBalancer) | 2025.0.0 |
| Database | PostgreSQL | localhost, one DB per service |
| ORM | JPA / Hibernate | via Spring Boot parent |
| Build | Maven | multi-module reactor |
| Testing | JUnit 5, Mockito, AssertJ | via Spring Boot parent |
| HTML parsing | JSoup | 1.17.2 |
| Browser automation | Selenium + WebDriverManager | 4.31.0 / 5.9.2 |
| API docs | springdoc-openapi (Swagger UI) | 2.1.0 |
| Auth | Spring Security, Google OAuth2, custom opaque tokens | — |
| Bot protection | Google reCAPTCHA v3 | — |
| Email | Jakarta Mail via Gmail SMTP | — |
| SMS | Twilio SDK + libphonenumber | 10.7.2 / 8.12.38 |
| Payments | Konnect (Tunisian payment gateway) | — |
| PDF/CSV export | iText, OpenCSV | 5.5.13.3 / 5.7.1 |
| Frontend framework | Angular (standalone components) | 22.1 |
| Frontend testing | Vitest | 4.0.8 |

## Project Structure

```
market-tool/
├── backend/
│   ├── pom.xml                      # Maven reactor parent, 10 modules
│   ├── common/                      # Shared library (entities, DTOs, exceptions, pagination)
│   ├── discovery-server/            # Eureka registry :8761
│   ├── collector/                   # Scraper + Graham valuation :8081
│   ├── api/                         # Security gateway / BFF :8082
│   ├── mailing-service/             # Transactional + newsletter email :8085
│   ├── sms-service/                 # Twilio SMS :8086
│   ├── recaptcha-service/           # reCAPTCHA v3 verification :8087
│   ├── payment-service/             # Konnect payments :8088
│   ├── billing-service/             # Payments + invoices + subscriptions :8088
│   ├── user-subscription-service/   # Subscriptions split-out (WIP) :8089
│   └── original-backup/             # Old snapshot of the backend — not live code
└── frontend/
    └── tunindex-market-tool/        # Angular 22 app — scaffold only, unimplemented
        └── src/app/
            ├── core/                # guards, interceptors, models, services (all stubs)
            ├── features/            # auth, dashboard, stocks, users, watchlist, analysis,
            │                        # account-management, market (all stub components)
            └── shared/components/   # navbar, sidebar, data-table, pagination, etc. (stubs)
```

## Backend Modules

### common (shared library)

No `@SpringBootApplication`, no controllers — pulled in as a dependency by every other module.

- **Base entities**: `AbstractEntity` (id, audit dates), `BaseUser` (used by `api`'s `User`)
- **Embedded value objects**: `PriceData`, `VolumeData`, `FundamentalData`, `RatiosData`, `TechnicalData`, `AnalystData`, `CalculatedValues`, `Address` (used by `collector`'s `Stock`)
- **Pagination framework**: `PaginationAndFilteringDto` (the standard request body for every `/filter` endpoint system-wide) and `PagedResponse<T>` (the standard response wrapper)
- **Constants**: API route roots, and a hardcoded reference table of all **73 BVMT-listed tickers** (symbol, company name, stockanalysis.com URL, ownership type, industry) that `collector` iterates over
- **Exceptions**: a shared hierarchy (`EntityNotFoundException`, `InvalidEntityException`, `DataFetchException`, `ParseException`, `RecaptchaException`, `SmsServiceException`, …) plus a centralized `ErrorCodes` enum spanning market-data, payment, subscription, and auth error codes

### collector — port 8081

Scrapes stockanalysis.com for the 73 BVMT tickers, parses/normalizes/enriches the data, computes Graham valuation metrics, and persists to `tunindex-collector`. No user-facing endpoints — internal only.

- **Pipeline**: `DataOrchestratorImpl` runs fetch → parse → normalize → enrich → upsert, parallelized 10-wide via Reactor. Triggered on startup and re-run every `market-tool.scheduler.interval-minutes` (default 30) by a self-managed background thread (not `@Scheduled`).
- **Provider**: `StockAnalysisProvider` fetches 3 pages per symbol from stockanalysis.com and parses them with JSoup.
- **Graham calculation**: `GrahamCalculatorImpl` computes fair value as `√(22.5 × EPS × BVPS)` and the resulting margin-of-safety percentage.
- **Anti-detection layer**: a bespoke stealth-scraping package (`webscraping/`) with rotating user-agents/fingerprints, rate limiting, retry backoff, a TTL cache, and an optional Selenium/Chrome path.
- **Endpoints** (`/internal/stock-data/**`, API-key gated): find by symbol / symbol+exchange, `POST /filter` (paginated), sector/ownership statistics, `PUT /refresh/{symbol}`.
- **Tests**: `StockControllerIntegrationTest`, `StockRepositoryTest`, `StockServiceImplTest`.

### api — port 8082 (largest module, 93 files)

The user-facing gateway: authentication, user/account management, and a thin proxy to `collector` for stock data. Owns `tunindex-api`.

- **Security**: full Spring Security chain — Google OAuth2 login *and* a custom opaque-token resource server (own `UnifiedToken` table covering OAuth2 access/refresh, password-reset, and 2FA tokens with IP/user-agent hash binding). Custom filter chain: input sanitizer → rate limiter → OAuth2 filter, plus reCAPTCHA and IP-hash utilities.
- **Endpoints**: `/auth/authenticate` (password, token-check, and refresh all multiplexed through one endpoint), `/auth/google/login-url`, `/users/**` (profile, password change, lookup, paginated listing), `/accounts/management/**` (admin/user creation, account lock toggle, deletion), `/api/v1/stocks/**` (proxied from collector).
- **Internal endpoints** consumed by other services: mailing/SMS recipient lookups, payment user-info lookup, 2FA generate/verify/resend, password reset.
- **Tests**: none present.

### mailing-service — port 8085

Centralized transactional + newsletter email via Gmail SMTP. Owns `tunindex-mailing`.

- **Endpoints**: `/internal/email/**` (send-2fa, send-html, send-simple, newsletter fan-out) is what other services actually call; `/api/newsletter/**` mirrors this for direct use.
- `NewsletterServiceImpl` pulls recipient lists from `api`'s internal endpoints before bulk-sending.
- **Tests**: the best-covered service — `EmailNewsletterControllerTest`, `InternalEmailControllerTest`, `EmailLogRepositoryTest`, `EmailServiceTest`, `NewsletterServiceTest`.

### sms-service — port 8086

SMS via Twilio, phone validation via `libphonenumber`. Owns `tunindex-sms`.

- **Endpoints**: `/internal/sms/**` (send, newsletter fan-out by role/user/email).
- ⚠️ `PhoneNumberUtil` region defaults to `"US"` — likely wrong for Tunisian `+216` numbers (see [Known Issues](#known-issues--architectural-debt)).
- **Tests**: none present.

### recaptcha-service — port 8087 (smallest, 9 files)

Centralizes Google reCAPTCHA v3 server-side verification so other services don't duplicate the Google API call. Owns `tunindex-recaptcha` (configured, effectively unused — no entities).

- **Endpoint**: `POST /internal/recaptcha/validate` — checks score (≥0.7 default), action match, and hostname allowlist.
- Validation is **skipped entirely** when the active Spring profile is `dev` or `test`.
- **Tests**: none present.

### payment-service — port 8088

Konnect (Tunisian payment gateway) integration: create payments, check status, webhooks, refunds. Owns `tunindex-payment`.

- `KonnectPaymentGateway` builds Konnect API requests, verifies webhook signatures via HMAC-SHA256, maps Konnect statuses to an internal `PaymentStatus` enum, and calls `mailing-service` directly to send receipt emails.
- **Endpoints**: `/api/payments/**` (create, status, refund, initiate, filter, statistics), `/internal/payments/webhook/konnect`, `/internal/refund/**`.
- **Tests**: `PaymentTransactionRepositoryTest`, `RefundRepositoryTest`, `KonnectPaymentGatewayTest`, `PaymentTransactionServiceImplTest`, `RefundServiceImplTest`.

### billing-service — port 8088 (largest of the payment family, 106 files)

A **superset of payment-service**: duplicates its entire payment/refund code and adds Invoicing, Promo Codes, Subscription Plans, User Subscriptions, and Auto-Renewal. See [Known Issues](#known-issues--architectural-debt) — this looks like an in-progress split that duplicated rather than replaced `payment-service`.

- **Additional entities**: `Invoice`, `PromoCode`, `SubscriptionPlan`, `UserSubscription`.
- **Additional endpoints**: `/api/invoices/**` (including PDF/CSV export via iText/OpenCSV), `/api/subscription-plans/**`, `/api/user-subscriptions/**`, `/api/auto-renewal/**`, plus a public `/api/refunds/**` that payment-service lacks.
- **Auto-renewal**: `KonnectAutoRenewalServiceImpl` is the only real `@Scheduled` job in the whole payment family — runs daily at 1 AM, renews subscriptions expiring within 1 day, retries up to 3 times before expiring the subscription. The actual re-charge step is a placeholder (`paymentSuccess = true // Replace with actual payment call`) — auto-renewal doesn't really charge the card yet.
- **Tests**: none present.

### user-subscription-service — port 8089 (28 files)

A further, apparently incomplete split-out of just the subscription-plan/user-subscription slice of `billing-service` — entities, controllers, and DTOs are near-identical copies with a different package name.

- ⚠️ `AutoRenewalController` injects an `AutoRenewalService`, but **no implementation of that interface exists in this module** — as configured, this service fails to start (`NoSuchBeanDefinitionException`).
- `spring.application.name` has a typo: `user-susbscription-service`.
- **Tests**: none present.

### discovery-server — port 8761 (1 file)

Plain Netflix Eureka registry. `register-with-eureka=false`, `fetch-registry=false` (it only serves the registry), self-preservation disabled, fast eviction — tuned for single-node dev use, not HA. No tests.

## Inter-Service Communication

- **Discovery**: every business service is `@EnableDiscoveryClient` against Eureka at `discovery-server:8761`.
- **Calls**: all internal service-to-service calls use a Spring Cloud `@LoadBalanced` `WebClient.Builder`, addressing peers by Eureka service name (e.g. `http://collector-service`). No Feign, no plain `RestTemplate` for internal calls.
- **Internal auth**: a static shared secret sent as an `X-API-Key` header, checked manually in every internal controller. No mTLS or OAuth2 between services.
- **User-facing auth**: only `api` has Spring Security + OAuth2; every other service trusts the shared API key alone.

## Frontend (Angular)

`frontend/tunindex-market-tool` is an Angular 22 app generated with the CLI and **not yet implemented**. The folder layout (`core/{guards,interceptors,models,services}`, `features/{auth,dashboard,stocks,users,watchlist,analysis,account-management,market}`, `shared/components/*`) mirrors the backend's domains and gives a clear sense of the planned feature set, but as of this writing:

- `app.routes.ts` is an empty `Routes` array.
- Every model file in `core/models/` is empty.
- Every service/guard/interceptor/component is a bare stub (e.g. `export class Auth {}`), including one that uses a non-existent `@Service()` decorator instead of `@Injectable()`.
- `app.html` is still the default `ng new` welcome page.

In short: the scaffolding (component boundaries, routing structure, test files) is in place; none of the actual UI or API integration has been written yet.

## Testing

| Module | Test coverage |
|---|---|
| common | `TestConfig` only (pure library) |
| collector | Controller integration test, repository test, service unit test |
| api | **None** |
| mailing-service | Controller, repository, and service tests (best-covered service) |
| sms-service | **None** |
| recaptcha-service | **None** |
| payment-service | Repository, gateway, and service tests |
| billing-service | **None** |
| user-subscription-service | **None** |
| discovery-server | **None** (nothing to test) |
| frontend | Every component/service has a `.spec.ts`, but they're the default Angular CLI "should create" stubs — no real assertions yet |

Run backend tests per module:

```bash
cd backend/collector
mvn test

cd backend/mailing-service
mvn test

cd backend/payment-service
mvn test
```

Run the whole reactor:

```bash
cd backend
mvn clean test
```

## Setup & Installation

### Prerequisites

- Java 17, Maven 3.8+
- PostgreSQL (one database per service, see below)
- Node.js + npm (for the frontend)
- A `backend/.env` with the OAuth2/security values `ApiApplication` expects (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `IP_SALT`, `TRUSTED_PROXIES`) — see `backend/.env` (gitignored, not committed)

### Databases

Create one Postgres database per service that needs one (all on `localhost:5432`, `ddl-auto=update` will create the schema on first boot):

```sql
CREATE DATABASE "tunindex-api";
CREATE DATABASE "tunindex-collector";
CREATE DATABASE "tunindex-mailing";
CREATE DATABASE "tunindex-sms";
CREATE DATABASE "tunindex-recaptcha";
CREATE DATABASE "tunindex-payment";           -- shared by payment-service & billing-service
CREATE DATABASE "tunindex-user-subscription";
```

### Start order

```bash
# 1. Eureka — everything else registers here
cd backend/discovery-server
mvn spring-boot:run

# 2. Core services
cd backend/collector  && mvn spring-boot:run
cd backend/api        && mvn spring-boot:run

# 3. Supporting services
cd backend/mailing-service    && mvn spring-boot:run
cd backend/sms-service        && mvn spring-boot:run
cd backend/recaptcha-service  && mvn spring-boot:run

# 4. Billing — pick ONE, both hardcode port 8088
cd backend/payment-service && mvn spring-boot:run
# or
cd backend/billing-service && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8090

# 5. user-subscription-service currently fails to start as-is
#    (missing AutoRenewalService bean) — needs a fix before running.
```

Verify: Eureka dashboard at http://localhost:8761, Swagger UI on each documented service at `http://localhost:<port>/swagger-ui.html`.

### Frontend (scaffold only)

```bash
cd frontend/tunindex-market-tool
npm install
npm start   # ng serve — shows the default Angular welcome page, no app functionality yet
```

## API Usage Examples

```bash
# Get a stock by symbol (via the api gateway, proxied to collector)
curl http://localhost:8082/api/v1/stocks/symbol/BH

# Filter stocks
curl -X POST http://localhost:8082/api/v1/stocks/filter \
  -H "Content-Type: application/json" \
  -d '{
    "page": 1,
    "size": 10,
    "filters": { "sector": "FINANCIALS", "undervalued": "true" }
  }'

# Trigger a data refresh for one symbol
curl -X PUT http://localhost:8082/api/v1/stocks/refresh/BH
```

## Known Issues / Architectural Debt

These were found while mapping the codebase and are worth fixing before relying on this system in production:

1. **payment-service vs billing-service duplication** — both declare the same package (`com.tunindex.market_tool.payment`), duplicate entities/DTOs/validators, and both hardcode `server.port=8088`, so they cannot run simultaneously as configured. This looks like an in-progress split of a monolithic payment service that was never finished or cleaned up.
2. **user-subscription-service is not startable** — `AutoRenewalController` depends on an `AutoRenewalService` bean that has no implementation in this module.
3. **Auto-renewal doesn't actually charge the card** — `KonnectAutoRenewalServiceImpl.processSingleRenewal` has a placeholder `paymentSuccess = true` instead of a real payment call.
4. **Secrets are committed in plaintext** in several `application.properties` files (Gmail SMTP app password, Twilio credentials, reCAPTCHA secret key, and a shared internal `X-API-Key` value used by every service). These should move to environment variables or a secrets manager and be rotated.
5. **No schema migration tool** — every service uses `ddl-auto=update`; there's no Flyway/Liquibase, so schema drift between environments isn't tracked.
6. **sms-service phone validation defaults to region "US"**, which will likely mis-validate Tunisian `+216` numbers.
7. **Frontend is unimplemented** — the Angular app is CLI scaffolding only; no routes, models, or API integration exist yet.
8. **Test coverage is uneven** — only `collector`, `mailing-service`, and `payment-service` have real tests; `api`, `sms-service`, `recaptcha-service`, `billing-service`, and `user-subscription-service` have none.

## Roadmap

- Resolve the payment-service / billing-service / user-subscription-service split (pick one and retire the others)
- Wire up real payment re-charging in auto-renewal
- Externalize all secrets to environment variables
- Add Flyway/Liquibase migrations
- Implement the Angular frontend against the `api` gateway
- Add an API Gateway (Spring Cloud Gateway) in front of `api`
- Add circuit breakers (Resilience4j) and distributed tracing (Zipkin)
- Add metrics monitoring (Prometheus + Grafana)

## License

MIT License — Copyright (c) 2026 Fares Ben Slama
