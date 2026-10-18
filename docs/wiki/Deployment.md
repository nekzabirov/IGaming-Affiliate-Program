# Deployment

## The image

The `Dockerfile` builds everything from source: the SPA (Node 22), the boot jar with the SPA inside (JDK 21), and a
`eclipse-temurin:21-jre-alpine` runtime that runs it as a non-root user.

    docker build -t igaming-affiliate-program .

One image, two roles, picked by the first argument:

| Command | Does |
|---|---|
| `app` (the default) | the service: SPA, API, the event consumer, the daily job |
| `schema` | applies the Flyway migrations to an **existing** database and exits (0 on success); it never consumes an event |

Run `schema` before every rollout (a Kubernetes Job, an init step, a compose service with
`service_completed_successfully`), then the service. The service never migrates by itself, so replicas starting
together never race. Migrations are additive: a new column is nullable or has a default, so the previous release
keeps working while the next one's migration runs first.

## Ports and probes

| Port | Serves | Expose |
|---|---|---|
| `SERVER_PORT` (8080) | the SPA and `/api` | to partners, through your reverse proxy / TLS terminator |
| `METRICS_PORT` (9464) | `/health` and `/metrics` | to your monitoring only, never to the internet |

Use `GET :9464/health` for liveness and readiness. The broker is not part of health: the cabinet keeps working
while RabbitMQ is away, and the listener reconnects by itself.

## Behind a reverse proxy

- Terminate TLS in front of the app and keep `SESSION_COOKIE_SECURE=true` (the default).
- Set `FORWARD_HEADERS_STRATEGY=framework` if the proxy sets `X-Forwarded-*`.
- Set `CLIENT_IP_HEADER` to the header your proxy puts the client's address in (`X-Real-IP` for a typical nginx,
  `CF-Connecting-IP` behind Cloudflare), so the sign-in and sign-up rate limits are per client and not per proxy.
  Leave it empty when the app is reached directly: then no header can change the address.

## RabbitMQ

The app declares its exchanges, queues and bindings itself ([Events](Events)). Configure on the broker:

- a **message TTL of at most 14 days** on `affiliate.dead` (a policy), so a dead letter is never replayed after the
  app forgot the idempotency keys of spins (15 days);
- permissions: the app declares `affiliate.events` (or your `EVENT_QUEUE`), `affiliate.dlx`, `affiliate.dead` and
  the two event exchanges, binds its queue to them and consumes, so its user needs configure, write and read on
  those names.

## Scaling

Several replicas can run against the same database and queue: every event is acted on once (a unique key in the
same transaction as its effects), and the daily job is idempotent. The sign-in and sign-up rate limits are in memory
per replica, so the effective limit is that many times higher. Postbacks are sent by each replica's own bounded
pool.

## Environment names

The app reads only the names in [Configuration](Configuration). It deliberately does not read generic names such
as `BASE_CURRENCY`, so a platform that sets those for every service does not change what partners are paid in.

## Deployment-specific defaults

To keep a deployment's own defaults out of its environment, add `src/main/resources/operator.properties` to the
image's build: `application.properties` imports it when present, its values win over `application.properties`,
environment variables win over both.
