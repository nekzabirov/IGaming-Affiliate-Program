# Development

## Requirements

- JDK 21 (the Gradle wrapper downloads Gradle; protoc and the gRPC generator come from Maven Central)
- Node 22 and npm, for the SPA
- Docker, for the tests (Testcontainers starts PostgreSQL 16 and RabbitMQ 3.13) and the local stack

## Build and test

```bash
./gradlew test                       # the whole suite; needs Docker
./gradlew bootJar                    # build/libs/affiliate-app.jar
cd frontend && npm ci && npm run build   # type-check (vue-tsc) and build the SPA into src/main/resources/static
```

Build the SPA before `bootJar` to get a jar that serves it; without it the jar serves the API only.

## Run locally

Infrastructure in Docker, the app from the jar (or your IDE):

```bash
docker compose up -d postgres rabbitmq                       # postgres :55450, rabbitmq :55680, management :15680
cp .env.example .env && set -a && . ./.env && set +a
./gradlew bootJar
java -jar build/libs/affiliate-app.jar schema                # Flyway, then exits
java -jar build/libs/affiliate-app.jar                       # http://localhost:8080, metrics on :9464
```

`.env.example` points the currency client at a Currency-Exchange-Service on `localhost:59090`; events in
`PARTNER_CURRENCY` do not need it ([Currency Conversion](Currency-Conversion)).

The SPA with hot reload: `cd frontend && npm run dev` (Vite on :5173, `/api` proxied to :8080, or to
`VITE_API_TARGET`).

## Tests

- Integration tests run the whole app on a random port against a real PostgreSQL migrated by Flyway; tables are
  emptied before each test. The broker listener is off except in `EventFlowTest`, which brings a real RabbitMQ.
- The currency service is an in-process gRPC fake at fixed rates (`FakeCurrencyManager`); no test reaches the
  network. DNS for the SSRF guard is faked too (`FakeDns`).
- `docs/events/examples/*.json` are read by `EventReaderTest`, so the documented examples always parse.

## Conventions

- Kotlin, flat packages by concern (see [Architecture](Architecture)); no ports-and-adapters layers, no JPA, no
  mediator. One top-level class per file.
- Plain SQL over `JdbcClient`, every column named (never `SELECT *`); counters move inside one statement.
- Money is a `Long` of nano units in every table; REST speaks decimal strings.
- Refusals are `DomainException` subclasses with a stable `code`; never rename a code.
- No remote call inside a database transaction.
- Every log line the app writes carries a stable `event` key (`<area>.<what>`); add keys, never rename them.
- Comments explain why, not what. English everywhere.
- Never edit an applied migration; add the next `V<n>__*.sql`.

## Documentation rule

> **Documentation rule.** Every change that affects behaviour, configuration, API, build or deployment MUST
> update `README.md` and the relevant pages in `docs/wiki/` in the same commit / pull request. A change is not
> done until the docs match the code.
