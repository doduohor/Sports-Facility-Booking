package com.doduohor.domain.model

import java.time.Instant

data class BookingTimeInterval(val startTime: Instant, val endTime: Instant) {
    init {
        require(startTime < endTime) { "The end time must be later than the start time" }
    }
}
