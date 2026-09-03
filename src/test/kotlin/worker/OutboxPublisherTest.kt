package com.doduohor.worker

import com.doduohor.events.ExpandAttemptResult
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEventStatus
import com.doduohor.events.OutboxEvents
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveErrorResult
import com.doduohor.events.SaveEventResult
import com.doduohor.events.StartPublishingResult
import com.doduohor.infrastructure.messaging.MessagePublisher
import com.doduohor.infrastructure.messaging.RabbitMqEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OutboxPublisherTest {
    @Test
    fun `successful publishing sends json and marks event as published`() = runBlocking {
        val event = event()
        val repository = FakeOutboxRepository(event)
        val publisher = FakeMessagePublisher()

        OutboxPublisher(repository, publisher).publishMessage()

        val message = publisher.messages.singleOrNull()
        assertNotNull(message)
        val rabbitEvent = Json.decodeFromString<RabbitMqEvent>(message)
        assertEquals(event.eventId.toString(), rabbitEvent.eventId)
        assertEquals(IntegrationEventType.MEASUREMENT_CREATED, rabbitEvent.eventType)
        assertEquals(event.payload, rabbitEvent.data)
        assertTrue(repository.markedPublished)
        assertFalse(repository.savedError)
    }

    @Test
    fun `publishing failure saves error and does not mark event as published`() = runBlocking {
        val event = event()
        val repository = FakeOutboxRepository(event)
        val publisher = FakeMessagePublisher(failure = IllegalStateException("Rabbit unavailable"))

        OutboxPublisher(repository, publisher).publishMessage()

        assertFalse(repository.markedPublished)
        assertTrue(repository.savedError)
        assertEquals("Rabbit unavailable", repository.savedErrorMessage)
    }

    @Test
    fun `event is skipped when publishing cannot be started`() = runBlocking {
        val event = event()
        val repository = FakeOutboxRepository(event, startResult = StartPublishingResult.AlreadyProcessing)
        val publisher = FakeMessagePublisher()

        OutboxPublisher(repository, publisher).publishMessage()

        assertTrue(publisher.messages.isEmpty())
        assertFalse(repository.markedPublished)
        assertFalse(repository.savedError)
    }

    @Test
    fun `polling continues after one iteration fails`() = runBlocking {
        val event = event()
        val secondIteration = CompletableDeferred<Unit>()
        val repository = FakeOutboxRepository(event, failuresBeforeEvent = 1, secondIteration = secondIteration)
        val publisher = OutboxPublisher(repository, FakeMessagePublisher(), pollIntervalMillis = 0)

        publisher.start(CoroutineScope(Dispatchers.Default))
        secondIteration.await()
        publisher.close()

        assertTrue(repository.findCalls >= 2)
    }

    @Test
    fun `close cancels publication in progress`() = runBlocking {
        val event = event()
        val publishingStarted = CompletableDeferred<Unit>()
        val repository = FakeOutboxRepository(event)
        val publisher = OutboxPublisher(
            repository,
            object : MessagePublisher {
                override suspend fun publish(message: String) {
                    publishingStarted.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
            },
            pollIntervalMillis = 0
        )

        publisher.start(CoroutineScope(Dispatchers.Default))
        publishingStarted.await()
        publisher.close()

        assertFalse(repository.markedPublished)
        assertFalse(repository.savedError)
    }

    @Test
    fun `cancellation after claim leaves processing event for explicit recovery`() = runBlocking {
        val publishingStarted = CompletableDeferred<Unit>()
        val repository = FakeOutboxRepository(event())
        val publisher = OutboxPublisher(
            repository,
            object : MessagePublisher {
                override suspend fun publish(message: String) {
                    publishingStarted.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
            }
        )

        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch { publisher.publishMessage() }
        publishingStarted.await()
        job.cancel()
        job.join()

        assertTrue(repository.processing)
        assertFalse(repository.markedPublished)
        assertFalse(repository.savedError)
    }

    @Test
    fun `close cancels polling in progress`() = runBlocking {
        val pollingStarted = CompletableDeferred<Unit>()
        val messagePublisher = FakeMessagePublisher()
        val publisher = OutboxPublisher(
            FakeOutboxRepository(event()),
            messagePublisher,
            findEvents = {
                pollingStarted.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            },
            pollIntervalMillis = 0
        )

        publisher.start(CoroutineScope(Dispatchers.Default))
        pollingStarted.await()
        publisher.close()

        assertTrue(messagePublisher.messages.isEmpty())
    }

    @Test
    fun `starting twice does not create another polling coroutine`() = runBlocking {
        val repository = FakeOutboxRepository(event())
        val publisher = OutboxPublisher(repository, FakeMessagePublisher(), pollIntervalMillis = 10)
        val scope = CoroutineScope(Dispatchers.Default)

        publisher.start(scope)
        publisher.start(scope)
        publisher.close()

        assertFailsWith<IllegalStateException> { publisher.start(scope) }
    }

    private fun event(): OutboxEvents = OutboxEvents(
        id = 1,
        eventId = Uuid.random(),
        eventType = IntegrationEventType.MEASUREMENT_CREATED,
        payload = buildJsonObject {
            put("id", 1)
            put("value", 24.5)
        },
        status = OutboxEventStatus.NEW,
        createdAt = OffsetDateTime.of(2026, 8, 19, 12, 0, 0, 0, ZoneOffset.UTC),
        publishedAt = null,
        attempt = 0,
        errorMessage = null
    )

    private class FakeMessagePublisher(
        private val failure: Exception? = null
    ) : MessagePublisher {
        val messages = mutableListOf<String>()

        override suspend fun publish(message: String) {
            failure?.let { throw it }
            messages += message
        }
    }

    private class FakeOutboxRepository(
        private val event: OutboxEvents,
        private val startResult: StartPublishingResult = StartPublishingResult.Started,
        private val failuresBeforeEvent: Int = 0,
        private val secondIteration: CompletableDeferred<Unit>? = null
    ) : OutboxEventsRepository {
        var findCalls = 0
        var markedPublished = false
        var savedError = false
        var savedErrorMessage: String? = null
        var processing = false

        override fun saveEvent(event: NewOutboxEvents): SaveEventResult = SaveEventResult.Success

        override fun findUnprocessedEvents(): List<OutboxEvents> {
            findCalls++
            if (findCalls <= failuresBeforeEvent) throw IllegalStateException("temporary polling failure")
            secondIteration?.complete(Unit)
            return listOf(event)
        }

        override fun tryStartPublishing(eventId: Uuid): StartPublishingResult = startResult.also {
            if (it == StartPublishingResult.Started) processing = true
        }

        override fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult {
            markedPublished = true
            processing = false
            return MakeAsPublishedResult.Success
        }

        override fun expandAttempt(eventId: Uuid): ExpandAttemptResult = ExpandAttemptResult.Success

        override fun saveError(eventId: Uuid, error: String): SaveErrorResult {
            savedError = true
            processing = false
            savedErrorMessage = error
            return SaveErrorResult.Success
        }
    }
}
