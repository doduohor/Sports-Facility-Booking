package com.doduohor.domain.model

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
    FIRE_ALARM
}

data class Equipment(
    val id: Long,
    val facilityId: Long,
    val name: String,
    val type: EquipmentType,
    val status: EquipmentStatus
){
    companion object{
        fun create(id: Long, facilityId: Long, name: String, type: EquipmentType, status: EquipmentStatus): Equipment {
            return Equipment(
                id = id,
                facilityId = facilityId,
                name = name,
                type = type,
                status = status
            )
        }
    }
}