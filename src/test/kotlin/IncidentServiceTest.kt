package com.doduohor

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.service.IncidentService
import com.doduohor.service.IncidentServiceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IncidentServiceTest {
    @Test
    fun `create incident returns success for valid data`() {
        val fixture = createFixture()
        val facility = fixture.facilityRepository.create("Central Pool", FacilityType.POOL)
        val equipment = fixture.equipmentRepository.create(facility.id, "Fire alarm", EquipmentType.FIRE_ALARM)

        val result = fixture.service.create(
            facilityId = facility.id,
            equipmentId = equipment.id,
            measurementId = 400,
            type = "SMOKE_DETECTED",
            severity = "CRITICAL",
            measurementType = "SMOKE",
            measurementUnit = "PERCENT",
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
            fixture.service.create(0, 200, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        )
        assertIs<IncidentServiceResult.InvalidEquipmentId>(
            fixture.service.create(1, 0, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        )
        assertIs<IncidentServiceResult.InvalidMeasurementId>(
            fixture.service.create(1, 200, 0, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        )
    }

    @Test
    fun `create incident rejects unknown enum values`() {
        val fixture = createFixture()

        assertIs<IncidentServiceResult.InvalidType>(
            fixture.service.create(1, 200, 400, "UNKNOWN", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        )
        assertIs<IncidentServiceResult.InvalidSeverity>(
            fixture.service.create(1, 200, 400, "SMOKE_DETECTED", "UNKNOWN", "SMOKE", "PERCENT", 80.0)
        )
        assertIs<IncidentServiceResult.InvalidMeasurementType>(
            fixture.service.create(1, 200, 400, "SMOKE_DETECTED", "CRITICAL", "UNKNOWN", "PERCENT", 80.0)
        )
        assertIs<IncidentServiceResult.InvalidMeasurementUnit>(
            fixture.service.create(1, 200, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "UNKNOWN", 80.0)
        )
    }

    @Test
    fun `create incident rejects missing facility and equipment`() {
        val fixture = createFixture()

        assertIs<IncidentServiceResult.NotFindFacilityId>(
            fixture.service.create(1, 200, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        )

        val facility = fixture.facilityRepository.create("Central Pool", FacilityType.POOL)
        assertIs<IncidentServiceResult.NotFindEquipmentId>(
            fixture.service.create(facility.id, 999999, 400, "SMOKE_DETECTED", "CRITICAL", "SMOKE", "PERCENT", 80.0)
        )
    }

    @Test
    fun `create incident rejects equipment from another facility`() {
        val fixture = createFixture()
        val pool = fixture.facilityRepository.create("Central Pool", FacilityType.POOL)
        val gym = fixture.facilityRepository.create("Central Gym", FacilityType.GYM)
        val equipment = fixture.equipmentRepository.create(gym.id, "Gym ventilation", EquipmentType.VENTILATION)

        val result = fixture.service.create(
            facilityId = pool.id,
            equipmentId = equipment.id,
            measurementId = 400,
            type = "HIGH_CO2",
            severity = "HIGH",
            measurementType = "CO2",
            measurementUnit = "PPM",
            value = 1200.0
        )

        assertIs<IncidentServiceResult.EquipmentDoesNotBelongToFacility>(result)
    }

    private fun createFixture(): IncidentServiceFixture {
        val facilityRepository = InMemoryFacilityRepository()
        val equipmentRepository = InMemoryEquipmentRepository()
        val incidentRepository = InMemoryIncidentRepository()

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
