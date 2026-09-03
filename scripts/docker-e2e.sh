#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT="sports-booking-e2e"
export TELEGRAM_NOTIFICATION_MODE=stub
COMPOSE=(docker compose --env-file .env.example -p "$PROJECT")
API_PORT="${API_PORT:-$(sed -n 's/^API_PORT=//p' .env.example | head -n 1)}"
API_PORT="${API_PORT:-8080}"
RABBIT_MANAGEMENT_PORT="${RABBIT_MANAGEMENT_PORT:-$(sed -n 's/^RABBIT_MANAGEMENT_PORT=//p' .env.example | head -n 1)}"
RABBIT_MANAGEMENT_PORT="${RABBIT_MANAGEMENT_PORT:-15672}"
TIMEOUT_SECONDS="${E2E_TIMEOUT_SECONDS:-180}"

cleanup() {
    status=$?
    if (( status != 0 )); then
        "${COMPOSE[@]}" logs --no-color || true
    fi
    if ! "${COMPOSE[@]}" down --volumes --remove-orphans; then
        if (( status == 0 )); then
            status=1
        fi
    fi
    exit "$status"
}
trap cleanup EXIT

"${COMPOSE[@]}" up --build -d

deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
    services=$("${COMPOSE[@]}" ps --format '{{.Service}} {{.State}} {{.Health}}')
    if printf '%s\n' "$services" | grep -q '^postgres running healthy$' \
        && printf '%s\n' "$services" | grep -q '^rabbit running healthy$' \
        && printf '%s\n' "$services" | grep -q '^mongo running healthy$' \
        && printf '%s\n' "$services" | grep -q '^api running healthy$' \
        && printf '%s\n' "$services" | grep -q '^worker running '; then
        break
    fi
    sleep 2
done

if (( SECONDS >= deadline )); then
    echo "Timed out waiting for the Compose stack" >&2
    exit 1
fi

api_url="http://localhost:${API_PORT}"
facility_id=$(curl --fail --silent --show-error \
    --user admin:admin \
    --header 'Content-Type: application/json' \
    --data '{"name":"E2E Facility","type":"POOL"}' \
    "$api_url/api/facilities" | jq --exit-status --raw-output '.id')

equipment_id=$(curl --fail --silent --show-error \
    --user admin:admin \
    --header 'Content-Type: application/json' \
    --data "{\"facilityId\":${facility_id},\"name\":\"E2E Sensor\",\"type\":\"FIRE_ALARM\"}" \
    "$api_url/api/equipments" | jq --exit-status --raw-output '.id')

curl --fail --silent --show-error \
    --user admin:admin \
    --header 'Content-Type: application/json' \
    --data "{\"equipmentId\":${equipment_id},\"type\":\"SMOKE\",\"unit\":\"PERCENT\",\"value\":12.0}" \
    "$api_url/api/measurements" >/dev/null

