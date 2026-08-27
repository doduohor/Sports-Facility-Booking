package com.doduohor.repository

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingCreationResult
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId

interface BookingRepository {
    fun createIfAvailable(facilityId: FacilityId, customerId: CustomerId, timeInterval: BookingTimeInterval): BookingCreationResult<Booking>
    fun findByBookingId(id: BookingId): Booking?
    fun findByFacilityId(id: FacilityId): List<Booking>
    fun findAll(): List<Booking>
}
