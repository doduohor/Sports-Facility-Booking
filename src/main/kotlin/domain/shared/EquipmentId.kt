package com.doduohor.domain.shared

@JvmInline
value class EquipmentId(val value: Long){
    init {
        require(value > 0) {"EquipmentId must be positive"}
    }
}