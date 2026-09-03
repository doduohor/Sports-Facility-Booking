# Sports Facility Booking

Backend-система для управления спортивными объектами, оборудованием, бронированиями и показаниями датчиков. При создании показания система проверяет совместимость оборудования и измерения, применяет пороговые правила инцидентов и публикует интеграционные события через transactional outbox.

Проект состоит из двух процессов:

- HTTP API на Kotlin/JVM, Ktor и Netty;
- отдельный worker, который публикует outbox-события в RabbitMQ, принимает их, сохраняет историю в MongoDB и отправляет важные инциденты в Telegram.

## Возможности

- создание и чтение спортивных объектов, бронирований, оборудования, измерений и инцидентов;
- активация объекта и проверка его статуса перед бронированием;
- запрет пересекающихся бронирований на уровне PostgreSQL через `EXCLUDE USING gist`;
- валидация диапазонов, единиц измерения и возможностей оборудования;
- автоматическое создание инцидента по пороговым правилам;
- атомарное сохранение измерения и outbox-событий в PostgreSQL;
- публикация событий с ограниченными повторами и dead-letter очередью;
- идемпотентная обработка событий в MongoDB по `eventId`;
- Telegram-уведомления для `HIGH` и `CRITICAL` инцидентов;
- SSE-поток серверных событий;
- единый формат API-ошибок `ErrorResponse`.

## Архитектура

```text
HTTP-клиенты ──► Ktor API ──► Services ──► Domain policies
                     │              │
                     │              └── PostgreSQL transaction
                     │                         ├── domain tables
                     │                         └── outbox_events
                     └── EventPublisher ──► SSE

PostgreSQL outbox ──► Worker/OutboxPublisher ──► RabbitMQ exchange/queue
                                                   │
                                                   ├──► DLX/DLQ при ошибке
                                                   └──► MessageHandler
                                                            ├── MongoDB event_history
                                                            └── Telegram API
```

### Ответственность пакетов

| Пакет | Назначение |
| --- | --- |
| `api` | DTO, разбор входных enum-значений и централизованное отображение ошибок. |
| `Routing.kt` | HTTP-маршруты, Basic Auth для записи и формирование ответов. |
| `domain` | Сущности, value objects, статусы и политики без инфраструктурных зависимостей. |
| `service` | Прикладные сценарии, проверки связей, бронирование и monitoring. |
| `repository` | Контракты хранилищ и транзакционная абстракция. |
| `infrastructure` | PostgreSQL/Exposed/Flyway, MongoDB, RabbitMQ, Telegram и часы. |
| `events` | Интеграционные события, outbox-модели и SSE. |
| `worker` | Жизненный цикл worker, публикация outbox и обработка RabbitMQ. |

Зависимости собираются через Koin в `di/AppModule.kt`. API подключается к PostgreSQL и MongoDB; worker создаёт собственные подключения к PostgreSQL, RabbitMQ и MongoDB.

## Доменная модель

### Спортивные объекты

Типы: `GYM`, `POOL`, `STADIUM`. Статусы: `INACTIVE`, `ACTIVE`, `MAINTENANCE`.

Новый объект неактивен. Бронирование разрешено только для `ACTIVE`. Переход выполняется через `PUT /api/facilities/{facilityId}/activate`; объект в `MAINTENANCE` активировать нельзя, повторная активация даёт конфликт.

### Оборудование

Типы: `VENTILATION`, `HEATING`, `WATER_SUPPLY`, `FIRE_ALARM`.

| Тип оборудования | Разрешённые измерения |
| --- | --- |
| `VENTILATION` | `TEMPERATURE`, `HUMIDITY`, `CO2` |
| `HEATING` | `TEMPERATURE` |
| `WATER_SUPPLY` | `TEMPERATURE` |
| `FIRE_ALARM` | `SMOKE`, `TEMPERATURE` |

Статусы модели: `ACTIVE`, `DISABLED`, `REPAIR`, `DEFECTIVE`, `NEEDS_REPLACEMENT`. Операции смены статуса реализованы в домене, но HTTP-маршруты для них пока не опубликованы.

### Измерения

Типы: `TEMPERATURE`, `HUMIDITY`, `CO2`, `SMOKE`. Единицы: `CELSIUS`, `PERCENT`, `PPM`.

