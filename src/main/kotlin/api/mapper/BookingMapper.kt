package com.doduohor.api.mapper

import com.doduohor.api.dto.BookingResponse
import com.doduohor.domain.model.Booking

fun Booking.toResponse(): BookingResponse =
    BookingResponse(
        id = id.value,
        facilityId = facilityId.value,
        customerId = customerId.value,
        timeInterval = "${timeInterval.startTime}/${timeInterval.endTime}",
        status = status.name.lowercase(),
        createdAt = createdAt.toString()
    )
