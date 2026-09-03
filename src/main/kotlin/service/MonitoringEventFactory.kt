package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.shared.Clock
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.ServerEvent
import com.doduohor.events.ServerEventType
import com.doduohor.events.toEventPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

data class MonitoringEvents(val outboxEvents: List<NewOutboxEvents>, val serverEvents: List<ServerEvent>)

class MonitoringEventFactory(
    private val clock: Clock,
    private val eventIdGenerator: () -> Uuid = { Uuid.random() }
) {
    fun create(measurement: Measurement, incident: Incident?): MonitoringEvents {
        val measurementPayload = Json.encodeToJsonElement(measurement.toEventPayload())
        val measurementCreatedAt = clock.now()
        val measurementOutbox = NewOutboxEvents.create(
            eventIdGenerator(), IntegrationEventType.MEASUREMENT_CREATED, measurementPayload,
            OffsetDateTime.ofInstant(measurementCreatedAt, ZoneOffset.UTC)
        )
        val serverEvents = mutableListOf(ServerEvent(ServerEventType.MEASUREMENT_CREATED, measurementPayload, measurementCreatedAt))
        val outboxEvents = mutableListOf(measurementOutbox)
        if (incident != null) {
            val incidentPayload = Json.encodeToJsonElement(incident.toEventPayload())
            val incidentCreatedAt = clock.now()
            outboxEvents += NewOutboxEvents.create(
                eventIdGenerator(), IntegrationEventType.INCIDENT_CREATED, incidentPayload,
                OffsetDateTime.ofInstant(incidentCreatedAt, ZoneOffset.UTC)
            )
            serverEvents += ServerEvent(ServerEventType.INCIDENT_CREATED, incidentPayload, incidentCreatedAt)
        }
        return MonitoringEvents(outboxEvents, serverEvents)
    }
}