| Тип | Единица | Допустимый диапазон |
| --- | --- | --- |
| `TEMPERATURE` | `CELSIUS` | `[-50, 100)` |
| `HUMIDITY` | `PERCENT` | `[0, 100)` |
| `CO2` | `PPM` | `[0, 10000)` |
| `SMOKE` | `PERCENT` | `[0, 100)` |

Диапазоны полуоткрытые: нижняя граница включается, верхняя — нет. `NaN` и бесконечности отклоняются.

### Инциденты

Типы: `SMOKE_DETECTED`, `HIGH_CO2`, `HIGH_TEMPERATURE`, `LOW_TEMPERATURE`, `HIGH_HUMIDITY`, `LOW_HUMIDITY`.

Уровни: `MEDIUM`, `HIGH`, `CRITICAL`. Статусы: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `FALSE_POSITIVE`, `REOPENED`.

Пороговые полосы находятся в `domain/policy/IncidentPolicy.kt`. При создании политики проверяются сортировка, непрерывность и отсутствие пересечений:

| Измерение | Диапазоны и уровень |
| --- | --- |
| Температура ниже нормы | `[10,16)` `CRITICAL`, `[16,18)` `HIGH`, `[18,20)` `MEDIUM` |
| Температура выше нормы | `[25,28)` `MEDIUM`, `[28,32)` `HIGH`, `[32,40)` `CRITICAL` |
| Влажность ниже нормы | `[5,10)` `CRITICAL`, `[10,15)` `HIGH`, `[15,20)` `MEDIUM` |
| Влажность выше нормы | `[60,65)` `MEDIUM`, `[65,70)` `HIGH`, `[70,90)` `CRITICAL` |
| `CO2` | `[5000,6000)` `MEDIUM`, `[6000,7000)` `HIGH`, `[7000,8000)` `CRITICAL` |
| Дым | `[5,10)` `MEDIUM`, `[10,15)` `HIGH`, `[15,50)` `CRITICAL` |

## Monitoring и transactional outbox

`POST /api/measurements` выполняет один прикладной сценарий:

1. Проверяет JSON, тип и единицу измерения.
2. В транзакции PostgreSQL проверяет оборудование, совместимость, единицу и диапазон.
3. Сохраняет измерение.
4. Применяет `IncidentPolicy` и при необходимости создаёт инцидент.
5. Формирует `MEASUREMENT_CREATED` и, если создан инцидент, `INCIDENT_CREATED`.
6. Сохраняет outbox-записи в той же транзакции.
7. После commit публикует серверные события в SSE.

Outbox является источником истины для интеграционного обмена; SSE публикуется после транзакции и не заменяет durable-доставку.

### Состояния доставки

`outbox_events` содержит уникальный UUID `event_id`, тип, JSONB payload, статусы `NEW`, `PROCESSING`, `PUBLISHED`, `FAILED`, время публикации, номер попытки и ошибку.

`OutboxPublisher` атомарно claim-ит событие, публикует его и отмечает `PUBLISHED`. Ошибка переводит запись в `FAILED`; максимум — `3` попытки. RabbitMQ использует direct exchange, очередь `sports.measurements`, routing key `measurement.created`, DLX и DLQ. Consumer работает с `basicQos(1)` и ручным `basicAck`/`basicNack`; ошибка обработки уходит в DLQ без повторной постановки в основную очередь.

В MongoDB коллекция `event_history` имеет уникальный индекс по `eventId`. Статусы `PROCESSING`, `PROCESSED`, `FAILED` позволяют подавлять дубликаты. Зависший `PROCESSING` можно перезапустить через 600 секунд, всего предусмотрено 3 попытки.

Worker отправляет в Telegram только инциденты уровней `HIGH` и `CRITICAL`; ошибка Telegram делает обработку события неуспешной.

## HTTP API

Операции записи защищены Basic Auth (`auth-basic`). GET-маршруты чтения и SSE в текущей конфигурации публичны. Списки возвращаются в формате `{ "items": [...] }`.

Ошибки имеют формат:

```json
{
  "code": 404,
  "name": "notFindFacilityId",
  "text": "The specified Facility ID does not exist"
}
```

Enum-значения принимаются без учёта регистра и внешних пробелов.

