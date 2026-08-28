package com.doduohor

import com.doduohor.api.dto.BookingCreate
import com.doduohor.api.dto.BookingsResponse
import com.doduohor.api.dto.EquipmentCreate
import com.doduohor.api.dto.EquipmentsResponse
import com.doduohor.api.dto.FacilitiesResponse
import com.doduohor.api.dto.FacilityCreate
import com.doduohor.api.dto.ErrorResponse
import com.doduohor.api.dto.IncidentCreate
import com.doduohor.api.dto.IncidentsResponse
import com.doduohor.api.dto.MeasurementCreate
import com.doduohor.api.dto.MeasurementsResponse
import com.doduohor.api.dto.SuccessResponse
import com.doduohor.api.mapper.ApiInputParsers
import com.doduohor.api.mapper.toResponse
import com.doduohor.domain.shared.ParsingResult
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.shared.CustomerId
import com.doduohor.events.EventPublisher
import com.doduohor.events.eventStreamRoutes
import com.doduohor.service.ActivateFacilityResult
import com.doduohor.service.BookingService
import com.doduohor.service.CreateBookingResult
import com.doduohor.service.CreateEquipmentResult
import com.doduohor.service.CreateFacilityResult
import com.doduohor.service.CreateMeasurementResult
import com.doduohor.service.EquipmentService
import com.doduohor.service.FacilityService
import com.doduohor.service.FindByFacilityResult
import com.doduohor.service.FindEquipmentIdResult
import com.doduohor.service.FindEquipmentsByFacilityIdResult
import com.doduohor.service.FindIncidentsByEquipmentIdResult
import com.doduohor.service.FindIncidentsByFacilityIdResult
import com.doduohor.service.IncidentService
import com.doduohor.service.IncidentServiceResult
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import com.doduohor.service.MonitoringServiceResult
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.getValue

fun Application.configureRouting() {
    routing {
        val facilityService by inject<FacilityService>()
        val bookingService by inject<BookingService>()
        val equipmentService by inject<EquipmentService>()
        val measurementService by inject<MeasurementService>()
        val incidentService by inject<IncidentService>()
        val monitoringService by inject<MonitoringService>()
        val eventPublisher by inject<EventPublisher>()
        healthRoutes()
        facilityRoutes(facilityService)
        bookingRoutes(bookingService)
        equipmentRoutes(equipmentService)
        measurementRoutes(measurementService, monitoringService)
        incidentRoutes(incidentService)
        eventStreamRoutes(eventPublisher)
    }
}

private suspend fun ApplicationCall.requireJsonContentType(): Boolean {
    val contentType = request.headers[HttpHeaders.ContentType]?.let { request.contentType().withoutParameters() }
    if (contentType == ContentType.Application.Json) return true

    respond(
        HttpStatusCode.UnsupportedMediaType,
        ErrorResponse(415, "unsupportedContentType", "The Content-Type header is not supported")
    )
    return false
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
    authenticate("auth-basic"){
        post("/api/facilities") {
            if (!call.requireJsonContentType()) return@post
            val request = call.receive<FacilityCreate>()
            when(val result = ApiInputParsers.parseFacilityType(request.type)){
                is ParsingResult.Error -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidType", "The specified type does not exist in the system")
                )
                is ParsingResult.Success -> {
                    when(val facility = facilityService.createFacility(
                        name = request.name,
                        type = result.value
                    )){
                        CreateFacilityResult.InvalidName -> call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(400, "invalidName", "Facility name must not be blank")
                        )
                        is CreateFacilityResult.Success -> call.respond(
                            HttpStatusCode.Created,
                            facility.facility.toResponse()
                        )
                    }
                }

            }
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

    authenticate("auth-basic") {
        put("/api/facilities/{facilityId}/activate") {
            if (!call.receiveChannel().readRemaining().exhausted()) {
                if (!call.requireJsonContentType()) return@put
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "unexpectedRequestBody", "This endpoint does not accept a request body")
                )
                return@put
            }
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

}

