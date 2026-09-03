package com.doduohor.events

import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class EventPublisherTest {

    @Test
    fun `publish sends event to subscribed channel`() = runTest {
        val eventPublisher = EventPublisher()
        val channel = eventPublisher.subscribe()
        val event = ServerEvent(
            type = ServerEventType.INCIDENT_CREATED,
            data = buildJsonObject {
                put("incidentId", 1L)
            },
            createdAt = Instant.EPOCH
        )

        val publishJob = launch {
            eventPublisher.publish(event)
        }

        val receivedEvent = withTimeout(1_000) {
            channel.receive()
        }

        publishJob.join()
        eventPublisher.unsubscribe(channel)

        assertEquals(event, receivedEvent)
    }

    @Test
    fun `publish sends event to all subscribed channels`() = runTest {
        val eventPublisher = EventPublisher()
        val firstChannel = eventPublisher.subscribe()
        val secondChannel = eventPublisher.subscribe()
        val event = ServerEvent(
            type = ServerEventType.MEASUREMENT_CREATED,
            data = buildJsonObject {
                put("measurementId", 1L)
            },
            createdAt = Instant.EPOCH
        )

        val publishJob = launch {
            eventPublisher.publish(event)
        }

        val firstReceivedEvent = withTimeout(1_000) {
            firstChannel.receive()
        }
        val secondReceivedEvent = withTimeout(1_000) {
            secondChannel.receive()
        }

        publishJob.join()
        eventPublisher.unsubscribe(firstChannel)
        eventPublisher.unsubscribe(secondChannel)

        assertEquals(event, firstReceivedEvent)
        assertEquals(event, secondReceivedEvent)
    }

    @Test
    fun `publish sends a list of events to subscribers in order`() = runTest {
        val eventPublisher = EventPublisher()
        val channel = eventPublisher.subscribe()
        val events = listOf(
            ServerEvent(ServerEventType.MEASUREMENT_CREATED, buildJsonObject { put("id", 1) }, Instant.EPOCH),
            ServerEvent(ServerEventType.INCIDENT_CREATED, buildJsonObject { put("id", 2) }, Instant.EPOCH)
        )

        val publishJob = launch { eventPublisher.publish(events) }
        val received = withTimeout(1_000) { listOf(channel.receive(), channel.receive()) }
        publishJob.join()
        eventPublisher.unsubscribe(channel)

        assertEquals(events, received)
    }
}
