#!/usr/bin/env bash
# Circuit Breaker в действии: notification-service останавливается, остальная система
# продолжает работать, а затем сервис возвращается. Запускать из корня репозитория на
# поднятом стеке (docker compose up); нужны curl и jq.
set -euo pipefail

API=${API:-http://localhost:8080/api/v1}
RUN=$(date +%s)
CB_RESPONSE=$(mktemp)

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
post() { curl -s -H 'Content-Type: application/json' -d "$2" "$API$1"; }
state() { curl -s "$API/monitors/$1" | jq -r .current_state; }
restore_notification_service() { docker compose start notification-service >/dev/null 2>&1 || true; }
cleanup() {
  restore_notification_service
  rm -f "$CB_RESPONSE"
}
trap cleanup EXIT

say "1. Останавливаем notification-service"
docker compose stop notification-service >/dev/null 2>&1 && echo "остановлен"

say "2. Шесть ошибок открывают Circuit Breaker маршрута notification-service"
for call in $(seq 1 6); do
  code=$(curl -s -o "$CB_RESPONSE" -w '%{http_code}' "$API/projects/1/channels")
  echo "вызов $call: HTTP $code"
done
jq -c '{status, title, detail}' "$CB_RESPONSE"

say "3. Gateway сообщает состояние OPEN"
CIRCUITS=$(curl -s "${API%/api/v1}/actuator/circuitbreakers")
echo "$CIRCUITS" | jq -c '(.circuitBreakers // .circuit_breakers)
  | to_entries | map({(.key): .value.state}) | add'
CB_STATE=$(echo "$CIRCUITS" | jq -r '(.circuitBreakers // .circuit_breakers)["notification-service"].state // empty')
[ "$CB_STATE" = OPEN ] || { echo "Ожидалось состояние OPEN, получено: ${CB_STATE:-нет данных}" >&2; exit 1; }

say "4. Монитор падает — инцидент открывается, хотя уведомить некого"
USER_ID=$(post /users "{\"email\":\"cb-$RUN@demo.io\",\"password\":\"secret123\",\"full_name\":\"CB\"}" | jq .id)
PROJECT_ID=$(post /projects "{\"owner_id\":$USER_ID,\"name\":\"CB\",\"slug\":\"cb-$RUN\"}" | jq .id)
MONITOR_ID=$(post "/projects/$PROJECT_ID/monitors" \
  '{"name":"Self","url":"http://monitor-service:8080/actuator/health","interval_sec":10,"expected_status":500}' | jq .id)
for _ in $(seq 1 30); do [ "$(state "$MONITOR_ID")" = DOWN ] && break; sleep 2; done
echo "монитор $MONITOR_ID: $(state "$MONITOR_ID")"
curl -s "$API/monitors/$MONITOR_ID/incidents" | jq -c '[.content[] | {id, status}]'

say "5. monitor-service записал, что уведомить не удалось (fallback)"
docker compose logs monitor-service --since 2m 2>&1 | grep 'notification-service unavailable' | tail -2 | cut -c1-200

say "6. Возвращаем notification-service"
restore_notification_service
trap - EXIT
rm -f "$CB_RESPONSE"
echo "запущен; через несколько секунд gateway снова найдёт его в Eureka"