fun Route.bookingRoutes(bookingService: BookingService) {
    authenticate("auth-basic") {
        post("/api/bookings") {
            if (!call.requireJsonContentType()) return@post
            val request = call.receive<BookingCreate>()
            val booking = bookingService.createBooking(
                request.facilityId,
                request.customerId,
                request.startTime,
                request.endTime,
                request.bookingDate
            )

            when (booking) {
                CreateBookingResult.InvalidFacilityId -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidFacilityId", "The Facility ID must be positive.")
                )

                CreateBookingResult.InvalidCustomerId -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidCustomerId", "Customer ID is not allowed to create bookings.")
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
    }

    get("/api/bookings") {
        val bookings = bookingService.findAll()
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
        val booking = bookingService.findByBookingId(bookingId)

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

        val bookings = bookingService.findByFacilityId(facilityId)
        when(bookings){
            FindByFacilityResult.InvalidFacilityId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId")
            )

            FindByFacilityResult.NotFindFacilityId -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
            )

            is FindByFacilityResult.Success -> call.respond(
                HttpStatusCode.OK,
                BookingsResponse(bookings.bookings.map {it -> it.toResponse()}))
        }
    }
}

fun Route.equipmentRoutes(equipmentService: EquipmentService){
    authenticate("auth-basic"){
        post("/api/equipments") {
            if (!call.requireJsonContentType()) return@post
            val request = call.receive<EquipmentCreate>()
            when(val parseTypeResult = ApiInputParsers.parseEquipmentType(request.type)){
                is ParsingResult.Error -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidType", "An incorrect type has been specified")
                )
                is ParsingResult.Success -> {
                    when(val equipment = equipmentService.create(request.facilityId, request.name, parseTypeResult.value)){
                        CreateEquipmentResult.InvalidFacilityId -> call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(400, "invalidFacilityId", "You entered an incorrect facilityId")
                        )

                        CreateEquipmentResult.NotFindFacilityId -> call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
                        )

                        CreateEquipmentResult.InvalidName -> call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(400, "invalidName", "An incorrect name has been specified")
                        )

                        is CreateEquipmentResult.Success -> call.respond(
                            HttpStatusCode.Created,
                            equipment.equipment.toResponse()
                        )
                    }
                }
            }
        }
    }

    get("/api/equipments"){
        val equipments = equipmentService.findAll()
        val response = equipments.map {equipments -> equipments.toResponse()}
        call.respond(HttpStatusCode.OK, EquipmentsResponse(response))
    }

    get("/api/equipments/{equipmentId}"){
        val equipmentId = call.parameters["equipmentId"]?.toLongOrNull()

        if(equipmentId == null){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
            )
            return@get
        }

        val equipment = equipmentService.findByEquipmentId(equipmentId)
        if(equipment == null){
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFound", "Equipment not found")
            )
            return@get
        }

        call.respond(HttpStatusCode.OK, equipment.toResponse())
    }

    get("/api/facilities/{facilityId}/equipments"){
        val facilityId = call.parameters["facilityId"]?.toLongOrNull()
        if(facilityId == null){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
            )
            return@get
        }

        val equipments = equipmentService.findByFacilityId(facilityId)
        when(equipments){
            FindEquipmentsByFacilityIdResult.InvalidFacilityId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
            )

            FindEquipmentsByFacilityIdResult.NotFindFacilityId -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
            )

            is FindEquipmentsByFacilityIdResult.Success -> call.respond(
                HttpStatusCode.OK,
                EquipmentsResponse(equipments.equipments.map {it -> it.toResponse()})
            )
        }
    }
}

