# AGENTS.md

> **Documentation rule.** Every change that affects behaviour, configuration, API, build or deployment MUST
> update `README.md` and the relevant pages in `docs/wiki/` in the same commit / pull request. A change is not
> done until the docs match the code.

Instructions for AI coding agents working in this repository.

## Project overview

IGaming Affiliate Program: a self-hosted affiliate program for casinos and sportsbooks. Kotlin 2.3 + Spring Boot 4
(MVC), plain SQL over `JdbcClient`, Flyway, RabbitMQ (Spring AMQP), a gRPC client of Currency-Exchange-Service,
and a Vue 3 SPA in `frontend/` served from the same jar.

- It learns about players only from four RabbitMQ events (`register.events`, `invoice.events`, `spin.events`,
  `bet.events`); the contract is `docs/wiki/Events.md` with JSON Schemas in `docs/events/`.
- The only service it calls is Currency-Exchange-Service (`currency.v1.CurrencyService/ConvertUnits`, contract in
  `src/main/proto/currency/v1/currency.proto`), and only for amounts outside `PARTNER_CURRENCY`.
- Docs: `README.md` and `docs/wiki/*.md` (the GitHub Wiki source).

## Build and test

Requirements: JDK 21, Node 22, Docker (the tests use Testcontainers for PostgreSQL and RabbitMQ).

```bash
./gradlew test                              # the whole suite
cd frontend && npm ci && npm run build      # vue-tsc type-check + Vite build into src/main/resources/static
./gradlew bootJar                           # build/libs/affiliate-app.jar
docker compose up --build                   # the full local stack
```

All of these must pass before you consider a change done.

## Layout

Root package `io.github.nekzabirov.affiliate`, flat by concern; dependencies point `rest -> services -> db / clients
/ events`:

    AffiliateApplication.kt   main + launchers (`app` default, `schema` runs Flyway and exits)
    config/     AppProperties (the app's own env, validated at boot), SPA, JSON, HTTP client, scheduling, RabbitMQ, gRPC
    model/      data classes and enums
    db/         one store per table over JdbcClient (+ StatsStore for analytics reads)
    events/     wire records, the strict envelope reader, the consumer, one handler per route
    services/   one class per concern; Money (nano units), PostbackDispatcher, Maintenance (the daily job)
    postback/   PostbackAdapter strategy + registry, KeitaroAdapter, UrlGuard (SSRF), GuardedDnsResolver
    clients/    CurrencyClient (Currency-Exchange-Service)
    rest/       controllers, request/response records, ApiExceptionHandler, SessionFilter, TraceFilter, RateLimiter
    errors/     DomainException(code, status) + subclasses

No ports-and-adapters layers, no use-case classes, no mediator, no CQRS, no JPA. One top-level class per file.

## Conventions observed in the code

- English everywhere. Comments explain why, never what. No TODOs, no stubs, no emojis.
- Plain SQL; never `SELECT *`. A counter moves inside one statement (`x = x + :d`, `ON CONFLICT`,
  `UPDATE ... WHERE balance >= :a`).
- Money is a `Long` of nano units (1e-9) of `PARTNER_CURRENCY` in every table; REST speaks decimal strings
  (`Money.format`, `Money.parsePositive`, `Money.parseNonNegative`). Percent = `round_half_up(amount * p / 100)`.
- Never call another service (the currency service, a tracker, DNS) inside a database transaction.
- Refusals are `DomainException` subclasses with a stable `code`; the code is contract, never rename one.
- Every partner endpoint scopes by the session's account; `/api/admin/**` is ADMIN only (filter and controller).
- Events: one durable queue, manual ack once the handler returned, any failure is an ERROR line and a nack without
  requeue. Every event is acted on once (`processed_fact` in the same transaction as its effects). Postbacks go out
  after the commit. Events of one player arrive in any order. Only real money (`realAmount`) counts.
- Wire enums keep only the values a handler acts on plus `OTHER` (`@JsonEnumDefaultValue`); unknown values are
  acknowledged, never dead-lettered.
- Every log line the app writes has a stable `event` key (`<area>.<what>`); add keys, never rename them. Never log a
  query string, a password, a session token or a full postback URL.
- Tests are named as sentences (Kotlin backticks); integration tests hit a real PostgreSQL and RabbitMQ.

## Hard rules

- Never edit an applied migration in `src/main/resources/db/migration`; add the next `V<n>__*.sql`. A new column is
  nullable or has a default.
- Do not change env variable names or defaults, routing keys, `affiliate.dlx` / `affiliate.dead`, the cookie
  `ap_session`, REST paths, error codes, log `event` names or ports: deployments depend on them.
- Never rename or renumber anything in `src/main/proto/`.
- Keep `README.md` and `docs/wiki/` in sync with the code (the documentation rule above).
