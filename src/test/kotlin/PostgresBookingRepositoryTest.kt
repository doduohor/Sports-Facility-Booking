package com.doduohor

import com.doduohor.domain.model.BookingStatus
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.model.FacilityType
import com.doduohor.infrastructure.database.postgres.BookingTable
import com.doduohor.infrastructure.database.postgres.FacilityTable
import com.doduohor.repository.postgres.PostgresBookingRepository
import com.doduohor.repository.postgres.PostgresFacilityRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class PostgresBookingRepositoryTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        private lateinit var dataSource: HikariDataSource
        lateinit var database: Database

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = postgres.driverClassName
                maximumPoolSize = 5
            }

            dataSource = HikariDataSource(hikariConfig)
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            database = Database.connect(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            dataSource.close()
        }
    }

    @BeforeEach
    fun clearTables() {
        transaction(database) {
            BookingTable.deleteAll()
            FacilityTable.deleteAll()
        }
    }

    @Test
    fun `create persists booking and findByBookingId returns it`() {
        val bookingRepository = PostgresBookingRepository(database)
        val facility = createFacility()
        val interval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")

        val createdBooking = bookingRepository.create(
            facilityId = facility.id,
            customerId = 900,
            timeInterval = interval
        )

        assertTrue(createdBooking.id > 0)
        assertEquals(facility.id, createdBooking.facilityId)
        assertEquals(900, createdBooking.customerId)
        assertEquals(interval, createdBooking.timeInterval)
        assertEquals(BookingStatus.RESERVED, createdBooking.status)

        val foundBooking = assertNotNull(bookingRepository.findByBookingId(createdBooking.id))
        assertEquals(createdBooking.id, foundBooking.id)
        assertEquals(createdBooking.facilityId, foundBooking.facilityId)
        assertEquals(createdBooking.customerId, foundBooking.customerId)
        assertEquals(createdBooking.timeInterval, foundBooking.timeInterval)
        assertEquals(createdBooking.status, foundBooking.status)
    }

    @Test
    fun `findByBookingId returns null when booking does not exist`() {
        val bookingRepository = PostgresBookingRepository(database)

        val foundBooking = bookingRepository.findByBookingId(999_999)

        assertNull(foundBooking)
    }

    @Test
    fun `create fails when facility foreign key does not exist`() {
        val bookingRepository = PostgresBookingRepository(database)

        assertFailsWith<ExposedSQLException> {
            bookingRepository.create(
                facilityId = 999_999,
                customerId = 900,
                timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
            )
        }
    }

    @Test
    fun `findByFacilityId returns only bookings for requested facility`() {
        val bookingRepository = PostgresBookingRepository(database)
        val firstFacility = createFacility("Main Gym", FacilityType.GYM)
        val secondFacility = createFacility("Pool", FacilityType.POOL)
        val firstBooking = bookingRepository.create(
            facilityId = firstFacility.id,
            customerId = 900,
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
        )
        bookingRepository.create(
            facilityId = secondFacility.id,
            customerId = 901,
            timeInterval = bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T10:00:00Z")
        )

        val bookings = bookingRepository.findByFacilityId(firstFacility.id)

        assertEquals(listOf(firstBooking.id), bookings.map { it.id })
    }

    @Test
    fun `findAll returns all persisted bookings`() {
        val bookingRepository = PostgresBookingRepository(database)
        val facility = createFacility()
        val firstBooking = bookingRepository.create(
            facilityId = facility.id,
            customerId = 900,
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
        )
        val secondBooking = bookingRepository.create(
            facilityId = facility.id,
            customerId = 901,
            timeInterval = bookingInterval("2026-08-12T08:00:00Z", "2026-08-12T09:00:00Z")
        )

        val bookings = bookingRepository.findAll()

        assertEquals(listOf(firstBooking.id, secondBooking.id), bookings.map { it.id })
    }

    @Test
    fun `findOverlappingByFacilityId returns true for overlapping interval`() {
        val bookingRepository = PostgresBookingRepository(database)
        val facility = createFacility()
        bookingRepository.create(
            facilityId = facility.id,
            customerId = 900,
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T09:00:00Z")
        )

        val hasOverlap = bookingRepository.findOverlappingByFacilityId(
            facilityId = facility.id,
            timeInterval = bookingInterval("2026-08-12T08:00:00Z", "2026-08-12T10:00:00Z")
        )

        assertTrue(hasOverlap)
    }

    @Test
    fun `findOverlappingByFacilityId returns false for adjacent interval`() {
        val bookingRepository = PostgresBookingRepository(database)
        val facility = createFacility()
        bookingRepository.create(
            facilityId = facility.id,
            customerId = 900,
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T09:00:00Z")
        )

        val hasOverlap = bookingRepository.findOverlappingByFacilityId(
            facilityId = facility.id,
            timeInterval = bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T10:00:00Z")
        )

        assertFalse(hasOverlap)
    }

    @Test
    fun `findOverlappingByFacilityId ignores bookings from another facility`() {
        val bookingRepository = PostgresBookingRepository(database)
        val firstFacility = createFacility("Main Gym", FacilityType.GYM)
        val secondFacility = createFacility("Pool", FacilityType.POOL)
        bookingRepository.create(
            facilityId = firstFacility.id,
            customerId = 900,
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T09:00:00Z")
        )

        val hasOverlap = bookingRepository.findOverlappingByFacilityId(
            facilityId = secondFacility.id,
            timeInterval = bookingInterval("2026-08-12T08:00:00Z", "2026-08-12T10:00:00Z")
        )

        assertFalse(hasOverlap)
    }

    @Test
    fun `findByBookingId fails when stored booking status is unknown`() {
        val bookingRepository = PostgresBookingRepository(database)
        val facility = createFacility()
        val interval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
        val bookingId = transaction(database) {
            BookingTable.insert {
                it[facilityId] = facility.id
                it[customerId] = 900
                it[startTime] = interval.startTime
                it[endTime] = interval.endTime
                it[status] = "UNKNOWN_STATUS"
                it[createdAt] = Instant.parse("2026-08-12T06:00:00Z")
            }[BookingTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            bookingRepository.findByBookingId(bookingId)
        }
    }

    private fun createFacility(
        name: String = "Main Gym",
        type: FacilityType = FacilityType.GYM
    ) = PostgresFacilityRepository(database).create(
        facilityName = name,
        facilityType = type
    )

    private fun bookingInterval(startTime: String, endTime: String) =
        BookingTimeInterval(
            startTime = Instant.parse(startTime),
            endTime = Instant.parse(endTime)
        )
}