fun Route.measurementRoutes(measurementService: MeasurementService, monitoringService: MonitoringService){
    authenticate("auth-basic") {
        post("/api/measurements") {
            if (!call.requireJsonContentType()) return@post
            val request = call.receive<MeasurementCreate>()
            val parseTypeResult = ApiInputParsers.parseMeasurementType(request.type)
            if (parseTypeResult is ParsingResult.Error) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidType", "An incorrect measurement type has been specified")
                )
                return@post
            }
            val measurementType = (parseTypeResult as ParsingResult.Success<MeasurementType>).value
            val parseUnitResult = ApiInputParsers.parseMeasurementUnit(request.unit)
            if (parseUnitResult is ParsingResult.Error) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidUnit", "An incorrect measurement unit has been specified")
                )
                return@post
            }
            val measurementUnit = (parseUnitResult as ParsingResult.Success<MeasurementUnit>).value
            val monitoringResult =
                monitoringService.processMeasurement(request.equipmentId, measurementType, measurementUnit, request.value)

            when (monitoringResult) {
                is MonitoringServiceResult.MeasurementCreateError -> when (monitoringResult.measurementResult) {
                    CreateMeasurementResult.InvalidEquipmentId -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
                    )

                    CreateMeasurementResult.NotFindEquipmentId -> call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
                    )

                    CreateMeasurementResult.InvalidMappingTypeAndUnit -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            400,
                            "invalidMappingTypeAndUnit",
                            "The measurement type does not match the specified unit"
                        )
                    )

                    CreateMeasurementResult.NotSupportedEquipmentType -> call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            500,
                            "notSupportedEquipmentType",
                            "Measurement rules are not configured for this equipment type"
                        )
                    )

                    CreateMeasurementResult.InvalidMeasurementType -> call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(
                            409,
                            "invalidMeasurementType",
                            "This measurement type is not supported by the specified equipment"
                        )
                    )

                    CreateMeasurementResult.InvalidValue -> call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(400, "invalidValue", "The measurement value is outside the allowed range")
                    )

                    CreateMeasurementResult.MeasurementRangeNotConfigured -> call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(500, "measurementRangeNotConfigured", "Measurement value range is not configured")
                    )

                    is CreateMeasurementResult.Success -> call.respond(
                        HttpStatusCode.Created,
                        monitoringResult.measurementResult.measurement.toResponse()
                    )

                    CreateMeasurementResult.NotFindMeasurementType -> call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(404, "notFindMeasurementType", "The specified Measurement Type does not exist")
                    )
                }

                is MonitoringServiceResult.EquipmentContextLost -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(
                        500,
                        "equipmentContextLost",
                        "Equipment context was not found after measurement creation"
                    )
                )

                is MonitoringServiceResult.IncidentCreateError -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(500, "incidentCreateError", "Measurement was created, but incident creation failed")
                )

                is MonitoringServiceResult.OutboxPersistenceError -> call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(500, "outboxPersistenceError", "Measurement event could not be stored")
                )

                is MonitoringServiceResult.SuccessWithIncident -> call.respond(
                    HttpStatusCode.Created,
                    monitoringResult.measurement.toResponse()
                )

                is MonitoringServiceResult.SuccessWithoutIncident -> call.respond(
                    HttpStatusCode.Created,
                    monitoringResult.measurement.toResponse()
                )
            }
        }
    }

    get("/api/measurements"){
        val measurements = measurementService.findAll()
        call.respond(HttpStatusCode.OK, MeasurementsResponse(measurements.map { it.toResponse() }))
    }

    get("/api/measurements/{measurementId}"){
        val measurementId = call.parameters["measurementId"]?.toLongOrNull()
        if(measurementId == null || measurementId <= 0){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidMeasurementId", "An incorrect Measurement ID has been specified")
            )
            return@get
        }

        val measurement = measurementService.findByMeasurementId(measurementId)
        if(measurement == null){
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindMeasurementId", "The specified Measurement ID does not exist")
            )
            return@get
        }

        call.respond(HttpStatusCode.OK, measurement.toResponse())
    }

    get("/api/equipments/{equipmentId}/measurements"){
        val equipmentId = call.parameters["equipmentId"]?.toLongOrNull()
        if(equipmentId == null){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
            )
            return@get
        }

        val measurements = measurementService.findByEquipmentId(equipmentId)
        when(measurements){
            FindEquipmentIdResult.InvalidEquipmentId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
            )

            FindEquipmentIdResult.NotFindEquipmentId -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
            )

            is FindEquipmentIdResult.Success -> call.respond(
                HttpStatusCode.OK,
                MeasurementsResponse(measurements.measurements.map { it.toResponse() })
            )
        }
    }
}

