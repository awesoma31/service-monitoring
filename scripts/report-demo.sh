#!/usr/bin/env bash
# Отчёты по REST API: загрузка готового файла, генерация CSV и PDF по данным monitor-service
# и check-service, опрос статуса до READY, скачивание и удаление.
#
# Нужны запущенный стек (docker compose up), curl и jq.
# Каждый прогон создаёт свой проект, поэтому базу между запусками чистить не нужно.
set -u

API=${API:-http://localhost:8080/api/v1}
HEALTH=${API%/api/v1}/actuator/health
RUN=$(date +%s)
OUT=${OUT:-$(mktemp -d)}

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "Нужен $tool" >&2; exit 1; }
done
curl -sf "$HEALTH" >/dev/null || { echo "Сервис не отвечает на $HEALTH — запустите docker compose up" >&2; exit 1; }
source "$(dirname "${BASH_SOURCE[0]}")/lib/auth.sh"
authenticate || exit 1

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
json() { api_curl -s -H 'Content-Type: application/json' "$@"; }
utc()  { date -u -d "$1" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v"$2" +%Y-%m-%dT%H:%M:%SZ; }

# Ждёт, пока задание выйдет из QUEUED/GENERATING, и печатает итоговый статус.
wait_ready() {
  local status
  for _ in $(seq 1 60); do
    status=$(api_curl -s "$API/reports/$1" | jq -r .status)
    case "$status" in QUEUED|GENERATING) sleep 2 ;; *) break ;; esac
  done
  echo "отчёт $1: $status"
}

say "1. Проект с монитором; check-service проверяет его по расписанию"
ME=$(json "$API/users?size=50" | jq -r '.content[] | select(.role=="SUPERVISOR") | .id' | head -n1)
PROJECT=$(json -d "{\"owner_id\":$ME,\"name\":\"Reports demo\",\"slug\":\"reports-$RUN\"}" "$API/projects" | jq .id)
MONITOR=$(json -d '{"name":"Example","url":"https://example.com","interval_sec":30,"timeout_ms":5000}' \
  "$API/projects/$PROJECT/monitors" | jq .id)
echo "проект $PROJECT, монитор $MONITOR"

say "2. Загрузка готового CSV: 201, READY, SHA-256 совпадает с локальным"
printf 'metric,value\nДоступность,99.9\n' > "$OUT/ready.csv"
UPLOADED=$(api_curl -s -F "file=@$OUT/ready.csv;type=text/csv" "$API/projects/$PROJECT/reports")
jq -c '{id, status, file_name, size_bytes, sha256}' <<<"$UPLOADED"
echo "локальный SHA-256: $(sha256sum "$OUT/ready.csv" 2>/dev/null | cut -d' ' -f1 || shasum -a 256 "$OUT/ready.csv" | cut -d' ' -f1)"

say "3. Генерация CSV и PDF за последние сутки: 202 и QUEUED; повтор с тем же ключом — то же задание"
FROM=$(utc '1 day ago' '-1d'); TO=$(utc 'now' '+0S')
KEY=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)
BODY="{\"from\":\"$FROM\",\"to\":\"$TO\",\"format\":\"CSV\"}"
CSV_ID=$(json -H "Idempotency-Key: $KEY" -d "$BODY" "$API/projects/$PROJECT/reports/generations" | jq -r .id)
AGAIN=$(json -H "Idempotency-Key: $KEY" -d "$BODY" "$API/projects/$PROJECT/reports/generations" | jq -r .id)
echo "CSV: $CSV_ID, повтор: $AGAIN"
PDF_ID=$(json -d "{\"from\":\"$FROM\",\"to\":\"$TO\",\"format\":\"PDF\",\"monitor_ids\":[$MONITOR]}" \
  "$API/projects/$PROJECT/reports/generations" | jq -r .id)
echo "PDF: $PDF_ID"

say "4. Опрос статуса до READY"
wait_ready "$CSV_ID"
wait_ready "$PDF_ID"

say "5. Скачивание: CSV с метриками и PDF"
api_curl -s -o "$OUT/report.csv" "$API/reports/$CSV_ID/content"
api_curl -s -o "$OUT/report.pdf" "$API/reports/$PDF_ID/content"
cat "$OUT/report.csv"
echo "PDF: $(head -c 5 "$OUT/report.pdf"), $(wc -c < "$OUT/report.pdf") байт → $OUT/report.pdf"

say "6. Список отчётов проекта"
api_curl -s -D - -o "$OUT/list.json" "$API/projects/$PROJECT/reports" | grep -iE '^x-(total-count|has-next)'
jq -c '.[] | {id, source, format, status}' "$OUT/list.json"

say "7. Удаление: 202, затем 404"
api_curl -s -o /dev/null -w 'DELETE: %{http_code}\n' -X DELETE "$API/reports/$CSV_ID"
api_curl -s -o /dev/null -w 'GET: %{http_code}\n' "$API/reports/$CSV_ID"
