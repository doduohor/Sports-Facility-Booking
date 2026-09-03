package com.doduohor.events

import kotlinx.serialization.json.JsonElement
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

enum class OutboxEventStatus{
    NEW,
    PROCESSING,
    PUBLISHED,
    FAILED,
}

data class OutboxEvents(
    val id: Long,
    val eventId: Uuid,
    val eventType: IntegrationEventType,
    val payload: JsonElement,
    val status: OutboxEventStatus,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
    val attempt: Int,
    val errorMessage: String?
)

data class NewOutboxEvents(
    val eventId: Uuid,
    val eventType: IntegrationEventType,
    val payload: JsonElement,
    val status: OutboxEventStatus,
    val createdAt: OffsetDateTime,
    val publishedAt: OffsetDateTime?,
    val attempt: Int,
    val errorMessage: String?
){
    companion object{
        fun create(
            eventId: Uuid,
            eventType: IntegrationEventType,
            payload: JsonElement,
            createdAt: OffsetDateTime
        ): NewOutboxEvents =
            NewOutboxEvents(
                eventId = eventId,
                eventType = eventType,
                payload = payload,
                status = OutboxEventStatus.NEW,
                createdAt = createdAt,
                publishedAt = null,
                attempt = 0,
                errorMessage = null
            )
    }
}
