package com.doduohor

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.service.IncidentService
import com.doduohor.service.IncidentServiceResult
import com.doduohor.service.CreateEquipmentResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IncidentServiceTest {
    @Test
    fun `create incident returns success for valid data`() {
        val fixture = createFixture()
        val facility = fixture.facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val equipment = assertIs<CreateEquipmentResult.Success>(fixture.equipmentRepository.create(facility.id, "Fire alarm", EquipmentType.FIRE_ALARM)).equipment

        val result = fixture.service.create(
            facilityId = facility.id.value,
            equipmentId = equipment.id.value,
            measurementId = 400,
            type = IncidentType.SMOKE_DETECTED,
            severity = IncidentSeverity.CRITICAL,
            measurementType = MeasurementType.SMOKE,
            measurementUnit = MeasurementUnit.PERCENT,
            value = 80.0
        )

        val success = assertIs<IncidentServiceResult.Success>(result)
        assertEquals(facility.id, success.incident.facilityId)
        assertEquals(equipment.id, success.incident.equipmentId)
    }

    @Test
    fun `create incident rejects invalid ids`() {
        val fixture = createFixture()

        assertIs<IncidentServiceResult.InvalidFacilityId>(
            fixture.service.create(0, 200, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
        assertIs<IncidentServiceResult.InvalidEquipmentId>(
            fixture.service.create(1, 0, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
        assertIs<IncidentServiceResult.InvalidMeasurementId>(
            fixture.service.create(1, 200, 0, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
    }

    @Test
    fun `create incident rejects missing facility and equipment`() {
        val fixture = createFixture()

        assertIs<IncidentServiceResult.NotFindFacilityId>(
            fixture.service.create(1, 200, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )

        val facility = fixture.facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        assertIs<IncidentServiceResult.NotFindEquipmentId>(
            fixture.service.create(facility.id.value, 999999, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
    }

    @Test
    fun `create incident rejects equipment from another facility`() {
        val fixture = createFixture()
        val pool = fixture.facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val gym = fixture.facilityRepository.create("Central Gym", FacilityType.GYM).getOrThrow()
        val equipment = assertIs<CreateEquipmentResult.Success>(fixture.equipmentRepository.create(gym.id, "Gym ventilation", EquipmentType.VENTILATION)).equipment

        val result = fixture.service.create(
            facilityId = pool.id.value,
            equipmentId = equipment.id.value,
            measurementId = 400,
            type = IncidentType.HIGH_CO2,
            severity = IncidentSeverity.HIGH,
            measurementType = MeasurementType.CO2,
            measurementUnit = MeasurementUnit.PPM,
            value = 1200.0
        )

        assertIs<IncidentServiceResult.EquipmentDoesNotBelongToFacility>(result)
    }

    private fun createFixture(): IncidentServiceFixture {
        val facilityRepository = InMemoryFacilityRepository()
        val equipmentRepository = InMemoryEquipmentRepository()
        val incidentRepository = InMemoryIncidentRepository(FixedClock(Instant.parse("2026-08-20T12:00:00Z")))

        return IncidentServiceFixture(
            facilityRepository = facilityRepository,
            equipmentRepository = equipmentRepository,
            service = IncidentService(facilityRepository, equipmentRepository, incidentRepository)
        )
    }

    private data class IncidentServiceFixture(
        val facilityRepository: InMemoryFacilityRepository,
        val equipmentRepository: InMemoryEquipmentRepository,
        val service: IncidentService
    )
}
