package com.doduohor.domain.model

import java.time.Instant

enum class BookingStatus {
    RESERVED,
    AVAILABLE_SOON,
    AVAILABLE,
    NOT_AVAILABLE
}

data class Booking(
    val id: Long,
    val facilityId: Long,
    val customerId: Int,
    val timeInterval: BookingTimeInterval,
    val status: BookingStatus,
    val createdAt: Instant
) {
    companion object {
        val DEFAULT_STATUS = BookingStatus.RESERVED
        fun createNew(id: Long, facilityId: Long, customerId: Int, timeInterval: BookingTimeInterval, createdAt: Instant = Instant.now()): Booking {
            return Booking(
                id = id,
                facilityId = facilityId,
                customerId = customerId,
                timeInterval = timeInterval,
                status = DEFAULT_STATUS,
                createdAt = createdAt
            )
        }
    }
}
