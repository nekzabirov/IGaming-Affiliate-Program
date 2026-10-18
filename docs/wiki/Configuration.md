# Configuration

All configuration comes from environment variables, read through `src/main/resources/application.properties`. A
value that is set but malformed fails the boot with the variable's name in the error.

## Server

| Env | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8080` | the public port: the SPA and `/api` |
| `METRICS_PORT` | `9464` | the management port: `/metrics` (Prometheus) and `/health`; never publish it |
| `CLIENT_IP_HEADER` | empty | the header a trusted proxy in front of the app puts the client's address in (`X-Real-IP`, `CF-Connecting-IP`). Empty: the socket's remote address, so a client reaching the app directly cannot pick its own rate-limit key. When set, that header comes first, then the first `X-Forwarded-For` hop, then the remote address. **Set it only when every request passes through that proxy.** |
| `FORWARD_HEADERS_STRATEGY` | `none` | Spring's `server.forward-headers-strategy`: `framework` trusts `X-Forwarded-*` (scheme, host, client address), for an app behind a reverse proxy; `none` ignores them |
| `LOG_LEVEL` | `INFO` | root log level; `DEBUG` adds the per-stake lines |
| `OPERATOR` | `local` | written as the `env` field of every log line, to tell deployments apart |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError` | JVM options, set by the Dockerfile |

## Database

| Env | Default | Meaning |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432` | host part only, without the database name |
| `DATABASE_NAME` | `affiliate` | appended to `DB_URL`; the database must exist |
| `DB_USERNAME` | `postgres` | |
| `DB_PASSWORD` | `postgres` | |
| `DB_POOL_SIZE` | `5` | Hikari maximum pool size |

The JDBC URL also carries `reWriteBatchedInserts=true&autosave=conservative`. PostgreSQL 16 is what the tests and
the compose file use.

## RabbitMQ

| Env | Default | Meaning |
|---|---|---|
| `RABBIT_HOST` | `localhost` | |
| `RABBIT_PORT` | `5672` | |
| `RABBIT_USER` | `guest` | |
| `RABBIT_PASSWORD` | `guest` | |
| `RABBIT_SSL_ENABLED` | `false` | TLS (AMQPS) with certificate verification |
| `RABBIT_PREFETCH` | `32` | unacknowledged deliveries per consumer |
| `PAM_EXCHANGE` | `pam.events` | the **player events exchange** (`register.events`, `invoice.events`) |
| `CASINO_EXCHANGE` | `casino.events` | the **casino events exchange** (`spin.events`, `bet.events`) |
| `EVENT_QUEUE` | `affiliate.events` | the one work queue |

The dead-letter exchange `affiliate.dlx` and queue `affiliate.dead` have fixed names. See [Events](Events).

## Currency

| Env | Default | Meaning |
|---|---|---|
| `CURRENCY_GRPC_HOST` | `localhost` | Currency-Exchange-Service gRPC host |
| `CURRENCY_GRPC_PORT` | `443` | its port |
| `CURRENCY_GRPC_PLAINTEXT` | `false` | `true` for plaintext HTTP/2, `false` for TLS |
| `PARTNER_CURRENCY` | `USDT` | the currency of every balance, commission and payout: an upper-case code of 2 to 10 letters or digits, registered in Currency-Exchange-Service if any event arrives in another currency |

See [Currency Conversion](Currency-Conversion).

## Program

| Env | Default | Meaning |
|---|---|---|
| `CPA_MIN_DEPOSIT` | `10` | decimal in `PARTNER_CURRENCY`: a first deposit must reach it for CPA to pay |
| `PAYOUT_NETWORKS` | `TRC20,ERC20,BEP20,TON` | comma-separated networks a partner may request a payout on; TRC20, ERC20, BEP20 and TON addresses are checked by format, any other network accepts 10 to 128 printable characters |
| `SITE_URL` | `http://localhost:3000` | the brand site's origin; partner links are `<SITE_URL>/?affiliate=<identity>&click_id={subid}` |
| `SUPPORT_TELEGRAM_URL` | empty | the support link shown in the cabinet; empty hides it |
| `POSTBACK_SOURCE_IP` | empty | the address postbacks leave from, shown to partners whose tracker allowlists; empty hides it |
| `PROMO_MANIFEST_URL` | empty | the promo library's catalogue (see [Branding](Branding#promo-library)); empty hides the Promo screen |
| `SESSION_COOKIE_SECURE` | `true` | the `Secure` flag of the `ap_session` cookie; `false` only for plain http on localhost |
| `SESSION_TTL_DAYS` | `30` | session lifetime, 1 to 365 |

## Deployment-specific defaults

`application.properties` imports `optional:classpath:operator.properties`. A deployment that builds its own image
can put its defaults there (`src/main/resources/operator.properties`, the same keys as `application.properties`);
values in it win over `application.properties`, environment variables win over both. Without the file every
default above applies.

## Frontend development

| Env | Default | Meaning |
|---|---|---|
| `VITE_API_TARGET` | `http://localhost:8080` | where `npm run dev` proxies `/api` |
