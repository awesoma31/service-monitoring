# service-monitoring

Сервис мониторинга веб-сайтов — групповой проект по курсу «Распределённые системы» (ИТМО).
Система периодически проверяет доступность сайтов, при сбое открывает инцидент и оповещает
по каналам проекта. Проект развивается через четыре лабораторные работы: от монолита к
микросервисам.

## Лабораторные работы

| № | Содержание | Статус | Материалы |
|---|---|---|---|
| 1 | Монолит на Spring Boot: REST API, PostgreSQL, Liquibase, транзакции, пагинация, тесты | готово | [описание](lab1.md), [отчёт](docs/report-lab1.md), релиз `v1.1.0-lab1` |
| 2 | Декомпозиция на микросервисы: Eureka, Config Server, Gateway, Feign, Circuit Breaker, Reactor | готово | [описание](lab2.md), [отчёт](docs/report-lab2.md) |
| 3 | Аутентификация: Spring Security, JWT, ролевая модель | не начато | |
| 4 | Обмен сообщениями через Kafka/RabbitMQ, файловый сервис, Clean Architecture | не начато | |

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
| `monitor-service` | пользователи, проекты, мониторы, теги, инциденты (Spring MVC + JPA) |
| `check-service` | планировщик, HTTP-проверки и их история (WebFlux + R2DBC) |
| `notification-service` | каналы и уведомления (WebFlux + JPA) |
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
docker compose up --build
```

Поднимаются PostgreSQL, config-server, eureka-server, три сервиса и gateway — в этом
порядке, каждый после готовности предыдущих. Наружу опубликованы gateway, Eureka и, для
отладки, PostgreSQL; сервисы общаются между собой внутри docker-сети.

| Что | Адрес |
|---|---|
| API | http://localhost:8080/api/v1/... |
| Swagger UI (все сервисы) | http://localhost:8080/swagger-ui.html |
| Eureka | http://localhost:8761 |
| Health gateway | http://localhost:8080/actuator/health |

Сквозная проверка основного сценария по API (нужны `curl` и `jq`, около минуты):

```bash
./scripts/demo.sh
```

Circuit Breaker в действии: скрипт останавливает notification-service, размыкает автомат
gateway серией неудачных вызовов, проверяет состояние `OPEN`, показывает, что остальная
система продолжает работать, и запускает сервис снова:

```bash
./scripts/circuit-breaker-demo.sh
```

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