| Метод | Путь | Назначение |
| --- | --- | --- |
| `GET` | `/`, `/health`, `/json/kotlinx-serialization` | Служебные проверки. |
| `GET` | `/api/events/stream` | SSE-поток. |
| `POST` | `/api/facilities` | Создать объект. |
| `GET` | `/api/facilities` | Список объектов. |
| `GET` | `/api/facilities/{facilityId}` | Объект по ID. |
| `PUT` | `/api/facilities/{facilityId}/activate` | Активировать объект. |
| `GET` | `/api/facilities/{facilityId}/readings` | Контракт чтения показаний с `limit`; реальная выдача пока не реализована. |
| `GET` | `/api/facilities/{facilityId}/bookings` | Бронирования объекта. |
| `GET` | `/api/facilities/{facilityId}/equipments` | Оборудование объекта. |
| `GET` | `/api/facilities/{facilityId}/incidents` | Инциденты объекта. |
| `POST` | `/api/bookings` | Создать бронирование. |
| `GET` | `/api/bookings`, `/api/bookings/{bookingId}` | Список и бронирование по ID. |
| `POST` | `/api/equipments` | Создать оборудование. |
| `GET` | `/api/equipments`, `/api/equipments/{equipmentId}` | Список и оборудование по ID. |
| `GET` | `/api/equipments/{equipmentId}/measurements` | Измерения оборудования. |
| `GET` | `/api/equipments/{equipmentId}/incidents` | Инциденты оборудования. |
| `POST` | `/api/measurements` | Создать измерение и запустить monitoring. |
| `GET` | `/api/measurements`, `/api/measurements/{measurementId}` | Список и измерение по ID. |
| `POST` | `/api/incidents` | Создать инцидент вручную. |
| `GET` | `/api/incidents`, `/api/incidents/{incidentId}` | Список и инцидент по ID. |

Идентификатор клиента для бронирования должен быть в диапазоне `900..1000`; длительность — от 1 до 12 часов. Операции `startProgress`, `resolve`, `close`, `reopen`, `markFalsePositive` есть в `IncidentService`, но HTTP-маршруты жизненного цикла инцидента пока отсутствуют.

## Хранение данных

Flyway-миграции `V1`–`V8` создают таблицы `facilities`, `bookings`, `equipments`, `measurements`, `incidents`, `outbox_events`, индексы по внешним ключам, внешние ключи и ограничение непересечения бронирований. PostgreSQL подключается через HikariCP (`maximumPoolSize = 5`), при старте запускаются миграции и `SELECT 1`. PostgreSQL обязателен.

MongoDB используется для `event_history` и не хранит доменные сущности API.

## Стек и запуск

Стек: Kotlin `2.4.0`, JVM 21, Ktor `3.5.0`, Netty, Koin `4.2.2`, PostgreSQL 17, Exposed `1.3.1`, Flyway `13.0.0`, HikariCP `7.0.2`, RabbitMQ `4.3.4`, MongoDB Atlas Local `8.0.0`, kotlinx.serialization, Logback, Testcontainers и JUnit Jupiter.

Требования: JDK 21; Docker Engine и Docker Compose v2 для полного стека; `bash`, `curl`, `jq` для проверочных скриптов.

```bash
./gradlew test
./gradlew build
./gradlew run
./gradlew runWorker
```

Для полного окружения:

```bash
cp .env.example .env
docker compose --env-file .env up --build
curl http://localhost:8080/health
```

Сервисы Compose: `api` на `8080`, `postgres` на `5432`, RabbitMQ на `5672` и management UI на `15672`, MongoDB на `27017`; `worker` портов не публикует.

Изолированные проверки:

```bash
bash scripts/docker-smoke.sh
bash scripts/docker-e2e.sh
```

Smoke проверяет healthcheck и сборку образа. E2E проверяет цепочку PostgreSQL outbox → RabbitMQ → MongoDB и защиту от повторной обработки.

## Конфигурация и безопасность

