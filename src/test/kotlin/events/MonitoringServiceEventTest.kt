package com.doduohor.events

import com.doduohor.getOrThrow
import com.doduohor.domain.policy.IncidentPolicy
import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.events.ServerEventPublisher
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import com.doduohor.repository.MonitoringTransaction
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import com.doduohor.service.MonitoringServiceResult
import com.doduohor.service.ProcessMeasurementCommand
import com.doduohor.service.CreateEquipmentResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class MonitoringServiceEventTest {
    private val fixedInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedOutboxTime = OffsetDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
    private val fixedClock = FixedClock(fixedInstant)

    @Test
    fun `monitoring service exposes only command based processing entry point`() {
        assertFalse(
            MonitoringService::class.java.declaredMethods.any {
                it.name == "processMeasurement" && it.parameterTypes.firstOrNull() == Long::class.javaPrimitiveType
            }
        )
    }

    @Test
    fun `successful measurement creates one outbox event and publishes SSE after transaction`() = runTest {
        val fixture = fixture()
        val channel = fixture.eventPublisher!!.subscribe()

        var result: MonitoringServiceResult? = null
        val processJob = launch {
            result = fixture.service.processMeasurement(
                ProcessMeasurementCommand(
                    fixture.equipment.id.value,
                    MeasurementType.TEMPERATURE,
                    MeasurementUnit.CELSIUS,
                    22.0
                )
            )
        }
        val event = withTimeout(1_000) { channel.receive() }
        processJob.join()

        assertIs<MonitoringServiceResult.SuccessWithoutIncident>(result)
        assertEquals(listOf(IntegrationEventType.MEASUREMENT_CREATED), fixture.outbox.events.map { it.eventType })
        assertEquals(listOf(fixedOutboxTime), fixture.outbox.events.map { it.createdAt })
        assertEquals(ServerEventType.MEASUREMENT_CREATED, event.type)
        assertEquals(fixedInstant, event.createdAt)
        assertEquals(22.0, event.data.jsonObject["value"]?.jsonPrimitive?.content?.toDouble())
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        fixture.eventPublisher!!.unsubscribe(channel)
    }

    @Test
    fun `alarming measurement creates measurement and incident outbox events in SSE order`() = runTest {
        val facilityRepository = InMemoryFacilityRepository()
        val facility = facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val equipmentRepository = InMemoryEquipmentRepository()
        val equipment = assertIs<CreateEquipmentResult.Success>(equipmentRepository.create(facility.id, "Fire alarm", EquipmentType.FIRE_ALARM)).equipment
        val eventPublisher = EventPublisher()
        val outbox = FakeOutboxRepository()
        val service = MonitoringService(
            MeasurementService(InMemoryMeasurementRepository(fixedClock), equipmentRepository),
            IncidentService(facilityRepository, equipmentRepository, InMemoryIncidentRepository(fixedClock), fixedClock),
            equipmentRepository,
            IncidentPolicy(),
            eventPublisher,
            outbox,
            ImmediateMonitoringTransaction,
            fixedClock
        )
        val channel = eventPublisher.subscribe()

        var result: MonitoringServiceResult? = null
        val processJob = launch {
            result = service.processMeasurement(
                ProcessMeasurementCommand(equipment.id.value, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 12.0)
            )
        }
        val events = listOf(
            withTimeout(1_000) { channel.receive() },
            withTimeout(1_000) { channel.receive() }
        )
        processJob.join()

        assertIs<MonitoringServiceResult.SuccessWithIncident>(result)
        assertEquals(
            listOf(IntegrationEventType.MEASUREMENT_CREATED, IntegrationEventType.INCIDENT_CREATED),
            outbox.events.map { it.eventType }
        )
        assertEquals(listOf(fixedOutboxTime, fixedOutboxTime), outbox.events.map { it.createdAt })
        assertEquals(
            listOf(ServerEventType.MEASUREMENT_CREATED, ServerEventType.INCIDENT_CREATED),
            events.map { it.type }
        )
        assertEquals(listOf(fixedInstant, fixedInstant), events.map { it.createdAt })
        eventPublisher.unsubscribe(channel)
    }

    @Test
    fun `outbox persistence error returns failure and does not publish SSE`() = runTest {
        val fixture = fixture(FakeOutboxRepository(failOnSave = true))
        val channel = fixture.eventPublisher!!.subscribe()

        val result = fixture.service.processMeasurement(
            ProcessMeasurementCommand(
                fixture.equipment.id.value,
                MeasurementType.TEMPERATURE,
                MeasurementUnit.CELSIUS,
                22.0
            )
        )

        assertIs<MonitoringServiceResult.OutboxPersistenceError>(result)
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        fixture.eventPublisher!!.unsubscribe(channel)
    }

    @Test
    fun `publishes only after transaction returns and all outbox writes complete`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(
            outbox = FakeOutboxRepository(onSave = { events += "write" }),
            eventPublisher = RecordingServerEventPublisher { events += "publish" },
            monitoringTransaction = RecordingMonitoringTransaction(events)
        )

        fixture.service.processMeasurement(commandFor(fixture))

        assertEquals(listOf("transaction-enter", "write", "transaction-return", "publish"), events)
    }

    @Test
    fun `transaction block exception after writes does not publish`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(
            outbox = FakeOutboxRepository(onSave = { events += "write" }),
            eventPublisher = RecordingServerEventPublisher { events += "publish" },
            monitoringTransaction = ThrowingMonitoringTransaction(events, throwAfterBlock = true)
        )

        kotlin.test.assertFailsWith<IllegalStateException> {
            fixture.service.processMeasurement(commandFor(fixture))
        }

        assertEquals(listOf("transaction-enter", "write", "transaction-block-failure"), events)
    }

    @Test
    fun `transaction commit exception after writes does not publish`() = runTest {
        val events = mutableListOf<String>()
        val fixture = fixture(
            outbox = FakeOutboxRepository(onSave = { events += "write" }),
            eventPublisher = RecordingServerEventPublisher { events += "publish" },
            monitoringTransaction = ThrowingMonitoringTransaction(events, throwAfterBlock = false)
        )

        kotlin.test.assertFailsWith<IllegalStateException> {
            fixture.service.processMeasurement(commandFor(fixture))
        }

        assertEquals(listOf("transaction-enter", "write", "transaction-return", "transaction-commit-failure"), events)
    }

    @Test
    fun `second outbox error preserves incident result and does not publish`() = runTest {
        val events = mutableListOf<String>()
        val fixture = incidentFixture(
            outbox = FakeOutboxRepository(failOnSaveNumber = 2, onSave = { events += "write" }),
            eventPublisher = RecordingServerEventPublisher { events += "publish" }
        )

        val result = fixture.service.processMeasurement(commandFor(fixture, MeasurementType.SMOKE, MeasurementUnit.PERCENT))

        assertIs<MonitoringServiceResult.OutboxPersistenceError>(result)
        assertEquals(listOf("write", "write"), events)
        assertEquals(0, (fixture.publisher as RecordingServerEventPublisher).publishedEventBatches)
    }

    private fun fixture(
        outbox: FakeOutboxRepository = FakeOutboxRepository(),
        eventPublisher: ServerEventPublisher = EventPublisher(),
        monitoringTransaction: MonitoringTransaction = ImmediateMonitoringTransaction
    ): Fixture {
        val equipmentRepository = InMemoryEquipmentRepository()
        val equipment = assertIs<CreateEquipmentResult.Success>(equipmentRepository.create(com.doduohor.domain.shared.FacilityId(1L), "Main ventilation", EquipmentType.VENTILATION)).equipment
        return Fixture(
            service = MonitoringService(
                MeasurementService(InMemoryMeasurementRepository(fixedClock), equipmentRepository),
                IncidentService(InMemoryFacilityRepository(), equipmentRepository, InMemoryIncidentRepository(fixedClock), fixedClock),
                equipmentRepository,
                IncidentPolicy(),
                eventPublisher,
                outbox,
                monitoringTransaction,
                fixedClock
            ),
            equipment = equipment,
            outbox = outbox,
            publisher = eventPublisher,
            eventPublisher = eventPublisher as? EventPublisher
        )
    }

    private fun incidentFixture(
        outbox: FakeOutboxRepository,
        eventPublisher: ServerEventPublisher
    ): Fixture {
        val facilityRepository = InMemoryFacilityRepository()
        val facility = facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val equipmentRepository = InMemoryEquipmentRepository()
        val equipment = assertIs<CreateEquipmentResult.Success>(
            equipmentRepository.create(facility.id, "Fire alarm", EquipmentType.FIRE_ALARM)
        ).equipment
        return Fixture(
            service = MonitoringService(
                MeasurementService(InMemoryMeasurementRepository(fixedClock), equipmentRepository),
                IncidentService(facilityRepository, equipmentRepository, InMemoryIncidentRepository(fixedClock), fixedClock),
                equipmentRepository,
                IncidentPolicy(),
                eventPublisher,
                outbox,
                ImmediateMonitoringTransaction,
                fixedClock
            ),
            equipment = equipment,
            outbox = outbox,
            publisher = eventPublisher,
            eventPublisher = eventPublisher as? EventPublisher
        )
    }

    private fun commandFor(
        fixture: Fixture,
        type: MeasurementType = MeasurementType.TEMPERATURE,
        unit: MeasurementUnit = MeasurementUnit.CELSIUS
    ) = ProcessMeasurementCommand(fixture.equipment.id.value, type, unit, 22.0)

    private data class Fixture(
        val service: MonitoringService,
        val equipment: Equipment,
        val outbox: FakeOutboxRepository,
        val eventPublisher: EventPublisher?,
        val publisher: ServerEventPublisher
    )

    private class FakeOutboxRepository(
        private val failOnSave: Boolean = false,
        private val failOnSaveNumber: Int? = null,
        private val onSave: () -> Unit = {}
    ) : OutboxEventsRepository {
        val events = mutableListOf<NewOutboxEvents>()
        private var saveCount = 0

        override fun saveEvent(event: NewOutboxEvents): SaveEventResult {
            saveCount += 1
            onSave()
            if (failOnSave || saveCount == failOnSaveNumber) return SaveEventResult.Error
            events += event
            return SaveEventResult.Success
        }

        override fun findUnprocessedEvents(): List<OutboxEvents> = emptyList()
        override fun tryStartPublishing(eventId: Uuid): StartPublishingResult = StartPublishingResult.NotFound
        override fun makeAsPublished(eventId: Uuid): MakeAsPublishedResult = MakeAsPublishedResult.NotFound
        override fun expandAttempt(eventId: Uuid): ExpandAttemptResult = ExpandAttemptResult.NotFound
        override fun saveError(eventId: Uuid, error: String): SaveErrorResult = SaveErrorResult.NotFound
    }

    private object ImmediateMonitoringTransaction : MonitoringTransaction {
        override fun <T> execute(block: () -> T): T = block()
    }

    private class RecordingServerEventPublisher(private val onPublish: () -> Unit) : ServerEventPublisher {
        var publishedEventBatches = 0
            private set

        override suspend fun publish(events: List<ServerEvent>) {
            publishedEventBatches += 1
            onPublish()
        }
    }

    private class RecordingMonitoringTransaction(private val events: MutableList<String>) : MonitoringTransaction {
        override fun <T> execute(block: () -> T): T {
            events += "transaction-enter"
            val result = block()
            events += "transaction-return"
            return result
        }
    }

    private class ThrowingMonitoringTransaction(
        private val events: MutableList<String>,
        private val throwAfterBlock: Boolean
    ) : MonitoringTransaction {
        override fun <T> execute(block: () -> T): T {
            events += "transaction-enter"
            if (throwAfterBlock) {
                block()
                events += "transaction-block-failure"
                throw IllegalStateException("transaction block failed")
            }
            val result = block()
            events += "transaction-return"
            events += "transaction-commit-failure"
            throw IllegalStateException("transaction commit failed")
        }
    }
}
