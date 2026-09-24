# Лаба 1 — Монолит на Spring Boot

Общая структура репозитория и git-flow — в [README.md](README.md). Полный текст задания по
всем лабам — в [TASK.md](TASK.md). Здесь — то, что специфично только для лабы 1.

Базовый пакет: `org.awesoma.monitoring`.

## Схема БД

- `users (id, email UK, password_hash, full_name, status)`
- `projects (id, owner_id FK -> users, name, slug UK, created_at)`
- `project_members (project_id FK, user_id FK, role, joined_at)` — M2M с доп. полем (`role`)
- `monitors (id, project_id FK, name, url, http_method, interval_sec, timeout_ms, expected_status, active, current_state)`
- `tags (id, name UK)` + `monitor_tags (monitor_id FK, tag_id FK)` — чистая M2M
- `check_results (id, monitor_id FK, checked_at, result, response_ms, http_status, error_message)`
- `incidents (id, monitor_id FK, started_at, resolved_at, status, severity, cause)`
- `channels (id, project_id FK, type, target, enabled)`
- `notifications (id, incident_id FK, channel_id FK, sent_at, status, attempts)`

Связи всех трёх типов, требуемых заданием:
- **One-to-Many / Many-to-One**: `projects → monitors`, `monitors → check_results`, `monitors → incidents`, `incidents → notifications`, `channels → notifications`.
- **Many-to-Many (чистая)**: `monitors ↔ tags` через `monitor_tags`.
- **Many-to-Many с доп. полем**: `projects ↔ users` через `project_members` (доп. поле `role`, `joined_at`).

### Enum'ы

Хранятся как строки (`@Enumerated(EnumType.STRING)`) + `CHECK`-constraint в миграции:

- `MemberRole`: `OWNER`, `EDITOR`, `VIEWER`
- `MonitorState`: `UP`, `DOWN`, `PAUSED`, `UNKNOWN`
- `CheckResultType`: `SUCCESS`, `TIMEOUT`, `BAD_STATUS`, `CONNECTION_ERROR`
- `IncidentStatus`: `OPEN`, `RESOLVED`
- `Severity`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `ChannelType`: `EMAIL`, `WEBHOOK`, `TELEGRAM`
- `NotificationStatus`: `PENDING`, `SENT`, `FAILED`
- `UserStatus`: `ACTIVE`, `INACTIVE`, `BLOCKED`
- `HttpMethod`: `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`

### Индексы

- `check_results(monitor_id, checked_at DESC)` — под выборку истории проверок монитора (Slice-пагинация без `count(*)`).
- `incidents(monitor_id, status)`
- Частичный `UNIQUE(monitor_id) WHERE status = 'OPEN'` — гарантирует не больше одного открытого инцидента на монитор на уровне БД (страхует бизнес-логику от гонки воркеров).

### Liquibase

`db/changelog/db.changelog-master.yaml` с `include` на changeset-файлы, **YAML**, один
changeset на изменение, у каждого прописан `rollback`. Применённые changeset'ы не
редактируются — только новые поверх. Порядок (с учётом FK):

1. `001-create-users`
2. `002-create-projects`
3. `003-create-project-members`
4. `004-create-tags`
5. `005-create-monitors`
6. `006-create-monitor-tags`
7. `007-create-check-results`
8. `008-index-check-results-history` — индекс `(monitor_id, checked_at DESC)`, сырым SQL:
   `createIndex` в Liquibase не умеет DESC
9. `009-create-incidents`
10. `010-index-incidents` — индекс `(monitor_id, status)` и частичный
    `UNIQUE(monitor_id) WHERE status = 'OPEN'`, тоже сырым SQL: нет поддержки WHERE
11. `011-create-channels`
12. `012-create-notifications`
13. `013-add-monitors-last-checked-at` — время последней проверки, по нему планировщик
    выбирает мониторы, которые пора проверить; отдельным changeset'ом, а не правкой 005

## Планировщик проверок

Максимально просто: `@Scheduled` + `RestClient`, без Quartz и собственных пулов потоков.
Логика полностью в пакете `checker` (не размазана по `service`), потому что в лабе 2 она
переезжает в отдельный реактивный check-service.

