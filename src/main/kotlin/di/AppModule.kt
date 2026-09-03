package com.doduohor.di

import com.doduohor.domain.model.Incident
import com.doduohor.domain.policy.IncidentPolicy
import com.doduohor.domain.shared.Clock
import com.doduohor.events.EventPublisher
import com.doduohor.events.OutboxEventWriter
import com.doduohor.events.ServerEventPublisher
import com.doduohor.infrastructure.database.mongo.MongoConnection
import com.doduohor.infrastructure.messaging.MessagePublisher
import com.doduohor.infrastructure.messaging.RabbitMqConfig
import com.doduohor.infrastructure.messaging.RabbitMqConnection
import com.doduohor.infrastructure.messaging.RabbitMqFactory
import com.doduohor.infrastructure.messaging.RabbitMqPublisher
import com.doduohor.repository.BookingRepository
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.repository.IncidentRepository
import com.doduohor.repository.MeasurementRepository
import com.doduohor.repository.MonitoringTransaction
import com.doduohor.repository.PostgresMonitoringTransaction
import com.doduohor.repository.mongo.EventHistoryRepository
import com.doduohor.repository.mongo.MongoEventHistoryRepository
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.infrastructure.time.SystemClock
import com.doduohor.repository.postgres.PostgresOutboxEventsRepository
import com.doduohor.repository.postgres.PostgresBookingRepository
import com.doduohor.repository.postgres.PostgresEquipmentRepository
import com.doduohor.repository.postgres.PostgresFacilityRepository
import com.doduohor.repository.postgres.PostgresIncidentRepository
import com.doduohor.repository.postgres.PostgresMeasurementRepository
import com.doduohor.service.BookingService
import com.doduohor.service.EquipmentService
import com.doduohor.service.FacilityService
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import com.doduohor.service.MonitoringEventFactory
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.ktor.plugin.Koin

internal val commonModule = module {
    single { FacilityService(get()) }
    single { BookingService(get(), get()) }
    single { EquipmentService(get(), get()) }
    single { MeasurementService(get(), get()) }
    single { IncidentService(get(), get(), get(), get()) }
    single { IncidentPolicy() }
    single { EventPublisher() }
    single<ServerEventPublisher> { get<EventPublisher>() }
    single<OutboxEventWriter> { get<OutboxEventsRepository>() }
    single { MonitoringEventFactory(get()) }
    single { MonitoringService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

private fun postgresModule(database: Database) = module {
    single<Database> {
        database
    }

    single<Clock> {
        SystemClock
    }

    single<FacilityRepository> {
        PostgresFacilityRepository(get())
    }

    single<BookingRepository> {
        PostgresBookingRepository(get(), get())
    }

    single<EquipmentRepository> {
        PostgresEquipmentRepository(get())
    }

    single<MeasurementRepository> {
        PostgresMeasurementRepository(get(), get())
    }

    single<IncidentRepository> {
        PostgresIncidentRepository(get(), get())
    }

    single<OutboxEventsRepository> {
        PostgresOutboxEventsRepository(get(), get())
    }

    single<MonitoringTransaction> {
        PostgresMonitoringTransaction(get())
    }

}

internal fun messagingModule(config: RabbitMqConfig, messagePublisher: MessagePublisher?) = module {
    single<RabbitMqConfig> { config }

    single<RabbitMqConnection> {
        RabbitMqFactory.connect(get())
    } onClose { connection ->
        connection?.close()
    }

    single<MessagePublisher>{
        messagePublisher ?: run {
            val rabbitConfig = get<RabbitMqConfig>()

            RabbitMqPublisher(
                rabbitMqConnection = get(),
                exchange = rabbitConfig.exchange,
                routingKey = rabbitConfig.routingKey
            )
        }
    }
}

private fun mongoModule(mongoConnection: MongoConnection) = module {
    single<MongoConnection>{
        mongoConnection
    }
    single<MongoDatabase>{
        mongoConnection.database
    }
    single<EventHistoryRepository> {
        MongoEventHistoryRepository(get(), get())
    }
}

fun Application.configureKoin(
    database: Database,
    messagePublisher: MessagePublisher? = null,
    mongoConnection: MongoConnection? = null
) {
    install(Koin) {
        val rabbitMqConfig = RabbitMqConfig.from(environment.config)
        val mongoModules: List<Module> = mongoConnection
            ?.let { listOf(mongoModule(it)) }
            ?: emptyList()

        modules(
            commonModule,
            postgresModule(database),
            messagingModule(rabbitMqConfig, messagePublisher),
            *mongoModules.toTypedArray()
        )
    }
}
