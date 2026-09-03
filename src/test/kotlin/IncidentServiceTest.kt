package com.doduohor

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.model.Incident
import com.doduohor.domain.shared.IncidentId
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.repository.InMemoryIncidentRepository
import com.doduohor.repository.IncidentRepository
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.service.IncidentService
import com.doduohor.service.IncidentLifecycleServiceResult
import com.doduohor.service.IncidentServiceResult
import com.doduohor.service.CreateEquipmentResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IncidentServiceTest {
    private val createdAt = Instant.parse("2026-08-20T12:00:00Z")
    private val transitionAt = Instant.parse("2026-08-20T13:00:00Z")

    @Test
    fun `lifecycle transition time is normalized to database precision`() {
        val preciseTransitionAt = Instant.parse("2026-08-20T13:00:00.123456789Z")
        val fixture = createLifecycleFixture(IncidentStatus.OPEN, FixedClock(preciseTransitionAt))

        val result = assertIs<IncidentLifecycleServiceResult.Success>(
            fixture.service.startProgress(fixture.incident.id.value)
        )

        assertEquals(Instant.parse("2026-08-20T13:00:00.123456Z"), result.incident.statusChangedAt)
    }

    @Test
    fun `create incident returns success for valid data`() {
        val fixture = createFixture()
        val facility = fixture.facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val equipment = assertIs<CreateEquipmentResult.Success>(fixture.equipmentRepository.create(facility.id, "Fire alarm", EquipmentType.FIRE_ALARM)).equipment

        val result = fixture.service.create(
            facilityId = facility.id.value,
            equipmentId = equipment.id.value,
            measurementId = 400,
            type = IncidentType.SMOKE_DETECTED,
            severity = IncidentSeverity.CRITICAL,
            measurementType = MeasurementType.SMOKE,
            measurementUnit = MeasurementUnit.PERCENT,
            value = 80.0
        )

        val success = assertIs<IncidentServiceResult.Success>(result)
        assertEquals(facility.id, success.incident.facilityId)
        assertEquals(equipment.id, success.incident.equipmentId)
    }

    @Test
    fun `create incident rejects invalid ids`() {
        val fixture = createFixture()

        assertIs<IncidentServiceResult.InvalidFacilityId>(
            fixture.service.create(0, 200, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
        assertIs<IncidentServiceResult.InvalidEquipmentId>(
            fixture.service.create(1, 0, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
        assertIs<IncidentServiceResult.InvalidMeasurementId>(
            fixture.service.create(1, 200, 0, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
    }

    @Test
    fun `create incident rejects missing facility and equipment`() {
        val fixture = createFixture()

        assertIs<IncidentServiceResult.NotFindFacilityId>(
            fixture.service.create(1, 200, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )

        val facility = fixture.facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        assertIs<IncidentServiceResult.NotFindEquipmentId>(
            fixture.service.create(facility.id.value, 999999, 400, IncidentType.SMOKE_DETECTED, IncidentSeverity.CRITICAL, MeasurementType.SMOKE, MeasurementUnit.PERCENT, 80.0)
        )
    }

    @Test
    fun `create incident rejects equipment from another facility`() {
        val fixture = createFixture()
        val pool = fixture.facilityRepository.create("Central Pool", FacilityType.POOL).getOrThrow()
        val gym = fixture.facilityRepository.create("Central Gym", FacilityType.GYM).getOrThrow()
        val equipment = assertIs<CreateEquipmentResult.Success>(fixture.equipmentRepository.create(gym.id, "Gym ventilation", EquipmentType.VENTILATION)).equipment

        val result = fixture.service.create(
            facilityId = pool.id.value,
            equipmentId = equipment.id.value,
            measurementId = 400,
            type = IncidentType.HIGH_CO2,
            severity = IncidentSeverity.HIGH,
            measurementType = MeasurementType.CO2,
            measurementUnit = MeasurementUnit.PPM,
            value = 1200.0
        )

        assertIs<IncidentServiceResult.EquipmentDoesNotBelongToFacility>(result)
    }

    @Test
    fun `start progress saves an incident from open`() {
        assertSuccessfulTransition(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS) { service, id -> service.startProgress(id) }
    }

    @Test
    fun `start progress saves a reopened incident`() {
        assertSuccessfulTransition(IncidentStatus.REOPENED, IncidentStatus.IN_PROGRESS) { service, id -> service.startProgress(id) }
    }

    @Test
    fun `mark false positive saves an open incident`() {
        assertSuccessfulTransition(IncidentStatus.OPEN, IncidentStatus.FALSE_POSITIVE) { service, id -> service.markFalsePositive(id) }
    }

    @Test
    fun `mark false positive saves an in progress incident`() {
        assertSuccessfulTransition(IncidentStatus.IN_PROGRESS, IncidentStatus.FALSE_POSITIVE) { service, id -> service.markFalsePositive(id) }
    }

    @Test
    fun `mark false positive saves a resolved incident`() {
        assertSuccessfulTransition(IncidentStatus.RESOLVED, IncidentStatus.FALSE_POSITIVE) { service, id -> service.markFalsePositive(id) }
    }

    @Test
    fun `mark false positive saves a reopened incident`() {
        assertSuccessfulTransition(IncidentStatus.REOPENED, IncidentStatus.FALSE_POSITIVE) { service, id -> service.markFalsePositive(id) }
    }

    @Test
    fun `resolve saves an in progress incident`() {
        assertSuccessfulTransition(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED) { service, id -> service.resolve(id) }
    }

    @Test
    fun `close saves a resolved incident`() {
        assertSuccessfulTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED) { service, id -> service.close(id) }
    }

    @Test
    fun `reopen saves a resolved incident`() {
        assertSuccessfulTransition(IncidentStatus.RESOLVED, IncidentStatus.REOPENED) { service, id -> service.reopen(id) }
    }

    @Test
    fun `reopen saves a closed incident`() {
        assertSuccessfulTransition(IncidentStatus.CLOSED, IncidentStatus.REOPENED) { service, id -> service.reopen(id) }
    }

    @Test
    fun `reopen saves a false positive incident`() {
        assertSuccessfulTransition(IncidentStatus.FALSE_POSITIVE, IncidentStatus.REOPENED) { service, id -> service.reopen(id) }
    }

    @Test
    fun `forbidden lifecycle transitions return invalid status and do not save`() {
        val attempts = listOf(
            TransitionAttempt("startProgress", IncidentStatus.RESOLVED),
            TransitionAttempt("startProgress", IncidentStatus.CLOSED),
            TransitionAttempt("startProgress", IncidentStatus.FALSE_POSITIVE),
            TransitionAttempt("resolve", IncidentStatus.OPEN),
            TransitionAttempt("resolve", IncidentStatus.CLOSED),
            TransitionAttempt("resolve", IncidentStatus.FALSE_POSITIVE),
            TransitionAttempt("resolve", IncidentStatus.REOPENED),
            TransitionAttempt("close", IncidentStatus.OPEN),
            TransitionAttempt("close", IncidentStatus.IN_PROGRESS),
            TransitionAttempt("close", IncidentStatus.FALSE_POSITIVE),
            TransitionAttempt("close", IncidentStatus.REOPENED),
            TransitionAttempt("reopen", IncidentStatus.OPEN),
            TransitionAttempt("reopen", IncidentStatus.IN_PROGRESS),
            TransitionAttempt("markFalsePositive", IncidentStatus.CLOSED)
        )

        attempts.forEach { attempt ->
            val fixture = createLifecycleFixture(attempt.status)
            val original = fixture.repository.findByIncidentId(fixture.incident.id)!!

            val result = attempt.invoke(fixture.service, fixture.incident.id.value)

            assertIs<IncidentLifecycleServiceResult.InvalidStatus>(result)
            assertEquals(0, fixture.repository.saveCalls)
            assertEquals(original, fixture.repository.findByIncidentId(fixture.incident.id))
        }
    }

    @Test
    fun `repeated lifecycle operations return already result and do not save`() {
        val attempts = listOf(
            RepeatedAttempt(IncidentStatus.IN_PROGRESS, "startProgress") to IncidentLifecycleServiceResult.AlreadyInProgress,
            RepeatedAttempt(IncidentStatus.FALSE_POSITIVE, "markFalsePositive") to IncidentLifecycleServiceResult.AlreadyInFalsePositive,
            RepeatedAttempt(IncidentStatus.RESOLVED, "resolve") to IncidentLifecycleServiceResult.AlreadyResolved,
            RepeatedAttempt(IncidentStatus.CLOSED, "close") to IncidentLifecycleServiceResult.AlreadyClosed,
            RepeatedAttempt(IncidentStatus.REOPENED, "reopen") to IncidentLifecycleServiceResult.AlreadyReopen
        )

        attempts.forEach { (attempt, expected) ->
            val fixture = createLifecycleFixture(attempt.status)
            val original = fixture.repository.findByIncidentId(fixture.incident.id)!!

            val result = attempt.invoke(fixture.service, fixture.incident.id.value)

            assertEquals(expected, result)
            assertEquals(0, fixture.repository.saveCalls)
            assertEquals(original, fixture.repository.findByIncidentId(fixture.incident.id))
        }
    }

    @Test
    fun `lifecycle operations reject invalid incident id without repository save`() {
        val fixture = createLifecycleFixture(IncidentStatus.OPEN)
        val attempts = listOf<(IncidentService, Long) -> IncidentLifecycleServiceResult>(
            { service, id -> service.startProgress(id) },
            { service, id -> service.markFalsePositive(id) },
            { service, id -> service.resolve(id) },
            { service, id -> service.close(id) },
            { service, id -> service.reopen(id) }
        )

        attempts.forEach { attempt ->
            val result = attempt(fixture.service, 0)

            assertIs<IncidentLifecycleServiceResult.InvalidIncidentId>(result)
        }
        assertEquals(0, fixture.repository.saveCalls)
    }

    @Test
    fun `lifecycle operations return missing incident without repository save`() {
        val fixture = createLifecycleFixture(IncidentStatus.OPEN)
        val attempts = listOf<(IncidentService, Long) -> IncidentLifecycleServiceResult>(
            { service, id -> service.startProgress(id) },
            { service, id -> service.markFalsePositive(id) },
            { service, id -> service.resolve(id) },
            { service, id -> service.close(id) },
            { service, id -> service.reopen(id) }
        )

        attempts.forEach { attempt ->
            val result = attempt(fixture.service, 999_999)

            assertIs<IncidentLifecycleServiceResult.NotFindIncidentId>(result)
        }
        assertEquals(0, fixture.repository.saveCalls)
    }

    private fun assertSuccessfulTransition(
        initialStatus: IncidentStatus,
        expectedStatus: IncidentStatus,
        operation: (IncidentService, Long) -> IncidentLifecycleServiceResult
    ) {
        val fixture = createLifecycleFixture(initialStatus)
        val serviceResult = operation(fixture.service, fixture.incident.id.value)

        val success = assertIs<IncidentLifecycleServiceResult.Success>(serviceResult)
        assertEquals(expectedStatus, success.incident.status)
        assertEquals(transitionAt, success.incident.statusChangedAt)
        assertEquals(1, fixture.repository.saveCalls)
        assertEquals(success.incident, fixture.repository.findByIncidentId(fixture.incident.id))
    }

    private fun createLifecycleFixture(
        status: IncidentStatus,
        lifecycleClock: FixedClock = FixedClock(transitionAt)
    ): LifecycleFixture {
        val repository = RecordingIncidentRepository(InMemoryIncidentRepository(FixedClock(createdAt)))
        val incident = assertIs<com.doduohor.domain.model.IncidentCreationResult.Success<Incident>>(repository.create(
            facilityId = com.doduohor.domain.shared.FacilityId(1),
            equipmentId = com.doduohor.domain.shared.EquipmentId(2),
            measurementId = com.doduohor.domain.shared.MeasurementId(3),
            type = IncidentType.HIGH_TEMPERATURE,
            severity = IncidentSeverity.HIGH,
            measurementType = MeasurementType.TEMPERATURE,
            measurementUnit = MeasurementUnit.CELSIUS,
            value = 35.0
        )).value
        val preparedIncident = Incident.restore(
            id = incident.id,
            facilityId = incident.facilityId,
            equipmentId = incident.equipmentId,
            measurementId = incident.measurementId,
            type = incident.type,
            severity = incident.severity,
            status = status,
            measurementType = incident.measurementType,
            measurementUnit = incident.measurementUnit,
            value = incident.value,
            createdAt = incident.createdAt,
            statusChangedAt = createdAt
        )
        repository.save(preparedIncident)
        repository.resetSaveCalls()

        return LifecycleFixture(
            service = IncidentService(
                InMemoryFacilityRepository(),
                InMemoryEquipmentRepository(),
                repository,
                lifecycleClock
            ),
            repository = repository,
            incident = preparedIncident
        )
    }

    private data class LifecycleFixture(
        val service: IncidentService,
        val repository: RecordingIncidentRepository,
        val incident: Incident
    )

    private data class TransitionAttempt(
        val name: String,
        val status: IncidentStatus
    ) {
        fun invoke(service: IncidentService, incidentId: Long): IncidentLifecycleServiceResult = when (name) {
            "startProgress" -> service.startProgress(incidentId)
            "markFalsePositive" -> service.markFalsePositive(incidentId)
            "resolve" -> service.resolve(incidentId)
            "close" -> service.close(incidentId)
            "reopen" -> service.reopen(incidentId)
            else -> error("Unknown transition: $name")
        }
    }

    private data class RepeatedAttempt(
        val status: IncidentStatus,
        val operation: String
    ) {
        fun invoke(service: IncidentService, incidentId: Long): IncidentLifecycleServiceResult = when (operation) {
            "startProgress" -> service.startProgress(incidentId)
            "markFalsePositive" -> service.markFalsePositive(incidentId)
            "resolve" -> service.resolve(incidentId)
            "close" -> service.close(incidentId)
            "reopen" -> service.reopen(incidentId)
            else -> error("Unknown operation: $operation")
        }
    }

    private class RecordingIncidentRepository(
        private val delegate: IncidentRepository
    ) : IncidentRepository by delegate {
        var saveCalls: Int = 0
            private set

        override fun save(incident: Incident): Incident? {
            saveCalls++
            return delegate.save(incident)
        }

        fun resetSaveCalls() {
            saveCalls = 0
        }
    }

    private fun createFixture(): IncidentServiceFixture {
        val facilityRepository = InMemoryFacilityRepository()
        val equipmentRepository = InMemoryEquipmentRepository()
        val incidentRepository = InMemoryIncidentRepository(FixedClock(Instant.parse("2026-08-20T12:00:00Z")))

        return IncidentServiceFixture(
            facilityRepository = facilityRepository,
            equipmentRepository = equipmentRepository,
            service = IncidentService(facilityRepository, equipmentRepository, incidentRepository, FixedClock(createdAt))
        )
    }

    private data class IncidentServiceFixture(
        val facilityRepository: InMemoryFacilityRepository,
        val equipmentRepository: InMemoryEquipmentRepository,
        val service: IncidentService
    )
}
