#!/usr/bin/env bash
# Circuit Breaker в действии: notification-service останавливается, остальная система
# продолжает работать, а затем сервис возвращается. Запускать из корня репозитория на
# поднятом стеке (docker compose up); нужны curl и jq.
set -u

API=${API:-http://localhost:8080/api/v1}
RUN=$(date +%s)

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
post() { curl -s -H 'Content-Type: application/json' -d "$2" "$API$1"; }
state() { curl -s "$API/monitors/$1" | jq -r .current_state; }

say "1. Останавливаем notification-service"
docker compose stop notification-service >/dev/null 2>&1 && echo "остановлен"

say "2. Каналы через gateway → 503 от fallback, а не ошибка балансировщика"
curl -s "$API/projects/1/channels" | jq -c '{status, title, detail}'

say "3. Монитор падает — инцидент открывается, хотя уведомить некого"
USER_ID=$(post /users "{\"email\":\"cb-$RUN@demo.io\",\"password\":\"secret123\",\"full_name\":\"CB\"}" | jq .id)
PROJECT_ID=$(post /projects "{\"owner_id\":$USER_ID,\"name\":\"CB\",\"slug\":\"cb-$RUN\"}" | jq .id)
MONITOR_ID=$(post "/projects/$PROJECT_ID/monitors" \
  '{"name":"Self","url":"http://monitor-service:8080/actuator/health","interval_sec":10,"expected_status":500}' | jq .id)
for _ in $(seq 1 30); do [ "$(state "$MONITOR_ID")" = DOWN ] && break; sleep 2; done
echo "монитор $MONITOR_ID: $(state "$MONITOR_ID")"
curl -s "$API/monitors/$MONITOR_ID/incidents" | jq -c '[.content[] | {id, status}]'

say "4. monitor-service записал, что уведомить не удалось (fallback)"
docker compose logs monitor-service --since 2m 2>&1 | grep 'notification-service unavailable' | tail -2 | cut -c1-200

say "5. Состояние автоматов в monitor-service"
docker compose exec -T monitor-service wget -qO- http://localhost:8080/actuator/circuitbreakers \
  | jq -c '.circuitBreakers // .circuit_breakers | to_entries | map({(.key): .value.state}) | add'

say "6. Возвращаем notification-service"
docker compose start notification-service >/dev/null 2>&1 && echo "запущен; через несколько секунд gateway снова найдёт его в Eureka"
