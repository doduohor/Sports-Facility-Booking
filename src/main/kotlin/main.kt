package com.doduohor

import com.doduohor.repository.InMemoryBookingRepository
import io.ktor.server.engine.*
import io.ktor.server.application.*
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.BookingService
import com.doduohor.service.FacilityService

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()

    val facilityRepository = InMemoryFacilityRepository()
    val bookingRepository = InMemoryBookingRepository()

    val facilityService = FacilityService(facilityRepository)
    val bookingService = BookingService(bookingRepository, facilityRepository)
    configureRouting(facilityService, bookingService)
}
