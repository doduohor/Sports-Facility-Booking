package com.doduohor

import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.IntegrationEventType
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.infrastructure.messaging.RabbitMqConfig
import com.doduohor.infrastructure.messaging.RabbitMqConsumer
import com.doduohor.infrastructure.messaging.RabbitMqConnection
import com.doduohor.infrastructure.messaging.RabbitMqEvent
import com.doduohor.infrastructure.messaging.RabbitMqFactory
import com.doduohor.infrastructure.notification.NotificationSender
import com.doduohor.infrastructure.notification.NotificationSenderResult
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.mongo.EventHistoryRepository
import com.doduohor.repository.mongo.MarkFailedResult
import com.doduohor.repository.mongo.MarkProcessedResult
import com.doduohor.repository.mongo.MarkStartProcessingResult
import com.doduohor.worker.MessageHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class RabbitMqConsumerIntegrationTest {
    private val fixedClock = FixedClock(Instant.parse("2026-08-20T12:00:00Z"))

    companion object {
        private const val USERNAME = "testRabbit"
        private const val PASSWORD = "testRabbit"

        @Container
        @JvmStatic
        val rabbit = RabbitMQContainer("rabbitmq:4.3-management-alpine")
            .withAdminUser(USERNAME)
            .withAdminPassword(PASSWORD)

        private lateinit var config: RabbitMqConfig

        @JvmStatic
        @BeforeAll
        fun connect() {
            config = RabbitMqConfig(
                host = rabbit.host,
                port = rabbit.amqpPort,
                username = USERNAME,
                password = PASSWORD,
                exchange = "test.events",
                queue = "test.events.queue",
                routingKey = "test.created",
                deadLetterExchange = "test.events.dlq",
                deadLetterQueue = "test.events.dlq.queue",
                deadLetterRoutingKey = "test.failed"
            )
        }
    }

    private lateinit var connection: RabbitMqConnection

    @BeforeEach
    fun purgeQueues() {
        connection = RabbitMqFactory.connect(config)
        connection.channel.queuePurge(config.queue)
        connection.channel.queuePurge(config.deadLetterQueue)
    }

    @AfterEach
    fun closeConnection() {
        connection.close()
    }

    @Test
    fun `successful message is acknowledged and is not sent to DLQ`() {
        val processedLatch = CountDownLatch(1)
        val historyRepository = FakeEventHistoryRepository(processedLatch = processedLatch)
        val consumer = RabbitMqConsumer(
            MessageHandler(FakeNotificationSender(), historyRepository, fixedClock)
        )
        consumer.startConsuming(connection, config)

        val message = validMeasurementMessage()
        connection.channel.basicPublish(
            config.exchange,
            config.routingKey,
            null,
            message.toByteArray()
        )

        assertTrue(processedLatch.await(10, TimeUnit.SECONDS))
        assertEquals(1, historyRepository.processedEventIds.size)
        assertNull(readFromQueue(config.deadLetterQueue))
    }

    @Test
    fun `failed message is rejected and sent to DLQ`() {
        val failedLatch = CountDownLatch(1)
        val historyRepository = FakeEventHistoryRepository(failedLatch = failedLatch)
        val consumer = RabbitMqConsumer(
            MessageHandler(FakeNotificationSender(), historyRepository, fixedClock)
        )
        consumer.startConsuming(connection, config)

        val message = invalidMeasurementMessage()
        connection.channel.basicPublish(
            config.exchange,
            config.routingKey,
            null,
            message.toByteArray()
        )

        assertTrue(failedLatch.await(10, TimeUnit.SECONDS))
        val deadLetterMessage = awaitMessage(config.deadLetterQueue)

        assertNotNull(deadLetterMessage)
        assertEquals(message, deadLetterMessage)
        assertEquals(1, historyRepository.failedEventIds.size)
    }

    @Test
    fun `factory declares DLQ topology on a clean broker`() {
        val topologyConfig = config.copy(
            exchange = "test.topology.events",
            queue = "test.topology.events.queue",
            deadLetterExchange = "test.topology.events.dlq",
            deadLetterQueue = "test.topology.events.dlq.queue"
        )

        val topologyConnection = RabbitMqFactory.connect(topologyConfig)
        try {
            assertEquals(topologyConfig.queue, topologyConnection.channel.queueDeclarePassive(topologyConfig.queue).queue)
            assertEquals(topologyConfig.deadLetterQueue, topologyConnection.channel.queueDeclarePassive(topologyConfig.deadLetterQueue).queue)
        } finally {
            topologyConnection.close()
        }
    }

    @Test
    fun `second message waits until first handler completes`() {
        val sender = BlockingNotificationSender()
        val processedLatch = CountDownLatch(2)
        val historyRepository = FakeEventHistoryRepository(processedLatch = processedLatch)
        RabbitMqConsumer(MessageHandler(sender, historyRepository, fixedClock)).startConsuming(connection, config)

        publish(importantIncidentMessage())
        publish(importantIncidentMessage())

        assertTrue(sender.firstSendStarted.await(10, TimeUnit.SECONDS))
        assertEquals(1, sender.sendCount.get())
        sender.releaseFirstSend.countDown()
        assertTrue(processedLatch.await(10, TimeUnit.SECONDS))
        assertEquals(2, sender.sendCount.get())
    }

    @Test
    fun `duplicate event id performs notification side effect once`() {
        val attemptsLatch = CountDownLatch(2)
        val sender = CountingNotificationSender()
        val historyRepository = DuplicateSuppressingEventHistoryRepository(attemptsLatch)
        RabbitMqConsumer(MessageHandler(sender, historyRepository, fixedClock)).startConsuming(connection, config)
        val message = importantIncidentMessage()

        publish(message)
        publish(message)

        assertTrue(attemptsLatch.await(10, TimeUnit.SECONDS))
        assertEquals(1, sender.sendCount.get())
    }

    @Test
    fun `closing channel during handling requeues message without sending it to DLQ`() {
        val sender = BlockingNotificationSender()
        val historyRepository = FakeEventHistoryRepository()
        RabbitMqConsumer(MessageHandler(sender, historyRepository, fixedClock)).startConsuming(connection, config)
        val message = importantIncidentMessage()

        publish(message)
        assertTrue(sender.firstSendStarted.await(10, TimeUnit.SECONDS))
        connection.channel.close()
        sender.releaseFirstSend.countDown()

        assertEquals(message, awaitMessage(config.queue))
        assertNull(readFromQueue(config.deadLetterQueue))
    }

    private fun readFromQueue(queue: String): String? {
        val inspectionConnection = RabbitMqFactory.connect(config)
        return try {
            inspectionConnection.channel.basicGet(queue, true)
                ?.body
                ?.toString(Charsets.UTF_8)
        } finally {
            inspectionConnection.close()
        }
    }

    private fun awaitMessage(queue: String): String? {
        val inspectionConnection = RabbitMqFactory.connect(config)
        return try {
            val received = arrayOfNulls<String>(1)
            val receivedLatch = CountDownLatch(1)
            inspectionConnection.channel.basicConsume(queue, true, { _, delivery ->
                received[0] = delivery.body.toString(Charsets.UTF_8)
                receivedLatch.countDown()
            }, {})
            if (receivedLatch.await(10, TimeUnit.SECONDS)) received[0] else null
        } finally {
            inspectionConnection.close()
        }
    }

    private fun publish(message: String) {
        connection.channel.basicPublish(config.exchange, config.routingKey, null, message.toByteArray())
    }

    private fun validMeasurementMessage(): String =
        Json.encodeToString(
            RabbitMqEvent.create(
                eventType = IntegrationEventType.MEASUREMENT_CREATED,
                clock = fixedClock,
                data = buildJsonObject {
                    put("id", 1L)
                    put("equipmentId", 2L)
                    put("type", "TEMPERATURE")
                    put("unit", "CELSIUS")
                    put("value", 24.5)
                    put("createdAt", "2026-08-18T12:00:00Z")
                }
            )
        )

    private fun invalidMeasurementMessage(): String =
        Json.encodeToString(
            RabbitMqEvent.create(
                eventType = IntegrationEventType.MEASUREMENT_CREATED,
                clock = fixedClock,
                data = buildJsonObject {
                    put("id", 1L)
                }
            )
        )

    private fun importantIncidentMessage(): String =
        Json.encodeToString(
            RabbitMqEvent.create(
                eventType = IntegrationEventType.INCIDENT_CREATED,
                clock = fixedClock,
                data = buildJsonObject {
                    put("id", 1L)
                    put("facilityId", 2L)
                    put("equipmentId", 3L)
                    put("measurementId", 4L)
                    put("type", "OUT_OF_RANGE")
                    put("severity", IncidentSeverity.CRITICAL.name)
                    put("status", "OPEN")
                    put("measurementType", "TEMPERATURE")
                    put("measurementUnit", "CELSIUS")
                    put("value", 99.0)
                    put("createdAt", "2026-08-18T12:00:00Z")
                }
            )
        )

    private class FakeNotificationSender : NotificationSender {
        override fun send(message: String): NotificationSenderResult =
            NotificationSenderResult.Success
    }

    private class CountingNotificationSender : NotificationSender {
        val sendCount = AtomicInteger()

        override fun send(message: String): NotificationSenderResult {
            sendCount.incrementAndGet()
            return NotificationSenderResult.Success
        }
    }

    private class BlockingNotificationSender : NotificationSender {
        val firstSendStarted = CountDownLatch(1)
        val releaseFirstSend = CountDownLatch(1)
        val sendCount = AtomicInteger()

        override fun send(message: String): NotificationSenderResult {
            if (sendCount.incrementAndGet() == 1) {
                firstSendStarted.countDown()
                check(releaseFirstSend.await(10, TimeUnit.SECONDS)) { "first handler was not released" }
            }
            return NotificationSenderResult.Success
        }
    }

    private class DuplicateSuppressingEventHistoryRepository(
        private val attemptsLatch: CountDownLatch
    ) : EventHistoryRepository {
        private var alreadyStarted = false

        override suspend fun save(event: EventHistoryDocument): EventHistoryDocument = event
        override suspend fun findById(eventId: String): EventHistoryDocument? = null
        override suspend fun findByType(type: String): List<EventHistoryDocument> = emptyList()
        override suspend fun createIndexes() = Unit
        override suspend fun tryStartProcessing(event: EventHistoryDocument): MarkStartProcessingResult {
            attemptsLatch.countDown()
            return if (alreadyStarted) MarkStartProcessingResult.AlreadyProcessed else {
                alreadyStarted = true
                MarkStartProcessingResult.Started
            }
        }
        override suspend fun markProcessed(eventId: String) = MarkProcessedResult.Updated
        override suspend fun markFailed(eventId: String, errorMessage: String) = MarkFailedResult.Updated
    }

    private class FakeEventHistoryRepository(
        private val processedLatch: CountDownLatch? = null,
        private val failedLatch: CountDownLatch? = null
    ) : EventHistoryRepository {
        val processedEventIds = mutableListOf<String>()
        val failedEventIds = mutableListOf<String>()

        override suspend fun save(event: EventHistoryDocument): EventHistoryDocument = event

        override suspend fun findById(eventId: String): EventHistoryDocument? = null

        override suspend fun findByType(type: String): List<EventHistoryDocument> = emptyList()

        override suspend fun createIndexes() = Unit

        override suspend fun tryStartProcessing(event: EventHistoryDocument): MarkStartProcessingResult =
            MarkStartProcessingResult.Started

        override suspend fun markProcessed(eventId: String): MarkProcessedResult {
            processedEventIds.add(eventId)
            processedLatch?.countDown()
            return MarkProcessedResult.Updated
        }

        override suspend fun markFailed(eventId: String, errorMessage: String): MarkFailedResult {
            failedEventIds.add(eventId)
            failedLatch?.countDown()
            return MarkFailedResult.Updated
        }
    }
}
