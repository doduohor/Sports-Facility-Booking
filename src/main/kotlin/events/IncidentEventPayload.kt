package com.doduohor.events

import kotlinx.serialization.Serializable

@Serializable
data class IncidentEventPayload(
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
    val createdAt: String
)
