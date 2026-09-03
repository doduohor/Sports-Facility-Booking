package com.doduohor.infrastructure.messaging

import com.doduohor.domain.shared.Clock
import com.doduohor.events.IntegrationEventType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.UUID

@Serializable
data class RabbitMqEvent(
    val eventId: String,
    val eventType: IntegrationEventType,
    val createdAt: String,
    val data: JsonElement
){
    companion object{
        fun create(eventType: IntegrationEventType, clock: Clock, data: JsonElement): RabbitMqEvent =
            RabbitMqEvent(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                createdAt = clock.now().toString(),
                data = data
            )
    }
}
