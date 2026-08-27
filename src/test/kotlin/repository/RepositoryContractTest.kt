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
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
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
        assertEquals("Main Gym", first.name)
        assertEquals(first, bundle.facilities.findById(FacilityId(first.id.value)))
        assertNull(bundle.facilities.findById(FacilityId(999_999)))
        assertEquals(listOf(first, second), bundle.facilities.findAll())
    }

    @Test
    fun `equipment contract preserves fields and filters by typed facility id`() {
        val firstFacility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val secondFacility = bundle.facilities.create("Pool", FacilityType.POOL).successFacility()
        val first = bundle.equipment.create(firstFacility.id, "Ventilation", EquipmentType.VENTILATION).equipment()
        val second = bundle.equipment.create(secondFacility.id, "Heating", EquipmentType.HEATING).equipment()

        assertEquals(first, bundle.equipment.findByEquipmentId(EquipmentId(first.id.value)))
        assertEquals(listOf(first), bundle.equipment.findByFacilityId(firstFacility.id))
        assertEquals(listOf(first, second), bundle.equipment.findAll())
        assertNull(bundle.equipment.findByEquipmentId(EquipmentId(999_999)))
    }

    @Test
    fun `booking contract uses reserved status, exact dates and half open intervals`() {
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val firstInterval = interval("2026-08-20T10:00:00Z", "2026-08-20T12:00:00Z")
        val first = bundle.bookings.createIfAvailable(facility.id, CustomerId(900), firstInterval).booking()
        val adjacent = bundle.bookings.createIfAvailable(
            facility.id, CustomerId(901), interval("2026-08-20T12:00:00Z", "2026-08-20T13:00:00Z")
        )

        assertEquals(BookingStatus.RESERVED, first.status)
        assertEquals(contractClock.now(), first.createdAt)
        assertEquals(first, bundle.bookings.findByBookingId(BookingId(first.id.value)))
        assertIs<BookingCreationResult.Success<Booking>>(adjacent)
        assertIs<BookingCreationResult.UnavailableRange>(
            bundle.bookings.createIfAvailable(facility.id, CustomerId(902), interval("2026-08-20T11:00:00Z", "2026-08-20T12:30:00Z"))
        )
        assertNull(bundle.bookings.findByBookingId(BookingId(999_999)))
    }

    @Test
    fun `measurement contract preserves reading, timestamp, filter and missing lookup`() {
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val equipment = bundle.equipment.create(facility.id, "Thermometer", EquipmentType.HEATING).equipment()
        val reading = MeasurementReading(MeasurementType.TEMPERATURE, MeasurementUnit.CELSIUS, 21.5)
        val measurement = bundle.measurements.create(equipment.id, reading)

        assertEquals(reading, measurement.measurementReading)
        assertEquals(contractClock.now(), measurement.createdAt)
        assertEquals(listOf(measurement), bundle.measurements.findByEquipmentId(EquipmentId(equipment.id.value)))
        assertEquals(measurement, bundle.measurements.findByMeasurementId(measurement.id))
        assertNull(bundle.measurements.findByMeasurementId(MeasurementId(999_999)))
    }

    @Test
    fun `incident contract preserves all fields, open status and filters`() {
        val facility = bundle.facilities.create("Gym", FacilityType.GYM).successFacility()
        val equipment = bundle.equipment.create(facility.id, "Smoke sensor", EquipmentType.FIRE_ALARM).equipment()
        val measurement = bundle.measurements.create(
            equipment.id, MeasurementReading(MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0)
        )
        val incident = bundle.incidents.create(
            facility.id, equipment.id, measurement.id, IncidentType.SMOKE_DETECTED, IncidentSeverity.HIGH,
            MeasurementType.SMOKE, MeasurementUnit.PPM, 12.0
        )

        assertEquals(IncidentStatus.OPEN, incident.status)
        assertEquals(contractClock.now(), incident.createdAt)
        assertEquals(incident, bundle.incidents.findByIncidentId(IncidentId(incident.id.value)))
        assertEquals(listOf(incident), bundle.incidents.findByFacilityId(facility.id))
        assertEquals(listOf(incident), bundle.incidents.findByEquipmentId(equipment.id))
        assertNull(bundle.incidents.findByIncidentId(IncidentId(999_999)))
    }

    @Test
    fun `outbox contract covers state transitions, attempts and duplicate event ids`() {
        val eventId = Uuid.random()
        val event = NewOutboxEvents.create(
            eventId, IntegrationEventType.MEASUREMENT_CREATED, buildJsonObject { put("value", JsonPrimitive(21)) },
            OffsetDateTime.parse("2026-08-20T12:00:00Z")
        )
        assertEquals(SaveEventResult.Success, bundle.outbox.saveEvent(event))
        val duplicateResult = runCatching { bundle.outbox.saveEvent(event) }
        duplicateResult.exceptionOrNull()?.let { assertIs<ExposedSQLException>(it) }
        duplicateResult.getOrNull()?.let { assertEquals(SaveEventResult.Error, it) }
        assertEquals(listOf(eventId), bundle.outbox.findUnprocessedEvents().map { it.eventId })

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
