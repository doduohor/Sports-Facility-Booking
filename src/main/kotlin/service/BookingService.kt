package com.doduohor.service

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.model.canBeBooked
import com.doduohor.repository.BookingRepository
import com.doduohor.repository.FacilityRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

class BookingService(private val bookingRepository: BookingRepository, private val facilityRepository: FacilityRepository) {
    private val bookingZoneId = ZoneId.of("Europe/Moscow")

    fun createBooking(facilityId: Long, customerId: Int, startTime: String, endTime: String, bookingDate: String): CreateBookingResult {
        if (facilityId <= 0) {
            return CreateBookingResult.InvalidFacilityId
        }

        if (customerId !in 100..1000) {
            return CreateBookingResult.InvalidCustomerId
        }

        val facility = facilityRepository.findById(facilityId) ?: return CreateBookingResult.NotFindFacilityId

        if (!facility.canBeBooked()) {
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

        val checkInterval = bookingRepository.findOverlappingByFacilityId(facility.id, interval)
        if(checkInterval)
            return CreateBookingResult.UnavailableRangeTimeLimit

        val booking = bookingRepository.create(facility.id, customerId, interval)
        return CreateBookingResult.Success(booking)
    }

    fun getByBookingId(id: Long): Booking? {
        return bookingRepository.findByBookingId(id)
    }

    fun getByFacilityId(id: Long): List<Booking> {
        return bookingRepository.findByFacilityId(id)
    }

    fun getBookings(): List<Booking> {
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