Основные переменные: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`; `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`, `RABBIT_EXCHANGE`, `RABBIT_QUEUE`, `RABBIT_ROUTING_KEY` и DLQ-параметры; `MONGO_HOST`, `MONGO_PORT`, `MONGO_USERNAME`, `MONGO_PASSWORD`, `MONGO_DATABASE`; `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`; `BASIC_AUTH_USERNAME`, `BASIC_AUTH_PASSWORD`; `API_PORT`.

Значения по умолчанию находятся в `application.conf`, Docker-значения — в `.env.example` и `compose.yaml`. Реальные пароли и токены нельзя коммитить; Basic Auth следует заменить на production-механизм и вынести внешний TLS на ingress.

## Тестирование

Набор содержит unit-тесты домена и политик, сервисные тесты, HTTP-контракты, тесты сериализации и аутентификации, контрактные тесты репозиториев, интеграционные Testcontainers-тесты PostgreSQL/MongoDB/RabbitMQ, проверки worker lifecycle, повторов, идемпотентности и outbox-переходов, а также Docker smoke/e2e.

```bash
./gradlew test
```

Для Testcontainers необходим доступный Docker daemon.

## Ограничения и технический долг

- `EventPublisher` использует обычный `mutableSetOf`; для production-нагрузки нужны потокобезопасное хранение подписчиков и продуманная политика backpressure.
- У outbox-события в `PROCESSING` нет lease timestamp, поэтому аварийно остановленная публикация не восстанавливается автоматически.
- SSE хранится только в памяти процесса: нет replay, durable subscriptions и горизонтальной координации.
- Нет OpenAPI/Swagger и версионирования `/api`.
- Часть пограничных ошибок маршрутизации формируется напрямую, хотя ошибки прикладного слоя централизованы через `ApiErrorMapper`.
- HTTP-операции смены статусов оборудования и жизненного цикла инцидента ещё не опубликованы.

## Структура репозитория

```text
src/main/kotlin/{api,domain,service,repository,infrastructure,events,worker}
src/main/kotlin/{Routing.kt,StatusPages.kt,Security.kt,main.kt}
src/main/resources/application.conf
src/main/resources/db/migration/V1__...–V8__...
scripts/{docker-smoke.sh,docker-e2e.sh}
```

Проект находится на стадии MVP/production-like разработки. Отдельный файл лицензии в репозитории не заявлен.

## Полный воспроизводимый запуск для технического лида

Ниже — последовательность для запуска всего контура: API, worker, PostgreSQL, RabbitMQ, MongoDB, smoke/e2e и Gradle-тестов.

### 1. Среда и предварительные требования

Рекомендуемая среда — Linux, macOS или WSL2 на Windows. На Windows без WSL2 нельзя напрямую выполнить скрипты `scripts/*.sh`; Docker Desktop должен быть установлен и запущен с включённой интеграцией WSL2.

Необходимо установить:

- Git;
- Docker Engine или Docker Desktop с Compose v2 и правом текущего пользователя обращаться к Docker daemon;
- JDK 21 для запуска Gradle-тестов вне контейнера;
- `bash`, `curl` и `jq` для smoke/e2e-скриптов;
- доступ в интернет при первом запуске: Gradle загрузит зависимости, Docker — базовые образы и образы инфраструктуры.

Проверка окружения:

```bash
git --version
docker version
docker compose version
java -version
bash --version
curl --version
jq --version
```

Перед запуском освободите локальные порты `8080`, `5432`, `5672`, `15672` и `27017`, либо измените соответствующие значения в `.env`.

### 2. Получение проекта и исправление известного блокера

```bash
git clone <URL_репозитория> sports-facility-booking
cd sports-facility-booking
cp .env.example .env
```

В текущей версии репозитория файл `gradlew` имеет CRLF-переводы строк. На Linux, macOS и в Docker Linux-контейнере это приводит к ошибке `bad interpreter: /bin/sh^M`. Поэтому до первого запуска нормализуйте wrapper в своей рабочей копии:

```bash
tr -d '\r' < gradlew > gradlew.lf
mv gradlew.lf gradlew
chmod +x gradlew
```

Это необходимый временный обход. Без него проект **не запускается полностью из чистой Linux/WSL2/Docker-среды**, потому что `Dockerfile` также выполняет `./gradlew` при сборке образа. На Windows `gradlew.bat` подходит для Gradle, но Docker-сборка всё равно использует Linux-wrapper и требует нормализации `gradlew`.

Проверьте wrapper и конфигурацию Compose:

```bash
./gradlew --version
docker compose --env-file .env config --quiet
```

### 3. Настройка локальных параметров

`.env.example` содержит безопасные только для локальной разработки значения. Для демонстрации достаточно оставить их как есть. Для реального Telegram-уведомления замените в `.env`:

```dotenv
TELEGRAM_BOT_TOKEN=<токен_бота>
TELEGRAM_CHAT_ID=<числовой_id_чата>
```

Также можно изменить `API_PORT`, PostgreSQL-, RabbitMQ- и MongoDB-порты, если локальные порты уже заняты. Не коммитьте `.env` с реальными секретами.

### 4. Запуск полного стека

```bash
docker compose --env-file .env up --build -d
docker compose --env-file .env ps
```

Ожидаемое состояние: `postgres`, `rabbit`, `mongo` и `api` имеют `healthy`; `worker` находится в состоянии `running`.

Проверка API:

```bash
curl --fail http://localhost:8080/health
```

Ожидаемый ответ:

```json
{"name":"Health","text":"UP"}
```

Для просмотра логов:

```bash
docker compose --env-file .env logs -f api worker
```

RabbitMQ Management UI доступен по адресу `http://localhost:15672`; учётные данные берутся из `RABBIT_USER` и `RABBIT_PASSWORD` в `.env`.

### 5. Проверка рабочего сценария API

Ниже — минимальный сценарий создания объекта, оборудования и измерения. Он создаёт низкоприоритетное измерение температуры, чтобы не требовать рабочего Telegram-токена.

```bash
API_URL=http://localhost:8080

FACILITY_ID=$(curl --fail --silent --show-error \
  --user admin:admin \
  --header 'Content-Type: application/json' \
  --data '{"name":"Tech Lead Demo Pool","type":"POOL"}' \
  "$API_URL/api/facilities" | jq --raw-output '.id')

EQUIPMENT_ID=$(curl --fail --silent --show-error \
  --user admin:admin \
  --header 'Content-Type: application/json' \
  --data "{\"facilityId\":$FACILITY_ID,\"name\":\"Demo temperature sensor\",\"type\":\"VENTILATION\"}" \
  "$API_URL/api/equipments" | jq --raw-output '.id')

curl --fail --silent --show-error \
  --user admin:admin \
  --header 'Content-Type: application/json' \
  --data "{\"equipmentId\":$EQUIPMENT_ID,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":22.0}" \
  "$API_URL/api/measurements" | jq
```

После этого можно проверить данные и outbox:

```bash
curl --fail --silent "$API_URL/api/measurements" | jq
docker compose --env-file .env exec -T postgres \
  psql -U sports -d sports_facility_booking \
  -c 'SELECT event_id, event_type, status, attempt, created_at, published_at FROM outbox_events ORDER BY id;'
```

### 6. Полный прогон проверок

Сначала остановите основной Compose-стек либо убедитесь, что его порты не конфликтуют с изолированными скриптами. Затем выполните:

```bash
./gradlew test
bash scripts/docker-smoke.sh
bash scripts/docker-e2e.sh
```

`./gradlew test` запускает unit-, контрактные и Testcontainers-интеграционные тесты. Ему нужен доступ к Docker daemon. `docker-smoke.sh` и `docker-e2e.sh` используют отдельные Compose project names, печатают логи при ошибке и удаляют только собственные контейнеры и volumes.

Обратите внимание: e2e-скрипт всегда читает `.env.example`, а не ваш `.env`. Он не должен использовать реальные Telegram-секреты.

### 7. Остановка и очистка

Остановка без удаления данных:

```bash
docker compose --env-file .env down
```

Полная очистка локальных данных PostgreSQL, RabbitMQ и MongoDB:

```bash
docker compose --env-file .env down --volumes --remove-orphans
```

Последняя команда удаляет только volumes этого Compose-проекта; данные после неё восстановить нельзя.

### Что в текущем окружении проекта отсутствует

В среде, где выполнялась проверка документации, доступны JDK 21, Docker Compose и `jq`, но текущему пользователю отказано в доступе к Docker daemon (`permission denied ... /var/run/docker.sock`). Поэтому здесь нельзя честно подтвердить фактический запуск контейнеров, smoke/e2e и Testcontainers. На рабочей машине технического лида это устраняется запуском Docker Desktop/Engine и предоставлением пользователю доступа к daemon (например, через корректно настроенную группу `docker` на Linux).
