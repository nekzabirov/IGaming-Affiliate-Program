# Events

IGaming Affiliate Program learns everything about players from four events on RabbitMQ. It never calls a
casino, a wallet or a player service: **any service that publishes this JSON works**, whatever language it is
written in. This page is the contract. Machine-readable JSON Schemas live in
[`docs/events/`](https://github.com/nekzabirov/IGaming-Affiliate-Program/tree/main/docs/events), with one example per
event in `docs/events/examples/`.

## Topology

At start the app declares, all durable (declaring what already exists with the same arguments is a no-op, so the
app may start before or after the publishers):

| Kind | Name | Notes |
|---|---|---|
| Topic exchange | `PAM_EXCHANGE` (default `pam.events`) | the **player events exchange**: registrations and invoices |
| Topic exchange | `CASINO_EXCHANGE` (default `casino.events`) | the **casino events exchange**: casino spins and sportsbook bets |
| Queue | `EVENT_QUEUE` (default `affiliate.events`) | the one work queue, argument `x-dead-letter-exchange=affiliate.dlx` |
| Binding | player events exchange -> work queue | routing key `register.events` |
| Binding | player events exchange -> work queue | routing key `invoice.events` |
| Binding | casino events exchange -> work queue | routing key `spin.events` |
| Binding | casino events exchange -> work queue | routing key `bet.events` |
| Fanout exchange | `affiliate.dlx` | the dead-letter exchange |
| Queue | `affiliate.dead` | bound to `affiliate.dlx`; failed events wait here |

The exchange names are configurable because they belong to the publishers; the routing keys, `affiliate.dlx` and
`affiliate.dead` are fixed. One queue carries all four routes on purpose: brokers shared by many services often
cap the number of queues per vhost. A durable queue cannot get `x-dead-letter-exchange` added later (the broker
answers 406), so if `affiliate.events` already exists without it, delete it before the first start.

**Dead-letter retention is a broker policy you configure**, not something the app declares. Give `affiliate.dead`
a message TTL of at most 14 days, for example:

    rabbitmqctl set_policy affiliate-dead '^affiliate\.dead$' '{"message-ttl":1209600000}' --apply-to queues

The app forgets the idempotency keys of casino spins after 15 days (see [Idempotency](#idempotency)), so a
spin that could still be replayed after that would be counted again.

## Delivery rules

- **One listener, manual acknowledgement.** A delivery is handed to the handler of the exchange and routing key it
  was published with, and acknowledged once the handler returned. Prefetch is `RABBIT_PREFETCH` (32).
- **Any failure dead-letters the event**: a body that is not the envelope, a `playerId` that is missing, `null`,
  quoted, fractional, not positive or different from the player inside `data`, a required field missing or of the
  wrong JSON type, a route nobody reads, the database or the currency service unavailable, a currency the
  currency service does not know. The app logs one ERROR line `broker.handler_failed` (`exchange`, `route`,
  `queue`, the cause) and nacks the delivery without requeue, so the broker moves it to `affiliate.dead`. There are
  **no retries** and no requeue loops.
- **A well-formed event the app has no business with is acknowledged**: another program's player, a
  withdrawal, a pending invoice, a win, an enum value added by a publisher later.
- **Extra fields are ignored**, at any depth. Publishers may send whole snapshots; only the fields below are read.
- **`content-type` and other AMQP properties are ignored**; the body is read as UTF-8 JSON.
- **Header `x-request-id`** (if it matches `^[A-Za-z0-9._:-]{1,128}$`) becomes the `traceId` of the event's log
  lines and of the postbacks it triggers.

## Envelope

Every event is a JSON object:

```json
{ "playerId": 1001, "data": { "...": "..." } }
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `playerId` | integer >= 1 | yes | The player the event is about. A JSON number: `"1001"`, `1001.0`, `1e3`, `0`, `-1` and `null` are refused. It must equal the player inside `data` (named per event below). |
| `data` | object | yes | The event itself. |

Inside `data`, numbers are never read from strings or fractions, enums never from numbers, and timestamps are
ISO-8601 strings (`2026-10-01T09:15:00Z`). Amounts are **signed 64-bit integers of nano units** (1e-9): 50 USDT
is `50000000000`.

## register.events

Exchange: the player events exchange (`PAM_EXCHANGE`). A new player, published once at registration.
Schema: `docs/events/register.events.schema.json`.

```json
{
  "playerId": 1001,
  "data": {
    "id": 1001,
    "affiliateTag": "partner:summer",
    "affiliateAttribution": { "click_id": "8f3k2a" },
    "country": "DE",
    "createdAt": "2026-10-01T09:15:00Z"
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | integer | yes | The player id; must equal `playerId`. |
| `affiliateTag` | string or null | no | What the brand site stored from `?affiliate=`. `partner:<identity>` (prefix in any letter case, identity read in lower case) names a company of this program. |
| `affiliateAttribution` | object of strings, or null | no | Free-form keys the site stored at sign-up. `click_id` is the partner tracker's click id. Kept whole on the referral. |
| `country` | string or null | no | ISO 3166-1 alpha-2, any letter case; anything that is not two letters is stored as unknown. |
| `createdAt` | ISO-8601 string | yes | Registration time; its UTC day is the day the registration counts on. |

What happens:

- Tag `partner:<identity>` of an **ACTIVE** company whose partner is not blocked: the player becomes that
  company's **referral**, once and for good (a `referral` row with the company, `click_id` if it matches
  `^[A-Za-z0-9._~-]{1,128}$`, the attribution, country and registration time), and a `REGISTRATION` postback is
  sent to the company's trackers after the commit.
- Tag `partner:<identity>` of an unknown company, one not ACTIVE, or of a blocked partner: recorded as **refused**
  (the key `register:<playerId>` with no company), so no later event attributes the player either.
- Any other tag, or none: acknowledged, nothing is written.

## invoice.events

Exchange: the player events exchange (`PAM_EXCHANGE`). Every invoice on creation and on every status change.
Schema: `docs/events/invoice.events.schema.json`.

```json
{
  "playerId": 1001,
  "data": {
    "id": 5001,
    "type": "CREDIT",
    "status": "SUCCESS",
    "amount": 50000000000,
    "currency": "USDT",
    "updatedAt": "2026-10-01T09:20:00Z",
    "player": {
      "id": 1001,
      "affiliateTag": "partner:summer",
      "affiliateAttribution": { "click_id": "8f3k2a" },
      "country": "DE",
      "createdAt": "2026-10-01T09:15:00Z"
    }
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | integer | yes | Invoice id; the idempotency key is `invoice:<id>`. |
| `type` | string | yes | `CREDIT` (a deposit) is acted on. Any other value (`PAYOUT`, `REFUND`, one added later) is acknowledged without effect. |
| `status` | string | yes | `SUCCESS` (the money landed) is acted on. Any other value (`PENDING`, `PROCESSING`, `REJECTED`, one added later) is acknowledged without effect. |
| `amount` | integer (nano) | yes | Amount in `currency`. Zero or negative is acknowledged without effect. |
| `currency` | string | yes | Currency code of `amount`; converted to `PARTNER_CURRENCY` unless it already is. |
| `updatedAt` | ISO-8601 string | yes | When the invoice reached this status; its UTC day is the day the deposit counts on. |
| `player` | object | yes | The owner as of the invoice, the same fields as `register.events` `data`: `id` (must equal `playerId`) and `createdAt` required, `affiliateTag`, `affiliateAttribution`, `country` optional. |

What happens to a `CREDIT` in `SUCCESS` with `amount` > 0:

1. If the player is not a referral yet and their `register.events` was not handled yet, the invoice's own
   `player` attributes them first, by exactly the registration rules and under the same `register:<playerId>`
   key (with its `REGISTRATION` postback); the registration that comes later changes nothing.
2. Not a referral, or the company is not ACTIVE or its partner is blocked: acknowledged, **nothing is recorded**,
   so a redelivery after the company is re-activated still counts.
3. Otherwise the amount is converted to `PARTNER_CURRENCY` (before the database transaction), and in one
   transaction: key `invoice:<id>`, the referral's first deposit if it is the first, the day's deposit count and
   sum (`activity_daily`), the commission (CPA once per player on a first deposit of at least `CPA_MIN_DEPOSIT`,
   source `cpa:<playerId>`; `REVSHARE_DEPOSIT` its percent of every deposit, source `deposit:<invoiceId>`;
   `REVSHARE_TURNOVER` nothing), and the partner's balance.
4. After the commit, a `DEPOSIT` postback with the deposit's whole amount as the payout.

## spin.events

Exchange: the casino events exchange (`CASINO_EXCHANGE`). One leg of money in a casino round.
Schema: `docs/events/spin.events.schema.json`.

```json
{
  "playerId": 1001,
  "data": {
    "id": 7001,
    "type": "PLACE",
    "amount": 2000000000,
    "realAmount": 2000000000,
    "reference": null,
    "round": { "playerId": 1001, "currency": "USDT", "createdAt": "2026-10-01T09:30:00Z" }
  }
}
```

| Field | Type | Required | Meaning |
|---|---|---|---|
| `id` | integer | yes | Leg id; the idempotency key is `spin:<id>`. |
| `type` | string | yes | `PLACE` (a stake) and `ROLLBACK` are acted on. Any other value (`SETTLE`, which is a win, or one added later) is acknowledged without effect. |
| `amount` | integer (nano) | yes | The whole leg: real, lock and bonus money. The postback payout of a stake. Zero or negative is acknowledged without effect. |
| `realAmount` | integer (nano) | yes | The player's own money in this leg, the only part that earns turnover and commission. Lock and bonus money never do. |
| `reference` | object or null | no | On a `ROLLBACK`, the reversed leg: `id` (integer) and `type` (string), both required when present. Read only on a `ROLLBACK`. |
| `round.playerId` | integer | yes | Must equal `playerId`. |
| `round.currency` | string | yes | Currency code of the amounts. |
| `round.createdAt` | ISO-8601 string | yes | Its UTC day is the day the leg counts on (a rollback is dated the same way). |

## bet.events

Exchange: the casino events exchange (`CASINO_EXCHANGE`). One leg of money on a sportsbook bet.
Schema: `docs/events/bet.events.schema.json`.

```json
{
  "playerId": 1001,
  "data": {
    "id": 8001,
    "type": "BET",
    "amount": 10000000000,
    "realAmount": 10000000000,
    "reference": null,
    "bet": { "playerId": 1001, "currency": "USDT", "createdAt": "2026-10-01T09:40:00Z" }
  }
}
```

The fields are those of `spin.events`, with `bet` instead of `round` and these types: `BET` (a stake) and
`ROLLBACK` are acted on; any other value (`WIN`, one added later) is acknowledged without effect. The idempotency
key is `bet:<id>`.

## Stakes and rollbacks

- A `PLACE` / `BET` with `amount` > 0 of a referral of an ACTIVE company with an active partner: its `realAmount`
  converted, then in one transaction the key `spin:<id>` / `bet:<id>` (with the turnover and commission it added),
  the day's turnover (`activity_daily`), and for `REVSHARE_TURNOVER` its percent on one commission row per company,
  player and day (source `turnover:<companyId>:<playerId>:<day>`) and the balance. A stake of lock or bonus money
  only records its key and earns nothing. After the commit, a `SPIN` / `BET` postback with the whole `amount` as
  the payout. A stake of a player who is not a referral is acknowledged and leaves nothing (stakes carry no tag to
  attribute from).
- A `ROLLBACK` whose `reference.type` is `PLACE` (spin) or `BET` (bet) takes back exactly the turnover and
  commission that stake recorded, whatever the company's status and terms are by then; the balance may go below
  zero. The rollback's own key is recorded so a redelivery changes nothing.
- A `ROLLBACK` handled **before** its stake records the stake's key itself, empty, so the stake never counts when
  it arrives.
- A `ROLLBACK` of anything else (a `SETTLE`, a `WIN`, an unknown type, or no `reference`) is acknowledged without
  effect.

A rollback example:

```json
{
  "playerId": 1001,
  "data": {
    "id": 7002,
    "type": "ROLLBACK",
    "amount": 2000000000,
    "realAmount": 2000000000,
    "reference": { "id": 7001, "type": "PLACE" },
    "round": { "playerId": 1001, "currency": "USDT", "createdAt": "2026-10-01T09:30:00Z" }
  }
}
```

## Idempotency

Delivery is at least once; every event is acted on once. Its key goes into `processed_fact` in the same
transaction as its effects:

| Event | Key |
|---|---|
| register.events (or the deposit that attributed first) | `register:<playerId>` |
| invoice.events | `invoice:<invoiceId>` |
| spin.events | `spin:<id>` (a rollback before its stake also writes `spin:<referenceId>`) |
| bet.events | `bet:<id>` (likewise `bet:<referenceId>`) |

Commission rows are unique per source (`cpa:<playerId>`, `deposit:<invoiceId>`,
`turnover:<companyId>:<playerId>:<day>`). The daily job at 03:17 UTC deletes `spin:` keys older than 15 days;
`register:`, `invoice:` and `bet:` keys are kept (a sportsbook bet can be voided weeks later).

## Ordering

Nothing depends on order. The routes travel apart, two replicas share the queue during a rollout, and a dead
letter may be replayed days later:

- a deposit before its registration attributes the player from the invoice's `player`;
- a rollback before its stake voids the stake;
- the same event twice, or a republished invoice, changes nothing.

## Currency

Amounts in a currency other than `PARTNER_CURRENCY` are converted by
[Currency-Exchange-Service](Currency-Conversion) (`ConvertUnits`) before the transaction; amounts already in
`PARTNER_CURRENCY` never call it. If the service refuses (an unknown currency, a missing rate) or does not answer,
the event is dead-lettered with the reason (`x-error-code`, e.g. `RATE_MISSING`) in the error line.

## Replaying dead letters

A failed event waits in `affiliate.dead` with its original exchange and routing key in its `x-death` header. Once
the cause is fixed, **move** it back into `affiliate.events` through the **default exchange** (exchange `""`,
routing key `affiliate.events`), keeping its properties and headers. The consumer recovers the original route from
the `x-death` entry for `affiliate.events`; an event already counted changes nothing.

Never republish it to the publisher's exchange: every other consumer bound to that route would receive a second
copy.

With the management UI: open the queue `affiliate.dead`, "Move messages", destination queue `affiliate.events`
(the shovel plugin must be enabled). Or with a few lines of Python (`pip install pika`):

```python
import pika

connection = pika.BlockingConnection(pika.ConnectionParameters("localhost", 55680))
channel = connection.channel()
channel.confirm_delivery()
waiting = channel.queue_declare("affiliate.dead", passive=True).method.message_count
for _ in range(waiting):
    method, properties, body = channel.basic_get("affiliate.dead")
    if method is None:
        break
    channel.basic_publish("", "affiliate.events", body, properties, mandatory=True)
    channel.basic_ack(method.delivery_tag)
connection.close()
```

It moves only what was dead when it started, and acks each dead letter only after the broker confirmed the copy.

## Publishing test events

With the stack from [Getting Started](Getting-Started) running:

    scripts/publish-sample-events.sh                     # register, invoice, spin and bet from docs/events/examples
    scripts/publish-sample-events.sh invoice.events      # one of them
    scripts/publish-sample-events.sh spin.events my.json # your own body

The script uses the RabbitMQ management API (`RABBIT_MANAGEMENT_URL`, default `http://localhost:15680`) and needs
only bash and curl. The examples attribute player 1001 to the company `summer`: create that company in the cabinet
and approve it as an admin first, or the events are acknowledged without effect.

## Log lines

INFO: `accrual.referral_created` (with `via`: `register.events`, or `invoice.events` for a deposit that came
first), `accrual.referral_ignored` and `accrual.deposit_ignored` (with `reason`: `unknown_company`,
`company_not_active`, `owner_blocked`), `accrual.deposit_counted`, `accrual.stake_reversed`. DEBUG (`LOG_LEVEL=DEBUG`):
`accrual.stake_counted`, `accrual.stake_ignored`, `accrual.stake_voided`, `accrual.duplicate`. ERROR:
`broker.handler_failed`.
