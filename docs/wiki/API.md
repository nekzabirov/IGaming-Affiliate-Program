# API

The cabinet's REST API, under `/api` on `SERVER_PORT` (8080), JSON in and out. The SPA is its only client, but
nothing in it is private to the SPA. Metrics and health are on the management port, not here.

## Conventions

- **Session**: `POST /api/auth/register` and `/login` set the cookie `ap_session` (HttpOnly, SameSite=Lax,
  `Secure` unless `SESSION_COOKIE_SECURE=false`, `SESSION_TTL_DAYS` long). Every other endpoint except
  `GET /api/config` needs it.
- **Money** is a decimal string of `PARTNER_CURRENCY` (`"25"`, `"0.75"`); timestamps are ISO-8601.
- **Request bodies** are `application/json`; an unknown field is a 400, a flag sent as a string or number is a 400.
- **Paging**: `page` (from 0) and `size` (1 to 200, default 50); answers are
  `{"items": [...], "page": 0, "size": 50, "total": 123}`.
- **Tracing**: send `X-Request-ID` (or get a new one); it is echoed back and is the `traceId` of the request's log
  lines.
- **Errors**: `{"code": "...", "message": "...", "field": "...", "rule": "..."}`, `field` and `rule` only for
  `VALIDATION`. The `code` is the contract; the message is English text for people.

| Code | Status | When |
|---|---|---|
| `VALIDATION` | 400 | `rule`: `required`, `length`, `format`, `invalid`, `positive`, `max`, `range`, `type`, `unknown_field`, `blocked_host`, `unresolvable_host` |
| `UNAUTHENTICATED` | 401 | no session, or an expired one |
| `INVALID_CREDENTIALS` | 401 | wrong login or password |
| `ACCOUNT_BLOCKED` | 403 | the account is blocked |
| `FORBIDDEN` | 403 | not an admin |
| `NOT_FOUND` | 404 | missing, or another partner's |
| `LOGIN_TAKEN`, `IDENTITY_TAKEN` | 409 | the login or the company identity exists |
| `INSUFFICIENT_BALANCE` | 409 | a payout above the balance |
| `LIMIT_REACHED` | 409 | an 11th postback on a company |
| `SELF_BLOCK` | 409 | an admin blocking their own account |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | a body that is not JSON |
| `TOO_MANY_REQUESTS` | 429 | sign-in or sign-up rate limit |
| `INTERNAL` | 500 | a failure of ours; the cause is logged, never sent |

## Public

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/config` | | `currency`, `supportUrl`, `siteUrl`, `tagPrefix` (`partner:`), `clickParam` (`click_id`), `cpaMinDeposit`, `payoutNetworks`, `postbackSourceIp`, `postbackTypes`, `promoManifestUrl` |
| `POST /api/auth/register` | `{"login", "password"}` | 201, the account (as `/api/me`), and the session cookie |
| `POST /api/auth/login` | `{"login", "password"}` | the account and the session cookie |
| `POST /api/auth/logout` | | 204, the session ends |

Rate limits, per instance and in memory: 10 failed sign-ins per login and 30 per client address in 15 minutes; 5
accounts per client address per hour. The client address is the socket's, or the trusted proxy header's
(`CLIENT_IP_HEADER`). Logins are 3 to 32 characters (`a-z`, `0-9`, `.`, `_`, `-`, stored lower-case), passwords 8
to 72 characters (at most 72 bytes).

## Partner

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/me` | | `id`, `login`, `role`, `balance`, `totalEarned`, `pendingPayouts`, `paidOut`, `currency`, `createdAt` |
| `GET /api/companies` | | the partner's companies: `identity`, `type`, `amount`, `status`, `tag`, `link`, `createdAt`, `stats` (`registrations`, `firstDeposits`, `earned`) |
| `POST /api/companies` | `{"identity", "type", "amount"}` | 201, the company, `IN_REVIEW` |
| `GET /api/companies/{identity}` | | the company |
| `GET /api/companies/{identity}/players` | `?page&size` | a page of its referrals |
| `GET /api/companies/{identity}/postbacks` | | its postbacks, each with `examples` (the exact requests it sends) |
| `POST /api/companies/{identity}/postbacks` | `{"type": "KEITARO", "url"}` | 201, the postback |
| `PATCH /api/companies/{identity}/postbacks/{id}` | `{"active"?, "url"?}` | the postback |
| `DELETE /api/companies/{identity}/postbacks/{id}` | | 204 |
| `GET /api/stats` | `?from&to&company` (UTC days `YYYY-MM-DD`) | daily series and totals ([Commissions](Commissions#statistics)) |
| `GET /api/commissions` | `?page&size&company` | the earnings ledger, newest first |
| `GET /api/payouts` | `?page&size` | the partner's payouts |
| `POST /api/payouts` | `{"amount", "network", "address"}` | 201, the payout, `PENDING`; the amount is reserved from the balance |

## Admin

`/api/admin/**` answers only to an `ADMIN` session.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/admin/companies` | `?status&q&page&size` | companies of every partner, with their owner and totals |
| `PATCH /api/admin/companies/{identity}` | `{"status"?, "amount"?}` | the company with its new status and/or final terms |
| `GET /api/admin/partners` | `?q&page&size` | partner accounts with their company counts |
| `PATCH /api/admin/partners/{id}` | `{"active": false}` | blocks (or with `true` unblocks) a partner |

Payout status and admin roles are changed in the database: see [Administration](Administration).

## Management port

`METRICS_PORT` (9464): `GET /health` (`{"status":"UP"}`, no details) and `GET /metrics` (Prometheus, including
`http_server_requests` with SLO buckets, gRPC client and JVM metrics). The RabbitMQ connection is not part of
health: the cabinet keeps serving while the broker is away, and events wait in the durable queue.
