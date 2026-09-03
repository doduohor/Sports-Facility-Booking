package com.doduohor.domain.model

import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId

enum class EquipmentStatus {
    ACTIVE,
    DISABLED,
    REPAIR,
    DEFECTIVE,
    NEEDS_REPLACEMENT
}

enum class EquipmentType{
    VENTILATION,
    HEATING,
    WATER_SUPPLY,
    FIRE_ALARM;

    companion object {
        fun fromString(value: String): EquipmentType? =
            entries.firstOrNull { it.name.equals( value.trim(), true) }
    }
}

data class Equipment(
    val id: EquipmentId,
    val facilityId: FacilityId,
    val name: String,
    val type: EquipmentType,
    var status: EquipmentStatus
){
    fun enable(): EquipmentEnableResult{
        return when(status){
            EquipmentStatus.ACTIVE -> EquipmentEnableResult.AlreadyActive
            EquipmentStatus.DISABLED -> {
                this.status = EquipmentStatus.ACTIVE
                EquipmentEnableResult.Success
            }
            EquipmentStatus.REPAIR -> EquipmentEnableResult.InvalidStatus
            EquipmentStatus.DEFECTIVE -> EquipmentEnableResult.InvalidStatus
            EquipmentStatus.NEEDS_REPLACEMENT -> EquipmentEnableResult.InvalidStatus
        }
    }

    fun disable(): EquipmentDisableResult{
        return when(status){
            EquipmentStatus.ACTIVE -> {
                this.status = EquipmentStatus.DISABLED
                EquipmentDisableResult.Success
            }
            EquipmentStatus.DISABLED -> EquipmentDisableResult.AlreadyDisable
            EquipmentStatus.REPAIR -> {
                this.status = EquipmentStatus.DISABLED
                EquipmentDisableResult.Success
            }
            EquipmentStatus.DEFECTIVE -> {
                this.status = EquipmentStatus.DISABLED
                EquipmentDisableResult.Success
            }
            EquipmentStatus.NEEDS_REPLACEMENT -> {
                this.status = EquipmentStatus.DISABLED
                EquipmentDisableResult.Success
            }
        }
    }

    fun sendToRepair(): EquipmentSendToRepairResult{
        return when(status){
            EquipmentStatus.ACTIVE -> {
                this.status = EquipmentStatus.REPAIR
                EquipmentSendToRepairResult.Success
            }
            EquipmentStatus.DISABLED -> EquipmentSendToRepairResult.InvalidStatus
            EquipmentStatus.REPAIR -> EquipmentSendToRepairResult.AlreadyRepair
            EquipmentStatus.DEFECTIVE -> EquipmentSendToRepairResult.InvalidStatus
            EquipmentStatus.NEEDS_REPLACEMENT -> {
                this.status = EquipmentStatus.REPAIR
                EquipmentSendToRepairResult.Success
            }
        }
    }

    fun markDefective(): EquipmentMarkDefectiveResult{
        return when(status){
            EquipmentStatus.ACTIVE -> EquipmentMarkDefectiveResult.InvalidStatus
            EquipmentStatus.DISABLED -> EquipmentMarkDefectiveResult.InvalidStatus
            EquipmentStatus.REPAIR -> {
                this.status = EquipmentStatus.DEFECTIVE
                EquipmentMarkDefectiveResult.Success
            }
            EquipmentStatus.DEFECTIVE -> EquipmentMarkDefectiveResult.AlreadyDefective
            EquipmentStatus.NEEDS_REPLACEMENT -> EquipmentMarkDefectiveResult.AlreadyDefective
        }
    }

    companion object{
        val DEFAULT_STATUS = EquipmentStatus.DISABLED
        fun createNew(id: EquipmentId, facilityId: FacilityId, name: String, type: EquipmentType): EquipmentCreateResult {
            return if(name.isBlank()) EquipmentCreateResult.InvalidName
            else EquipmentCreateResult
                .Success(
                    Equipment(
                        id = id,
                        facilityId = facilityId,
                        name = name.trim(),
                        type = type,
                        status = EquipmentStatus.DISABLED
                    )
                )
        }
    }
}

sealed interface EquipmentCreateResult{
    data class Success(val equipment: Equipment): EquipmentCreateResult
    data object InvalidName: EquipmentCreateResult
}

sealed interface EquipmentEnableResult{
    data object Success: EquipmentEnableResult
    data object AlreadyActive: EquipmentEnableResult
    data object InvalidStatus: EquipmentEnableResult
}

sealed interface EquipmentDisableResult{
    data object Success: EquipmentDisableResult
    data object AlreadyDisable: EquipmentDisableResult
}

sealed interface EquipmentSendToRepairResult{
    data object Success: EquipmentSendToRepairResult
    data object AlreadyRepair: EquipmentSendToRepairResult
    data object InvalidStatus: EquipmentSendToRepairResult
}

sealed interface EquipmentMarkDefectiveResult{
    data object Success: EquipmentMarkDefectiveResult
    data object AlreadyDefective: EquipmentMarkDefectiveResult
    data object InvalidStatus: EquipmentMarkDefectiveResult
}