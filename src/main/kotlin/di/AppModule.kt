package com.doduohor.di

import com.doduohor.events.EventPublisher
import com.doduohor.repository.BookingRepository
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import com.doduohor.repository.IncidentRepository
import com.doduohor.repository.MeasurementRepository
import com.doduohor.repository.postgres.PostgresBookingRepository
import com.doduohor.repository.postgres.PostgresEquipmentRepository
import com.doduohor.repository.postgres.PostgresFacilityRepository
import com.doduohor.repository.postgres.PostgresIncidentRepository
import com.doduohor.repository.postgres.PostgresMeasurementRepository
import com.doduohor.service.BookingService
import com.doduohor.service.EquipmentService
import com.doduohor.service.FacilityService
import com.doduohor.service.IncidentPolicy
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

private val commonModule = module {
    single { FacilityService(get()) }
    single { BookingService(get(), get()) }
    single { EquipmentService(get(), get()) }
    single { MeasurementService(get(), get()) }
    single { IncidentService(get(), get(), get()) }
    single { IncidentPolicy() }
    single { EventPublisher() }
    single { MonitoringService(get(), get(), get(), get(), get()) }
}

private val inMemoryModule = module {
    single<FacilityRepository> {
        InMemoryFacilityRepository()
    }

    single<BookingRepository> {
        InMemoryBookingRepository()
    }

    single<EquipmentRepository> {
        InMemoryEquipmentRepository()
    }

    single<MeasurementRepository> {
        InMemoryMeasurementRepository()
    }

    single<IncidentRepository> {
        InMemoryIncidentRepository()
    }
}

private fun postgresModule(database: Database) = module {
    single<Database> {
        database
    }

    single<FacilityRepository> {
        PostgresFacilityRepository(get())
    }

    single<BookingRepository> {
        PostgresBookingRepository(get())
    }

    single<EquipmentRepository> {
        PostgresEquipmentRepository(get())
    }

    single<MeasurementRepository> {
        PostgresMeasurementRepository(get())
    }

    single<IncidentRepository> {
        PostgresIncidentRepository(get())
    }
}

fun Application.configureKoin(database: Database?) {
    install(Koin) {
        val persistenceModule = database?.let(::postgresModule) ?: inMemoryModule
        modules(commonModule, persistenceModule)
    }
}
