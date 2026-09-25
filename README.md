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
каналов проекта, при восстановлении закрывает инцидент.

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

Circuit Breaker в действии: скрипт останавливает notification-service, показывает, что
остальная система продолжает работать, и запускает сервис снова:

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
