package com.doduohor

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.events.EventPublisher
import com.doduohor.events.ExpandAttemptResult
import com.doduohor.events.IntegrationEventType
import com.doduohor.events.MakeAsPublishedResult
import com.doduohor.events.NewOutboxEvents
import com.doduohor.events.OutboxEvents
import com.doduohor.events.OutboxEventsRepository
import com.doduohor.events.SaveErrorResult
import com.doduohor.events.SaveEventResult
import com.doduohor.events.ServerEventType
import com.doduohor.events.StartPublishingResult
import com.doduohor.infrastructure.database.postgres.BookingTable
import com.doduohor.infrastructure.database.postgres.EquipmentTable
import com.doduohor.infrastructure.database.postgres.FacilityTable
import com.doduohor.infrastructure.database.postgres.IncidentTable
import com.doduohor.infrastructure.database.postgres.MeasurementTable
import com.doduohor.infrastructure.database.postgres.OutboxEventsTable
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.PostgresMonitoringTransaction
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.IncidentRepository
import com.doduohor.repository.MeasurementRepository
import com.doduohor.repository.MonitoringTransaction
import com.doduohor.repository.postgres.PostgresEquipmentRepository
import com.doduohor.repository.postgres.PostgresFacilityRepository
import com.doduohor.repository.postgres.PostgresIncidentRepository
import com.doduohor.repository.postgres.PostgresMeasurementRepository
import com.doduohor.repository.postgres.PostgresOutboxEventsRepository
import com.doduohor.service.CreateEquipmentResult
import com.doduohor.service.IncidentPolicy
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import com.doduohor.service.MonitoringServiceResult
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

@Testcontainers
class PostgresMonitoringServiceTransactionalOutboxTest {
    private val fixedClock = FixedClock(Instant.parse("2026-08-20T12:00:00Z"))

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private lateinit var dataSource: HikariDataSource
        private lateinit var database: Database

