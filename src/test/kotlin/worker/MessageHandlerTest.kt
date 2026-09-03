package com.doduohor.worker

import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.doduohor.events.IncidentEventPayload
import com.doduohor.events.IntegrationEventType
import com.doduohor.infrastructure.messaging.RabbitMqEvent
import com.doduohor.infrastructure.notification.NotificationSender
import com.doduohor.infrastructure.notification.NotificationSenderResult
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.mongo.EventHistoryRepository
import com.doduohor.repository.mongo.MarkFailedResult
import com.doduohor.repository.mongo.MarkProcessedResult
import com.doduohor.repository.mongo.MarkStartProcessingResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageHandlerTest {
    private val fixedInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedClock = FixedClock(fixedInstant)

    private class FakeEventHistoryRepository : EventHistoryRepository {
        val savedEvents = mutableListOf<EventHistoryDocument>()

        override suspend fun save(event: EventHistoryDocument): EventHistoryDocument {
            savedEvents.add(event)
            return event
        }

        override suspend fun findById(eventId: String): EventHistoryDocument? =
            savedEvents.find { it.eventId == eventId }

        override suspend fun findByType(type: String): List<EventHistoryDocument> =
            savedEvents.filter { it.eventType == type }

        override suspend fun createIndexes() {
        }

        override suspend fun tryStartProcessing(event: EventHistoryDocument): MarkStartProcessingResult {
            val existingEvent = savedEvents.firstOrNull { it.eventId == event.eventId }
            if (existingEvent == null) {
                savedEvents.add(
                    event.copy(
                        status = EventHistoryStatus.PROCESSING,
                        attempt = 1,
                        processedAt = null,
                        errorMessage = null
                    )
                )
                return MarkStartProcessingResult.Started
            }

            return when (existingEvent.status) {
                EventHistoryStatus.PROCESSING -> MarkStartProcessingResult.AlreadyProcessing
                EventHistoryStatus.PROCESSED -> MarkStartProcessingResult.AlreadyProcessed
                EventHistoryStatus.FAILED -> {
                    if (existingEvent.attempt >= 3) {
                        MarkStartProcessingResult.AttemptsExceeded
                    } else {
                        savedEvents.remove(existingEvent)
                        savedEvents.add(
                            existingEvent.copy(
                                status = EventHistoryStatus.PROCESSING,
                                attempt = existingEvent.attempt + 1,
                                processedAt = null,
                                errorMessage = null
                            )
                        )
                        MarkStartProcessingResult.Started
                    }
                }
            }
        }

        override suspend fun markProcessed(eventId: String): MarkProcessedResult {
            val event = savedEvents.firstOrNull { it.eventId == eventId }
                ?: return MarkProcessedResult.NotFound
            if (event.status != EventHistoryStatus.PROCESSING) {
                return MarkProcessedResult.NotProcessing
            }
            savedEvents.remove(event)
            savedEvents.add(event.copy(status = EventHistoryStatus.PROCESSED))
            return MarkProcessedResult.Updated
        }

        override suspend fun markFailed(eventId: String, errorMessage: String): MarkFailedResult {
            val event = savedEvents.firstOrNull { it.eventId == eventId }
                ?: return MarkFailedResult.NotFound
            if (event.status != EventHistoryStatus.PROCESSING) {
                return MarkFailedResult.NotProcessing
            }
            savedEvents.remove(event)
            savedEvents.add(
                event.copy(
                    status = EventHistoryStatus.FAILED,
                    errorMessage = errorMessage
                )
            )
            return MarkFailedResult.Updated
        }
    }
    private class FakeNotificationSender(
        private val result: NotificationSenderResult = NotificationSenderResult.Success
    ) : NotificationSender {
        val sentMessages = mutableListOf<String>()

        override fun send(message: String): NotificationSenderResult {
            sentMessages.add(message)
            return result
        }
    }

    @Test
    fun `valid measurement created event returns success`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val historyRepository = FakeEventHistoryRepository()
        val handler = MessageHandler(notificationSender, historyRepository, fixedClock)
        val message = measurementCreatedMessage()

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(emptyList(), notificationSender.sentMessages)
        assertEquals(1, historyRepository.savedEvents.size)
    }

    @Test
    fun `event history timestamps use injected clock`() = runBlocking {
        val historyRepository = FakeEventHistoryRepository()
        val handler = MessageHandler(FakeNotificationSender(), historyRepository, fixedClock)

        val result = handler.handle(measurementCreatedMessage())

        assertEquals(MessageHandlerResult.Success, result)
        val savedEvent = historyRepository.savedEvents.single()
        assertEquals(fixedInstant.toString(), savedEvent.receivedAt)
        assertEquals(fixedInstant.toString(), savedEvent.processingStartedAt)
    }

    @Test
    fun `unknown event type returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository(), fixedClock)
        val message = """
            {
              "eventType": "BOOKING_CREATED",
              "createdAt": "2026-08-14T12:00:00Z",
              "data": {
                "id": 1
              }
            }
        """.trimIndent()

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
    }

    @Test
    fun `broken json returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository(), fixedClock)
        val message = "{ broken json"

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
    }

    @Test
    fun `measurement created event with invalid payload returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository(), fixedClock)
        val message = Json.encodeToString(
            RabbitMqEvent.create(
                eventType = IntegrationEventType.MEASUREMENT_CREATED,
                clock = fixedClock,
                data = buildJsonObject {
                    put("id", 1L)
                }
            )
        )

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
    }

    @Test
    fun `incident created with high severity sends notification`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository(), fixedClock)
        val message = incidentCreatedMessage(IncidentSeverity.HIGH)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(1, notificationSender.sentMessages.size)
    }

    @Test
    fun `incident created with critical severity sends notification`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository(), fixedClock)
        val message = incidentCreatedMessage(IncidentSeverity.CRITICAL)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(1, notificationSender.sentMessages.size)
    }

    @Test
    fun `duplicate incident event is processed only once`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val historyRepository = FakeEventHistoryRepository()
        val handler = MessageHandler(notificationSender, historyRepository, fixedClock)
        val message = incidentCreatedMessage(
            severity = IncidentSeverity.HIGH,
            eventId = "incident-duplicate-test"
        )

        val firstResult = handler.handle(message)
        val secondResult = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, firstResult)
        assertEquals(MessageHandlerResult.Success, secondResult)
        assertEquals(1, notificationSender.sentMessages.size)
        assertEquals(1, historyRepository.savedEvents.size)
        assertEquals(EventHistoryStatus.PROCESSED, historyRepository.savedEvents.single().status)
    }

    @Test
    fun `incident created with medium severity does not send notification`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository(), fixedClock)
        val message = incidentCreatedMessage(IncidentSeverity.MEDIUM)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(emptyList(), notificationSender.sentMessages)
    }

    @Test
    fun `incident created returns failure when notification sender fails`() = runBlocking {
        val notificationSender = FakeNotificationSender(
            NotificationSenderResult.Failure("test failure")
        )
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository(), fixedClock)
        val message = incidentCreatedMessage(IncidentSeverity.HIGH)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
        assertEquals(1, notificationSender.sentMessages.size)
    }

    @Test
    fun `incident created event with invalid payload returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository(), fixedClock)
        val message = Json.encodeToString(
            RabbitMqEvent.create(
                eventType = IntegrationEventType.INCIDENT_CREATED,
                clock = fixedClock,
                data = buildJsonObject {
                    put("id", 1L)
                }
            )
        )

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
    }

    private fun measurementCreatedMessage(): String =
        Json.encodeToString(
            RabbitMqEvent.create(
                eventType = IntegrationEventType.MEASUREMENT_CREATED,
                clock = fixedClock,
                data = buildJsonObject {
                    put("id", 1L)
                    put("equipmentId", 2L)
                    put("type", "temperature")
                    put("unit", "celsius")
                    put("value", 24.5)
                    put("createdAt", "2026-08-14T12:00:00Z")
                }
            )
        )

    private fun incidentCreatedMessage(
        severity: IncidentSeverity,
        eventId: String? = null
    ): String {
        val payload = IncidentEventPayload(
            id = 1L,
            facilityId = 2L,
            equipmentId = 3L,
            measurementId = 4L,
            type = "HIGH_TEMPERATURE",
            severity = severity,
            status = "OPEN",
            measurementType = "TEMPERATURE",
            measurementUnit = "CELSIUS",
            value = 36.5,
            createdAt = "2026-08-14T12:00:00Z",
            statusChangedAt = "2026-08-14T12:00:00Z"
        )

        val event = eventId?.let {
            RabbitMqEvent(
                eventId = it,
                eventType = IntegrationEventType.INCIDENT_CREATED,
                createdAt = "2026-08-14T12:00:00Z",
                data = Json.encodeToJsonElement(payload)
            )
        } ?: RabbitMqEvent.create(
            eventType = IntegrationEventType.INCIDENT_CREATED,
            clock = fixedClock,
            data = Json.encodeToJsonElement(payload)
        )

        return Json.encodeToString(event)
    }
}
