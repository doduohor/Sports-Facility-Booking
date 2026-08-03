package com.doduohor

import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryEquipmentRepository
import io.ktor.server.engine.*
import io.ktor.server.application.*
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import com.doduohor.service.BookingService
import com.doduohor.service.EquipmentService
import com.doduohor.service.FacilityService
import com.doduohor.service.IncidentPolicy
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()

    val facilityRepository = InMemoryFacilityRepository()
    val bookingRepository = InMemoryBookingRepository()
    val equipmentRepository = InMemoryEquipmentRepository()
    val measurementRepository = InMemoryMeasurementRepository()
    val incidentRepository = InMemoryIncidentRepository()

    val facilityService = FacilityService(facilityRepository)
    val bookingService = BookingService(bookingRepository, facilityRepository)
    val equipmentService = EquipmentService(equipmentRepository, facilityRepository)
    val measurementService = MeasurementService(measurementRepository, equipmentRepository)
    val incidentService = IncidentService(facilityRepository, equipmentRepository, incidentRepository)
    val incidentPolicy = IncidentPolicy()
    val monitoringService = MonitoringService(measurementService, incidentService, equipmentRepository, incidentPolicy)

    configureRouting(facilityService, bookingService, equipmentService, measurementService, incidentService, monitoringService)
}
