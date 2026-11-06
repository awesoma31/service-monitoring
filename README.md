# service-monitoring

Сервис мониторинга веб-сайтов — групповой проект по курсу «Распределённые системы» (ИТМО).
Система периодически проверяет доступность сайтов, при сбое открывает инцидент и оповещает
по каналам проекта. Проект развивается через четыре лабораторные работы: от монолита к
микросервисам.

## Лабораторные работы

| № | Содержание | Статус | Материалы |
|---|---|---|---|
| 1 | Монолит на Spring Boot: REST API, PostgreSQL, Liquibase, транзакции, пагинация, тесты | готово | [описание](lab1.md), [отчёт](docs/report-lab1.md), релиз `v1.1.0-lab1` |
| 2 | Декомпозиция на микросервисы: Eureka, Config Server, Gateway, Feign, Circuit Breaker, Reactor | готово | [описание](lab2.md), [отчёт](docs/report-lab2.md), релиз `v2.1.0-lab2` |
| 3 | Аутентификация: Spring Security, JWT, ролевая модель | готово | [описание](lab3.md), [отчёт](docs/report-lab3.md) |
| 4 | Обмен сообщениями через Kafka/RabbitMQ, файловый сервис, Clean Architecture | в работе: код готов, нет отчёта | [описание](lab4.md), [границы архитектуры](docs/clean-architecture.md) |

Полный текст задания — [TASK.md](TASK.md).

## Предметная область

Пользователи объединяются в проекты. В проекте заводятся мониторы — проверяемые адреса с
интервалом, таймаутом и ожидаемым кодом ответа — и каналы оповещения. Планировщик проверяет
мониторы, записывает историю проверок, при сбое открывает инцидент и создаёт уведомления для
каналов проекта, при восстановлении закрывает инцидент. Владелец проекта может отключить
будущие уведомления, не удаляя настроенные каналы.

## Сервисы

| Модуль | Назначение |
|---|---|
| `gateway` | единственная точка входа в API и общий Swagger UI |
| `user-service` | пользователи, пароли, роли, вход и выпуск JWT (Spring MVC + JPA) |
| `monitor-service` | проекты, участники, мониторы, теги, инциденты (Spring MVC + JPA) |
| `check-service` | планировщик, HTTP-проверки и их история (WebFlux + R2DBC) |
| `notification-service` | каналы и уведомления (WebFlux + JPA) |
| `report-service` | загрузка, генерация и выдача отчётов CSV/PDF; файлы в MinIO (Spring MVC + JPA, Clean Architecture) |
| `config-server` | конфигурация сервисов из `config-server/src/main/resources/config-repo/` |
| `eureka-server` | реестр сервисов |

Все модули — подпроекты одной Gradle-сборки; подробности — в [lab2.md](lab2.md).
Проверки можно горизонтально масштабировать: инстансы check-service получают разные batch
мониторов через PostgreSQL lease и `FOR UPDATE SKIP LOCKED` в monitor-service.
Тем же способом масштабируется доставка уведомлений: инстансы notification-service
резервируют разные сообщения в PostgreSQL, отправляют их через SMTP, Telegram или webhook
и повторяют временно неудачные попытки.

## Быстрый старт

Нужен Docker с `docker compose` и BuildKit (в Docker Desktop он встроен, для colima —
`brew install docker-buildx`); для запуска тестов — ещё JDK 21.

```bash
cp .env.example .env
# Задайте SUPERVISOR_PASSWORD и отдельные *_CLIENT_SECRET в .env перед запуском.
docker compose up --build
```

Поднимаются PostgreSQL, config-server, eureka-server, user-service, остальные сервисы и gateway — в этом
порядке, каждый после готовности предыдущих. Наружу опубликованы gateway, Eureka и, для
отладки, PostgreSQL; сервисы общаются между собой внутри docker-сети.

| Что | Адрес |
|---|---|
| API | http://localhost:8080/api/v1/... |
| Swagger UI (все сервисы) | http://localhost:8080/swagger-ui.html |
| Eureka | http://localhost:8761 |
| Health gateway | http://localhost:8080/actuator/health |

