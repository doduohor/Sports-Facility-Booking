package com.doduohor

import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.infrastructure.database.postgres.BookingTable
import com.doduohor.infrastructure.database.postgres.EquipmentTable
import com.doduohor.infrastructure.database.postgres.FacilityTable
import com.doduohor.infrastructure.database.postgres.IncidentTable
import com.doduohor.infrastructure.database.postgres.MeasurementTable
import com.doduohor.repository.postgres.PostgresEquipmentRepository
import com.doduohor.repository.postgres.PostgresFacilityRepository
import com.doduohor.repository.postgres.PostgresIncidentRepository
import com.doduohor.repository.postgres.PostgresMeasurementRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class PostgresMonitoringRepositoriesTest {
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
            IncidentTable.deleteAll()
            MeasurementTable.deleteAll()
            BookingTable.deleteAll()
            EquipmentTable.deleteAll()
            FacilityTable.deleteAll()
        }
    }

    @Test
    fun `equipment repository persists and finds equipment`() {
        val equipmentRepository = PostgresEquipmentRepository(database)
        val facility = createFacility()

        val createdEquipment = equipmentRepository.create(
            facilityId = facility.id,
            name = "Main ventilation",
            type = EquipmentType.VENTILATION
        )

        assertTrue(createdEquipment.id > 0)
        assertEquals(facility.id, createdEquipment.facilityId)
        assertEquals("Main ventilation", createdEquipment.name)
        assertEquals(EquipmentType.VENTILATION, createdEquipment.type)
        assertEquals(EquipmentStatus.DISABLED, createdEquipment.status)
        assertEquals(createdEquipment, equipmentRepository.findByEquipmentId(createdEquipment.id))
        assertEquals(listOf(createdEquipment), equipmentRepository.findByFacilityId(facility.id))
        assertEquals(listOf(createdEquipment), equipmentRepository.findAll())
        assertNull(equipmentRepository.findByEquipmentId(999_999))
    }

    @Test
    fun `equipment create fails when facility foreign key does not exist`() {
        val equipmentRepository = PostgresEquipmentRepository(database)

        assertFailsWith<ExposedSQLException> {
            equipmentRepository.create(
                facilityId = 999_999,
                name = "Orphan ventilation",
                type = EquipmentType.VENTILATION
            )
        }
    }

    @Test
    fun `findByEquipmentId fails when stored equipment type is unknown`() {
        val equipmentRepository = PostgresEquipmentRepository(database)
        val facility = createFacility()
        val equipmentId = transaction(database) {
            EquipmentTable.insert {
                it[facilityId] = facility.id
                it[name] = "Broken equipment"
                it[type] = "UNKNOWN_TYPE"
                it[status] = EquipmentStatus.DISABLED.name
            }[EquipmentTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            equipmentRepository.findByEquipmentId(equipmentId)
        }
    }

    @Test
    fun `findByEquipmentId fails when stored equipment status is unknown`() {
        val equipmentRepository = PostgresEquipmentRepository(database)
        val facility = createFacility()
        val equipmentId = transaction(database) {
            EquipmentTable.insert {
                it[facilityId] = facility.id
                it[name] = "Broken equipment"
                it[type] = EquipmentType.VENTILATION.name
                it[status] = "UNKNOWN_STATUS"
            }[EquipmentTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            equipmentRepository.findByEquipmentId(equipmentId)
        }
    }

    @Test
    fun `measurement repository persists and finds measurements`() {
        val measurementRepository = PostgresMeasurementRepository(database)
        val equipment = createEquipment()

        val createdMeasurement = measurementRepository.create(
            equipmentId = equipment.id,
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 24.5
        )

        assertTrue(createdMeasurement.id > 0)
        assertEquals(equipment.id, createdMeasurement.equipmentId)
        assertEquals(MeasurementType.TEMPERATURE, createdMeasurement.type)
        assertEquals(MeasurementUnit.CELSIUS, createdMeasurement.unit)
        assertEquals(24.5, createdMeasurement.value)
        assertEquals(createdMeasurement, measurementRepository.findByMeasurementId(createdMeasurement.id))
        assertEquals(listOf(createdMeasurement), measurementRepository.findByEquipmentId(equipment.id))
        assertEquals(listOf(createdMeasurement), measurementRepository.findAll())
        assertNull(measurementRepository.findByMeasurementId(999_999))
    }

    @Test
    fun `measurement create fails when equipment foreign key does not exist`() {
        val measurementRepository = PostgresMeasurementRepository(database)

        assertFailsWith<ExposedSQLException> {
            measurementRepository.create(
                equipmentId = 999_999,
                type = MeasurementType.TEMPERATURE,
                unit = MeasurementUnit.CELSIUS,
                value = 24.5
            )
        }
    }

    @Test
    fun `findByMeasurementId fails when stored measurement type is unknown`() {
        val measurementRepository = PostgresMeasurementRepository(database)
        val equipment = createEquipment()
        val measurementId = transaction(database) {
            MeasurementTable.insert {
                it[equipmentId] = equipment.id
                it[type] = "UNKNOWN_TYPE"
                it[unit] = MeasurementUnit.CELSIUS.name
                it[value] = 24.5
                it[createdAt] = Instant.parse("2026-08-12T06:00:00Z")
            }[MeasurementTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            measurementRepository.findByMeasurementId(measurementId)
        }
    }

    @Test
    fun `findByMeasurementId fails when stored measurement unit is unknown`() {
        val measurementRepository = PostgresMeasurementRepository(database)
        val equipment = createEquipment()
        val measurementId = transaction(database) {
            MeasurementTable.insert {
                it[equipmentId] = equipment.id
                it[type] = MeasurementType.TEMPERATURE.name
                it[unit] = "UNKNOWN_UNIT"
                it[value] = 24.5
                it[createdAt] = Instant.parse("2026-08-12T06:00:00Z")
            }[MeasurementTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            measurementRepository.findByMeasurementId(measurementId)
        }
    }

    @Test
    fun `incident repository persists and finds incidents`() {
        val incidentRepository = PostgresIncidentRepository(database)
        val facility = createFacility()
        val equipment = createEquipment(facility.id)
        val measurement = PostgresMeasurementRepository(database).create(
            equipmentId = equipment.id,
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.PERCENT,
            value = 12.0
        )

        val createdIncident = incidentRepository.create(
            facilityId = facility.id,
            equipmentId = equipment.id,
            measurementId = measurement.id,
            type = IncidentType.SMOKE_DETECTED,
            severity = IncidentSeverity.HIGH,
            measurementType = MeasurementType.SMOKE,
            measurementUnit = MeasurementUnit.PERCENT,
            value = 12.0
        )

        assertTrue(createdIncident.id > 0)
        assertEquals(facility.id, createdIncident.facilityId)
        assertEquals(equipment.id, createdIncident.equipmentId)
        assertEquals(measurement.id, createdIncident.measurementId)
        assertEquals(IncidentType.SMOKE_DETECTED, createdIncident.type)
        assertEquals(IncidentSeverity.HIGH, createdIncident.severity)
        assertEquals(IncidentStatus.OPEN, createdIncident.status)
        assertEquals(MeasurementType.SMOKE, createdIncident.measurementType)
        assertEquals(MeasurementUnit.PERCENT, createdIncident.measurementUnit)
        assertEquals(12.0, createdIncident.value)
        assertEquals(createdIncident, incidentRepository.findByIncidentId(createdIncident.id))
        assertEquals(listOf(createdIncident), incidentRepository.findByFacilityId(facility.id))
        assertEquals(listOf(createdIncident), incidentRepository.findByEquipmentId(equipment.id))
        assertEquals(listOf(createdIncident), incidentRepository.findAll())
        assertNull(incidentRepository.findByIncidentId(999_999))
    }

    @Test
    fun `incident create fails when measurement foreign key does not exist`() {
        val incidentRepository = PostgresIncidentRepository(database)
        val facility = createFacility()
        val equipment = createEquipment(facility.id)

        assertFailsWith<ExposedSQLException> {
            incidentRepository.create(
                facilityId = facility.id,
                equipmentId = equipment.id,
                measurementId = 999_999,
                type = IncidentType.SMOKE_DETECTED,
                severity = IncidentSeverity.HIGH,
                measurementType = MeasurementType.SMOKE,
                measurementUnit = MeasurementUnit.PERCENT,
                value = 12.0
            )
        }
    }

    @Test
    fun `findByIncidentId fails when stored incident status is unknown`() {
        val incidentRepository = PostgresIncidentRepository(database)
        val facility = createFacility()
        val equipment = createEquipment(facility.id)
        val measurement = PostgresMeasurementRepository(database).create(
            equipmentId = equipment.id,
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.PERCENT,
            value = 12.0
        )
        val incidentId = transaction(database) {
            IncidentTable.insert {
                it[facilityId] = facility.id
                it[equipmentId] = equipment.id
                it[measurementId] = measurement.id
                it[type] = IncidentType.SMOKE_DETECTED.name
                it[severity] = IncidentSeverity.HIGH.name
                it[status] = "UNKNOWN_STATUS"
                it[measurementType] = MeasurementType.SMOKE.name
                it[measurementUnit] = MeasurementUnit.PERCENT.name
                it[value] = 12.0
                it[createdAt] = Instant.parse("2026-08-12T06:00:00Z")
            }[IncidentTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            incidentRepository.findByIncidentId(incidentId)
        }
    }

    @Test
    fun `findByIncidentId fails when stored incident type is unknown`() {
        val incidentRepository = PostgresIncidentRepository(database)
        val facility = createFacility()
        val equipment = createEquipment(facility.id)
        val measurement = PostgresMeasurementRepository(database).create(
            equipmentId = equipment.id,
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.PERCENT,
            value = 12.0
        )
        val incidentId = transaction(database) {
            IncidentTable.insert {
                it[facilityId] = facility.id
                it[equipmentId] = equipment.id
                it[measurementId] = measurement.id
                it[type] = "UNKNOWN_TYPE"
                it[severity] = IncidentSeverity.HIGH.name
                it[status] = IncidentStatus.OPEN.name
                it[measurementType] = MeasurementType.SMOKE.name
                it[measurementUnit] = MeasurementUnit.PERCENT.name
                it[value] = 12.0
                it[createdAt] = Instant.parse("2026-08-12T06:00:00Z")
            }[IncidentTable.id]
        }

        assertFailsWith<IllegalArgumentException> {
            incidentRepository.findByIncidentId(incidentId)
        }
    }

    private fun createFacility() =
        PostgresFacilityRepository(database).create(
            facilityName = "Main Gym",
            facilityType = FacilityType.GYM
        )

    private fun createEquipment(facilityId: Long = createFacility().id) =
        PostgresEquipmentRepository(database).create(
            facilityId = facilityId,
            name = "Main ventilation",
            type = EquipmentType.VENTILATION
        )
}
