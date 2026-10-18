# Currency Conversion

**Requires [Currency-Exchange-Service](https://github.com/nekzabirov/Currency-Exchange-Service)** whenever events
arrive in a currency other than `PARTNER_CURRENCY`. It is the only service this app calls (besides the partners'
trackers); everything else comes in through [Events](Events).

## Why

Every balance, commission, turnover and payout is stored as a signed 64-bit integer of **nano units (1e-9) of
`PARTNER_CURRENCY`** (USDT by default). Events carry nano units of the player's currency, so each amount is
exchanged once, on the way in, at the rate of that moment. A rollback takes back the amounts recorded then, so a
rate change never makes a reversal differ from the accrual.

## The call

One unary gRPC call, `currency.v1.CurrencyService/ConvertUnits`, from the vendored contract
[`src/main/proto/currency/v1/currency.proto`](https://github.com/nekzabirov/IGaming-Affiliate-Program/blob/main/src/main/proto/currency/v1/currency.proto):

```proto
rpc ConvertUnits(ConvertUnitsRequest) returns (UnitsResponse);

message ConvertUnitsRequest { int64 units = 1; string currency = 2; string to_currency = 3; }
message UnitsResponse       { int64 units = 1; }
```

The request is `units` (nano of the event's currency), `currency` (the event's), `to_currency` =
`PARTNER_CURRENCY`; the answer is nano units of `PARTNER_CURRENCY`. Postbacks make a second kind of call, to `EUR`:
the payout a tracker hears of is converted from the event's currency, after the event has counted and only when a
request will go out ([Postbacks](Postbacks)). The service must therefore know `EUR` too.

- **No call when the event is already in `PARTNER_CURRENCY`**: the amount is taken as it is. A deployment whose
  events all arrive in the partner currency runs without the service.
- **Never inside a database transaction**: the call happens before the transaction that records the event.
- **3 second deadline** per call; **one more attempt on `UNAVAILABLE` only** (a connection that sat idle across a
  NAT comes back dead once). Any other failure is final.
- **A refusal dead-letters the event.** The service names the reason in the `x-error-code` trailer
  (`UNKNOWN_CURRENCY`, `RATE_MISSING`, `INVALID_AMOUNT`, `INTERNAL`); the app logs it in `broker.handler_failed`.
  Fix the cause (register the currency, sync rates) and [replay](Events#replaying-dead-letters) the dead letters.

## Configuration

| Env | Default | Meaning |
|---|---|---|
| `CURRENCY_GRPC_HOST` | `localhost` | host of Currency-Exchange-Service's gRPC port |
| `CURRENCY_GRPC_PORT` | `443` | its port (the service listens on 9090 by default; its own compose publishes 59090) |
| `CURRENCY_GRPC_PLAINTEXT` | `false` | `true` for plain HTTP/2 (inside a private network or on a laptop), `false` for TLS with the JVM's trust store |
| `PARTNER_CURRENCY` | `USDT` | the target currency; it must be registered in the service when any event arrives in another currency |

The channel sends keep-alive pings every 30 s (10 s timeout), so a load balancer or NAT in between does not drop
it silently, and exports gRPC client metrics to `/metrics`.

## Running it

[Getting Started](Getting-Started)'s `docker compose up` builds the service from its public repository and runs
its migration and seed. The seed registers `EUR`, `UAH`, `USDT`, `BTC` and `USDC`, but `ConvertUnits` needs
rates, which only the service's `sync` job loads (fiat rates need an OpenExchangeRates app id, crypto rates come
from Coinbase without a key; see its README):

    OPENEXCHANGE_APP_ID=<your-app-id> docker compose run --rm currency-sync

Until then, events in another currency than `PARTNER_CURRENCY` are dead-lettered with `RATE_MISSING`, while
events in `PARTNER_CURRENCY` (the sample events use USDT) work. To run the service on its own:

    git clone https://github.com/nekzabirov/Currency-Exchange-Service.git
    cd Currency-Exchange-Service && docker compose up --build -d     # gRPC on localhost:59090

then point the app at it with `CURRENCY_GRPC_HOST=localhost CURRENCY_GRPC_PORT=59090 CURRENCY_GRPC_PLAINTEXT=true`.