Начиная с лабораторной 3, API требует `Authorization: Bearer <JWT>`. Первый супервайзер
создаётся из `SUPERVISOR_EMAIL` и `SUPERVISOR_PASSWORD` в `.env`.
Пользователи и хэши паролей хранятся в `user_db`, которой владеет user-service.
Новых пользователей создаёт супервайзер через `POST /api/v1/users` или защищённый
`POST /api/v1/auth/register`.

В Swagger выберите `user-service` → `Authentication`, вызовите `POST /api/v1/auth/login`
и вставьте полученный `access_token` в **Authorize**. Роли и полный пример запросов — в
[lab3.md](lab3.md).

JWT подписывает только user-service (RS256); остальные сервисы проверяют открытый ключ
через JWKS. После перехода с HS256 нужно войти заново.

Сквозная проверка основного сценария по API (нужны `curl` и `jq`, около минуты):

```bash
export SUPERVISOR_PASSWORD='значение-из-.env'
./scripts/demo.sh
```

Circuit Breaker в действии: скрипт останавливает notification-service, размыкает автомат
gateway серией неудачных вызовов, проверяет состояние `OPEN`, показывает, что остальная
система продолжает работать, и запускает сервис снова:

```bash
./scripts/circuit-breaker-demo.sh
```

Для проверки исходящих вызовов из `monitor-service` есть две ручки (доступны также в Swagger):

```bash
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/services/check-service/health
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/services/notification-service/health
```

Каждая вызывает `/actuator/health` соответствующего сервиса через Feign и его Circuit Breaker.
При доступном сервисе возвращается HTTP 200, например:

```json
{"service":"check-service","status":"UP","fallback":false,"circuit_breaker_state":"CLOSED"}
```

При ошибке или открытом автомате возвращается HTTP 503 с `status: "UNAVAILABLE"` и
`fallback: true`. Для ручной проверки остановите нужный сервис и сделайте шесть запросов:

```bash
docker compose stop check-service
for i in {1..6}; do
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/services/check-service/health
  echo
done
docker compose start check-service
```

После пяти ошибок `circuit_breaker_state` станет `OPEN`; дальнейшие запросы не дойдут до
соседнего сервиса. Через 10 секунд автомат перейдёт в `HALF_OPEN`; после восстановления
сервиса и его регистрации в Eureka два успешных пробных вызова вернут его в `CLOSED`.
Для `notification-service` проверка аналогична. Это автоматы отдельных Feign-методов
`CheckHistoryClienthealth` и `NotificationClienthealth` в `monitor-service`; у маршрутов
gateway и у бизнес-вызовов свои автоматы.

### Формат ответов WebFlux

В `check-service` и `notification-service` списковые ручки возвращают `Flux<DTO>`:
по умолчанию JSON-массив, при `Accept: application/x-ndjson` — отдельный JSON-объект
на каждой строке. Параметры `page` и `size` сохранены. Метаданные пагинации передаются
в заголовках: `X-Has-Next`, а для каналов и уведомлений также `X-Total-Count`.
История проверок по-прежнему не выполняет запрос общего количества строк.
Обёрток `Page`/`Slice` и поля `content` в теле этих ответов больше нет.
Создание, чтение и обновление одного канала возвращают `Mono<DTO>` как JSON-объект;
операции без тела — `Mono<Void>`. При создании канала сохранены HTTP 201 и `Location`,
при удалении — HTTP 204.

Тесты и контроль покрытия всех модулей (сборка падает при покрытии строк ниже 70%):

```bash
./gradlew check
```

Интеграционные тесты поднимают PostgreSQL через Testcontainers, поэтому Docker должен быть
запущен. При использовании colima укажите путь к её сокету:

```bash
echo "docker.host=unix://$HOME/.colima/default/docker.sock" >> ~/.testcontainers.properties
```

Остановить стек — `docker compose down`, вместе с данными — `docker compose down -v`.

### Kafka и выбор транспорта

По умолчанию три межсервисных потока используют HTTP, и для обычного запуска Kafka не нужна.
В `.env` можно независимо выбрать `http` или `kafka`:

