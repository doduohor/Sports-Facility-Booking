package com.doduohor.domain.model

import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import java.time.Instant

enum class BookingStatus {
    RESERVED,
    AVAILABLE_SOON,
    AVAILABLE,
    NOT_AVAILABLE
}

data class Booking(
    val id: BookingId,
    val facilityId: FacilityId,
    val customerId: CustomerId,
    val timeInterval: BookingTimeInterval,
    val status: BookingStatus,
    val createdAt: Instant
) {
    companion object {
        val DEFAULT_STATUS = BookingStatus.RESERVED
        fun createNew(id: BookingId, facilityId: FacilityId, customerId: CustomerId, timeInterval: BookingTimeInterval, createdAt: Instant ): Booking {
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

sealed interface BookingCreationResult<out T>{
    data class Success<T>(val value: T): BookingCreationResult<T>
    data object UnavailableRange: BookingCreationResult<Nothing>
}
