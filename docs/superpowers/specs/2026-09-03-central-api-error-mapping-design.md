# Central API Error Mapping — дизайн

## Цель

Сделать единый mapping типизированных результатов application/domain в HTTP-ответы, сохранив существующий публичный контракт API: HTTP status codes, поля `ErrorResponse`, имена ошибок и тексты.

## Границы

Изменяются только HTTP-слой и его тесты. Доменные модели и sealed-result-типы сервисов не меняются. Обработка исключений Ktor в `StatusPages.kt` остаётся отдельным механизмом для ошибок границы запроса и неожиданных исключений.

## Решение

Добавить в `api/mapper` единый `ApiErrorMapper` с небольшим значением `ApiError(status: HttpStatusCode, response: ErrorResponse)`. Mapper принимает результаты сервисов и возвращает либо соответствующую ошибку, либо `null` для успешного результата. Вложенный `MonitoringServiceResult` маппится через тот же механизм для результата создания измерения.

В `Routing.kt` оставить parsing входных DTO и выбор успешного response DTO, но удалить прямое создание `ErrorResponse` для service/domain results. Маршрут вызывает общий helper `respondApiError`, что делает границу HTTP единообразной и сохраняет сериализацию через Ktor.

Boundary errors, не являющиеся service results (невалидные path/query parameters, неподдерживаемый `Content-Type`, неожиданный body для activate), используют тот же helper и фиксированные существующие контракты.

## Категории mapping

- `InvalidInput`: 400 Bad Request;
- `NotFound`: 404 Not Found;
- `Conflict`: 409 Conflict;
- `InternalFailure`: 500 Internal Server Error;
- `OutboxPersistenceError`: 500 Internal Server Error.

Для старых специализированных кодов (`invalidFacilityId`, `notFindEquipmentId`, `invalidValue` и т. п.) сохраняются текущие значения; категория является внутренней классификацией mapper-а и не меняет JSON-контракт.

## Проверки

Добавляются unit-тесты mapper-а на все ветви service results и API-тесты на основные ошибки Facility, Booking, Equipment, Measurement и Incident. Существующие ServerTest сохраняются и должны проходить без изменения успешных ответов. После каждого этапа выполняются целевые тесты и полный `./gradlew test --no-daemon --console=plain`.

## Ограничения

- Не менять публичные status codes, `ErrorResponse.code`, `name` или `text` без доказанной необходимости.
- Не добавлять новые зависимости.
- Не включать Telegram, database или RabbitMQ в unit-тесты mapper-а.
- Все production-изменения делать после соответствующего failing test по TDD.
