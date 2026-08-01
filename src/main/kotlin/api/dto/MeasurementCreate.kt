package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementCreate(
    val equipmentId: Long,
    val type: String,
    val unit: String,
    val value: Double
)
