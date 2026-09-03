package com.doduohor.infrastructure.messaging

import com.doduohor.events.OutboxEvents

object OutboxEventMapper {
    fun toRabbitMqEvent(event: OutboxEvents): RabbitMqEvent =
        RabbitMqEvent(
            eventId = event.eventId.toString(),
            eventType = event.eventType,
            createdAt = event.createdAt.toString(),
            data = event.payload
        )
}