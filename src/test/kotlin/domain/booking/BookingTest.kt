package com.doduohor.domain.booking

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingStatus
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class BookingTest {
    @Test
    fun `create new applies reserved status`() {
        val interval = BookingTimeInterval(
            startTime = Instant.parse("2026-08-12T07:00:00Z"),
            endTime = Instant.parse("2026-08-12T08:00:00Z")
        )
        val createdAt = Instant.parse("2026-08-12T06:30:00Z")

        val booking = Booking.createNew(
            id = BookingId(1),
            facilityId = FacilityId(1),
            customerId = CustomerId(900),
            timeInterval = interval,
            createdAt = createdAt
        )

        assertEquals(BookingId(1), booking.id)
        assertEquals(FacilityId(1), booking.facilityId)
        assertEquals(CustomerId(900), booking.customerId)
        assertEquals(interval, booking.timeInterval)
        assertEquals(BookingStatus.RESERVED, booking.status)
        assertEquals(createdAt, booking.createdAt)
    }
}
