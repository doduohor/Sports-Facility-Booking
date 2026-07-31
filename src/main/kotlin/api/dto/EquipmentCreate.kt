package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class EquipmentCreate(
    val facilityId: Long,
    val name: String,
    val type: String
)
