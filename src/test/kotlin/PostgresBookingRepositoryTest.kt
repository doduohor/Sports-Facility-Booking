package com.doduohor

import com.doduohor.domain.model.Booking
import com.doduohor.domain.model.BookingCreationResult
import com.doduohor.domain.model.BookingStatus
import com.doduohor.domain.shared.BookingId
import com.doduohor.domain.shared.CustomerId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.model.BookingTimeInterval
import com.doduohor.domain.model.FacilityType
import com.doduohor.infrastructure.database.postgres.BookingTable
import com.doduohor.infrastructure.database.postgres.FacilityTable
import com.doduohor.infrastructure.time.FixedClock
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Testcontainers
class PostgresBookingRepositoryTest {
    private val fixedInstant = Instant.parse("2026-08-20T12:00:00Z")
    private val fixedClock = FixedClock(fixedInstant)

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
        val bookingRepository = bookingRepository()
        val facility = createFacility()
        val interval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")

        val createdBooking = bookingRepository.createIfAvailable(
            facilityId = facility.id,
            customerId = CustomerId(900),
            timeInterval = interval
        ).bookingOrFail()

        assertTrue(createdBooking.id.value > 0)
        assertEquals(facility.id, createdBooking.facilityId)
        assertEquals(900, createdBooking.customerId.value)
        assertEquals(interval, createdBooking.timeInterval)
        assertEquals(BookingStatus.RESERVED, createdBooking.status)
        assertEquals(fixedInstant, createdBooking.createdAt)

