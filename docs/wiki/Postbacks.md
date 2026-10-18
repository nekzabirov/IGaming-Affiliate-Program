# Postbacks

A partner adds, per company, up to 10 postback URLs of their tracker. On each registration, deposit, casino spin and
sports bet of the company's players the app sends a server-to-server GET, so the tracker records them against its
click ids. Supported tracker: **Keitaro** (`type` `KEITARO`).

## Keitaro

The partner pastes the URL Keitaro shows under Settings, Postback URL (before Keitaro 11.1: Maintenance, Postback
URL). The app keeps `scheme://host[:port]/<key>/postback`, drops the template query, and sends its own:

| Event | Query |
|---|---|
| registration | `?subid=<click_id>&status=registration` |
| deposit (every one, the first included) | `?subid=<click_id>&status=deposit&payout=<deposit>&currency=eur&tid=dep-<invoice id>` |
| casino spin (`PLACE`) | `?subid=<click_id>&status=spin&payout=<whole stake>&currency=eur&tid=spin-<leg id>` |
| sports bet (`BET`) | `?subid=<click_id>&status=bet&payout=<whole stake>&currency=eur&tid=bet-<leg id>` |

`payout` is the deposit or the whole stake (`amount`: real, lock and bonus money alike), never the commission,
converted from the event's currency to EUR with 2 decimals; Keitaro has no USDT. `registration` and `deposit` are
Keitaro's own conversion types; `spin` and `bet` are custom ones the partner creates under Settings, Conversion
Types (Keitaro ignores a status it does not know). Every conversion but the registration carries a unique `tid`, so
none overwrites another. Every postback in the API carries `examples`, the exact requests the adapter builds, so the
cabinet shows precisely what is sent. A player without a `click_id` is recorded as skipped.

## Delivery

- After the database commit, never inside it, on a bounded pool: 4 threads, a queue of 1000; when it is full the
  oldest waiting delivery is dropped with a WARN.
- Connect timeout 3 s, response timeout 10 s, the whole exchange cut at 20 s. Redirects are never followed, nothing
  is retried, the response body is never read. User-Agent `affiliate-app-postback/1.0`.
- The outcome of the last attempt is kept on the postback (`last_sent_at`, `last_status`, `last_error`) and logged
  as `postback.sent`, `postback.failed` or `postback.skipped` with the host and the path, the key masked
  (`/***/postback`). The query is never logged. A response marked as a Cloudflare challenge is recorded as such:
  the partner should allowlist `POSTBACK_SOURCE_IP`.
- The `x-request-id` of the event that triggered a postback is its `traceId`.
- The amount is converted to EUR once per event, only when a request will go out (the company has an active
  postback and the player a `click_id`). When the currency service refuses, every postback of the company records
  `Currency conversion failed` and nothing is sent; the event itself has already counted.

## SSRF guard

Partner URLs are untrusted. Names like `localhost`, single labels, and `*.local`, `*.internal`, `*.svc`,
`*.cluster.local`, `*.localhost` are refused, and every resolved address is checked against the private,
loopback, link-local, CGNAT, multicast and reserved ranges: when the URL is saved, and again in the HTTP client's
DNS resolver at connect time, so a name that later resolves inside your network is still refused.