```dotenv
CHECK_RESULTS_TRANSPORT=kafka
INCIDENT_EVENTS_TRANSPORT=kafka
RESOURCE_EVENTS_TRANSPORT=kafka
REPORT_EVENTS_TRANSPORT=kafka
```

Эти значения Compose передаёт всем бизнес-сервисам, поэтому отправитель и получатель видят
одинаковую настройку. Получение заданий на проверку через `/internal/monitors/due` и чтение API
остаются на HTTP. Допустимы смешанные настройки, например только инциденты через Kafka.

Для запуска с Kafka используйте дополнительный Compose-файл:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.kafka.yml ps
```

Overlay поднимает три брокера Apache Kafka в KRaft-режиме и создаёт топики после их
готовности. У каждого брокера свой именованный том. Кластер доступен только внутри
сети Compose; приведённые ниже команды Kafka запускаются внутри контейнера. Если все
транспорты установлены в `http`, основной стек можно запустить без overlay обычной
командой `docker compose up -d --build`.

| Топик | Сообщения | Потребители |
|---|---|---|
| `checks.completed.v1` | Результаты проверок | monitor-service |
| `incidents.events.v1` | Открытие и закрытие инцидентов | notification-service |
| `resources.events.v1` | Удаление монитора или проекта | check-service, notification-service и report-service |
| `reports.events.v1` | Успешная загрузка файла отчёта | notification-service |
| `config.changes.v1` | Снимки настроек сервисов, топик со сжатием | все бизнес-сервисы и gateway |

У каждого топика три партиции, фактор репликации 3 и `min.insync.replicas=2`.
Начальная настройка также создаёт соответствующие топики с суффиксом `.dlt`.
Репликация позволяет продолжать работу после отказа одного брокера: с `acks=all` запись
подтверждается всеми синхронными репликами, которых должно оставаться не меньше двух.
Три контейнера на одном компьютере не защищают от отказа этого компьютера.

Посмотреть лидера, копии и набор синхронных реплик (ISR) каждой партиции:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml exec kafka-1 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --describe --topic incidents.events.v1
```

Для демонстрации отказа остановите один брокер, повторите команду и затем верните его:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml stop kafka-3
docker compose -f docker-compose.yml -f docker-compose.kafka.yml exec kafka-1 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --describe --topic incidents.events.v1
docker compose -f docker-compose.yml -f docker-compose.kafka.yml start kafka-3
```

После восстановления брокер догонит остальные реплики, и в поле ISR снова появятся три
идентификатора. Для проверки накопления уведомлений остановите `notification-service`,
создайте инцидент проверкой сайта, затем запустите сервис снова:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml stop notification-service
# Вызовите проверку и дождитесь создания инцидента через API.
docker compose -f docker-compose.yml -f docker-compose.kafka.yml start notification-service
docker compose -f docker-compose.yml -f docker-compose.kafka.yml logs -f notification-service
```

При смене транспорта обработайте отложенные события и перезапустите все бизнес-сервисы:
условные компоненты выбираются при старте, накопившиеся сообщения автоматически в другой
транспорт не переносятся. Запись в outbox запоминает выбранный при её создании транспорт:
если переключиться с непустым outbox, соответствующий поток остановится на такой записи.
Чтобы продолжить обработку, верните прежний транспорт, дождитесь опустошения outbox и
только затем переключите конфигурацию. Автоматического fallback с Kafka на HTTP нет.

Изменение данных и запись исходящего события в outbox выполняются в одной транзакции БД.
Отправка повторяется при ошибке с тем же `eventId`; получатель фиксирует ID в inbox
вместе с изменениями и пропускает повторную обработку. Доставка поэтому происходит как
минимум один раз. Некорректный JSON или событие, не прошедшее проверку, отправляется в
`.dlt`; при временной ошибке БД consumer повторяет обработку исходного сообщения.
Гарантия относится к обработке событий: внешняя доставка Email, Telegram и webhook
может повториться, если отправка прошла, а её результат не удалось записать в БД.

