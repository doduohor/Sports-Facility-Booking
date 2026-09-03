package com.doduohor.repository

import com.doduohor.domain.model.*
import com.doduohor.domain.shared.*
import com.doduohor.events.*
import com.doduohor.infrastructure.database.postgres.*
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.postgres.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.*
import kotlin.uuid.Uuid

private val contractClock = FixedClock(Instant.parse("2026-08-20T12:00:00Z"))

data class RepositoryBundle(
    val facilities: FacilityRepository,
    val equipment: EquipmentRepository,
    val bookings: BookingRepository,
    val measurements: MeasurementRepository,
    val incidents: IncidentRepository,
    val outbox: OutboxEventsRepository,
    val reset: () -> Unit
)

abstract class RepositoryContractTest {
    private lateinit var bundle: RepositoryBundle
    protected abstract fun createBundle(): RepositoryBundle

    @BeforeEach
    fun setUp() {
        bundle = createBundle()
        bundle.reset()
    }

    @Test
    fun `facility contract preserves fields, typed id, order, empty result and missing lookup`() {
        assertTrue(bundle.facilities.findAll().isEmpty())
        val first = bundle.facilities.create("  Main Gym  ", FacilityType.GYM).successFacility()
        val second = bundle.facilities.create("Pool", FacilityType.POOL).successFacility()

        assertTrue(first.id.value > 0)
        assertFacility(bundle.facilities.findById(first.id), first.id, "Main Gym", FacilityType.GYM, FacilityStatus.INACTIVE)
        assertFacility(bundle.facilities.findById(second.id), second.id, "Pool", FacilityType.POOL, FacilityStatus.INACTIVE)
        assertNull(bundle.facilities.findById(FacilityId(999_999)))
        bundle.facilities.findAll().also { facilities ->
            assertEquals(listOf(first.id, second.id), facilities.map { it.id })
            assertFacility(facilities[0], first.id, "Main Gym", FacilityType.GYM, FacilityStatus.INACTIVE)
            assertFacility(facilities[1], second.id, "Pool", FacilityType.POOL, FacilityStatus.INACTIVE)
        }

        val updated = Facility(first.id, "Updated Gym", FacilityType.STADIUM, FacilityStatus.ACTIVE)
        assertFacility(bundle.facilities.save(updated), first.id, "Updated Gym", FacilityType.STADIUM, FacilityStatus.ACTIVE)
        assertFacility(bundle.facilities.findById(first.id), first.id, "Updated Gym", FacilityType.STADIUM, FacilityStatus.ACTIVE)
        assertNull(
            bundle.facilities.save(Facility(FacilityId(999_999), "Missing", FacilityType.POOL, FacilityStatus.ACTIVE))
        )
    }

    @Test
    fun `equipment contract preserves fields and filters by typed facility id`() {
        assertTrue(bundle.equipment.findAll().isEmpty())
        assertTrue(bundle.equipment.findByFacilityId(FacilityId(999_999)).isEmpty())
        val firstFacility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val secondFacility = bundle.facilities.create("Pool", FacilityType.POOL).successFacility()
        val first = bundle.equipment.create(firstFacility.id, "Ventilation", EquipmentType.VENTILATION).equipment()
        val second = bundle.equipment.create(secondFacility.id, "Heating", EquipmentType.HEATING).equipment()

        assertEquipment(bundle.equipment.findByEquipmentId(first.id), first.id, firstFacility.id, "Ventilation", EquipmentType.VENTILATION)
        assertEquipment(bundle.equipment.findByEquipmentId(second.id), second.id, secondFacility.id, "Heating", EquipmentType.HEATING)
        bundle.equipment.findByFacilityId(firstFacility.id).also { equipment ->
            assertEquals(listOf(first.id), equipment.map { it.id })
            assertEquipment(equipment[0], first.id, firstFacility.id, "Ventilation", EquipmentType.VENTILATION)
        }
        bundle.equipment.findAll().also { equipment ->
            assertEquals(listOf(first.id, second.id), equipment.map { it.id })
            assertEquipment(equipment[0], first.id, firstFacility.id, "Ventilation", EquipmentType.VENTILATION)
            assertEquipment(equipment[1], second.id, secondFacility.id, "Heating", EquipmentType.HEATING)
        }
        assertNull(bundle.equipment.findByEquipmentId(EquipmentId(999_999)))
    }

