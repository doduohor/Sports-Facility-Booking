package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.IncidentId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.ServerEventType
import com.doduohor.infrastructure.time.FixedClock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MonitoringEventFactoryTest {
    private val instant = Instant.parse("2026-08-20T12:00:00Z")
    private val measurement = Measurement(
        MeasurementId(7),
        EquipmentId(11),
        MeasurementReading(MeasurementType.SMOKE, MeasurementUnit.PERCENT, 12.0),
        instant
    )

    @Test
    fun `creates matching measurement and incident outbox and server events in order`() {
        val incident = Incident.restore(
            IncidentId(13), FacilityId(17), EquipmentId(11), MeasurementId(7),
            IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH, com.doduohor.domain.model.IncidentStatus.OPEN,
            MeasurementType.SMOKE, MeasurementUnit.PERCENT, 12.0, instant, instant
        )

        val events = MonitoringEventFactory(FixedClock(instant)).create(measurement, incident)

        assertEquals(
            listOf(IntegrationEventType.MEASUREMENT_CREATED, IntegrationEventType.INCIDENT_CREATED),
            events.outboxEvents.map { it.eventType }
        )
        assertEquals(
            listOf(ServerEventType.MEASUREMENT_CREATED, ServerEventType.INCIDENT_CREATED),
            events.serverEvents.map { it.type }
        )
        assertEquals(events.outboxEvents.map { it.payload }, events.serverEvents.map { it.data })
        assertEquals(events.outboxEvents.map { it.createdAt.toInstant() }, events.serverEvents.map { it.createdAt })
        assertNotEquals(events.outboxEvents[0].eventId, events.outboxEvents[1].eventId)
    }

    @Test
    fun `creates only measurement event when incident is absent`() {
        val events = MonitoringEventFactory(FixedClock(instant)).create(measurement, null)

        assertEquals(1, events.outboxEvents.size)
        assertEquals(1, events.serverEvents.size)
        assertEquals(IntegrationEventType.MEASUREMENT_CREATED, events.outboxEvents.single().eventType)
        assertEquals(ServerEventType.MEASUREMENT_CREATED, events.serverEvents.single().type)
        assertNull(events.serverEvents.singleOrNull { it.type == ServerEventType.INCIDENT_CREATED })
    }
}
