package com.doduohor.repository

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingCreationResult
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.Clock
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
class InMemoryBookingRepository(
    private val clock: Clock
) : BookingRepository {
    private val bookings = mutableMapOf<BookingId, Booking>()
    private var nextId = 100L

    override fun createIfAvailable(
        facilityId: FacilityId,
        customerId: CustomerId,
        timeInterval: BookingTimeInterval
    ): BookingCreationResult<Booking> {
        val hasOverlap = bookings.values.any {
            it.facilityId == facilityId && it.timeInterval.overlaps(timeInterval)
        }

        if (hasOverlap) {
            return BookingCreationResult.UnavailableRange
        }

        val id = BookingId(nextId)
        nextId++

        val booking = Booking.createNew(
            id = id,
            facilityId = facilityId,
            customerId = customerId,
            timeInterval = timeInterval,
            createdAt = clock.now()
        )

        bookings[id] = booking
        return BookingCreationResult.Success(booking)
    }

    override fun findByBookingId(id: BookingId): Booking? = bookings[id]

    override fun findByFacilityId(id: FacilityId): List<Booking> = bookings.values.filter { it.facilityId == id }

    override fun findAll(): List<Booking> = bookings.values.toList()

}
