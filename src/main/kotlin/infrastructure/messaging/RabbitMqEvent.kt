package com.doduohor.infrastructure.messaging

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.UUID

enum class RabbitMqEventType{
    MEASUREMENT_CREATED,
    INCIDENT_CREATED
}

@Serializable
data class RabbitMqEvent(
    val eventId: String,
    val eventType: RabbitMqEventType,
    val createdAt: String,
    val data: JsonElement
){
    companion object{
        fun create(eventType: RabbitMqEventType, data: JsonElement): RabbitMqEvent =
            RabbitMqEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                createdAt = Instant.now().toString(),
                data = data
            )
    }
}