        val foundBooking = assertNotNull(bookingRepository.findByBookingId(createdBooking.id))
        assertEquals(createdBooking.id, foundBooking.id)
        assertEquals(createdBooking.facilityId, foundBooking.facilityId)
        assertEquals(createdBooking.customerId, foundBooking.customerId)
        assertEquals(createdBooking.timeInterval, foundBooking.timeInterval)
        assertEquals(createdBooking.status, foundBooking.status)
    }

    @Test
    fun `findByBookingId returns null when booking does not exist`() {
        val bookingRepository = bookingRepository()

        val foundBooking = bookingRepository.findByBookingId(BookingId(999_999))

        assertNull(foundBooking)
    }

    @Test
    fun `create fails when facility foreign key does not exist`() {
        val bookingRepository = bookingRepository()

        assertFailsWith<ExposedSQLException> {
            bookingRepository.createIfAvailable(
                facilityId = FacilityId(999_999),
                customerId = CustomerId(900),
                timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
            )
        }
    }

    @Test
    fun `findByFacilityId returns only bookings for requested facility`() {
        val bookingRepository = bookingRepository()
        val firstFacility = createFacility("Main Gym", FacilityType.GYM)
        val secondFacility = createFacility("Pool", FacilityType.POOL)
        val firstBooking = bookingRepository.createIfAvailable(
            facilityId = firstFacility.id,
            customerId = CustomerId(900),
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
        ).bookingOrFail()
        bookingRepository.createIfAvailable(
            facilityId = secondFacility.id,
            customerId = CustomerId(901),
            timeInterval = bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T10:00:00Z")
        )

        val bookings = bookingRepository.findByFacilityId(firstFacility.id)

        assertEquals(listOf(firstBooking.id), bookings.map { it.id })
    }

    @Test
    fun `findAll returns all persisted bookings`() {
        val bookingRepository = bookingRepository()
        val facility = createFacility()
        val firstBooking = bookingRepository.createIfAvailable(
            facilityId = facility.id,
            customerId = CustomerId(900),
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
        ).bookingOrFail()
        val secondBooking = bookingRepository.createIfAvailable(
            facilityId = facility.id,
            customerId = CustomerId(901),
            timeInterval = bookingInterval("2026-08-12T08:00:00Z", "2026-08-12T09:00:00Z")
        ).bookingOrFail()

        val bookings = bookingRepository.findAll()

        assertEquals(listOf(firstBooking.id, secondBooking.id), bookings.map { it.id })
    }

    @Test
    fun `createIfAvailable returns unavailable for overlapping interval`() {
        val bookingRepository = bookingRepository()
        val facility = createFacility()
        bookingRepository.createIfAvailable(
            facilityId = facility.id,
            customerId = CustomerId(900),
            timeInterval = bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T12:00:00Z")
        )

        val intervals = listOf(
            "2026-08-12T10:00:00Z" to "2026-08-12T11:00:00Z",
            "2026-08-12T11:00:00Z" to "2026-08-12T12:00:00Z",
            "2026-08-12T09:00:00Z" to "2026-08-12T13:00:00Z"
        )

        intervals.forEachIndexed { index, (start, end) ->
            assertIs<BookingCreationResult.UnavailableRange>(bookingRepository.createIfAvailable(
                facilityId = facility.id,
                customerId = CustomerId(901 + index),
                timeInterval = bookingInterval(start, end)
            ))
        }
    }

    @Test
    fun `createIfAvailable allows adjacent and other facility intervals`() {
        val bookingRepository = bookingRepository()
        val firstFacility = createFacility("Main Gym", FacilityType.GYM)
        val secondFacility = createFacility("Pool", FacilityType.POOL)
        bookingRepository.createIfAvailable(
            facilityId = firstFacility.id,
            customerId = CustomerId(900),
            timeInterval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T09:00:00Z")
        )

        val adjacentBooking = bookingRepository.createIfAvailable(
            facilityId = firstFacility.id,
            customerId = CustomerId(901),
            timeInterval = bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T10:00:00Z")
        )
        val otherFacilityBooking = bookingRepository.createIfAvailable(
            facilityId = secondFacility.id,
            customerId = CustomerId(902),
            timeInterval = bookingInterval("2026-08-12T08:00:00Z", "2026-08-12T10:00:00Z")
        )

        assertIs<BookingCreationResult.Success<Booking>>(adjacentBooking)
        assertIs<BookingCreationResult.Success<Booking>>(otherFacilityBooking)
    }

    @Test
    fun `createIfAvailable returns unavailable when interval overlaps booking for same facility`() {
        val bookingRepository = bookingRepository()
        val firstFacility = createFacility("Main Gym", FacilityType.GYM)
        val secondFacility = createFacility("Pool", FacilityType.POOL)
        bookingRepository.createIfAvailable(
            facilityId = firstFacility.id,
            customerId = CustomerId(900),
            timeInterval = bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T12:00:00Z")
        )

        val result = bookingRepository.createIfAvailable(
            facilityId = firstFacility.id,
            customerId = CustomerId(901),
            timeInterval = bookingInterval("2026-08-12T11:00:00Z", "2026-08-12T13:00:00Z")
        )

        val adjacentBooking = bookingRepository.createIfAvailable(
            facilityId = firstFacility.id,
            customerId = CustomerId(902),
            timeInterval = bookingInterval("2026-08-12T12:00:00Z", "2026-08-12T13:00:00Z")
        ).bookingOrFail()
        val otherFacilityBooking = bookingRepository.createIfAvailable(
            facilityId = secondFacility.id,
            customerId = CustomerId(903),
            timeInterval = bookingInterval("2026-08-12T11:00:00Z", "2026-08-12T13:00:00Z")
        ).bookingOrFail()

        assertIs<BookingCreationResult.UnavailableRange>(result)
        assertEquals(firstFacility.id, adjacentBooking.facilityId)
        assertEquals(secondFacility.id, otherFacilityBooking.facilityId)
    }

    @Test
    fun `concurrent createIfAvailable allows only one overlapping booking`() {
        val facility = createFacility()
        val interval = bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T12:00:00Z")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = (900..901).map { customerId ->
                executor.submit<BookingCreationResult<Booking>> {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS)) { "Concurrent test did not start in time" }

                    bookingRepository().createIfAvailable(
                        facilityId = facility.id,
                        customerId = CustomerId(customerId),
                        timeInterval = interval
                    )
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()

            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it is BookingCreationResult.Success })
            assertEquals(1, results.count { it is BookingCreationResult.UnavailableRange })
            assertEquals(1, bookingRepository().findByFacilityId(facility.id).size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `findByBookingId fails when stored booking status is unknown`() {
        val bookingRepository = bookingRepository()
        val facility = createFacility()
        val interval = bookingInterval("2026-08-12T07:00:00Z", "2026-08-12T08:00:00Z")
        val bookingId = transaction(database) {
            BookingTable.insert {
                it[facilityId] = facility.id.value
                it[customerId] = 900
                it[startTime] = interval.startTime
                it[endTime] = interval.endTime
                it[status] = "UNKNOWN_STATUS"
                it[createdAt] = Instant.parse("2026-08-12T06:00:00Z")
            }[BookingTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            bookingRepository.findByBookingId(BookingId(bookingId))
        }
    }

    private fun createFacility(
        name: String = "Main Gym",
        type: FacilityType = FacilityType.GYM
    ) = PostgresFacilityRepository(database).create(
        facilityName = name,
        facilityType = type
    ).getOrThrow()

    private fun bookingInterval(startTime: String, endTime: String) =
        BookingTimeInterval(
            startTime = Instant.parse(startTime),
            endTime = Instant.parse(endTime)
        )

    private fun bookingRepository() = PostgresBookingRepository(database, fixedClock)

    private fun BookingCreationResult<Booking>.bookingOrFail(): Booking =
        assertIs<BookingCreationResult.Success<Booking>>(this).value
}