fun Route.incidentRoutes(incidentService: IncidentService){
    authenticate("auth-basic") {
        post("/api/incidents") {
            if (!call.requireJsonContentType()) return@post
            val request = call.receive<IncidentCreate>()
            val typeResult = ApiInputParsers.parseIncidentType(request.type)
            if (typeResult is ParsingResult.Error) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidType", "An incorrect incident type has been specified")
                )
                return@post
            }
            val severityResult = ApiInputParsers.parseIncidentSeverity(request.severity)
            if (severityResult is ParsingResult.Error) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidSeverity", "An incorrect incident severity has been specified")
                )
                return@post
            }
            val measurementTypeResult = ApiInputParsers.parseMeasurementType(request.measurementType)
            if (measurementTypeResult is ParsingResult.Error) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidMeasurementType", "An incorrect measurement type has been specified")
                )
                return@post
            }
            val measurementUnitResult = ApiInputParsers.parseMeasurementUnit(request.measurementUnit)
            if (measurementUnitResult is ParsingResult.Error) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidMeasurementUnit", "An incorrect measurement unit has been specified")
                )
                return@post
            }
            val incident = incidentService.create(
                facilityId = request.facilityId,
                equipmentId = request.equipmentId,
                measurementId = request.measurementId,
                type = (typeResult as ParsingResult.Success<IncidentType>).value,
                severity = (severityResult as ParsingResult.Success<IncidentSeverity>).value,
                measurementType = (measurementTypeResult as ParsingResult.Success<MeasurementType>).value,
                measurementUnit = (measurementUnitResult as ParsingResult.Success<MeasurementUnit>).value,
                value = request.value
            )

            when (incident) {
                IncidentServiceResult.InvalidFacilityId -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
                )

                IncidentServiceResult.InvalidEquipmentId -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
                )

                IncidentServiceResult.InvalidMeasurementId -> call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "invalidMeasurementId", "An incorrect Measurement ID has been specified")
                )

                IncidentServiceResult.NotFindFacilityId -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
                )

                IncidentServiceResult.NotFindEquipmentId -> call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
                )

                IncidentServiceResult.EquipmentDoesNotBelongToFacility -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(
                        409,
                        "equipmentDoesNotBelongToFacility",
                        "The equipment does not belong to the specified facility"
                    )
                )

                is IncidentServiceResult.Success -> call.respond(
                    HttpStatusCode.Created,
                    incident.incident.toResponse()
                )
            }
        }
    }

    get("/api/incidents"){
        val incidents = incidentService.findAll()
        call.respond(HttpStatusCode.OK, IncidentsResponse(incidents.map { it.toResponse() }))
    }

    get("/api/incidents/{incidentId}"){
        val incidentId = call.parameters["incidentId"]?.toLongOrNull()
        if(incidentId == null || incidentId <= 0){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidIncidentId", "An incorrect Incident ID has been specified")
            )
            return@get
        }

        val incident = incidentService.findByIncidentId(incidentId)
        if(incident == null){
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindIncidentId", "The specified Incident ID does not exist")
            )
            return@get
        }

        call.respond(HttpStatusCode.OK, incident.toResponse())
    }

    get("/api/facilities/{facilityId}/incidents"){
        val facilityId = call.parameters["facilityId"]?.toLongOrNull()
        if(facilityId == null){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
            )
            return@get
        }

        val incidents = incidentService.findByFacilityId(facilityId)
        when(incidents){
            FindIncidentsByFacilityIdResult.InvalidFacilityId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidFacilityId", "An incorrect Facility ID has been specified")
            )

            FindIncidentsByFacilityIdResult.NotFindFacilityId -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindFacilityId", "The specified Facility ID does not exist")
            )

            is FindIncidentsByFacilityIdResult.Success -> call.respond(
                HttpStatusCode.OK,
                IncidentsResponse(incidents.incidents.map { it.toResponse() })
            )
        }
    }

    get("/api/equipments/{equipmentId}/incidents"){
        val equipmentId = call.parameters["equipmentId"]?.toLongOrNull()
        if(equipmentId == null){
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
            )
            return@get
        }

        val incidents = incidentService.findByEquipmentId(equipmentId)
        when(incidents){
            FindIncidentsByEquipmentIdResult.InvalidEquipmentId -> call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(400, "invalidEquipmentId", "An incorrect Equipment ID has been specified")
            )

            FindIncidentsByEquipmentIdResult.NotFindEquipmentId -> call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(404, "notFindEquipmentId", "The specified Equipment ID does not exist")
            )

            is FindIncidentsByEquipmentIdResult.Success -> call.respond(
                HttpStatusCode.OK,
                IncidentsResponse(incidents.incidents.map { it.toResponse() })
            )
        }
    }
}
