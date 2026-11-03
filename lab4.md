# Лабораторная работа 4: Kafka, файловый сервис, Clean Architecture

Полный текст задания — в [TASK.md](TASK.md).

| Требование | Реализация | Статус |
|---|---|---|
| Межсервисное взаимодействие через Kafka/RabbitMQ, где это возможно | результаты проверок, события инцидентов и удаления ресурсов идут через Kafka с outbox и inbox; транспорт выбирается настройкой | готово |
| Уведомления через брокер: минимум 3 брокера, несколько топиков, репликация | три брокера Kafka в режиме KRaft, три топика и их `.dlt`, 3 партиции, фактор репликации 3, `min.insync.replicas=2` | готово |
| Микросервис для загрузки и получения файлов | `report-service`: загрузка готовых отчётов CSV/PDF, фоновая генерация отчётов о мониторинге, хранение в MinIO | готово |
| Все микросервисы соответствуют Clean Architecture | `report-service` построен по слоям domain → application → adapters; остальные сервисы ещё в прежней слоистой структуре | в работе |

## Запуск

Скопируйте `.env.example` в `.env` и задайте `SUPERVISOR_PASSWORD` и отдельные секреты
`*_CLIENT_SECRET`, включая новый `REPORT_SERVICE_CLIENT_SECRET`. Учётные данные MinIO
(`MINIO_ROOT_*` для первичной настройки и `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` для сервиса)
тоже задаются там.

```bash
docker compose up -d --build
```

По умолчанию все потоки событий идут по HTTP, и Kafka не нужна. Стек с тремя брокерами
поднимается дополнительным файлом; транспорт каждого потока выбирается в `.env`:

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d --build
```

Устройство топиков, репликации, outbox и inbox, а также команды для демонстрации отказа
брокера описаны в разделе «Kafka и выбор транспорта» [README](README.md).

## Файловый сервис

`report-service` доступен через gateway и общий Swagger UI (`report-service` в списке).
Содержимое хранится в приватном bucket `reports` MinIO, метаданные и задания — в `report_db`.

| Метод и путь | Что делает |
|---|---|
| `POST /api/v1/projects/{id}/reports` | загружает готовый `.csv` (UTF-8) или `.pdf` (поле `file`), отвечает `201` |
| `POST /api/v1/projects/{id}/reports/generations` | ставит генерацию CSV/PDF за период в очередь, отвечает `202` |
| `GET /api/v1/projects/{id}/reports` | список отчётов проекта, `X-Total-Count`, `X-Has-Next` |
| `GET /api/v1/reports/{id}` | метаданные и статус: `UPLOADING`, `QUEUED`, `GENERATING`, `READY`, `FAILED` |
| `GET /api/v1/reports/{id}/content` | скачивает готовый файл; до `READY` — `409` |
| `DELETE /api/v1/reports/{id}` | удаляет отчёт или отменяет генерацию, отвечает `202` |

Что стоит показать:

- загрузку файла: ответ `READY`, SHA-256 в метаданных совпадает с локальным;
- отказы: пустой файл и неверный UTF-8 — `400`, `.txt` и чужой `Content-Type` — `415`,
  файл больше 10 MiB — `413` уже на gateway;
- генерацию: `202` и `QUEUED`, тот же ответ при повторе с тем же `Idempotency-Key`,
  `409` при том же ключе с другими параметрами, опрос до `READY` и скачивание CSV и PDF;
- права: `VIEWER` читает и скачивает, но не загружает и не удаляет;
- удаление проекта: его отчёты исчезают из API, а файлы удаляет фоновая очистка.

Всё это, кроме отказов и удаления проекта, проходит скрипт:

```bash
export SUPERVISOR_PASSWORD='значение-из-.env'
./scripts/report-demo.sh
```

### Как устроена генерация

Запрос проверяет период (с часовым поясом, `[from, to)`, не длиннее 31 дня, не позже момента
запроса), формат и мониторы, затем одной транзакцией сохраняет задание `QUEUED` и ключ
идемпотентности. Работник берёт задание через `FOR UPDATE SKIP LOCKED` с lease, постранично
читает мониторы, проверки и инциденты из внутренних API monitor-service и check-service,
считает метрики и строит файл. Опубликовать `READY` может только попытка, которая всё ещё
держит lease. Временный сбой повторяется (до 3 попыток), превышение лимита или отзыв прав
завершает задание `FAILED` с кодом причины; обрезанный отчёт не публикуется никогда.

| Метрика | Правило |
|---|---|
| проверка в периоде | `checkedAt >= from AND checkedAt < to` |
| доля успешных | `100 × SUCCESS / total`, округление до 2 знаков HALF_UP; это доля проверок, а не uptime |
| время ответа | min, avg и max по всем проверкам с измеренным временем, включая `0` |
| инцидент в периоде | `startedAt < to AND (resolvedAt IS NULL OR resolvedAt > from)` |
| открыт на конец | `resolvedAt IS NULL OR resolvedAt > to` |

## Clean Architecture в report-service

```text
report-service/src/main/java/org/awesoma/report/
  domain/        Report и правила его состояний, период, выбор мониторов, метрики — только JDK
  application/   сценарии (ReportService, GenerationService, GenerationWorker, …) и порты
  adapters/in/   web, приём событий (Kafka и HTTP), планировщик фоновых задач
  adapters/out/  PostgreSQL, MinIO, HTTP-клиенты источников данных, рендереры CSV и PDF
  config/        сборка сценариев из адаптеров
```

Зависимости направлены внутрь: домен не знает Spring и JPA, сценарии работают только через
свои порты (`ReportRepository`, `ObjectStorage`, `ReportDataSource`, `ProjectAccessPort`,
`TransactionRunner`, …) и создаются в `config`, а не через аннотации Spring.

## Тесты

Каждый модуль запускается отдельно, полный прогон всей системы занимает около 25 минут:

```bash
./gradlew :report-service:check
```

Для `report-service` это около 6 минут: 170 тестов на настоящих PostgreSQL, MinIO и Kafka
через Testcontainers, покрытие строк около 95%.
