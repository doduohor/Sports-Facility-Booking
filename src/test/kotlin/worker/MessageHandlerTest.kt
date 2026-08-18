package com.doduohor.worker

import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.EventHistoryStatus
import com.doduohor.events.IncidentEventPayload
import com.doduohor.infrastructure.messaging.RabbitMqEvent
import com.doduohor.infrastructure.messaging.RabbitMqEventType
import com.doduohor.infrastructure.notification.NotificationSender
import com.doduohor.infrastructure.notification.NotificationSenderResult
import com.doduohor.repository.mongo.EventHistoryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageHandlerTest {
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

        override suspend fun tryStartProcessing(event: EventHistoryDocument): Boolean {
            if (savedEvents.any { it.eventId == event.eventId }) {
                return false
            }
            savedEvents.add(event)
            return true
        }

        override suspend fun markProcessed(eventId: String) {
            val event = savedEvents.first { it.eventId == eventId }
            savedEvents.remove(event)
            savedEvents.add(event.copy(status = EventHistoryStatus.PROCESSED))
        }

        override suspend fun markFailed(eventId: String, errorMessage: String) {
            val event = savedEvents.first { it.eventId == eventId }
            savedEvents.remove(event)
            savedEvents.add(
                event.copy(
                    status = EventHistoryStatus.FAILED,
                    errorMessage = errorMessage
                )
            )
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
        val handler = MessageHandler(notificationSender, historyRepository)
        val message = measurementCreatedMessage()

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(emptyList(), notificationSender.sentMessages)
        assertEquals(1, historyRepository.savedEvents.size)
    }

    @Test
    fun `unknown event type returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository())
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
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository())
        val message = "{ broken json"

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
    }

    @Test
    fun `measurement created event with invalid payload returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository())
        val message = Json.encodeToString(
            RabbitMqEvent.create(
                eventType = RabbitMqEventType.MEASUREMENT_CREATED,
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
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository())
        val message = incidentCreatedMessage(IncidentSeverity.HIGH)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(1, notificationSender.sentMessages.size)
    }

    @Test
    fun `incident created with critical severity sends notification`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository())
        val message = incidentCreatedMessage(IncidentSeverity.CRITICAL)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Success, result)
        assertEquals(1, notificationSender.sentMessages.size)
    }

    @Test
    fun `duplicate incident event is processed only once`() = runBlocking {
        val notificationSender = FakeNotificationSender()
        val historyRepository = FakeEventHistoryRepository()
        val handler = MessageHandler(notificationSender, historyRepository)
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
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository())
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
        val handler = MessageHandler(notificationSender, FakeEventHistoryRepository())
        val message = incidentCreatedMessage(IncidentSeverity.HIGH)

        val result = handler.handle(message)

        assertEquals(MessageHandlerResult.Failure, result)
        assertEquals(1, notificationSender.sentMessages.size)
    }

    @Test
    fun `incident created event with invalid payload returns failure`() = runBlocking {
        val handler = MessageHandler(FakeNotificationSender(), FakeEventHistoryRepository())
        val message = Json.encodeToString(
            RabbitMqEvent.create(
                eventType = RabbitMqEventType.INCIDENT_CREATED,
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
                eventType = RabbitMqEventType.MEASUREMENT_CREATED,
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
            createdAt = "2026-08-14T12:00:00Z"
        )

        val event = eventId?.let {
            RabbitMqEvent(
                eventId = it,
                eventType = RabbitMqEventType.INCIDENT_CREATED,
                createdAt = "2026-08-14T12:00:00Z",
                data = Json.encodeToJsonElement(payload)
            )
        } ?: RabbitMqEvent.create(
            eventType = RabbitMqEventType.INCIDENT_CREATED,
            data = Json.encodeToJsonElement(payload)
        )

        return Json.encodeToString(event)
    }
}
