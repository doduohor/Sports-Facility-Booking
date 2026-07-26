package com.doduohor

import com.doduohor.api.dto.FacilitiesResponse
import com.doduohor.api.dto.FacilityCreate
import com.doduohor.api.dto.ErrorResponse
import com.doduohor.api.dto.SuccessResponse
import com.doduohor.api.mapper.toResponse
import com.doduohor.service.CreateFacilityResult
import com.doduohor.service.FacilityService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(facilityService: FacilityService) {
    routing {
        healthRoutes()
        facilityRoutes(facilityService)
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
}
