package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class IncidentResponse(
    val id: Long,
    val facilityId: Long,
    val equipmentId: Long,
    val measurementId: Long,
    val type: String,
    val severity: String,
    val status: String,
    val measurementType: String,
    val measurementUnit: String,
    val value: Double,
    val createdAt: String,
    val statusChangedAt: String
)
