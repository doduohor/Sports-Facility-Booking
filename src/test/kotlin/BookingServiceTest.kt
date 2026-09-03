package com.doduohor

import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.FacilityActivateResult
import com.doduohor.domain.model.Facility
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.BookingService
import com.doduohor.service.CreateBookingResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BookingServiceTest {
    private val clock = FixedClock(Instant.parse("2026-08-12T06:30:00Z"))

    @Test
    fun `create booking accepts lower customer id boundary`() {
        val fixture = fixture()
        val facility = fixture.createActiveFacility()

        val result = fixture.service.createBooking(
            facilityId = facility.id.value,
            customerId = 900,
            startTime = "10:00",
            endTime = "11:00",
            bookingDate = "2026-08-12"
        )

        val success = assertIs<CreateBookingResult.Success>(result)
        assertEquals(900, success.booking.customerId.value)
    }

    @Test
    fun `create booking accepts upper customer id boundary`() {
        val fixture = fixture()
        val facility = fixture.createActiveFacility()

        val result = fixture.service.createBooking(
            facilityId = facility.id.value,
            customerId = 1000,
            startTime = "10:00",
            endTime = "11:00",
            bookingDate = "2026-08-12"
        )

        val success = assertIs<CreateBookingResult.Success>(result)
        assertEquals(1000, success.booking.customerId.value)
    }

    @Test
    fun `create booking returns unavailable range when interval is already reserved`() {
        val fixture = fixture()
        val facility = fixture.createActiveFacility()

        val firstResult = fixture.service.createBooking(
            facilityId = facility.id.value,
            customerId = 900,
            startTime = "10:00",
            endTime = "12:00",
            bookingDate = "2026-08-12"
        )
        assertIs<CreateBookingResult.Success>(firstResult)

        val overlappingResult = fixture.service.createBooking(
            facilityId = facility.id.value,
            customerId = 901,
            startTime = "11:00",
            endTime = "13:00",
            bookingDate = "2026-08-12"
        )

        assertIs<CreateBookingResult.UnavailableRangeTimeLimit>(overlappingResult)
    }

    @Test
    fun `create booking rejects customer id below lower boundary`() {
        val fixture = fixture()
        val facility = fixture.facilityRepository.create("Main Gym", FacilityType.GYM).getOrThrow()

        val result = fixture.service.createBooking(
            facilityId = facility.id.value,
            customerId = 899,
            startTime = "10:00",
            endTime = "11:00",
            bookingDate = "2026-08-12"
        )

        assertIs<CreateBookingResult.InvalidCustomerId>(result)
    }

    @Test
    fun `create booking rejects customer id above upper boundary`() {
        val fixture = fixture()
        val facility = fixture.facilityRepository.create("Main Gym", FacilityType.GYM).getOrThrow()

        val result = fixture.service.createBooking(
            facilityId = facility.id.value,
            customerId = 1001,
            startTime = "10:00",
            endTime = "11:00",
            bookingDate = "2026-08-12"
        )

        assertIs<CreateBookingResult.InvalidCustomerId>(result)
    }

    private fun fixture(): Fixture {
        val bookingRepository = InMemoryBookingRepository(clock)
        val facilityRepository = InMemoryFacilityRepository()
        return Fixture(
            facilityRepository = facilityRepository,
            service = BookingService(bookingRepository, facilityRepository)
        )
    }

    private data class Fixture(
        val facilityRepository: InMemoryFacilityRepository,
        val service: BookingService
    ) {
        fun createActiveFacility(): Facility {
            val facility = facilityRepository.create("Main Gym", FacilityType.GYM).getOrThrow()
            val activated = when (val result = facility.activate()) {
                is FacilityActivateResult.Success -> result.facility
                FacilityActivateResult.AlreadyActive -> facility
                FacilityActivateResult.InvalidStatus -> error("Test fixture facility cannot be activated")
            }
            return requireNotNull(facilityRepository.save(activated))
        }
    }
}