deadline=$((SECONDS + TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
    event_id=$("${COMPOSE[@]}" exec -T postgres psql \
        -U "${POSTGRES_USER:-sports}" \
        -d "${POSTGRES_DB:-sports_facility_booking}" \
        -tAc "SELECT event_id::text FROM outbox_events WHERE event_type = 'MEASUREMENT_CREATED' ORDER BY id DESC LIMIT 1;")

    if [[ -z "$event_id" ]]; then
        sleep 2
        continue
    fi

    incident_event_id=$("${COMPOSE[@]}" exec -T postgres psql \
        -U "${POSTGRES_USER:-sports}" \
        -d "${POSTGRES_DB:-sports_facility_booking}" \
        -tAc "SELECT event_id::text FROM outbox_events WHERE event_type = 'INCIDENT_CREATED' ORDER BY id DESC LIMIT 1;")

    if [[ -z "$incident_event_id" ]]; then
        sleep 2
        continue
    fi

    published=$("${COMPOSE[@]}" exec -T postgres psql \
        -U "${POSTGRES_USER:-sports}" \
        -d "${POSTGRES_DB:-sports_facility_booking}" \
        -tAc "SELECT count(*) FROM outbox_events WHERE event_id = '${event_id}' AND event_type = 'MEASUREMENT_CREATED' AND status = 'PUBLISHED';")
    history=$("${COMPOSE[@]}" exec -T mongo mongosh \
        --quiet \
        --username "${MONGO_ROOT_USERNAME:-mongo_admin}" \
        --password "${MONGO_ROOT_PASSWORD:-mongo_admin}" \
        --authenticationDatabase admin \
        "${MONGO_DATABASE:-sports_facility_booking}" \
        --eval "db.event_history.countDocuments({eventId: '${event_id}', eventType: 'MEASUREMENT_CREATED', status: 'PROCESSED'})")
    incident_history=$("${COMPOSE[@]}" exec -T mongo mongosh \
        --quiet \
        --username "${MONGO_ROOT_USERNAME:-mongo_admin}" \
        --password "${MONGO_ROOT_PASSWORD:-mongo_admin}" \
        --authenticationDatabase admin \
        "${MONGO_DATABASE:-sports_facility_booking}" \
        --eval "db.event_history.countDocuments({eventId: '${incident_event_id}', eventType: 'INCIDENT_CREATED', status: 'PROCESSED'})")
    queue=$("${COMPOSE[@]}" exec -T rabbit rabbitmqctl list_queues name messages \
        | awk '$1 == "sports.measurements" { print $2 }')

    if [[ "$published" =~ ^[[:space:]]*1[[:space:]]*$ ]] \
        && [[ "$history" =~ ^[[:space:]]*1[[:space:]]*$ ]] \
        && [[ "$incident_history" =~ ^[[:space:]]*1[[:space:]]*$ ]] \
        && [[ "$queue" =~ ^[[:space:]]*[0-9]+[[:space:]]*$ ]]; then
        message=$("${COMPOSE[@]}" exec -T postgres psql \
            -U "${POSTGRES_USER:-sports}" \
            -d "${POSTGRES_DB:-sports_facility_booking}" \
            -tAc "SELECT json_build_object('eventId', event_id::text, 'eventType', event_type, 'createdAt', created_at::text, 'data', payload)::text FROM outbox_events WHERE event_id = '${event_id}' AND status = 'PUBLISHED';")
        publish_request=$(jq -n \
            --arg exchange "${RABBIT_EXCHANGE:-sports.events}" \
            --arg routing_key "${RABBIT_ROUTING_KEY:-measurement.created}" \
            --arg payload "$message" \
            '{properties: {}, routing_key: $routing_key, payload: $payload, payload_encoding: "string"}')
        publish_response=$(curl --fail --silent --show-error \
            --user "${RABBIT_USER:-testRabbit}:${RABBIT_PASSWORD:-change_me}" \
            --header 'Content-Type: application/json' \
            --data "$publish_request" \
            "http://localhost:${RABBIT_MANAGEMENT_PORT}/api/exchanges/%2F/${RABBIT_EXCHANGE:-sports.events}/publish")
        jq -e '.routed == true' <<<"$publish_response" >/dev/null

        sleep 4
        duplicate_history=$("${COMPOSE[@]}" exec -T mongo mongosh \
            --quiet \
            --username "${MONGO_ROOT_USERNAME:-mongo_admin}" \
            --password "${MONGO_ROOT_PASSWORD:-mongo_admin}" \
            --authenticationDatabase admin \
            "${MONGO_DATABASE:-sports_facility_booking}" \
            --eval "db.event_history.countDocuments({eventId: '${event_id}', eventType: 'MEASUREMENT_CREATED', status: 'PROCESSED'})")
        [[ "$duplicate_history" =~ ^[[:space:]]*1[[:space:]]*$ ]]
        exit 0
    fi
    sleep 2
done

echo "Timed out waiting for the measurement event flow" >&2
exit 1
