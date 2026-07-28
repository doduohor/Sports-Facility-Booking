package com.doduohor.repository

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingTimeInterval

class InMemoryBookingRepository : BookingRepository {
    private val bookings = mutableMapOf<Long, Booking>()
    private var nextId = 1L

    override fun create(facilityId: Long, customerId: Int, timeInterval: BookingTimeInterval): Booking {
        val id = nextId
        nextId++

        val booking = Booking.createNew(
            id = id,
            facilityId = facilityId,
            customerId = customerId,
            timeInterval = timeInterval
        )

        bookings[id] = booking
        return booking
    }

    override fun findByBookingId(id: Long): Booking? = bookings[id]

    override fun findByFacilityId(id: Long): List<Booking> = bookings.values.filter { it.facilityId == id }

    override fun findAll(): List<Booking> = bookings.values.toList()

    override fun findOverlappingByFacilityId(facilityId: Long, timeInterval: BookingTimeInterval): Boolean =
        bookings.values.filter { it.facilityId == facilityId }.any {
            it.timeInterval.startTime < timeInterval.endTime &&
                    it.timeInterval.endTime > timeInterval.startTime }
}
