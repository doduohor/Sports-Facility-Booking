package com.doduohor.events

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.infrastructure.messaging.MessagePublisher
import com.doduohor.service.IncidentPolicy
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import com.doduohor.service.MonitoringServiceResult
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MonitoringServiceEventTest {

    private class FakeMessagePublisher: MessagePublisher{
        val messages = mutableListOf<String>()

        override fun publish(message: String){
            messages.add(message)
        }
    }

    @Test
    fun `successful measurement creation publishes measurement created event`() = runTest {
        val facilityRepository = InMemoryFacilityRepository()
        val equipmentRepository = InMemoryEquipmentRepository()
        val measurementService = MeasurementService(
            InMemoryMeasurementRepository(),
            equipmentRepository
        )
        val incidentService = IncidentService(
            facilityRepository,
            equipmentRepository,
            InMemoryIncidentRepository()
        )
        val messagePublisher = FakeMessagePublisher()
        val eventPublisher = EventPublisher()
        val monitoringService = MonitoringService(
            measurementService,
            incidentService,
            equipmentRepository,
            IncidentPolicy(),
            eventPublisher,
            messagePublisher
        )

        val equipment = equipmentRepository.create(
            facilityId = 1L,
            name = "Main ventilation",
            type = EquipmentType.VENTILATION
        )
        val channel = eventPublisher.subscribe()

        val processJob = launch {
            monitoringService.processMeasurement(
                equipmentId = equipment.id,
                type = "TEMPERATURE",
                unit = "CELSIUS",
                value = 22.0
            )
        }

        val event = withTimeout(1_000) {
            channel.receive()
        }

        processJob.join()
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        eventPublisher.unsubscribe(channel)

        assertEquals(ServerEventType.MEASUREMENT_CREATED, event.type)
        assertEquals(
            equipment.id,
            event.data.jsonObject["equipmentId"]?.jsonPrimitive?.content?.toLong()
        )
        assertEquals(
            22.0,
            event.data.jsonObject["value"]?.jsonPrimitive?.content?.toDouble()
        )
    }

    @Test
    fun `alarming measurement publishes incident created event after measurement event`() = runTest {
        val facilityRepository = InMemoryFacilityRepository()
        val facility = facilityRepository.create("Central Pool", FacilityType.POOL)
        val equipmentRepository = InMemoryEquipmentRepository()
        val incidentRepository = InMemoryIncidentRepository()
        val messagePublisher = FakeMessagePublisher()
        val eventPublisher = EventPublisher()
        val monitoringService = MonitoringService(
            MeasurementService(InMemoryMeasurementRepository(), equipmentRepository),
            IncidentService(facilityRepository, equipmentRepository, incidentRepository),
            equipmentRepository,
            IncidentPolicy(),
            eventPublisher,
            messagePublisher
        )
        val equipment = equipmentRepository.create(
            facilityId = facility.id,
            name = "Fire alarm",
            type = EquipmentType.FIRE_ALARM
        )
        val channel = eventPublisher.subscribe()

        val processJob = launch {
            monitoringService.processMeasurement(
                equipmentId = equipment.id,
                type = "SMOKE",
                unit = "PERCENT",
                value = 12.0
            )
        }

        val measurementEvent = withTimeout(1_000) { channel.receive() }
        val incidentEvent = withTimeout(1_000) { channel.receive() }

        processJob.join()
        eventPublisher.unsubscribe(channel)

        assertEquals(ServerEventType.MEASUREMENT_CREATED, measurementEvent.type)
        assertEquals(ServerEventType.INCIDENT_CREATED, incidentEvent.type)
        assertEquals(
            facility.id,
            incidentEvent.data.jsonObject["facilityId"]?.jsonPrimitive?.content?.toLong()
        )
        assertEquals(
            equipment.id,
            incidentEvent.data.jsonObject["equipmentId"]?.jsonPrimitive?.content?.toLong()
        )
        assertEquals(
            "smoke_detected",
            incidentEvent.data.jsonObject["type"]?.jsonPrimitive?.content
        )
        assertEquals(
            "HIGH",
            incidentEvent.data.jsonObject["severity"]?.jsonPrimitive?.content
        )
    }

    @Test
    fun `invalid measurement creation does not publish event`() = runTest {
        val facilityRepository = InMemoryFacilityRepository()
        val equipmentRepository = InMemoryEquipmentRepository()
        val messagePublisher = FakeMessagePublisher()
        val eventPublisher = EventPublisher()
        val monitoringService = MonitoringService(
            MeasurementService(InMemoryMeasurementRepository(), equipmentRepository),
            IncidentService(
                facilityRepository,
                equipmentRepository,
                InMemoryIncidentRepository()
            ),
            equipmentRepository,
            IncidentPolicy(),
            eventPublisher,
            messagePublisher
        )
        val channel = eventPublisher.subscribe()

        val result = monitoringService.processMeasurement(
            equipmentId = 999L,
            type = "TEMPERATURE",
            unit = "CELSIUS",
            value = 22.0
        )

        assertIs<MonitoringServiceResult.MeasurementCreateError>(result)
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        eventPublisher.unsubscribe(channel)
    }
}
