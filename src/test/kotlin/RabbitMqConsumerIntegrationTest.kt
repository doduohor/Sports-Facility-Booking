package com.doduohor

import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.IntegrationEventType
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
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        val inspectionConnection = RabbitMqFactory.connect(config)
        return try {
            while (System.nanoTime() < deadline) {
                val message = inspectionConnection.channel.basicGet(queue, true)
                    ?.body
                    ?.toString(Charsets.UTF_8)
                if (message != null) {
                    return message
                }
                Thread.sleep(100)
            }
            null
        } finally {
            inspectionConnection.close()
        }
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

    private class FakeNotificationSender : NotificationSender {
        override fun send(message: String): NotificationSenderResult =
            NotificationSenderResult.Success
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