    @Test
    fun `equipment rejected create does not mutate collections`() {
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()

        assertIs<com.doduohor.service.CreateEquipmentResult.InvalidName>(
            bundle.equipment.create(facility.id, "   ", EquipmentType.HEATING)
        )

        assertTrue(bundle.equipment.findAll().isEmpty())
        assertTrue(bundle.equipment.findByFacilityId(facility.id).isEmpty())
    }

    @Test
    fun `booking contract uses reserved status, exact dates and half open intervals`() {
        assertTrue(bundle.bookings.findAll().isEmpty())
        assertTrue(bundle.bookings.findByFacilityId(FacilityId(999_999)).isEmpty())
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val firstInterval = interval("2026-08-20T10:00:00Z", "2026-08-20T12:00:00Z")
        val first = bundle.bookings.createIfAvailable(facility.id, CustomerId(900), firstInterval).booking()
        val adjacent = bundle.bookings.createIfAvailable(
            facility.id, CustomerId(901), interval("2026-08-20T12:00:00Z", "2026-08-20T13:00:00Z")
        ).booking()

        assertBooking(bundle.bookings.findByBookingId(first.id), first.id, facility.id, CustomerId(900), firstInterval)
        assertBooking(bundle.bookings.findByBookingId(adjacent.id), adjacent.id, facility.id, CustomerId(901), interval("2026-08-20T12:00:00Z", "2026-08-20T13:00:00Z"))
        listOf(bundle.bookings.findByFacilityId(facility.id), bundle.bookings.findAll()).forEach { bookings ->
            assertEquals(listOf(first.id, adjacent.id), bookings.map { it.id })
            assertBooking(bookings[0], first.id, facility.id, CustomerId(900), firstInterval)
            assertBooking(bookings[1], adjacent.id, facility.id, CustomerId(901), interval("2026-08-20T12:00:00Z", "2026-08-20T13:00:00Z"))
        }
        assertIs<BookingCreationResult.UnavailableRange>(
            bundle.bookings.createIfAvailable(facility.id, CustomerId(902), interval("2026-08-20T11:00:00Z", "2026-08-20T12:30:00Z"))
        )
        assertNull(bundle.bookings.findByBookingId(BookingId(999_999)))
    }

    @Test
    fun `measurement contract preserves all fields and insertion order in collections and filters`() {
        assertTrue(bundle.measurements.findAll().isEmpty())
        assertTrue(bundle.measurements.findByEquipmentId(EquipmentId(999_999)).isEmpty())
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val equipment = bundle.equipment.create(facility.id, "Thermometer", EquipmentType.HEATING).equipment()
        val firstReading = MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 21.5)
        val secondReading = MeasurementReading(MeasurementType.HUMIDITY, MeasurementUnit.PERCENT, 42.0)
        val first = bundle.measurements.create(equipment.id, firstReading)
        val second = bundle.measurements.create(equipment.id, secondReading)

