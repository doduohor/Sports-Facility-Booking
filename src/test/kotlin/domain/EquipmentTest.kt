package com.doduohor.domain

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentCreateResult
import com.doduohor.domain.model.EquipmentDisableResult
import com.doduohor.domain.model.EquipmentEnableResult
import com.doduohor.domain.model.EquipmentMarkDefectiveResult
import com.doduohor.domain.model.EquipmentSendToRepairResult
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EquipmentTest {
    @Test
    fun `create new rejects blank name`() {
        val result = Equipment.createNew(EquipmentId(1), FacilityId(1), "   ", EquipmentType.VENTILATION)

        assertIs<EquipmentCreateResult.InvalidName>(result)
    }

    @Test
    fun `create new trims name and applies disabled status`() {
        val result = Equipment.createNew(EquipmentId(1), FacilityId(1), "  Main ventilation  ", EquipmentType.VENTILATION)

        val success = assertIs<EquipmentCreateResult.Success>(result)
        assertEquals("Main ventilation", success.equipment.name)
        assertEquals(EquipmentType.VENTILATION, success.equipment.type)
        assertEquals(FacilityId(1), success.equipment.facilityId)
        assertEquals(EquipmentStatus.DISABLED, success.equipment.status)
    }

    @Test
    fun `disabled equipment can be enabled`() {
        val equipment = equipmentWithStatus(EquipmentStatus.DISABLED)

        val result = equipment.enable()

        val success = assertIs<EquipmentEnableResult.Success>(result)
        assertEquals(EquipmentStatus.ACTIVE, equipment.status)
    }

    @Test
    fun `active equipment reports already active when enabled again`() {
        val equipment = equipmentWithStatus(EquipmentStatus.ACTIVE)

        val result = equipment.enable()

        assertIs<EquipmentEnableResult.AlreadyActive>(result)
    }

    @Test
    fun `repair equipment cannot be enabled directly`() {
        val equipment = equipmentWithStatus(EquipmentStatus.REPAIR)

        val result = equipment.enable()

        assertIs<EquipmentEnableResult.InvalidStatus>(result)
    }

    @Test
    fun `equipment needing replacement cannot be enabled directly`() {
        val equipment = equipmentWithStatus(EquipmentStatus.NEEDS_REPLACEMENT)

        val result = equipment.enable()

        assertIs<EquipmentEnableResult.InvalidStatus>(result)
    }

    @Test
    fun `active equipment can be disabled`() {
        val equipment = equipmentWithStatus(EquipmentStatus.ACTIVE)

        val result = equipment.disable()

        val success = assertIs<EquipmentDisableResult.Success>(result)
        assertEquals(EquipmentStatus.DISABLED, equipment.status)
    }

    @Test
    fun `disabled equipment reports already disabled when disabled again`() {
        val equipment = equipmentWithStatus(EquipmentStatus.DISABLED)

        val result = equipment.disable()

        assertIs<EquipmentDisableResult.AlreadyDisable>(result)
    }

    @Test
    fun `active equipment can be sent to repair`() {
        val equipment = equipmentWithStatus(EquipmentStatus.ACTIVE)

        val result = equipment.sendToRepair()

        val success = assertIs<EquipmentSendToRepairResult.Success>(result)
        assertEquals(EquipmentStatus.REPAIR, equipment.status)
    }

    @Test
    fun `disabled equipment cannot be sent to repair`() {
        val equipment = equipmentWithStatus(EquipmentStatus.DISABLED)

        val result = equipment.sendToRepair()

        assertIs<EquipmentSendToRepairResult.InvalidStatus>(result)
    }

    @Test
    fun `equipment needing replacement can be sent to repair`() {
        val equipment = equipmentWithStatus(EquipmentStatus.NEEDS_REPLACEMENT)

        val result = equipment.sendToRepair()

        val success = assertIs<EquipmentSendToRepairResult.Success>(result)
        assertEquals(EquipmentStatus.REPAIR, equipment.status)
    }

    @Test
    fun `equipment needing replacement can be administratively disabled`() {
        val equipment = equipmentWithStatus(EquipmentStatus.NEEDS_REPLACEMENT)

        val result = equipment.disable()

        val success = assertIs<EquipmentDisableResult.Success>(result)
        assertEquals(EquipmentStatus.DISABLED, equipment.status)
    }

    @Test
    fun `repair equipment can be marked defective`() {
        val equipment = equipmentWithStatus(EquipmentStatus.REPAIR)

        val result = equipment.markDefective()

        val success = assertIs<EquipmentMarkDefectiveResult.Success>(result)
        assertEquals(EquipmentStatus.DEFECTIVE, equipment.status)
    }

    @Test
    fun `active equipment cannot be marked defective without repair inspection`() {
        val equipment = equipmentWithStatus(EquipmentStatus.ACTIVE)

        val result = equipment.markDefective()

        assertIs<EquipmentMarkDefectiveResult.InvalidStatus>(result)
    }

    private fun equipmentWithStatus(status: EquipmentStatus): Equipment {
        return Equipment(
            id = EquipmentId(1),
            facilityId = FacilityId(1),
            name = "Main ventilation",
            type = EquipmentType.VENTILATION,
            status = status
        )
    }
}
