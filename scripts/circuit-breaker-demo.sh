#!/usr/bin/env bash
set -euo pipefail

API=${API:-http://localhost:8080/api/v1}
GATEWAY=${API%/api/v1}
RECOVERY_TIMEOUT=${RECOVERY_TIMEOUT:-90}
RUN=$(date +%s)
CB_RESPONSE=$(mktemp)
STACK_SERVICES=(
  postgres
  config-server
  eureka-server
  monitor-service
  check-service
  notification-service
  gateway
)

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
post() { curl -s -H 'Content-Type: application/json' -d "$2" "$API$1"; }
state() { curl -s "$API/monitors/$1" | jq -r .current_state; }
restore_notification_service() { docker compose start notification-service >/dev/null 2>&1 || true; }
circuit_state() {
  curl -fsS "$GATEWAY/actuator/circuitbreakers" | jq -r \
    '(.circuitBreakers // .circuit_breakers)["notification-service"].state // empty'
}
container_health() {
  local container_id
  container_id=$(docker compose ps -q "$1" 2>/dev/null)
  if [ -z "$container_id" ]; then
    printf 'stopped'
    return
  fi
  docker inspect --format \
    '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
    "$container_id" 2>/dev/null || printf 'unknown'
}
wait_for_stack() {
  local deadline=$((SECONDS + RECOVERY_TIMEOUT))
  local service health pending

  while ((SECONDS < deadline)); do
    pending=''
    for service in "${STACK_SERVICES[@]}"; do
      health=$(container_health "$service")
      if [ "$health" != healthy ]; then
        pending="${pending}${pending:+, }$service=$health"
      fi
    done
    if [ -z "$pending" ]; then
      echo "все постоянные контейнеры имеют статус healthy"
      return
    fi
    echo "ожидаем healthcheck: $pending"
    sleep 2
  done

  echo "Стек не восстановился за ${RECOVERY_TIMEOUT} с" >&2
  docker compose ps >&2
  return 1
}
wait_for_gateway_recovery() {
  local deadline=$((SECONDS + RECOVERY_TIMEOUT))
  local code breaker

  while ((SECONDS < deadline)); do
    code=$(curl -sS --connect-timeout 2 --max-time 5 -o "$CB_RESPONSE" -w '%{http_code}' \
      "$API/projects/$PROJECT_ID/channels" || true)
    breaker=$(circuit_state 2>/dev/null || true)
    echo "пробный запрос: HTTP ${code:-000}, Circuit Breaker: ${breaker:-UNKNOWN}"

    if [ "$code" = 200 ] && [ "$breaker" = CLOSED ]; then
      jq -e 'if type == "array" then .[0].content else .content end | type == "array"' \
        "$CB_RESPONSE" >/dev/null
      echo "gateway снова отдаёт ответ notification-service"
      return
    fi
    sleep 2
  done

  echo "Gateway не восстановил маршрут notification-service за ${RECOVERY_TIMEOUT} с" >&2
  echo "Последнее состояние Circuit Breaker: ${breaker:-UNKNOWN}, HTTP ${code:-000}" >&2
  return 1
}
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
CIRCUITS=$(curl -s "$GATEWAY/actuator/circuitbreakers")
echo "$CIRCUITS" | jq -c '(.circuitBreakers // .circuit_breakers)
  | to_entries | map({(.key): .value.state}) | add'
CB_STATE=$(echo "$CIRCUITS" | jq -r \
  '(.circuitBreakers // .circuit_breakers)["notification-service"].state // empty')
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

say "7. Ждём восстановления стека"
wait_for_stack

say "8. Проверяем HALF_OPEN → CLOSED и запрос через gateway"
wait_for_gateway_recovery
echo "ответ GET $API/projects/$PROJECT_ID/channels:"
jq -c 'if type == "array" then .[0] else . end | {content, total_elements}' "$CB_RESPONSE"

say "9. Восстановление подтверждено"
echo "notification-service: healthy"
echo "Circuit Breaker notification-service: $(circuit_state)"
echo "GET /projects/$PROJECT_ID/channels: HTTP 200"
docker compose ps

trap - EXIT
rm -f "$CB_RESPONSE"