        assertMeasurement(bundle.measurements.findByMeasurementId(first.id), first.id, equipment.id, firstReading)
        assertMeasurement(bundle.measurements.findByMeasurementId(second.id), second.id, equipment.id, secondReading)
        assertMeasurements(bundle.measurements.findByEquipmentId(equipment.id), listOf(first.id to firstReading, second.id to secondReading), equipment.id)
        assertMeasurements(bundle.measurements.findAll(), listOf(first.id to firstReading, second.id to secondReading), equipment.id)
        assertNull(bundle.measurements.findByMeasurementId(MeasurementId(999_999)))
    }

    @Test
    fun `incident contract preserves all fields and insertion order in collections and filters`() {
        assertTrue(bundle.incidents.findAll().isEmpty())
        assertTrue(bundle.incidents.findByFacilityId(FacilityId(999_999)).isEmpty())
        assertTrue(bundle.incidents.findByEquipmentId(EquipmentId(999_999)).isEmpty())
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val equipment = bundle.equipment.create(facility.id, "Smoke sensor", EquipmentType.FIRE_ALARM).equipment()
        val firstMeasurement = bundle.measurements.create(
            equipment.id, MeasurementReading(MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0)
        )
        val secondMeasurement = bundle.measurements.create(
            equipment.id, MeasurementReading(MeasurementType.CO2, MeasurementUnit.PPM, 950.0)
        )
        val first = assertIs<IncidentCreationResult.Success<Incident>>(bundle.incidents.create(
            facility.id, equipment.id, firstMeasurement.id, IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH,
            MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0
        )).value
        val second = assertIs<IncidentCreationResult.Success<Incident>>(bundle.incidents.create(
            facility.id, equipment.id, secondMeasurement.id, IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL,
            MeasurementType.CO2, MeasurementUnit.PPM, 950.0
        )).value

        assertIncident(bundle.incidents.findByIncidentId(first.id), first.id, facility.id, equipment.id, firstMeasurement.id,
            IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH, MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0)
        assertIncident(bundle.incidents.findByIncidentId(second.id), second.id, facility.id, equipment.id, secondMeasurement.id,
            IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL, MeasurementType.CO2, MeasurementUnit.PPM, 950.0)
        val expectedIds = listOf(first.id, second.id)
        listOf(
            bundle.incidents.findByFacilityId(facility.id),
            bundle.incidents.findByEquipmentId(equipment.id),
            bundle.incidents.findAll()
        ).forEach { incidents ->
            assertEquals(expectedIds, incidents.map { it.id })
            assertIncident(incidents[0], first.id, facility.id, equipment.id, firstMeasurement.id,
                IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH, MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0)
            assertIncident(incidents[1], second.id, facility.id, equipment.id, secondMeasurement.id,
                IncidentType.HIGH_CO2, IncidentSeverity.CRITICAL, MeasurementType.CO2, MeasurementUnit.PPM, 950.0)
        }

        val statusChangedAt = contractClock.now().plusSeconds(60)
        val updated = assertIs<IncidentTransitionResult.Success>(first.startProgress(statusChangedAt)).incident
        assertIncident(
            bundle.incidents.save(updated), updated.id, facility.id, equipment.id, firstMeasurement.id,
            IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH, MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0,
            IncidentStatus.IN_PROGRESS, statusChangedAt
        )
        assertIncident(
            bundle.incidents.findByIncidentId(updated.id), updated.id, facility.id, equipment.id, firstMeasurement.id,
            IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH, MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0,
            IncidentStatus.IN_PROGRESS, statusChangedAt
        )
        assertNull(bundle.incidents.save(Incident.restore(
            id = IncidentId(999_999),
            facilityId = updated.facilityId,
            equipmentId = updated.equipmentId,
            measurementId = updated.measurementId,
            type = updated.type,
            severity = updated.severity,
            status = updated.status,
            measurementType = updated.measurementType,
            measurementUnit = updated.measurementUnit,
            value = updated.value,
            createdAt = updated.createdAt,
            statusChangedAt = updated.statusChangedAt
        )))

        assertNull(bundle.incidents.findByIncidentId(IncidentId(999_999)))
    }

    @Test
    fun `outbox contract covers state transitions, attempts and duplicate event ids`() {
        assertTrue(bundle.outbox.findUnprocessedEvents().isEmpty())
        val eventId = Uuid.random()
        val event = NewOutboxEvents.create(
            eventId, IntegrationEventType.MEASUREMENT_CREATED, buildJsonObject { put("value", JsonPrimitive(21)) },
            OffsetDateTime.parse("2026-08-20T15:00:00+03:00")
        )
        val failedEvent = NewOutboxEvents(
            eventId = Uuid.random(),
            eventType = IntegrationEventType.INCIDENT_CREATED,
            payload = buildJsonObject { put("incident", JsonPrimitive("smoke")) },
            status = OutboxEventStatus.FAILED,
            createdAt = OffsetDateTime.parse("2026-08-20T10:30:00-02:00"),
            publishedAt = null,
            attempt = 1,
            errorMessage = "first attempt failed"
        )
        assertEquals(SaveEventResult.Success, bundle.outbox.saveEvent(event))
        assertEquals(SaveEventResult.Error, bundle.outbox.saveEvent(event))
        assertEquals(SaveEventResult.Success, bundle.outbox.saveEvent(failedEvent))
        val saved = bundle.outbox.findUnprocessedEvents()
        assertEquals(listOf(eventId, failedEvent.eventId), saved.map { it.eventId })
        assertOutboxEvent(saved[0], event)
        assertOutboxEvent(saved[1], failedEvent)

        assertEquals(StartPublishingResult.Started, bundle.outbox.tryStartPublishing(eventId))
        assertEquals(StartPublishingResult.AlreadyProcessing, bundle.outbox.tryStartPublishing(eventId))
        assertEquals(SaveErrorResult.Success, bundle.outbox.saveError(eventId, "temporary"))
        assertEquals(StartPublishingResult.Started, bundle.outbox.tryStartPublishing(eventId))
        assertEquals(MakeAsPublishedResult.Success, bundle.outbox.makeAsPublished(eventId))
        assertEquals(MakeAsPublishedResult.ActualPublished, bundle.outbox.makeAsPublished(eventId))
        assertEquals(SaveErrorResult.NotProcessing, bundle.outbox.saveError(eventId, "late"))
    }

    @Test
    fun `outbox contract stops retrying after maximum attempts`() {
        val eventId = Uuid.random()
        val event = NewOutboxEvents.create(
            eventId, IntegrationEventType.INCIDENT_CREATED, buildJsonObject { put("value", JsonPrimitive(99)) },
            OffsetDateTime.parse("2026-08-20T12:00:00Z")
        )
        bundle.outbox.saveEvent(event)

        repeat(EventProcessingPolicy.MAX_ATTEMPTS) {
            assertEquals(StartPublishingResult.Started, bundle.outbox.tryStartPublishing(eventId))
            assertEquals(SaveErrorResult.Success, bundle.outbox.saveError(eventId, "temporary"))
        }

        assertEquals(StartPublishingResult.AttemptsExceeded, bundle.outbox.tryStartPublishing(eventId))
        assertTrue(bundle.outbox.findUnprocessedEvents().none { it.eventId == eventId })
    }

    private fun interval(start: String, end: String) = BookingTimeInterval(Instant.parse(start), Instant.parse(end))
    private fun assertFacility(actual: Facility?, id: FacilityId, name: String, type: FacilityType, status: FacilityStatus) {
        assertNotNull(actual)
        assertEquals(id, actual.id)
        assertEquals(name, actual.name)
        assertEquals(type, actual.type)
        assertEquals(status, actual.status)
    }
    private fun assertEquipment(actual: com.doduohor.domain.model.Equipment?, id: EquipmentId, facilityId: FacilityId, name: String, type: EquipmentType) {
        assertNotNull(actual)
        assertEquals(id, actual.id)
        assertEquals(facilityId, actual.facilityId)
        assertEquals(name, actual.name)
        assertEquals(type, actual.type)
        assertEquals(EquipmentStatus.DISABLED, actual.status)
    }
    private fun assertBooking(actual: Booking?, id: BookingId, facilityId: FacilityId, customerId: CustomerId, interval: BookingTimeInterval) {
        assertNotNull(actual)
        assertEquals(id, actual.id)
        assertEquals(facilityId, actual.facilityId)
        assertEquals(customerId, actual.customerId)
        assertEquals(interval, actual.timeInterval)
        assertEquals(BookingStatus.RESERVED, actual.status)
        assertEquals(contractClock.now(), actual.createdAt)
    }
    private fun assertMeasurement(actual: Measurement?, id: MeasurementId, equipmentId: EquipmentId, reading: MeasurementReading) {
        assertNotNull(actual)
        assertEquals(id, actual.id)
        assertEquals(equipmentId, actual.equipmentId)
        assertEquals(reading, actual.measurementReading)
        assertEquals(contractClock.now(), actual.createdAt)
    }
    private fun assertMeasurements(actual: List<Measurement>, expected: List<Pair<MeasurementId, MeasurementReading>>, equipmentId: EquipmentId) {
        assertEquals(expected.map { it.first }, actual.map { it.id })
        actual.zip(expected).forEach { (measurement, expectation) ->
            assertMeasurement(measurement, expectation.first, equipmentId, expectation.second)
        }
    }
    private fun assertIncident(
        actual: Incident?, id: IncidentId, facilityId: FacilityId, equipmentId: EquipmentId, measurementId: MeasurementId,
        type: IncidentType, severity: IncidentSeverity, measurementType: MeasurementType, measurementUnit: MeasurementUnit, value: Double,
        status: IncidentStatus = IncidentStatus.OPEN, statusChangedAt: Instant = contractClock.now()
    ) {
        assertNotNull(actual)
        assertEquals(id, actual.id)
        assertEquals(facilityId, actual.facilityId)
        assertEquals(equipmentId, actual.equipmentId)
        assertEquals(measurementId, actual.measurementId)
        assertEquals(type, actual.type)
        assertEquals(severity, actual.severity)
        assertEquals(status, actual.status)
        assertEquals(measurementType, actual.measurementType)
        assertEquals(measurementUnit, actual.measurementUnit)
        assertEquals(value, actual.value)
        assertEquals(contractClock.now(), actual.createdAt)
        assertEquals(statusChangedAt, actual.statusChangedAt)
    }
    private fun assertOutboxEvent(actual: OutboxEvents, expected: NewOutboxEvents) {
        assertTrue(actual.id > 0)
        assertEquals(expected.eventId, actual.eventId)
        assertEquals(expected.eventType, actual.eventType)
        assertEquals(expected.payload, actual.payload)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.createdAt.toInstant(), actual.createdAt.toInstant())
        assertEquals(expected.publishedAt?.toInstant(), actual.publishedAt?.toInstant())
        assertEquals(expected.attempt, actual.attempt)
        assertEquals(expected.errorMessage, actual.errorMessage)
    }
    private fun FacilityCreationResult<Facility>.successFacility() = assertIs<FacilityCreationResult.Success<Facility>>(this).value
    private fun com.doduohor.service.CreateEquipmentResult.equipment() = assertIs<com.doduohor.service.CreateEquipmentResult.Success>(this).equipment
    private fun BookingCreationResult<Booking>.booking() = assertIs<BookingCreationResult.Success<Booking>>(this).value
}

