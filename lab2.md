# Лаба 2 — Микросервисы

Запуск и процесс разработки — в [README.md](README.md), отчёт — в
[docs/report-lab2.md](docs/report-lab2.md). Полный текст задания — в [TASK.md](TASK.md).
Здесь — то, что специфично для лабы 2.

## Модули

| Модуль | Стек | Роль | База данных |
|---|---|---|---|
| `config-server` | Spring Cloud Config (native) | конфигурация всех сервисов из `config-repo/` | — |
| `eureka-server` | Spring Cloud Netflix Eureka | реестр сервисов | — |
| `gateway` | Spring Cloud Gateway (WebFlux) | единая точка входа, общий Swagger UI, Circuit Breaker | — |
| `monitor-service` | Spring MVC + Spring Data JPA | пользователи, проекты, участники, мониторы, теги, инциденты | `monitoring` |
| `check-service` | WebFlux + R2DBC | планировщик, HTTP-проверки, история проверок | `check_db` |
| `notification-service` | WebFlux + Spring Data JPA | каналы и уведомления | `notification_db` |

Все модули — подпроекты одной Gradle-сборки. Общее (Java 21, репозитории, JaCoCo с
порогом 70% в `check`, Spring Cloud BOM `2025.0.3`) — в корневом `build.gradle.kts`.

## Данные

Своя база на сервис (один контейнер PostgreSQL, отдельные БД). Ссылки между сервисами —
только по id, без внешних ключей: `check_results.monitor_id`, `channels.project_id`,
`notifications.incident_id`. Базы `check_db` и `notification_db` создаёт одноразовый
контейнер `postgres-init` (`docker/postgres/create-databases.sql`) — в том числе на уже
существующем томе.

Миграции monitor-service, убравшие перенесённые таблицы:

- `016-drop-check-results` — история проверок уехала в check-service;
- `017-drop-channels-and-notifications` — каналы и уведомления уехали в notification-service.

Откаты обеих восстанавливают таблицы пустыми.

## Межсервисные вызовы

Все вызовы — Feign по имени сервиса из Eureka, каждый клиент — с Circuit Breaker и fallback.

| Кто → кого | Вызов | Fallback |
|---|---|---|
| check-service → monitor-service | `GET /internal/monitors/due` | пустой список — проход пропускается |
| check-service → monitor-service | `POST /internal/monitors/{id}/outcomes` | результат уже в истории, состояние догонит следующая проверка |
| check-service → monitor-service | `GET /internal/monitors/{id}/exists` | история отвечает 503 |
| notification-service → monitor-service | `GET /internal/projects/{id}/exists`, `/internal/incidents/{id}/exists` | 503 |
| monitor-service → notification-service | `POST /internal/notifications`, `DELETE /internal/projects/{id}/channels` | запись в лог |
| monitor-service → check-service | `DELETE /internal/results?monitor_id=` | запись в лог |

Вызовы monitor-service идут из `@TransactionalEventListener` — только после коммита.
Реактивные сервисы выполняют блокирующие Feign-вызовы на `Schedulers.boundedElastic()`.
`/internal/**` gateway не маршрутизирует, в Swagger эти методы скрыты.

## Маршруты gateway

| Путь | Сервис |
|---|---|
| `/api/v1/monitors/*/results` | check-service |
| `/api/v1/projects/*/channels`, `/api/v1/channels/**`, `/api/v1/incidents/*/notifications` | notification-service |
| `/v3/api-docs/<сервис>` | документация сервиса для общего Swagger UI |
| `/api/v1/**` (остальное) | monitor-service |

У каждого маршрута — фильтр `CircuitBreaker` с fallback: 503 ProblemDetail с именем сервиса.

Заголовки `X-Forwarded-*` gateway добавляет только для запросов с адресов из
`trusted-proxies` (локальный и частные сети). Без них сервисы указывали бы в своих
спецификациях адрес контейнера, и «Try it out» в общем Swagger UI не работал бы.

## Circuit Breaker

Настройки общие, в `config-repo/application.yml`: размыкается при ≥ 50% ошибок из
последних 10 вызовов (не раньше 5 вызовов), 10 с в разомкнутом состоянии, затем 2 пробных
вызова; вызов дольше 3 с считается ошибкой. Состояние —
`/actuator/circuitbreakers` каждого сервиса. Сценарий с остановкой notification-service —
`scripts/circuit-breaker-demo.sh`.

## Конфигурация

`config-server` отдаёт файлы из `config-server/src/main/resources/config-repo/`:
`application.yml` — общее для всех (snake_case, Eureka, Circuit Breaker, actuator,
`forward-headers-strategy`), и по файлу на сервис. Сервис сам знает только своё имя и адрес
config-server (`spring.config.import`). Секреты — через переменные окружения (`${DB_...}`).
Тесты подключают те же файлы напрямую, без config-server и Eureka.

## Требования из задания → чек-лист

- [x] Декомпозиция на микросервисы — 3 бизнес-сервиса + config-server, eureka-server, gateway
- [x] Регистрация в Eureka — все сервисы и gateway, адресация по имени (`lb://…`, Feign)
- [x] Конфигурация из Config Server — native backend, `config-repo/`
- [x] Доступ через Spring Cloud Gateway — единственный опубликованный порт API (8080)
- [x] Взаимодействие через Feign Client — 8 внутренних вызовов, таблица выше
- [x] Circuit Breaker — Resilience4j на всех Feign-клиентах и на маршрутах gateway
- [x] Reactor + R2DBC — check-service
- [x] Reactor + Spring Data JPA — notification-service
- [x] Общий Swagger — Swagger UI на gateway со спецификациями всех сервисов

## Ветки лабы 2

1. `feature/lab2-multimodule` — многомодульная сборка, монолит → `monitor-service`
2. `feature/lab2-infrastructure` — config-server, eureka-server, gateway
3. `feature/lab2-check-service` — реактивный check-service (WebFlux + R2DBC)
4. `feature/lab2-notification-service` — notification-service (WebFlux + JPA)
5. `feature/lab2-circuit-breaker` — Circuit Breaker и fallback'и
6. `feature/lab2-swagger` — общий Swagger UI на gateway
7. `feature/lab2-docs` — описание, отчёт, README
