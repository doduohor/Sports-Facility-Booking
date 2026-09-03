#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT="sports-booking-smoke"
COMPOSE=(docker compose --env-file .env.example -p "$PROJECT")
API_PORT="${API_PORT:-$(sed -n 's/^API_PORT=//p' .env.example | head -n 1)}"
API_PORT="${API_PORT:-8080}"

cleanup() {
    status=$?
    if (( status != 0 )); then
        "${COMPOSE[@]}" logs --no-color || true
    fi
    "${COMPOSE[@]}" down --volumes --remove-orphans || true
    exit "$status"
}
trap cleanup EXIT

"${COMPOSE[@]}" up --build -d

deadline=$((SECONDS + ${SMOKE_TIMEOUT_SECONDS:-180}))
while (( SECONDS < deadline )); do
    services=$("${COMPOSE[@]}" ps --format '{{.Service}} {{.State}} {{.Health}}')
    if printf '%s\n' "$services" | grep -q '^postgres running healthy$' \
        && printf '%s\n' "$services" | grep -q '^rabbit running healthy$' \
        && printf '%s\n' "$services" | grep -q '^mongo running healthy$' \
        && printf '%s\n' "$services" | grep -q '^api running healthy$' \
        && printf '%s\n' "$services" | grep -q '^worker running '; then
        curl --fail "http://localhost:${API_PORT}/health"
        exit 0
    fi
    sleep 2
done

echo "Timed out waiting for API healthcheck" >&2
exit 1