- `checker/CheckScheduler` — раз в `CHECKER_INTERVAL_MS` берёт мониторы, которым пора
  проверяться, и по одному отдаёт их на проверку; сбой одного не прерывает остальных.
- `checker/MonitorProbe` — один HTTP-запрос, без состояния и без доступа к БД.
- `service/MonitorCheckService` — записывает результат и открывает/закрывает инциденты.
- `domain/model` (`MonitorTarget`, `ProbeOutcome`) — типы обмена между ними. Лежат не в
  `checker`, чтобы сервисный слой не импортировал пакет, который уедет в лабе 2.

## Транзакции (обоснование)

1. **openIncident** — в одной транзакции: запись `check_result`, перевод
   `monitor.current_state → DOWN`, создание `incident`, создание `notification` на все
   активные каналы проекта. Без атомарности возможен монитор с состоянием `DOWN`, но без
   инцидента (или наоборот) при сбое посреди операции — рассинхрон состояния и алертинга.
2. **resolveIncident** — `SELECT ... FOR UPDATE` по монитору (защита от гонки двух
   параллельных прогонов воркера, которые одновременно пытаются закрыть/открыть инцидент
   на один и тот же монитор) + закрытие инцидента + возврат `monitor.current_state → UP` +
   создание notification о восстановлении.
3. **createProject** — создание проекта + добавление владельца в `project_members` с ролью
   `OWNER`. Без транзакции возможен проект без единого участника при сбое между двумя
   INSERT'ами.

Где в коде: транзакции 1 и 2 — это один метод `MonitorCheckService.record`, ветка
выбирается по результату проверки. Блокировка (`MonitorRepository.findByIdForUpdate`,
`@Lock(PESSIMISTIC_WRITE)`) берётся в самом начале, до чтения состояния, поэтому защищает
обе ветки. Сетевой запрос делается до транзакции, внутри только запись в БД. Частичный
unique-индекс по открытым инцидентам страхует ту же гонку на уровне базы. Транзакция 3 —
`ProjectService.create`.

## Пагинация

- `GET /api/v1/monitors/{id}/results` — бесконечная прокрутка, `Slice<CheckResultResponse>`, без `count(*)`.
- `GET /api/v1/projects/{id}/monitors` — `Page<MonitorResponse>` + заголовок `X-Total-Count`.
- `size` везде `@Max(50)`, дефолт `20` — общий параметр-объект `PageParams` на все контроллеры, чтобы не дублировать валидацию.
- Что `Slice` действительно не делает `count(*)`, а теги мониторов не грузятся по одному,
  проверяют тесты со счётчиком SQL-запросов: `CheckHistoryQueryCountTest`, `MonitorTagLoadingTest`.

## Слои и структура пакетов

```
org.awesoma.monitoring
 ├── MonitoringApplication.java
 ├── config/            // CryptoConfig (BCrypt), OpenApiConfig, SchedulingConfig
 ├── web/
 │    ├── controller/    // User, Project (+ участники), Monitor, Tag, Channel,
 │    │                  // Incident (+ история проверок и уведомления)
 │    ├── dto/           // подпакеты по агрегатам: channel, incident, monitor, project,
 │    │                  // tag, user; common — PageParams и модель ошибки для OpenAPI
 │    ├── mapper/        // MapStruct, по одному на агрегат, unmappedTargetPolicy = ERROR
 │    └── exception/     // GlobalExceptionHandler (@RestControllerAdvice, ProblemDetail),
 │                       // NotFoundException, ConflictStateException
 ├── service/            // бизнес-логика и границы транзакций
 ├── repository/         // Spring Data JPA + кастомные @Query, блокировка строки монитора
 ├── domain/
 │    ├── entity/        // JPA-сущности, ProjectMember + ProjectMemberId для M2M с доп. полями
 │    ├── enums/
 │    └── model/         // MonitorTarget, ProbeOutcome — обмен с checker
 └── checker/            // CheckScheduler (@Scheduled), MonitorProbe (RestClient)
```

Entity никогда не выходит за пределы `service`/`repository`/`domain` — в контроллер только DTO.

## Требования из задания → чек-лист

Всё закрыто. Проверка: `./gradlew check` (113 тестов, покрытие строк 97,5% при пороге 70%)
и `scripts/demo.sh` на стеке из `docker compose up`.

