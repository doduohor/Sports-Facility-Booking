package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class EquipmentResponse(
    val id: Long,
    val facilityId: Long,
    val name: String,
    val type: String,
    val status: String
)
