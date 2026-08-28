package com.doduohor.infrastructure.messaging

import com.doduohor.domain.shared.Clock
import com.doduohor.events.EventHistoryDocument
import com.doduohor.events.IntegrationEventType
import com.doduohor.repository.mongo.EventHistoryRepository
import com.doduohor.repository.mongo.MarkFailedResult
import com.doduohor.repository.mongo.MarkProcessedResult
import com.doduohor.repository.mongo.MarkStartProcessingResult
import com.doduohor.worker.MessageHandler
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import com.rabbitmq.client.Envelope
import java.lang.reflect.Proxy
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RabbitMqConsumerTest {
    @Test
    fun `successful delivery configures one in-flight message and records a successful ACK`() {
        val channel = RecordingChannel()
        val consumer = RabbitMqConsumer(
            MessageHandler(NoopNotificationSender, SuccessfulEventHistoryRepository, FixedClock)
        )

        consumer.startConsuming(RabbitMqConnection(noopConnection(), channel.proxy), testConfig)
        channel.deliver(validMessage, deliveryTag = 42)
        channel.awaitCompletion()

        assertEquals(listOf(1), channel.prefetchCounts)
        assertEquals(listOf(42L), channel.ackAttempts)
        assertEquals(listOf(42L), channel.successfulAcks)
        assertEquals(emptyList(), channel.nackedDeliveryTags)
    }

    @Test
    fun `invalid delivery is negatively acknowledged once without requeue`() {
        val channel = RecordingChannel()
        val consumer = RabbitMqConsumer(
            MessageHandler(NoopNotificationSender, SuccessfulEventHistoryRepository, FixedClock)
        )

        consumer.startConsuming(RabbitMqConnection(noopConnection(), channel.proxy), testConfig)
        channel.deliver("not-json", deliveryTag = 43)
        channel.awaitCompletion()

        assertEquals(emptyList(), channel.ackAttempts)
        assertEquals(emptyList(), channel.successfulAcks)
        assertEquals(listOf(Nack(43, multiple = false, requeue = false)), channel.nacks)
    }

    @Test
    fun `failed acknowledgement records no successful ACK and sends no negative acknowledgement`() {
        val channel = RecordingChannel(acknowledgementFailure = IllegalStateException("channel closed"))
        val consumer = RabbitMqConsumer(
            MessageHandler(NoopNotificationSender, SuccessfulEventHistoryRepository, FixedClock)
        )

        consumer.startConsuming(RabbitMqConnection(noopConnection(), channel.proxy), testConfig)
        channel.deliver(validMessage, deliveryTag = 44)
        channel.awaitCompletion()

        assertEquals(listOf(44L), channel.ackAttempts)
        assertEquals(emptyList(), channel.successfulAcks)
        assertTrue(channel.nacks.isEmpty())
    }

    private class RecordingChannel(
        private val acknowledgementFailure: Exception? = null
    ) {
        val prefetchCounts = mutableListOf<Int>()
        val ackAttempts = mutableListOf<Long>()
        val successfulAcks = mutableListOf<Long>()
        val nackedDeliveryTags = mutableListOf<Long>()
        val nacks = mutableListOf<Nack>()
        private val completion = CountDownLatch(1)
        private var deliveryCallback: DeliverCallback? = null

        val proxy: Channel = Proxy.newProxyInstance(
            Channel::class.java.classLoader,
            arrayOf(Channel::class.java)
        ) { _, method, args ->
            when (method.name) {
                "basicQos" -> prefetchCounts += args!![0] as Int
                "basicConsume" -> {
                    deliveryCallback = args!!.filterIsInstance<DeliverCallback>().single()
                    "consumer"
                }
                "basicAck" -> {
                    val deliveryTag = args!![0] as Long
                    ackAttempts += deliveryTag
                    acknowledgementFailure?.let { completion.countDown(); throw it }
                    successfulAcks += deliveryTag
                    completion.countDown()
                }
                "basicNack" -> {
                    nackedDeliveryTags += args!![0] as Long
                    nacks += Nack(args[0] as Long, args[1] as Boolean, args[2] as Boolean)
                    completion.countDown()
                }
                "isOpen" -> true
                "getChannelNumber" -> 1
                "getConnection" -> noopConnection()
                else -> null
            }
        } as Channel

        fun deliver(message: String, deliveryTag: Long) {
            deliveryCallback!!.handle(
                "consumer",
                Delivery(Envelope(deliveryTag, false, "test", "key"), null, message.toByteArray())
            )
        }

        fun awaitCompletion() {
            assertTrue(completion.await(2, TimeUnit.SECONDS))
        }
    }

    private data class Nack(val deliveryTag: Long, val multiple: Boolean, val requeue: Boolean)

    private object SuccessfulEventHistoryRepository : EventHistoryRepository {
        override suspend fun save(event: EventHistoryDocument): EventHistoryDocument = event
        override suspend fun findById(eventId: String): EventHistoryDocument? = null
        override suspend fun findByType(type: String): List<EventHistoryDocument> = emptyList()
        override suspend fun createIndexes() = Unit
        override suspend fun tryStartProcessing(event: EventHistoryDocument) = MarkStartProcessingResult.Started
        override suspend fun markProcessed(eventId: String) = MarkProcessedResult.Updated
        override suspend fun markFailed(eventId: String, errorMessage: String) = MarkFailedResult.Updated
    }

    private object NoopNotificationSender : com.doduohor.infrastructure.notification.NotificationSender {
        override fun send(message: String) = com.doduohor.infrastructure.notification.NotificationSenderResult.Success
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-08-27T00:00:00Z")
    }

    private companion object {
        val testConfig = RabbitMqConfig("host", 5672, "user", "password", "exchange", "queue", "key", "dlx", "dlq", "dlq-key")
        const val validMessage = "{\"eventId\":\"event-1\",\"eventType\":\"MEASUREMENT_CREATED\",\"createdAt\":\"2026-08-27T00:00:00Z\",\"data\":{\"id\":1,\"equipmentId\":2,\"type\":\"TEMPERATURE\",\"unit\":\"CELSIUS\",\"value\":20.0,\"createdAt\":\"2026-08-27T00:00:00Z\"}}"

        fun noopConnection(): Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, _ -> if (method.name == "isOpen") true else null } as Connection
    }
}
