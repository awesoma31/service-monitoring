# service-monitoring

Групповой учебный проект по курсу «Распределённые системы» (ИТМО). Сервис мониторинга
веб-сайтов, поэтапно эволюционирующий через 4 лабораторные работы. Полный текст задания —
[TASK.md](TASK.md). Детали текущей лабы — [lab1.md](lab1.md).

## Лабораторные работы

| Лаба | Что делаем | Статус |
|---|---|---|
| 1 | Монолит на Spring Boot | 🚧 в разработке — см. [lab1.md](lab1.md) |
| 2 | Декомпозиция на микросервисы (Eureka, Config Server, Gateway, Feign, Circuit Breaker) | не начато |
| 3 | Авторизация: Spring Security + JWT, ролевая модель | не начато |
| 4 | Межсервисное взаимодействие через Kafka/RabbitMQ, файловый микросервис, Clean Architecture | не начато |

## Предметная область

Сервис мониторинга веб-сайтов: пользователи создают проекты, в проектах заводят мониторы
(URL + интервал + таймаут + ожидаемый статус-код), система периодически их проверяет,
при падении открывает инцидент и рассылает уведомления по каналам проекта, считает отчёты
по uptime. Подробная схема БД — в [lab1.md](lab1.md).

## Технологический стек (лаба 1)

- Java 21, Gradle (Kotlin DSL), версии — через Spring Boot BOM
- Spring Boot (Web, Data JPA, Validation)
- PostgreSQL, Liquibase (YAML changelog'и)
- MapStruct (DTO-мапперы), Lombok
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito (юнит-тесты), Testcontainers (интеграционные тесты)
- JaCoCo, порог покрытия — 70%, встроен в задачу `check`
- Docker / Docker Compose

Базовый Java-пакет: `org.awesoma.monitoring`.

## Структура репозитория (актуализируется по ходу разработки)

```
org.awesoma.monitoring
 ├── MonitoringApplication.java
 ├── config/            // OpenApiConfig, RestClientConfig, SchedulingConfig
 ├── web/
 │    ├── controller/    // REST-контроллеры
 │    ├── dto/           // request/response DTO, без сущностей в контроллерах
 │    ├── mapper/        // MapStruct
 │    └── exception/     // GlobalExceptionHandler (@RestControllerAdvice, ProblemDetail)
 ├── service/            // бизнес-логика
 ├── repository/         // Spring Data JPA
 ├── domain/
 │    ├── entity/
 │    └── enums/
 └── checker/            // планировщик проверок (@Scheduled), изолирован —
                         // в лабе 2 переезжает в отдельный реактивный сервис
```

## Git-flow

Соблюдается с первого коммита, единая схема для всех лаб.

**Ветки:**
- `main` — только релизные срезы (сдача лабы). Прямых коммитов нет; мерж только через PR
  из `develop`, без force-push, без удаления без явного согласия обоих участников.
- `develop` — интеграционная ветка текущей лабы. Прямых коммитов нет; мерж только через PR
  из `feature/*` / `fix/*`.
- `feature/lab<N>-<задача>`, `fix/lab<N>-<баг>` — одна ветка = одна задача, всегда создаётся
  от актуального `develop`.

**Работа вдвоём:**
- Перед стартом новой ветки — согласовать с напарником, кто какую задачу берёт (чтобы не
  редактировать одни и те же пакеты одновременно), и сделать `git pull origin develop`.
- Список задач/веток для текущей лабы — в соответствующем `labN.md`.

**Коммиты:**
- [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) на **каждый** коммит:
  `feat(monitor): add CRUD endpoints`, `fix(incident): prevent duplicate open incident`,
  `test(project): add testcontainers integration test`, `chore(gradle): add jacoco coverage check`.
- Перед каждым коммитом — `./gradlew check` должен проходить (тесты + порог JaCoCo).

**Мерж:**
- `feature/* → develop` — через Pull Request на GitHub, с ревью от второго участника.
- Стратегия мержа — обычный merge commit (`--no-ff`), не squash: история должна сохранять
  все conventional-commit'ы из ветки.
- Конфликты разрешаются локально в feature-ветке до мержа.
- `develop → main` — отдельный PR в конце лабы (например, `Release: Lab 1`), опционально
  с тегом (`v1.0-lab1`).

## Требования

- JDK 21
- Docker с поддержкой `docker compose`

## Запуск

```bash
cp .env.example .env   # при необходимости поправьте значения
docker compose up --build
```

Поднимаются Postgres и приложение, причём приложение стартует только после того,
как healthcheck Postgres сообщит о готовности базы.

| Что | Адрес |
|---|---|
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

Остановить — `docker compose down`, вместе с данными БД — `docker compose down -v`.

Конфигурация задаётся только переменными окружения (см. `.env.example`); значений
по умолчанию для доступа к БД нет, поэтому без них приложение не стартует.

## Проверка вручную

На запущенном стеке `scripts/demo.sh` проходит основной сценарий через API: создание
пользователя и проекта, конфликты (409), валидацию (400), обе формы пагинации и полный
цикл инцидента — монитор падает, планировщик открывает инцидент и ставит уведомления,
затем монитор восстанавливается и инцидент закрывается сам. Нужны `curl` и `jq`.

```bash
./scripts/demo.sh
```

Прогон занимает около минуты: скрипт ждёт двух проходов планировщика. Каждый запуск
создаёт свои данные, так что чистить базу между запусками не нужно.

## Сборка и тесты

```bash
./gradlew check
```

`check` прогоняет тесты и проверяет покрытие: при line coverage ниже 70% сборка
падает. HTML-отчёт — `build/reports/jacoco/test/html/index.html`.

Интеграционные тесты поднимают Postgres через Testcontainers, поэтому Docker должен
быть запущен. С Docker Desktop настройка не нужна. Для colima укажите путь к её сокету —
иначе тесты падают с `Could not find a valid Docker environment`:

```bash
echo "docker.host=unix://$HOME/.colima/default/docker.sock" >> ~/.testcontainers.properties
```
