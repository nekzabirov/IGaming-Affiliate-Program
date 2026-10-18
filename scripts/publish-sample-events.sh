#!/usr/bin/env bash
#
# Publishes the sample events in docs/events/examples/ to a local RabbitMQ through its management HTTP API, the
# way a publisher would: to the publisher's exchange, with the routing key, as JSON.
#
#   scripts/publish-sample-events.sh                              # register, invoice, spin, bet, in that order
#   scripts/publish-sample-events.sh invoice.events               # one route
#   scripts/publish-sample-events.sh spin.events my-spin.json     # one route, your own body
#
# Environment (defaults match docker-compose.yml):
#   RABBIT_MANAGEMENT_URL  http://localhost:15680
#   RABBIT_USER            guest
#   RABBIT_PASSWORD        guest
#   RABBIT_VHOST           /
#   PAM_EXCHANGE           pam.events      the player events exchange (register.events, invoice.events)
#   CASINO_EXCHANGE        casino.events   the casino events exchange (spin.events, bet.events)
#   REQUEST_ID             (none)          sent as the x-request-id header, the trace id of the app's log lines
#
# Needs bash and curl only.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXAMPLES="$ROOT/docs/events/examples"

URL="${RABBIT_MANAGEMENT_URL:-http://localhost:15680}"
USER_NAME="${RABBIT_USER:-guest}"
PASSWORD="${RABBIT_PASSWORD:-guest}"
VHOST="${RABBIT_VHOST:-/}"
PLAYER_EXCHANGE="${PAM_EXCHANGE:-pam.events}"
CASINO="${CASINO_EXCHANGE:-casino.events}"
REQUEST_ID="${REQUEST_ID:-}"

usage() {
    echo "usage: $0 [routing-key [body.json]]" >&2
    echo "routing keys: register.events invoice.events spin.events bet.events" >&2
    exit 2
}

exchange_of() {
    case "$1" in
        register.events | invoice.events) echo "$PLAYER_EXCHANGE" ;;
        spin.events | bet.events) echo "$CASINO" ;;
        *) echo "unknown routing key: $1" >&2; usage ;;
    esac
}

# The body travels as a JSON string inside the API's own JSON: backslashes and quotes escaped, line breaks dropped
# (they only ever sit between tokens of a pretty-printed example).
as_json_string() {
    sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' "$1" | tr -d '\n\r'
}

urlencode_vhost() {
    if [[ "$VHOST" == "/" ]]; then echo "%2F"; else echo "$VHOST"; fi
}

publish() {
    local key="$1" file="$2"
    [[ -f "$file" ]] || { echo "no such file: $file" >&2; exit 1; }
    local exchange headers payload request answer
    exchange="$(exchange_of "$key")"
    headers="{}"
    if [[ -n "$REQUEST_ID" ]]; then
        headers="{\"x-request-id\":\"$REQUEST_ID\"}"
    fi
    payload="$(as_json_string "$file")"
    request="{\"properties\":{\"content_type\":\"application/json\",\"delivery_mode\":2,\"headers\":$headers},\"routing_key\":\"$key\",\"payload\":\"$payload\",\"payload_encoding\":\"string\"}"
    answer="$(curl -sS -u "$USER_NAME:$PASSWORD" -H 'Content-Type: application/json' -X POST \
        --data-binary "$request" "$URL/api/exchanges/$(urlencode_vhost)/$exchange/publish")"
    # The broker answers {"routed":true} when a queue was bound to the route, false when nobody was.
    echo "$exchange / $key <- ${file#"$ROOT"/}: $answer"
}

case "$#" in
    0)
        for key in register.events invoice.events spin.events bet.events; do
            publish "$key" "$EXAMPLES/$key.json"
        done
        ;;
    1) publish "$1" "$EXAMPLES/$1.json" ;;
    2) publish "$1" "$2" ;;
    *) usage ;;
esac
