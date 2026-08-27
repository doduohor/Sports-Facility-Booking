package com.doduohor.service

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingCreationResult
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.policy.BookingPolicy
import com.doduohor.infrastructure.time.TimeProvider
import com.doduohor.repository.BookingRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

class BookingService(private val bookingRepository: BookingRepository, private val facilityRepository: FacilityRepository) {
    private val bookingZoneId = TimeProvider.zoneId

    fun createBooking(facilityId: Long, customerId: Int, startTime: String, endTime: String, bookingDate: String): CreateBookingResult {
        if (facilityId <= 0) {
            return CreateBookingResult.InvalidFacilityId
        }

        val customerIdShared = CustomerId(customerId)

        if (!BookingPolicy.isValidCustomerId(customerIdShared)) {
            return CreateBookingResult.InvalidCustomerId
        }

        val facility = facilityRepository.findById(FacilityId(facilityId)) ?: return CreateBookingResult.NotFindFacilityId

        if (!facility.canAcceptBooking()) {
            return CreateBookingResult.InvalidStatusFacilityId
        }

        val interval = try {
            val date = LocalDate.parse(bookingDate)
            val start = LocalTime.parse(startTime)
            val end = LocalTime.parse(endTime)
            val startInstant = LocalDateTime.of(date, start).atZone(bookingZoneId).toInstant()
            val endInstant = LocalDateTime.of(date, end).atZone(bookingZoneId).toInstant()

            BookingTimeInterval(startInstant, endInstant)
        } catch (e: DateTimeParseException) {
            return CreateBookingResult.InvalidTimeInterval
        } catch (e: IllegalArgumentException) {
            return CreateBookingResult.InvalidTimeInterval
        }

        return when(val booking = bookingRepository.createIfAvailable(facility.id, customerIdShared, interval)){
            is BookingCreationResult.Success -> CreateBookingResult.Success(booking.value)
            BookingCreationResult.UnavailableRange -> CreateBookingResult.UnavailableRangeTimeLimit
        }
    }

    fun findByBookingId(id: Long): Booking? {
        if (id <= 0) return null
        return bookingRepository.findByBookingId(BookingId(id))
    }

    fun findByFacilityId(id: Long): FindByFacilityResult {
        if (id <= 0) {
            return FindByFacilityResult.InvalidFacilityId
        }

        val facility = facilityRepository.findById(FacilityId(id)) ?: return FindByFacilityResult.NotFindFacilityId
        return FindByFacilityResult.Success(bookingRepository.findByFacilityId(facility.id))
    }

    fun findAll(): List<Booking> {
        return bookingRepository.findAll()
    }
}

sealed interface CreateBookingResult {
    data class Success(val booking: Booking) : CreateBookingResult
    data object InvalidTimeInterval : CreateBookingResult
    data object NotFindFacilityId : CreateBookingResult
    data object InvalidCustomerId : CreateBookingResult
    data object InvalidFacilityId : CreateBookingResult
    data object InvalidStatusFacilityId : CreateBookingResult
    data object UnavailableRangeTimeLimit : CreateBookingResult
}

sealed interface FindByFacilityResult {
    data class Success(val bookings: List<Booking>): FindByFacilityResult
    data object InvalidFacilityId: FindByFacilityResult
    data object NotFindFacilityId: FindByFacilityResult
}
