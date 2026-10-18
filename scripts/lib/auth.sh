#!/usr/bin/env bash
# Shared by local demos. Supply TOKEN, or export the supervisor credentials from .env.
authenticate() {
  if [ -n "${TOKEN:-}" ]; then return; fi
  if [ -z "${SUPERVISOR_PASSWORD:-}" ]; then
    echo 'Укажите TOKEN или экспортируйте SUPERVISOR_PASSWORD (тот же, что в .env).' >&2
    return 1
  fi
  local body
  body=$(jq -n --arg email "${SUPERVISOR_EMAIL:-supervisor@example.com}" \
    --arg password "$SUPERVISOR_PASSWORD" '{email: $email, password: $password}')
  TOKEN=$(command curl -fsS -H 'Content-Type: application/json' -d "$body" "$API/auth/login" | jq -er '.access_token') || {
    echo 'Не удалось войти супервайзером. Проверьте запуск сервисов и учётные данные.' >&2
    return 1
  }
}

api_curl() { command curl -H "Authorization: Bearer $TOKEN" "$@"; }
