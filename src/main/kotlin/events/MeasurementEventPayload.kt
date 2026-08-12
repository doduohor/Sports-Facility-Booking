package com.doduohor.events

import kotlinx.serialization.Serializable

@Serializable
data class MeasurementEventPayload(
    val id: Long,
    val equipmentId: Long,
    val type: String,
    val unit: String,
    val value: Double,
    val createdAt: String
)
