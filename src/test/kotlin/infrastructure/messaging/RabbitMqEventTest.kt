package com.doduohor.infrastructure.messaging

import com.doduohor.events.IntegrationEventType
import com.doduohor.infrastructure.time.FixedClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RabbitMqEventTest {
    @Test
    fun `serialized event contains event id`() {
        val event = RabbitMqEvent.create(
            eventType = IntegrationEventType.MEASUREMENT_CREATED,
            clock = FixedClock(Instant.parse("2026-08-20T12:00:00Z")),
            data = Json.parseToJsonElement("{\"id\":1}")
        )

        val json = Json.encodeToString(event).let(Json::parseToJsonElement).jsonObject

        assertTrue("eventId" in json)
        assertEquals(event.eventId, json["eventId"]?.toString()?.trim('"'))
    }

    @Test
    fun `createdAt uses injected clock`() {
        val instant = Instant.parse("2026-08-20T12:00:00Z")
        val event = RabbitMqEvent.create(
            eventType = IntegrationEventType.MEASUREMENT_CREATED,
            clock = FixedClock(instant),
            data = Json.parseToJsonElement("{\"id\":1}")
        )

        assertEquals(instant.toString(), event.createdAt)
    }
}
