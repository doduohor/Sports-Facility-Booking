package com.doduohor.di

import com.doduohor.domain.shared.Clock
import com.doduohor.infrastructure.messaging.MessagePublisher
import com.doduohor.infrastructure.messaging.RabbitMqConfig
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.BookingRepository
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.repository.IncidentRepository
import com.doduohor.repository.MeasurementRepository
import com.doduohor.repository.MonitoringTransaction
import com.doduohor.repository.InMemoryBookingRepository
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import com.doduohor.repository.InMemoryOutboxEventsRepository
import com.doduohor.events.OutboxEventsRepository
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Instant

private val testPersistenceModule = module {
    single<Clock> { FixedClock(Instant.parse("2026-08-20T12:00:00Z")) }
    single<FacilityRepository> { InMemoryFacilityRepository() }
    single<BookingRepository> { InMemoryBookingRepository(get()) }
    single<EquipmentRepository> { InMemoryEquipmentRepository() }
    single<MeasurementRepository> { InMemoryMeasurementRepository(get()) }
    single<IncidentRepository> { InMemoryIncidentRepository(get()) }
    single<OutboxEventsRepository> { InMemoryOutboxEventsRepository(get()) }
    single<MonitoringTransaction> {
        object : MonitoringTransaction {
            override fun <T> execute(block: () -> T): T = block()
        }
    }
}

fun Application.configureTestKoin(messagePublisher: MessagePublisher? = null) {
    install(Koin) {
        modules(
            commonModule,
            testPersistenceModule,
            messagingModule(RabbitMqConfig.from(environment.config), messagePublisher)
        )
    }
}
