package com.doduohor.service

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.policy.IncidentPolicy
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.doduohor.getOrThrow

class IncidentCoordinatorTest {
    private val clock = FixedClock(Instant.parse("2026-09-03T10:00:00Z"))

    @Test
    fun `normal measurement produces no incident`() {
        val fixture = fixture()

        val result = fixture.coordinator.coordinate(measurement(fixture.equipment.id.value, 22.0))

        assertIs<IncidentCoordinationResult.NoIncident>(result)
    }

    @Test
    fun `alarming measurement creates incident`() {
        val fixture = fixture()

        val result = fixture.coordinator.coordinate(measurement(fixture.equipment.id.value, 12.0))

        val created = assertIs<IncidentCoordinationResult.Created>(result)
        assertEquals(fixture.equipment.id, created.incident.equipmentId)
        assertEquals(12.0, created.incident.value)
    }

    @Test
    fun `alarming measurement without equipment context returns context lost`() {
        val fixture = fixture()

        val measurement = measurement(equipmentId = 999L, value = 12.0)
        val result = fixture.coordinator.coordinate(measurement)

        assertEquals(IncidentCoordinationResult.EquipmentContextLost(measurement), result)
    }

    private fun fixture(): Fixture {
        val facilityRepository = InMemoryFacilityRepository()
        val facility = facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val equipmentRepository = InMemoryEquipmentRepository()
        val equipment = assertIs<CreateEquipmentResult.Success>(
            equipmentRepository.create(facility.id, "Boiler", EquipmentType.HEATING)
        ).equipment
        val incidentService = IncidentService(
            facilityRepository,
            equipmentRepository,
            InMemoryIncidentRepository(clock),
            clock
        )
        return Fixture(
            equipment = equipment,
            coordinator = IncidentCoordinator(
                IncidentPolicy(),
                equipmentRepository,
                incidentService
            )
        )
    }

    private fun measurement(equipmentId: Long, value: Double): Measurement = Measurement.create(
        id = MeasurementId(400L),
        equipmentId = EquipmentId(equipmentId),
        measurementReading = MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, value),
        createdAt = clock.now()
    )

    private data class Fixture(
        val equipment: com.doduohor.domain.model.Equipment,
        val coordinator: IncidentCoordinator
    )
}
