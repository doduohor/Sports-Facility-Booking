package com.doduohor.repository

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingTimeInterval

interface BookingRepository {
    fun create(facilityId: Long, customerId: Int, timeInterval: BookingTimeInterval): Booking
    fun findByBookingId(id: Long): Booking?
    fun findByFacilityId(id: Long): List<Booking>
    fun findAll(): List<Booking>
    fun findOverlappingByFacilityId(facilityId: Long, timeInterval: BookingTimeInterval): Boolean
}
