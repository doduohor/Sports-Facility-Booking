package com.doduohor.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookingsResponse(val items: List<BookingResponse>)