class InMemoryRepositoryContractTest : RepositoryContractTest() {
    override fun createBundle() = RepositoryBundle(
        InMemoryFacilityRepository(), InMemoryEquipmentRepository(), InMemoryBookingRepository(contractClock),
        InMemoryMeasurementRepository(contractClock), InMemoryIncidentRepository(contractClock), InMemoryOutboxEventsRepository(contractClock)
    ) { }
}

@Testcontainers
class PostgresRepositoryContractTest : RepositoryContractTest() {
    companion object {
        @Container @JvmField val postgres = PostgreSQLContainer("postgres:17-alpine")
        private lateinit var dataSource: HikariDataSource
        private lateinit var database: Database

        @BeforeAll @JvmStatic
        fun start() {
            dataSource = HikariDataSource(HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl; username = postgres.username; password = postgres.password
                driverClassName = postgres.driverClassName; maximumPoolSize = 8
            })
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            database = Database.connect(dataSource)
        }

        @AfterAll @JvmStatic
        fun stop() = dataSource.close()
    }

    override fun createBundle() = RepositoryBundle(
        PostgresFacilityRepository(database), PostgresEquipmentRepository(database),
        PostgresBookingRepository(database, contractClock), PostgresMeasurementRepository(database, contractClock),
        PostgresIncidentRepository(database, contractClock), PostgresOutboxEventsRepository(database, contractClock)
    ) {
        transaction(database) {
            OutboxEventsTable.deleteAll(); IncidentTable.deleteAll(); MeasurementTable.deleteAll()
            BookingTable.deleteAll(); EquipmentTable.deleteAll(); FacilityTable.deleteAll()
        }
    }
}