        @JvmStatic
        @BeforeAll
        fun migrateDatabase() {
            dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = postgres.driverClassName
            })
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            database = Database.connect(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun closeDataSource() {
            dataSource.close()
        }
    }

    @BeforeEach
    fun clearDatabase() {
        transaction(database) {
            OutboxEventsTable.deleteAll()
            IncidentTable.deleteAll()
            MeasurementTable.deleteAll()
            BookingTable.deleteAll()
            EquipmentTable.deleteAll()
            FacilityTable.deleteAll()
        }
    }

    @Test
    fun `normal measurement commits measurement and exactly one outbox event then publishes SSE`() = runTest {
        val fixture = fixture()
        val channel = fixture.eventPublisher.subscribe()

        var result: MonitoringServiceResult? = null
        val processing = launch {
            result = fixture.service.processMeasurement(
                fixture.equipment.id.value,
                MeasurementType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                22.0
            )
        }
        val event = withTimeout(1_000) { channel.receive() }
        processing.join()

        assertIs<MonitoringServiceResult.SuccessWithoutIncident>(result)
        fixture.assertAllWritesUseExternalTransaction()
        assertEquals(1, tableSize(MeasurementTable))
        assertEquals(0, tableSize(IncidentTable))
        assertEquals(1, tableSize(OutboxEventsTable))
        assertEquals(listOf(IntegrationEventType.MEASUREMENT_CREATED), outboxEventTypes())
        assertEquals(ServerEventType.MEASUREMENT_CREATED, event.type)
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        fixture.eventPublisher.unsubscribe(channel)
    }

    @Test
    fun `alarming measurement commits measurement incident and two ordered outbox and SSE events`() = runTest {
        val fixture = fixture(EquipmentType.FIRE_ALARM)
        val channel = fixture.eventPublisher.subscribe()

        var result: MonitoringServiceResult? = null
        val processing = launch {
            result = fixture.service.processMeasurement(
                fixture.equipment.id.value,
                MeasurementType.SMOKE,
                MeasurementUnit.PERCENT,
                12.0
            )
        }
        val events = listOf(
            withTimeout(1_000) { channel.receive() },
            withTimeout(1_000) { channel.receive() }
        )
        processing.join()

        assertIs<MonitoringServiceResult.SuccessWithIncident>(result)
        fixture.assertAllWritesUseExternalTransaction()
        assertEquals(1, tableSize(MeasurementTable))
        assertEquals(1, tableSize(IncidentTable))
        assertEquals(2, tableSize(OutboxEventsTable))
        assertEquals(
            listOf(IntegrationEventType.MEASUREMENT_CREATED, IntegrationEventType.INCIDENT_CREATED),
            outboxEventTypes()
        )
        assertEquals(
            listOf(ServerEventType.MEASUREMENT_CREATED, ServerEventType.INCIDENT_CREATED),
            events.map { it.type }
        )
        fixture.eventPublisher.unsubscribe(channel)
    }

    @Test
    fun `outbox failure after business insert rolls back measurement and publishes no SSE`() = runTest {
        val fixture = fixture(failOnOutboxSave = 1)
        val channel = fixture.eventPublisher.subscribe()

        val result = fixture.service.processMeasurement(
            fixture.equipment.id.value,
            MeasurementType.TEMPERATURE,
            MeasurementUnit.CELSIUS,
            22.0
        )

        assertIs<MonitoringServiceResult.OutboxPersistenceError>(result)
        fixture.assertAllWritesUseExternalTransaction()
        assertEquals(0, tableSize(MeasurementTable))
        assertEquals(0, tableSize(IncidentTable))
        assertEquals(0, tableSize(OutboxEventsTable))
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        fixture.eventPublisher.unsubscribe(channel)
    }

    @Test
    fun `second outbox failure rolls back measurement incident and first outbox event and publishes no SSE`() = runTest {
        val fixture = fixture(EquipmentType.FIRE_ALARM, failOnOutboxSave = 2)
        val channel = fixture.eventPublisher.subscribe()

        val result = fixture.service.processMeasurement(
            fixture.equipment.id.value,
            MeasurementType.SMOKE,
            MeasurementUnit.PERCENT,
            12.0
        )

        assertIs<MonitoringServiceResult.OutboxPersistenceError>(result)
        fixture.assertAllWritesUseExternalTransaction()
        assertEquals(0, tableSize(MeasurementTable))
        assertEquals(0, tableSize(IncidentTable))
        assertEquals(0, tableSize(OutboxEventsTable))
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        fixture.eventPublisher.unsubscribe(channel)
    }

    private fun fixture(
        equipmentType: EquipmentType = EquipmentType.VENTILATION,
        failOnOutboxSave: Int? = null
    ): Fixture {
        val facilityRepository = PostgresFacilityRepository(database)
        val equipmentRepository = PostgresEquipmentRepository(database)
        val facility = facilityRepository.create("Central facility", FacilityType.POOL).getOrThrow()
        val equipment = assertIs<CreateEquipmentResult.Success>(
            equipmentRepository.create(facility.id, "Monitoring equipment", equipmentType)
        ).equipment
        val transactionRecorder = TransactionRecorder()
        val recordingMonitoringTransaction = RecordingMonitoringTransaction(PostgresMonitoringTransaction(database))
        val recordingEquipmentRepository = RecordingEquipmentRepository(equipmentRepository, transactionRecorder)
        val recordingMeasurementRepository = RecordingMeasurementRepository(
            PostgresMeasurementRepository(database, fixedClock),
            transactionRecorder
        )
        val recordingIncidentRepository = RecordingIncidentRepository(
            PostgresIncidentRepository(database, fixedClock),
            transactionRecorder
        )
        val outboxRepository: OutboxEventsRepository = PostgresOutboxEventsRepository(database, fixedClock)
        val failingOutboxRepository = failOnOutboxSave?.let { FailingOutboxRepository(outboxRepository, it) } ?: outboxRepository
        val recordingOutboxRepository = RecordingOutboxEventsRepository(failingOutboxRepository, transactionRecorder)
        val eventPublisher = EventPublisher()
        return Fixture(
            service = MonitoringService(
                measurementService = MeasurementService(recordingMeasurementRepository, recordingEquipmentRepository),
                incidentService = IncidentService(
                    facilityRepository,
                    recordingEquipmentRepository,
                    recordingIncidentRepository
                ),
                equipmentRepository = recordingEquipmentRepository,
                incidentPolicy = IncidentPolicy(),
                eventPublisher = eventPublisher,
                outboxEventRepository = recordingOutboxRepository,
                monitoringTransaction = recordingMonitoringTransaction,
                clock = fixedClock
            ),
            equipment = equipment,
            eventPublisher = eventPublisher,
            transactionRecorder = transactionRecorder,
            monitoringTransaction = recordingMonitoringTransaction
        )
    }

    private fun tableSize(table: org.jetbrains.exposed.v1.core.Table): Int = transaction(database) {
        table.selectAll().count().toInt()
    }

    private fun outboxEventTypes(): List<IntegrationEventType> = transaction(database) {
        OutboxEventsTable.selectAll()
            .orderBy(OutboxEventsTable.id)
            .map { IntegrationEventType.valueOf(it[OutboxEventsTable.eventType]) }
    }

    private data class Fixture(
        val service: MonitoringService,
        val equipment: Equipment,
        val eventPublisher: EventPublisher,
        val transactionRecorder: TransactionRecorder,
        val monitoringTransaction: RecordingMonitoringTransaction
    ) {
        fun assertAllWritesUseExternalTransaction() {
            val externalTransaction = assertNotNull(monitoringTransaction.externalTransaction)
            assertNotNull(transactionRecorder.transactions.firstOrNull())
            transactionRecorder.transactions.forEach { transaction ->
                assertSame(externalTransaction, assertNotNull(transaction))
            }
        }
    }

    private class TransactionRecorder {
        val transactions = mutableListOf<Any?>()

        fun recordCurrentTransaction() {
            transactions += TransactionManager.currentOrNull()
        }
    }

    private class RecordingMonitoringTransaction(
        private val delegate: MonitoringTransaction
    ) : MonitoringTransaction {
        var externalTransaction: Any? = null

        override fun <T> execute(block: () -> T): T = delegate.execute {
            externalTransaction = TransactionManager.currentOrNull()
            block()
        }
    }

    private class RecordingMeasurementRepository(
        private val delegate: MeasurementRepository,
        private val recorder: TransactionRecorder
    ) : MeasurementRepository by delegate {
        override fun create(equipmentId: com.doduohor.domain.shared.EquipmentId, measurementReading: com.doduohor.domain.model.MeasurementReading): com.doduohor.domain.model.Measurement {
            recorder.recordCurrentTransaction()
            return delegate.create(equipmentId, measurementReading)
        }
    }

    private class RecordingIncidentRepository(
        private val delegate: IncidentRepository,
        private val recorder: TransactionRecorder
    ) : IncidentRepository by delegate {
        override fun create(
            facilityId: com.doduohor.domain.shared.FacilityId,
            equipmentId: com.doduohor.domain.shared.EquipmentId,
            measurementId: com.doduohor.domain.shared.MeasurementId,
            type: com.doduohor.domain.model.IncidentType,
            severity: com.doduohor.domain.model.IncidentSeverity,
            measurementType: MeasurementType,
            measurementUnit: MeasurementUnit,
            value: Double
        ): com.doduohor.domain.model.Incident {
            recorder.recordCurrentTransaction()
            return delegate.create(
                facilityId, equipmentId, measurementId, type, severity, measurementType, measurementUnit, value
            )
        }
    }

    private class RecordingEquipmentRepository(
        private val delegate: EquipmentRepository,
        private val recorder: TransactionRecorder
    ) : EquipmentRepository by delegate {
        override fun create(
            facilityId: com.doduohor.domain.shared.FacilityId,
            name: String,
            type: EquipmentType
        ): CreateEquipmentResult {
            recorder.recordCurrentTransaction()
            return delegate.create(facilityId, name, type)
        }
    }

    private class RecordingOutboxEventsRepository(
        private val delegate: OutboxEventsRepository,
        private val recorder: TransactionRecorder
    ) : OutboxEventsRepository by delegate {
        override fun saveEvent(event: NewOutboxEvents): SaveEventResult {
            recorder.recordCurrentTransaction()
            return delegate.saveEvent(event)
        }
    }

    private class FailingOutboxRepository(
        private val delegate: OutboxEventsRepository,
        private val failingSaveNumber: Int
    ) : OutboxEventsRepository {
        private var saveCount = 0

        override fun saveEvent(event: NewOutboxEvents): SaveEventResult {
            saveCount += 1
            return if (saveCount == failingSaveNumber) SaveEventResult.Error else delegate.saveEvent(event)
        }

        override fun findUnprocessedEvents(): List<OutboxEvents> = delegate.findUnprocessedEvents()
        override fun tryStartPublishing(eventId: Uuid): StartPublishingResult = delegate.tryStartPublishing(eventId)
        override fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult = delegate.makeAsPublished(eventId)
        override fun expandAttempt(eventId: Uuid): ExpandAttemptResult = delegate.expandAttempt(eventId)
        override fun saveError(eventId: Uuid, error: String): SaveErrorResult = delegate.saveError(eventId, error)
    }
}
