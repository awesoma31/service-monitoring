#!/usr/bin/env bash
# Сквозной сценарий лабы 1 по REST API: CRUD, конфликты, валидация, пагинация
# и полный цикл инцидента через планировщик.
#
# Нужны запущенный стек (docker compose up), curl и jq.
# Запросы идут с ролью OWNER в заголовке X-User-Role: ей разрешены все операции.
# Каждый прогон создаёт свои данные, поэтому базу между запусками чистить не нужно.
set -u

API=${API:-http://localhost:8080/api/v1}
HEALTH=${API%/api/v1}/actuator/health
RUN=$(date +%s)
AUTH=(-H "X-User-Role: OWNER")

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "Нужен $tool: brew install $tool" >&2; exit 1; }
done
curl -sf "$HEALTH" >/dev/null || { echo "Сервис не отвечает на $HEALTH — запустите docker compose up" >&2; exit 1; }

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
call() { local m=$1 p=$2 b=${3:-}; curl -s -X "$m" "${AUTH[@]}" -H 'Content-Type: application/json' ${b:+-d "$b"} -w '\n%{http_code}' "$API$p"; }
body() { sed '$d' <<<"$1"; }
show() { local r; r=$(call "$@"); echo "HTTP $(tail -n1 <<<"$r")"; body "$r" | jq -c . 2>/dev/null; }
state() { curl -s "${AUTH[@]}" "$API/monitors/$1" | jq -r .currentState; }
# Планировщик проходит раз в CHECKER_INTERVAL_MS (15 с), поэтому ждём до минуты.
wait_state() { for _ in $(seq 1 30); do [ "$(state "$1")" = "$2" ] && break; sleep 2; done; echo "монитор $1: $(state "$1")"; }

say "1. Пользователь: 201 + Location, пароля в ответе нет"
CREATED=$(curl -s -i "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"email\":\"owner-$RUN@demo.io\",\"password\":\"secret123\",\"fullName\":\"Demo Owner\"}" "$API/users")
grep -iE '^HTTP|^Location|^\{' <<<"$CREATED"
USER_ID=$(tail -n1 <<<"$CREATED" | jq .id)

say "2. Проект: владелец сразу в участниках (транзакция createProject)"
R=$(call POST /projects "{\"ownerId\":$USER_ID,\"name\":\"Demo\",\"slug\":\"demo-$RUN\"}")
PROJECT_ID=$(body "$R" | jq .id)
show GET "/projects/$PROJECT_ID/members"

say "3. Тот же slug → 409"
show POST /projects "{\"ownerId\":$USER_ID,\"name\":\"Other\",\"slug\":\"demo-$RUN\"}"

say "4. Последний владелец не может покинуть проект → 409"
show DELETE "/projects/$PROJECT_ID/members/$USER_ID"

say "5. Канал уведомлений"
show POST "/projects/$PROJECT_ID/channels" '{"type":"WEBHOOK","target":"https://example.org/hook"}'

say "6. Валидация: 400 с именами полей"
show POST "/projects/$PROJECT_ID/monitors" '{"name":"","url":"ftp://x","intervalSec":1}'

say "7. Монитор с настройками по умолчанию и тегом (many-to-many)"
show POST "/projects/$PROJECT_ID/monitors" '{"name":"Minimal","url":"https://example.org","tags":["prod"]}'

say "8. Монитор ждёт 500 от health самого приложения, получает 200 → DOWN, инцидент, уведомление"
R=$(call POST "/projects/$PROJECT_ID/monitors" \
  '{"name":"Self","url":"http://app:8080/actuator/health","intervalSec":10,"timeoutMs":2000,"expectedStatus":500}')
BROKEN=$(body "$R" | jq .id)
wait_state "$BROKEN" DOWN
show GET "/monitors/$BROKEN/incidents"
INCIDENT=$(curl -s "${AUTH[@]}" "$API/monitors/$BROKEN/incidents" | jq '.content[0].id')
show GET "/incidents/$INCIDENT/notifications"

say "9. Ожидаем 200 → следующая проверка закрывает инцидент сама"
call PUT "/monitors/$BROKEN" \
  '{"name":"Self","url":"http://app:8080/actuator/health","httpMethod":"GET","intervalSec":10,"timeoutMs":2000,"expectedStatus":200,"active":true}' >/dev/null
wait_state "$BROKEN" UP
show GET "/incidents/$INCIDENT"
echo "уведомлений по инциденту: $(curl -s "${AUTH[@]}" "$API/incidents/$INCIDENT/notifications" | jq '.content | length') (падение + восстановление)"

say "10. Повторное закрытие инцидента → 409"
show POST "/incidents/$INCIDENT/resolve"

say "11. История проверок — Slice, без totalElements"
curl -s "${AUTH[@]}" "$API/monitors/$BROKEN/results?size=2" | jq -c '{rows: (.content | map(.result)), hasTotal: has("totalElements"), last}'

say "12. Мониторы проекта — Page, общее число в X-Total-Count"
curl -s -D - -o /dev/null "${AUTH[@]}" "$API/projects/$PROJECT_ID/monitors?size=1" | grep -i x-total-count

say "13. Страница больше 50 → 400"
show GET "/users?size=51"

say "14. Несуществующий ресурс → 404"
show GET /monitors/999999

say "15. Запрос без заголовка роли → 401"
curl -s -H 'Content-Type: application/json' -w '\n%{http_code}' "$API/projects/$PROJECT_ID" \
  | { r=$(cat); echo "HTTP $(tail -n1 <<<"$r")"; sed '$d' <<<"$r" | jq -c .; }

say "16. Роль VIEWER не может удалить монитор → 403"
curl -s -X DELETE -H 'X-User-Role: VIEWER' -w '\n%{http_code}' "$API/monitors/$BROKEN" \
  | { r=$(cat); echo "HTTP $(tail -n1 <<<"$r")"; sed '$d' <<<"$r" | jq -c .; }
