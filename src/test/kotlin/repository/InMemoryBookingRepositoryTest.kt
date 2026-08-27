package com.doduohor.repository

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingCreationResult
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.infrastructure.time.FixedClock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs

class InMemoryBookingRepositoryTest {
    @Test
    fun `createIfAvailable uses start inclusive and end exclusive interval semantics`() {
        val repository = InMemoryBookingRepository(FixedClock(Instant.parse("2026-08-12T06:30:00Z")))
        val facilityId = FacilityId(1)
        repository.createIfAvailable(
            facilityId = facilityId,
            customerId = CustomerId(900),
            timeInterval = bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T12:00:00Z")
        ).successBooking()

        assertIs<BookingCreationResult.Success<Booking>>(repository.createIfAvailable(
            facilityId,
            CustomerId(901),
            bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T10:00:00Z")
        ))
        assertIs<BookingCreationResult.UnavailableRange>(repository.createIfAvailable(
            facilityId,
            CustomerId(902),
            bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T11:00:00Z")
        ))
        assertIs<BookingCreationResult.UnavailableRange>(repository.createIfAvailable(
            facilityId,
            CustomerId(903),
            bookingInterval("2026-08-12T11:00:00Z", "2026-08-12T12:00:00Z")
        ))
        assertIs<BookingCreationResult.Success<Booking>>(repository.createIfAvailable(
            facilityId,
            CustomerId(904),
            bookingInterval("2026-08-12T12:00:00Z", "2026-08-12T13:00:00Z")
        ))
        assertIs<BookingCreationResult.UnavailableRange>(repository.createIfAvailable(
            facilityId,
            CustomerId(905),
            bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T13:00:00Z")
        ))
    }

    private fun BookingCreationResult<Booking>.successBooking(): Booking =
        assertIs<BookingCreationResult.Success<Booking>>(this).value

    private fun bookingInterval(startTime: String, endTime: String) =
        BookingTimeInterval(
            startTime = Instant.parse(startTime),
            endTime = Instant.parse(endTime)
        )
}
