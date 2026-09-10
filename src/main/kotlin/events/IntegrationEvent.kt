package com.doduohor.events

import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

sealed interface IntegrationEvent {
    val eventId: Uuid
    val eventType: IntegrationEventType
    val createdAt: Instant
    val payload: JsonElement

    data class MeasurementCreated(
        override val eventId: Uuid,
        override val createdAt: Instant,
        override val payload: JsonElement
    ) : IntegrationEvent {
        override val eventType: IntegrationEventType = IntegrationEventType.MEASUREMENT_CREATED
    }

    data class IncidentCreated(
        override val eventId: Uuid,
        override val createdAt: Instant,
        override val payload: JsonElement
    ) : IntegrationEvent {
        override val eventType: IntegrationEventType = IntegrationEventType.INCIDENT_CREATED
    }
}

fun IntegrationEvent.toNewOutboxEvent(): NewOutboxEvents = NewOutboxEvents.create(
    eventId = eventId,
    eventType = eventType,
    payload = payload,
    createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
)

fun IntegrationEvent.toServerEvent(): ServerEvent = ServerEvent(
    type = ServerEventType.valueOf(eventType.name),
    data = payload,
    createdAt = createdAt
)