- [x] CRUD с REST API на основных сущностях, правильные HTTP-статусы — 33 эндпоинта; 201 +
  `Location`, 204 на удаление, 404, 409 на конфликт состояния, 400 на невалидный запрос
  (для ошибок в полях выбран 400, а не 422: клиенту важно, какое поле исправить)
- [x] Spring Data JPA для доступа к БД — `repository/*`
- [x] Валидация на уровне DTO (Bean Validation) и Entity/миграции — аннотации в `web/dto` и
  `domain/entity`, `NOT NULL` / `CHECK` / `UNIQUE` в changeset'ах
- [x] Схема БД — через Liquibase-миграции (YAML, rollback, без правки применённых) — 13
  changeset'ов, откат проверяет `LiquibaseMigrationsTest`
- [x] Юнит-тесты (Mockito) + интеграционные (Testcontainers + JUnit 5) — один контейнер
  Postgres на прогон (`support/PostgresContainer`)
- [x] Конфигурация только через переменные окружения (`${VAR}` в `application.yml`)
- [x] Сборка и запуск через `docker compose up` (app + postgres + healthcheck)
- [x] Пагинация везде, максимум 50 записей за запрос — `PageParams`, `size > 50` → 400
- [x] `Slice`-эндпоинт без `count(*)` (`/monitors/{id}/results`) — `CheckHistoryQueryCountTest`
- [x] `Page`-эндпоинт с `X-Total-Count` (`/projects/{id}/monitors`)
- [x] Минимум 2 транзакции с обоснованием (реализовано 3: `openIncident`, `resolveIncident`,
  `createProject`) — см. раздел «Транзакции»
- [x] Разделение Entity/DTO — MapStruct, сущности не выходят за сервисный слой
- [x] Чистая архитектура: controller/service/repository/domain/config
- [x] Все enum'ы — строками (`@Enumerated(EnumType.STRING)`) + `CHECK` в миграциях
- [x] `@RestControllerAdvice` + `ProblemDetail`, человекочитаемые ошибки — с именами полей в
  `violations`
- [x] Связи M2M, O2M/M2O, M2M с доп. полем — все три типа реализованы
- [x] OpenAPI 3 + Swagger UI (springdoc) — все операции с описанием и кодами ответов
- [x] JaCoCo, порог покрытия 70%, встроен в `check`
- [x] Git feature branching + conventional commits с первого коммита

## План веток (лаба 1)

Одна ветка = одна задача, от `develop`, PR-мерж с ревью напарника. Порядок ориентировочный,
можно менять местами/делить между двумя участниками параллельно там, где нет зависимостей
по коду (например, `monitor-crud` и `checker-scheduler` можно вести параллельно после того,
как влиты `domain-entities`).

1. `feature/lab1-project-init` — Gradle-скелет, JaCoCo (порог 70%), `.gitignore`, пакеты,
   `application.yml`, минимальный `docker-compose.yml` (app + postgres + healthcheck).
2. `feature/lab1-liquibase-init` — все миграции схемы.
3. `feature/lab1-domain-entities` — JPA entity + enum'ы + связи (M2M, O2M, M2M+доп.поле).
4. `feature/lab1-user-project-crud` — Users/Projects/ProjectMembers CRUD + транзакция `createProject`.
5. `feature/lab1-monitor-crud` — Monitor/Tag CRUD, пагинация (`Page` + `X-Total-Count`).
6. `feature/lab1-checker-scheduler` — `@Scheduled`-воркер, транзакции `openIncident`/`resolveIncident`.
7. `feature/lab1-incident-notification` — Incident/Channel/Notification эндпоинты, `Slice`-пагинация результатов.
8. `feature/lab1-exception-handling` — единый `GlobalExceptionHandler`.
9. `feature/lab1-openapi` — springdoc + Swagger UI.
10. `feature/lab1-tests` — добор покрытия до 70% (юнит + интеграционные).

Дальше — `fix/lab1-*` по мере обнаружения багов. Сверх плана влиты
`feature/lab1-refactor` (Lombok, единообразные имена), `fix/lab1-monitor-validation`
(имена полей в ошибках, значения по умолчанию для интервала и таймаута) и
`feature/lab1-demo-script` (`scripts/demo.sh`).
