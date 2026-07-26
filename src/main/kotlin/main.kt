package com.doduohor

import io.ktor.server.engine.*
import io.ktor.server.application.*
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.FacilityService

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()

    val facilityRepository = InMemoryFacilityRepository()
    val facilityService = FacilityService(facilityRepository)
    configureRouting(facilityService)
}
