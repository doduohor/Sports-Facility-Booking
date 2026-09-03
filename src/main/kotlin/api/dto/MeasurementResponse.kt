package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementResponse(
    val id: Long,
    val equipmentId: Long,
    val type: String,
    val unit: String,
    val value: Double,
    val createdAt: String
)
