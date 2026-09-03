package com.doduohor.events

import com.doduohor.getOrThrow
import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import com.doduohor.repository.MonitoringTransaction
import com.doduohor.service.IncidentPolicy
import com.doduohor.service.IncidentService
import com.doduohor.service.MeasurementService
import com.doduohor.service.MonitoringService
import com.doduohor.service.MonitoringServiceResult
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class MonitoringServiceEventTest {
    private val fixedInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedOutboxTime = OffsetDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
    private val fixedClock = FixedClock(fixedInstant)

    @Test
    fun `successful measurement creates one outbox event and publishes SSE after transaction`() = runTest {
        val fixture = fixture()
        val channel = fixture.eventPublisher.subscribe()

        var result: MonitoringServiceResult? = null
        val processJob = launch {
            result = fixture.service.processMeasurement(fixture.equipment.id.value, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 22.0)
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
        fixture.eventPublisher.unsubscribe(channel)
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
            IncidentService(facilityRepository, equipmentRepository, InMemoryIncidentRepository(fixedClock)),
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
            result = service.processMeasurement(equipment.id.value, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 12.0)
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
        val channel = fixture.eventPublisher.subscribe()

        val result = fixture.service.processMeasurement(fixture.equipment.id.value, MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 22.0)

        assertIs<MonitoringServiceResult.OutboxPersistenceError>(result)
        assertNull(withTimeoutOrNull(100) { channel.receive() })
        fixture.eventPublisher.unsubscribe(channel)
    }

    private fun fixture(outbox: FakeOutboxRepository = FakeOutboxRepository()): Fixture {
        val equipmentRepository = InMemoryEquipmentRepository()
        val equipment = assertIs<CreateEquipmentResult.Success>(equipmentRepository.create(com.doduohor.domain.shared.FacilityId(1L), "Main ventilation", EquipmentType.VENTILATION)).equipment
        val eventPublisher = EventPublisher()
        return Fixture(
            service = MonitoringService(
                MeasurementService(InMemoryMeasurementRepository(fixedClock), equipmentRepository),
                IncidentService(InMemoryFacilityRepository(), equipmentRepository, InMemoryIncidentRepository(fixedClock)),
                equipmentRepository,
                IncidentPolicy(),
                eventPublisher,
                outbox,
                ImmediateMonitoringTransaction,
                fixedClock
            ),
            equipment = equipment,
            outbox = outbox,
            eventPublisher = eventPublisher
        )
    }

    private data class Fixture(
        val service: MonitoringService,
        val equipment: Equipment,
        val outbox: FakeOutboxRepository,
        val eventPublisher: EventPublisher
    )

    private class FakeOutboxRepository(private val failOnSave: Boolean = false) : OutboxEventsRepository {
        val events = mutableListOf<NewOutboxEvents>()

        override fun saveEvent(event: NewOutboxEvents): SaveEventResult {
            if (failOnSave) return SaveEventResult.Error
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
}
