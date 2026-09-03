package com.doduohor

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.FacilityType
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.CreateEquipmentResult
import com.doduohor.service.EquipmentService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EquipmentServiceTest {
    @Test
    fun `create equipment accepts typed equipment type`() {
        val facilityRepository = InMemoryFacilityRepository()
        val equipmentRepository = InMemoryEquipmentRepository()
        val service = EquipmentService(equipmentRepository, facilityRepository)
        val facility = facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()

        val result = service.create(facility.id.value, "Main ventilation", EquipmentType.VENTILATION)

        val success = assertIs<CreateEquipmentResult.Success>(result)
        assertEquals("Main ventilation", success.equipment.name)
        assertEquals(EquipmentType.VENTILATION, success.equipment.type)
        assertEquals(EquipmentStatus.DISABLED, success.equipment.status)
        assertEquals(facility.id, success.equipment.facilityId)
    }

    @Test
    fun `create equipment rejects blank name`() {
        val facilityRepository = InMemoryFacilityRepository()
        val service = EquipmentService(InMemoryEquipmentRepository(), facilityRepository)
        val facility = facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()

        val result = service.create(facility.id.value, "   ", EquipmentType.VENTILATION)

        assertIs<CreateEquipmentResult.InvalidName>(result)
    }

    @Test
    fun `create equipment rejects missing facility`() {
        val service = EquipmentService(
            InMemoryEquipmentRepository(),
            InMemoryFacilityRepository()
        )

        val result = service.create(999, "Main ventilation", EquipmentType.VENTILATION)

        assertIs<CreateEquipmentResult.NotFindFacilityId>(result)
    }
}
