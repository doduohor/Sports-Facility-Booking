package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookingResponse(
    val id: Long,
    val facilityId: Long,
    val customerId: Int,
    val timeInterval: String,
    val status: String,
    val createdAt: String
)
