package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.shared.Clock
import com.doduohor.events.IntegrationEvent
import com.doduohor.events.ServerEvent
import com.doduohor.events.toNewOutboxEvent
import com.doduohor.events.toServerEvent
import com.doduohor.events.toEventPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.uuid.Uuid

data class MonitoringEvents(val integrationEvents: List<IntegrationEvent>) {
    val outboxEvents get() = integrationEvents.map(IntegrationEvent::toNewOutboxEvent)
    val serverEvents: List<ServerEvent> get() = integrationEvents.map(IntegrationEvent::toServerEvent)
}

class MonitoringEventFactory(
    private val clock: Clock,
    private val eventIdGenerator: () -> Uuid = { Uuid.random() }
) {
    fun create(measurement: Measurement, incident: Incident?): MonitoringEvents {
        val measurementPayload = Json.encodeToJsonElement(measurement.toEventPayload())
        val measurementCreatedAt = clock.now()
        val integrationEvents = mutableListOf<IntegrationEvent>(
            IntegrationEvent.MeasurementCreated(
                eventId = eventIdGenerator(),
                createdAt = measurementCreatedAt,
                payload = measurementPayload
            )
        )
        if (incident != null) {
            val incidentPayload = Json.encodeToJsonElement(incident.toEventPayload())
            val incidentCreatedAt = clock.now()
            integrationEvents += IntegrationEvent.IncidentCreated(
                eventId = eventIdGenerator(),
                createdAt = incidentCreatedAt,
                payload = incidentPayload
            )
        }
        return MonitoringEvents(integrationEvents)
    }
}
