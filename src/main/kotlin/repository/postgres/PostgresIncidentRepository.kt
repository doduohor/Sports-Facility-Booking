package com.doduohor.repository.postgres

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.infrastructure.database.IncidentTable
import com.doduohor.repository.IncidentRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresIncidentRepository(private val database: Database) : IncidentRepository {
    override fun create(
        facilityId: Long,
        equipmentId: Long,
        measurementId: Long,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double
    ): Incident = transaction(database) {
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val insertedRow = IncidentTable.insert {
            it[IncidentTable.facilityId] = facilityId
            it[IncidentTable.equipmentId] = equipmentId
            it[IncidentTable.measurementId] = measurementId
            it[IncidentTable.type] = type.name
            it[IncidentTable.severity] = severity.name
            it[IncidentTable.status] = IncidentStatus.OPEN.name
            it[IncidentTable.measurementType] = measurementType.name
            it[IncidentTable.measurementUnit] = measurementUnit.name
            it[IncidentTable.value] = value
            it[IncidentTable.createdAt] = createdAt
        }

        Incident(
            id = insertedRow[IncidentTable.id],
            facilityId = facilityId,
            equipmentId = equipmentId,
            measurementId = measurementId,
            type = type,
            severity = severity,
            status = IncidentStatus.OPEN,
            measurementType = measurementType,
            measurementUnit = measurementUnit,
            value = value,
            createdAt = createdAt
        )
    }

    override fun findByIncidentId(incidentId: Long): Incident? = transaction(database) {
        val foundRow = IncidentTable.selectAll()
            .where { IncidentTable.id eq incidentId }
            .singleOrNull()
            ?: return@transaction null

        toIncident(foundRow)
    }

    override fun findByFacilityId(facilityId: Long): List<Incident> = transaction(database) {
        IncidentTable.selectAll()
            .where { IncidentTable.facilityId eq facilityId }
            .map { row -> toIncident(row) }
    }

    override fun findByEquipmentId(equipmentId: Long): List<Incident> = transaction(database) {
        IncidentTable.selectAll()
            .where { IncidentTable.equipmentId eq equipmentId }
            .map { row -> toIncident(row) }
    }

    override fun findAll(): List<Incident> = transaction(database) {
        IncidentTable.selectAll().map { row -> toIncident(row) }
    }

    private fun toIncident(row: ResultRow): Incident =
        Incident(
            id = row[IncidentTable.id],
            facilityId = row[IncidentTable.facilityId],
            equipmentId = row[IncidentTable.equipmentId],
            measurementId = row[IncidentTable.measurementId],
            type = IncidentType.valueOf(row[IncidentTable.type]),
            severity = IncidentSeverity.valueOf(row[IncidentTable.severity]),
            status = IncidentStatus.valueOf(row[IncidentTable.status]),
            measurementType = MeasurementType.valueOf(row[IncidentTable.measurementType]),
            measurementUnit = MeasurementUnit.valueOf(row[IncidentTable.measurementUnit]),
            value = row[IncidentTable.value],
            createdAt = row[IncidentTable.createdAt]
        )
}
