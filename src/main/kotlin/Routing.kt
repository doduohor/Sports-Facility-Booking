package com.doduohor
import com.doduohor.api.dto.SuccessResponse
import com.doduohor.api.dto.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(HttpStatusCode.OK, SuccessResponse("Result","Hello, World!"))
        }
        get("/json/kotlinx-serialization") {
            call.respond(HttpStatusCode.OK, SuccessResponse("Serialization", "OK"))
        }
        get("/health"){
            call.respond(HttpStatusCode.OK, SuccessResponse("Health", "UP"))
        }
        get("/api/facilities/{facilityId}/readings") {
            val facilityId = call.parameters["facilityId"]?.toLongOrNull()
            val limitCnt = call.request.queryParameters["limit"]
            if(facilityId == null){
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400,"Error","Invalid facilityId"))
                return@get
            }
            if(limitCnt != null && limitCnt.toIntOrNull() == null){
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400,"Error", "Invalid limit"))
                return@get
            }
            val limit = limitCnt?.toIntOrNull()
            if(limit != null && (limit !in 1..1000)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "Invalid limit", "The limit must not fall within the range of 1 to 1,000")
                )
                return@get
            }
            call.respond(HttpStatusCode.OK, SuccessResponse("Readings", "OK"))
        }
    }
}