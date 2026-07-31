package com.doduohor

import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryEquipmentRepository
import io.ktor.server.engine.*
import io.ktor.server.application.*
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.BookingService
import com.doduohor.service.EquipmentService
import com.doduohor.service.FacilityService

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()

    val facilityRepository = InMemoryFacilityRepository()
    val bookingRepository = InMemoryBookingRepository()
    val equipmentRepository = InMemoryEquipmentRepository()

    val facilityService = FacilityService(facilityRepository)
    val bookingService = BookingService(bookingRepository, facilityRepository)
    val equipmentService = EquipmentService(equipmentRepository, facilityRepository)
    configureRouting(facilityService, bookingService, equipmentService)
}
