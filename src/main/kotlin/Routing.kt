package com.doduohor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/json/kotlinx-serialization") {
            call.respond(mapOf("hello" to "world"))
        }
        get("/health"){
            call.respond(mapOf("status" to "UP"))
        }
        get("/api/facilities/{facilityId}/readings") {
            val facilityId = call.parameters["facilityId"]?.toLongOrNull()
            val limitCnt = call.request.queryParameters["limit"]
            if(facilityId == null){
                call.respond(HttpStatusCode.BadRequest, "Invalid facilityId")
                return@get
            }
            if(limitCnt != null && limitCnt.toIntOrNull() == null){
                call.respond(HttpStatusCode.BadRequest, "Invalid limit")
                return@get
            }
            call.respond(mapOf("readings" to "OK"))
        }
    }
}