package com.doduohor.infrastructure.messaging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RabbitMqEventTest {
    @Test
    fun `serialized event contains event id`() {
        val event = RabbitMqEvent.create(
            eventType = RabbitMqEventType.MEASUREMENT_CREATED,
            data = Json.parseToJsonElement("{\"id\":1}")
        )

        val json = Json.encodeToString(event).let(Json::parseToJsonElement).jsonObject

        assertTrue("eventId" in json)
        assertEquals(event.eventId, json["eventId"]?.toString()?.trim('"'))
    }
}
