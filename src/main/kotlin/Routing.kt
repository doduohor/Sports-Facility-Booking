package com.doduohor

import com.doduohor.api.dto.BookingCreate
import com.doduohor.api.dto.BookingsResponse
import com.doduohor.api.dto.FacilitiesResponse
import com.doduohor.api.dto.FacilityCreate
import com.doduohor.api.dto.ErrorResponse
import com.doduohor.api.dto.SuccessResponse
import com.doduohor.api.mapper.toResponse
import com.doduohor.service.ActivateFacilityResult
import com.doduohor.service.BookingService
import com.doduohor.service.CreateBookingResult
import com.doduohor.service.CreateFacilityResult
import com.doduohor.service.FacilityService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(facilityService: FacilityService, bookingService: BookingService) {
    routing {
        healthRoutes()
        facilityRoutes(facilityService)
        bookingRoutes(bookingService)
    }
}

fun Route.healthRoutes() {
    get("/") {
        call.respond(HttpStatusCode.OK, SuccessResponse("Result", "Hello, World!"))
    }
    get("/json/kotlinx-serialization") {
        call.respond(HttpStatusCode.OK, SuccessResponse("Serialization", "OK"))
    }
    get("/health") {
        call.respond(HttpStatusCode.OK, SuccessResponse("Health", "UP"))
    }
}

fun Route.facilityRoutes(facilityService: FacilityService) {
    post("/api/facilities") {
        val request = call.receive<FacilityCreate>()
        val facility = facilityService.createFacility(
            name = request.name,
            type = request.type
        )

        when (facility) {
            CreateFacilityResult.InvalidName -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidName", "Facility name must not be blank")
            )

            CreateFacilityResult.InvalidType -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidType", "The specified type does not exist in the system")
            )

            is CreateFacilityResult.Success -> call.respond(
                HttpStatusCode.Created,
                facility.facility.toResponse()
            )
        }
    }

    get("/api/facilities/{facilityId}/readings") {
        val facilityId = call.parameters["facilityId"]?.toLongOrNull()
        val limitCnt = call.request.queryParameters["limit"]
        if (facilityId == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Error", "Invalid facilityId"))
            return@get
        }
        if (limitCnt != null && limitCnt.toIntOrNull() == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Error", "Invalid limit"))
            return@get
        }
        val limit = limitCnt?.toIntOrNull()
        if (limit != null && (limit !in 1..1000)) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "Invalid limit", "The limit must not fall within the range of 1 to 1,000")
            )
            return@get
        }
        call.respond(HttpStatusCode.OK, SuccessResponse("Readings", "OK"))
    }
    get("/api/facilities/{facilityId}") {
        val facilityId = call.parameters["facilityId"]?.toLongOrNull()
        if (facilityId == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Error", "Invalid facilityId"))
            return@get
        }
        val facility = facilityService.getFacilityById(facilityId)
        if (facility == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Error", "Not Found"))
            return@get
        }
        call.respond(HttpStatusCode.OK, facility.toResponse())
    }

    get("/api/facilities") {
        val facilities = facilityService.getFacilities()
        val response = facilities.map { facility -> facility.toResponse() }
        call.respond(HttpStatusCode.OK, FacilitiesResponse(response))
    }

    put("/api/facilities/{facilityId}/activate") {
        val facilityId = call.parameters["facilityId"]?.toLongOrNull()

        if (facilityId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId")
            )
            return@put
        }
        val activateFacility = facilityService.activateFacility(facilityId)
        when (activateFacility) {
            ActivateFacilityResult.InvalidStatus -> call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(409, "invalidStatus", "The object cannot be activated from its current status")
            )

            ActivateFacilityResult.AlreadyActive -> call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(409, "alreadyActive", "The object is already active")
            )

            ActivateFacilityResult.NotFound -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "Error", "Not Found")
            )

            is ActivateFacilityResult.Success -> call.respond(
                HttpStatusCode.OK,
                activateFacility.facility.toResponse()
            )
        }
    }
}

fun Route.bookingRoutes(bookingService: BookingService) {
    post("/api/bookings") {
        val request = call.receive<BookingCreate>()
        val booking = bookingService.createBooking(request.facilityId, request.customerId, request.startTime, request.endTime, request.bookingDate)

        when (booking) {
            CreateBookingResult.InvalidFacilityId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "The Facility ID must be positive.")
            )

            CreateBookingResult.InvalidCustomerId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidCustomerId", "The Customer ID must be in the range of 100 to 1000.")
            )

            CreateBookingResult.InvalidTimeInterval -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidTimeInterval", "The time interval must be between 1 and 12 hours.")
            )

            CreateBookingResult.NotFindFacilityId -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindFacilityId", "The specified facilityId was not found.")
            )

            CreateBookingResult.InvalidStatusFacilityId -> call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    409,
                    "invalidStatusFacilityId",
                    "The status of this facility does not allow bookings to be created."
                )
            )

            CreateBookingResult.UnavailableRangeTimeLimit -> call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(
                    409,
                    "unavailableRangeTimeLimit",
                    "The specified time slot is partially or fully booked."
                )
            )

            is CreateBookingResult.Success -> call.respond(
                HttpStatusCode.Created,
                booking.booking.toResponse()
            )
        }
    }

    get("/api/bookings") {
        val bookings = bookingService.getBookings()
        val response = bookings.map { booking -> booking.toResponse() }
        call.respond(HttpStatusCode.OK, BookingsResponse(response))
    }

    get("/api/bookings/{bookingId}") {
        val bookingId = call.parameters["bookingId"]?.toLongOrNull()

        if (bookingId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidBookingId", "You entered an incorrect bookingId")
            )
            return@get
        }
        val booking = bookingService.getByBookingId(bookingId)

        if (booking == null) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "Error", "Not found")
            )
            return@get
        }
        call.respond(HttpStatusCode.OK, booking.toResponse())
    }

    get("/api/facilities/{facilityId}/bookings") {
        val facilityId = call.parameters["facilityId"]?.toLongOrNull()

        if (facilityId == null) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId")
            )
            return@get
        }

        val bookings = bookingService.getByFacilityId(facilityId)
        val response = bookings.map { booking -> booking.toResponse() }
        call.respond(HttpStatusCode.OK, BookingsResponse(response))
    }
}
