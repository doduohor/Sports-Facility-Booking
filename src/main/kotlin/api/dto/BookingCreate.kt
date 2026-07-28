package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookingCreate(
    val facilityId: Long,
    val customerId: Int,
    val startTime: String,
    val endTime: String,
    val bookingDate: String
)