Для остановки Kafka-стека используйте те же оба Compose-файла:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml down
```

## Конфигурация: профили и обновление на лету

Файлы лежат в `config-server/src/main/resources/config-repo/`: общие в корне, настройки сервиса
в его папке. Профиль задаётся переменной `SPRING_PROFILES_ACTIVE` (`dev` или `prod`), файлы
профиля дополняют базовые и важнее их:

```text
config-repo/
  application.yml                         всем сервисам
  application-dev.yml                     всем сервисам с профилем dev
  report-service/report-service.yml       только report-service
  report-service/report-service-dev.yml   только report-service с профилем dev
```

При запуске с Kafka-оверлеем эта папка монтируется в `config-server`, и он раз в 5 секунд
проверяет файлы. Если настройки сервиса изменились, в топик `config.changes.v1` уходит их полный
снимок без значений секретов; ключи вида `password`, `secret`, `token` в него не попадают.
Запуск сервиса по-прежнему идёт по HTTP, а через Kafka приходят изменения. Сервис сравнивает снимок
с тем, с чем запустился, и затем:

- `logging.level.*` применяет сразу, без рестарта (список задаёт `config.updates.hot-prefixes`);
- при любом другом изменении, в том числе удалении ключа, завершается с кодом 75, и Compose поднимает
  его заново (`restart: on-failure`). После запуска отличий нет, поэтому рестарт не повторяется.

Сервисы перезапускаются не одновременно: паузу задаёт `config.updates.restart-delay` в файле
сервиса, от 0 до 100 секунд. Поэтому изменение общего файла затрагивает всех по очереди, но готовность
предыдущего сервиса не проверяется. Файл с ошибкой YAML ничего не меняет: сервисы работают с прежними
настройками. Переменные окружения важнее файлов, поэтому значение, заданное в Compose или `.env`,
так поменять нельзя. Активным может быть только один профиль.

```bash
# Сервисы перейдут на DEBUG без рестарта:
sed -i 's/org.awesoma: ${APP_LOG_LEVEL:INFO}/org.awesoma: DEBUG/' config-server/src/main/resources/config-repo/application.yml
# report-service перезапустится примерно через 85 секунд:
sed -i 's/max-page-size: 50/max-page-size: 40/' config-server/src/main/resources/config-repo/report-service/report-service.yml
docker compose -f docker-compose.yml -f docker-compose.kafka.yml logs -f report-service
```

## Отчёты: report-service и MinIO

report-service хранит отчёты проектов: готовые файлы, загруженные пользователями, и отчёты
о мониторинге, которые он строит сам в фоне. Метаданные и задания лежат в `report_db`,
содержимое — в приватном bucket `reports` MinIO. Compose поднимает один сервер MinIO с томом
`minio-data`; `minio-init` при каждом старте создаёт bucket и учётную запись сервиса с правами
только на объекты этого bucket. Root-учётка MinIO сервису не передаётся. Официальные образы
MinIO больше не публикуются, поэтому используется поддерживаемая сообществом сборка
`pgsty/minio` с зафиксированной версией.

| Метод и путь | Назначение | Ответ |
|---|---|---|
| `POST /api/v1/projects/{id}/reports` | загрузить `.csv` (UTF-8) или `.pdf`, поле `file` | `201`, `READY` |
| `POST /api/v1/projects/{id}/reports/generations` | поставить генерацию CSV/PDF в очередь | `202`, `QUEUED` |
| `GET /api/v1/projects/{id}/reports` | список, `X-Total-Count` и `X-Has-Next` | `200` |
| `GET /api/v1/reports/{id}` | метаданные и статус | `200` |
| `GET /api/v1/reports/{id}/content` | скачать готовый файл | `200`; `409` до `READY` |
| `DELETE /api/v1/reports/{id}` | удалить отчёт или отменить генерацию | `202` |

Права проверяет monitor-service: `VIEWER` читает и скачивает, `OPERATOR` и `SUPERVISOR` ещё
загружают, генерируют и удаляют. Файл проверяется на лету: CSV должен быть UTF-8, PDF —
начинаться с `%PDF-`; лимит 10 MiB (`REPORT_MAX_FILE_SIZE`), запрос — 12 MiB, его проверяют и
gateway, и сервис. Отчёт становится `READY` только после подтверждённой записи в MinIO;
SHA-256 считается по самим байтам.

Генерация принимает период с часовым поясом (`[from, to)`, не длиннее 31 дня и не позже
момента запроса), формат `CSV` или `PDF` и необязательный `monitor_ids`. Заголовок
`Idempotency-Key` делает повтор запроса безопасным: те же параметры вернут то же задание,
другие — `409`. Фоновый работник берёт задание через `FOR UPDATE SKIP LOCKED` с lease,
постранично читает данные из внутренних API monitor-service и check-service, считает метрики
и публикует файл, только пока держит lease. Временные сбои повторяются (до 3 попыток), превышение
лимитов и отзыв прав завершают задание `FAILED` с кодом причины; частичный отчёт не публикуется.
Доля успешных проверок — доля результатов `SUCCESS`, а не время доступности.

```bash
TOKEN=... # access_token из POST /api/v1/auth/login
curl -s -X POST http://localhost:8080/api/v1/projects/1/reports/generations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"from":"2026-10-01T00:00:00Z","to":"2026-10-02T00:00:00Z","format":"PDF"}'
```

Удаление проекта доходит до report-service тем же транспортом, что и до остальных сервисов
(`RESOURCE_EVENTS_TRANSPORT`): из Kafka-топика `resources.events.v1` в своей группе
`report-resource-events-v1` или HTTP-вызовом `/internal/integration/events` из outbox
monitor-service. Событие записывается в inbox, проект помечается удалённым, его отчёты
скрываются, а файлы затем удаляет фоновая очистка. Периодическая сверка bucket с метаданными
удаляет объекты, на которые ничего не ссылается.

Сквозная демонстрация — загрузка готового файла, генерация CSV и PDF, опрос статуса и
скачивание (нужны `curl` и `jq`):

```bash
export SUPERVISOR_PASSWORD='значение-из-.env'
./scripts/report-demo.sh
```

## Локальная отправка уведомлений

Доставка намеренно выключена по умолчанию. Сначала скопируйте `.env.example` в `.env`,
настройте нужные транспорты, а затем установите:

```dotenv
NOTIFICATION_DELIVERY_ENABLED=true
```

У проекта должен быть включён `owner_notifications_enabled` (значение по умолчанию), а в
notification-service должен существовать включённый канал `EMAIL` или `TELEGRAM`.

### Email через SMTP

Для настоящего SMTP укажите данные своего почтового провайдера. Типичный сервер с
STARTTLS на порту 587 настраивается так:

```dotenv
EMAIL_ENABLED=true
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=monitoring@example.com
MAIL_PASSWORD=app-password-from-provider
MAIL_FROM=monitoring@example.com
MAIL_SMTP_AUTH=true
MAIL_CONNECTION_TIMEOUT_MS=10000
MAIL_READ_TIMEOUT_MS=10000
MAIL_WRITE_TIMEOUT_MS=10000
MAIL_STARTTLS=true
MAIL_STARTTLS_REQUIRED=true
```

Обычно провайдер требует отдельный пароль приложения, а `MAIL_FROM` должен совпадать с
разрешённым отправителем. Для безопасной локальной проверки без отправки писем наружу
можно запустить SMTP-песочницу Mailpit на хосте (SMTP `1025`, UI `8025`) и оставить:

```dotenv
EMAIL_ENABLED=true
MAIL_HOST=host.docker.internal
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=monitoring@localhost
MAIL_SMTP_AUTH=false
MAIL_CONNECTION_TIMEOUT_MS=10000
MAIL_READ_TIMEOUT_MS=10000
MAIL_WRITE_TIMEOUT_MS=10000
MAIL_STARTTLS=false
MAIL_STARTTLS_REQUIRED=false
```

### Telegram через VPN и HTTP-прокси хоста

VPN должен быть подключён на хостовой машине. На ней же запустите обычный HTTP forward
proxy с поддержкой метода `CONNECT`: он должен слушать не только `127.0.0.1`, а интерфейс,
доступный Docker-контейнерам, например `0.0.0.0:3128`. Ограничьте доступ к этому порту
локальным firewall или Docker-подсетью — публиковать открытый прокси в интернет нельзя.

Получите токен бота у BotFather, напишите боту хотя бы одно сообщение и найдите `chat.id`
в ответе Bot API:

```bash
export TELEGRAM_BOT_TOKEN='token-from-BotFather'
curl --proxy http://127.0.0.1:3128 \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates"
```

Затем перенесите токен и адрес прокси в `.env`:

```dotenv
TELEGRAM_ENABLED=true
TELEGRAM_BOT_TOKEN=123456789:replace-with-real-token
TELEGRAM_API_BASE_URL=https://api.telegram.org
TELEGRAM_PROXY_HOST=host.docker.internal
TELEGRAM_PROXY_PORT=3128
TELEGRAM_REQUEST_TIMEOUT=PT15S
```

`TELEGRAM_PROXY_HOST` применяется только к Bot API: Config Server, Eureka, Feign и webhook
через него не идут. На Docker Desktop и Linux с данным `docker-compose.yml` используется
`host.docker.internal`; для colima при недоступности этого имени укажите
`host.lima.internal`. В target канала `TELEGRAM` передаётся найденный числовой `chat.id`;
для групп и каналов он обычно отрицательный.

После изменения `.env` пересоздайте сервис:

```bash
docker compose up -d --build --force-recreate notification-service
docker compose logs -f notification-service
```

Уведомления доставляются не чаще одного batch каждые 5 секунд. Ошибка записывается в
`last_error`, после пяти попыток статус меняется с `PENDING` на `FAILED`; успешное сообщение
получает `SENT`. Эти поля доступны в `GET /api/v1/incidents/{id}/notifications`.
Доставка — «хотя бы один раз»: если после отправки не удалось записать `SENT`, сообщение
может прийти повторно. Webhook получает `notification_id`, по которому повтор можно отсеять.

## Логирование и Sentry

В логах используются шаблоны SLF4J с параметрами: ID пользователя, монитора, проекта,
инцидента и уведомления, операция, канал и номер попытки. Уровень задаётся через
`APP_LOG_LEVEL` (по умолчанию `INFO`, для подробностей — `DEBUG`).

Ошибки проверок и потерянные межсервисные операции записываются с уровнем `ERROR`.
Повторяемые ошибки доставки — `WARN`, исчерпание попыток — `ERROR`.
Изменения пользователей и автоматические переходы инцидентов записываются после commit;
откат транзакции не создаёт запись об успешном изменении. Пароли, JWT и тела запросов
в новые сообщения логов не передаются. Ошибки транспорта уведомлений записываются
с безопасным сообщением без исходного исключения, которое может содержать токен в URL.

Для отправки ошибок в Sentry укажите в `.env`:

```dotenv
SENTRY_DSN=https://public-key@your-sentry-host/project-id
SENTRY_ENVIRONMENT=local
```

Затем пересоздайте нужные сервисы через `docker compose up -d --build`.
Интеграция Logback отправляет `ERROR` приложения как события Sentry и собирает
`INFO/WARN` как breadcrumbs — контекст перед ошибкой. Имя сервиса передаётся в теге
`service`. Неперехваченные ошибки HTTP обрабатывает Spring-интеграция Sentry.
Без `SENTRY_DSN` отправка отключена; в автоматических тестах DSN всегда пустой.
Настройки общие: `config-server/src/main/resources/config-repo/application.yml`.

## Процесс разработки

- `main` — релизы лабораторных работ, `develop` — интеграционная ветка; в обе изменения
  попадают только через pull request.
- Каждая задача — отдельная ветка `feature/*` или `fix/*` от `develop`, слияние обычным
  merge-коммитом после ревью второго участника.
- Сообщения коммитов по [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/);
  перед коммитом проходит `./gradlew check`.

## Участники

- [awesoma31](https://github.com/awesoma31)
- [nifreebie](https://github.com/nifreebie)
