package com.doduohor

import com.doduohor.infrastructure.database.FacilityTable
import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityActivateResult
import com.doduohor.domain.model.FacilityStatus
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.activate
import com.doduohor.repository.postgres.PostgresFacilityRepository
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class PostgresFacilityRepositoryTest {
    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:17-alpine")
        private lateinit var dataSource: HikariDataSource
        lateinit var database: Database

        @JvmStatic
        @BeforeAll
        fun beforeAll(): Unit {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = postgres.driverClassName
                maximumPoolSize = 5
            }
            dataSource = HikariDataSource(hikariConfig)
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            database = Database.connect(dataSource)
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            dataSource.close()
        }
    }
    
    @BeforeEach
    fun clearFacility(): Unit {
        transaction (database) {
            FacilityTable.deleteAll()
        }
    }

    @Test
    fun `create persists facility and findById returns it`() {
        val repository = PostgresFacilityRepository(database)

        val createdFacility = repository.create(
            facilityName = "Main Gym",
            facilityType = FacilityType.GYM
        )

        assertTrue(createdFacility.id > 0)
        assertEquals("Main Gym", createdFacility.name)
        assertEquals(FacilityType.GYM, createdFacility.type)
        assertEquals(FacilityStatus.INACTIVE, createdFacility.status)

        val foundFacility = repository.findById(createdFacility.id)

        assertEquals(createdFacility, foundFacility)
    }

    @Test
    fun `findById returns null when facility does not exist`() {
        val repository = PostgresFacilityRepository(database)

        val foundFacility = repository.findById(999_999)

        assertNull(foundFacility)
    }

    @Test
    fun `save updates existing facility`() {
        val repository = PostgresFacilityRepository(database)
        val createdFacility = repository.create(
            facilityName = "Pool",
            facilityType = FacilityType.POOL
        )
        val activatedFacility = createdFacility.activate().successFacility()

        val savedFacility = repository.save(activatedFacility)
        val foundFacility = repository.findById(createdFacility.id)

        assertEquals(activatedFacility, savedFacility)
        assertEquals(activatedFacility, foundFacility)
    }

    @Test
    fun `save returns null when facility does not exist`() {
        val repository = PostgresFacilityRepository(database)
        val missingFacility = Facility(
            id = 999_999,
            name = "Missing facility",
            type = FacilityType.STADIUM,
            status = FacilityStatus.INACTIVE
        )

        val savedFacility = repository.save(missingFacility)

        assertNull(savedFacility)
    }

    @Test
    fun `findAll returns all persisted facilities`() {
        val repository = PostgresFacilityRepository(database)
        val firstFacility = repository.create(
            facilityName = "Gym",
            facilityType = FacilityType.GYM
        )
        val secondFacility = repository.create(
            facilityName = "Stadium",
            facilityType = FacilityType.STADIUM
        )

        val facilities = repository.findAll()

        assertEquals(listOf(firstFacility, secondFacility), facilities)
    }

    @Test
    fun `findById fails when stored facility type is unknown`() {
        val repository = PostgresFacilityRepository(database)
        val facilityId = transaction(database) {
            FacilityTable.insert {
                it[name] = "Broken facility"
                it[type] = "UNKNOWN_TYPE"
                it[status] = FacilityStatus.INACTIVE.name
            }[FacilityTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            repository.findById(facilityId)
        }
    }

    @Test
    fun `findById fails when stored facility status is unknown`() {
        val repository = PostgresFacilityRepository(database)
        val facilityId = transaction(database) {
            FacilityTable.insert {
                it[name] = "Broken facility"
                it[type] = FacilityType.GYM.name
                it[status] = "UNKNOWN_STATUS"
            }[FacilityTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            repository.findById(facilityId)
        }
    }

    private fun FacilityActivateResult.successFacility(): Facility {
        val result = assertNotNull(this as? FacilityActivateResult.Success)
        return result.facility
    }
}
