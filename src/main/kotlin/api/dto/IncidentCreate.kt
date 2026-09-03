package com.doduohor.api.dto
import kotlinx.serialization.Serializable

@Serializable
data class IncidentCreate(
    val facilityId: Long,
    val equipmentId: Long,
    val measurementId: Long,
    val type: String,
    val severity: String,
    val measurementType: String,
    val measurementUnit: String,
    val value: Double,
)
