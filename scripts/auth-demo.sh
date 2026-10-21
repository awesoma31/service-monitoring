#!/usr/bin/env bash
# Lab 3: run against a started stack, with TOKEN or SUPERVISOR_PASSWORD exported.
set -euo pipefail
API=${API:-http://localhost:8080/api/v1}
source "$(dirname "${BASH_SOURCE[0]}")/lib/auth.sh"
authenticate
RUN="$(date +%s)-$RANDOM"
RESPONSE=$(mktemp)
trap 'rm -f "$RESPONSE"' EXIT

expect() {
  local expected=$1 token=$2 method=$3 path=$4 body=${5:-} code
  code=$(command curl -sS -o "$RESPONSE" -w '%{http_code}' -X "$method" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    ${body:+-d "$body"} "$API$path")
  if [ "$code" != "$expected" ]; then
    echo "$method $path: ожидалось $expected, получено $code" >&2
    jq . "$RESPONSE" >&2
    exit 1
  fi
  echo "$method $path: HTTP $code"
}
login_as() {
  command curl -fsS -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"demo-password-123\"}" "$API/auth/login" | jq -er '.access_token'
}

code=$(command curl -sS -o /dev/null -w '%{http_code}' "$API/projects")
[ "$code" = 401 ] || { echo "Ожидался HTTP 401 без токена, получено $code" >&2; exit 1; }
echo 'Без токена: HTTP 401'
expect 401 invalid GET /projects
expect 200 "$TOKEN" GET /auth/me

expect 201 "$TOKEN" POST /auth/register \
  "{\"email\":\"operator-$RUN@demo.io\",\"password\":\"demo-password-123\",\"full_name\":\"Operator\",\"role\":\"OPERATOR\"}"
OPERATOR_ID=$(jq -er '.id' "$RESPONSE")
jq -e 'has("password") == false and has("password_hash") == false' "$RESPONSE" >/dev/null
expect 201 "$TOKEN" POST /users \
  "{\"email\":\"viewer-$RUN@demo.io\",\"password\":\"demo-password-123\",\"full_name\":\"Viewer\",\"role\":\"VIEWER\"}"
VIEWER_ID=$(jq -er '.id' "$RESPONSE")
OPERATOR_TOKEN=$(login_as "operator-$RUN@demo.io")
VIEWER_TOKEN=$(login_as "viewer-$RUN@demo.io")

expect 403 "$OPERATOR_TOKEN" POST /users \
  "{\"email\":\"forbidden-$RUN@demo.io\",\"password\":\"demo-password-123\",\"full_name\":\"Forbidden\",\"role\":\"SUPERVISOR\"}"
expect 403 "$OPERATOR_TOKEN" POST /auth/register \
  "{\"email\":\"forbidden-register-$RUN@demo.io\",\"password\":\"demo-password-123\",\"full_name\":\"Forbidden\",\"role\":\"SUPERVISOR\"}"
expect 201 "$OPERATOR_TOKEN" POST /projects \
  "{\"owner_id\":$OPERATOR_ID,\"name\":\"Auth demo\",\"slug\":\"auth-$RUN\"}"
PROJECT_ID=$(jq -er '.id' "$RESPONSE")
expect 403 "$VIEWER_TOKEN" GET "/projects/$PROJECT_ID"
expect 201 "$OPERATOR_TOKEN" POST "/projects/$PROJECT_ID/members" "{\"user_id\":$VIEWER_ID}"
expect 200 "$VIEWER_TOKEN" GET "/projects/$PROJECT_ID"
expect 403 "$VIEWER_TOKEN" POST "/projects/$PROJECT_ID/monitors" \
  '{"name":"Forbidden","url":"https://example.com"}'
expect 201 "$OPERATOR_TOKEN" POST "/projects/$PROJECT_ID/monitors" \
  '{"name":"Self","url":"http://monitor-service:8080/actuator/health"}'
MONITOR_ID=$(jq -er '.id' "$RESPONSE")
expect 200 "$VIEWER_TOKEN" GET "/monitors/$MONITOR_ID/results"
expect 403 "$VIEWER_TOKEN" POST "/projects/$PROJECT_ID/channels" \
  '{"type":"EMAIL","target":"auth-demo@example.com"}'
expect 201 "$OPERATOR_TOKEN" POST "/projects/$PROJECT_ID/channels" \
  '{"type":"EMAIL","target":"auth-demo@example.com"}'
CHANNEL_ID=$(jq -er '.id' "$RESPONSE")
expect 200 "$OPERATOR_TOKEN" PUT "/channels/$CHANNEL_ID" \
  '{"target":"auth-demo@example.com","enabled":false}'
expect 200 "$VIEWER_TOKEN" GET "/projects/$PROJECT_ID/channels"
echo 'Лабораторная 3: JWT, роли и доступ к проектам проверены через gateway.'
