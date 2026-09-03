package com.doduohor.repository.postgres

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentCreationResult
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.Clock
import com.doduohor.infrastructure.database.postgres.IncidentTable
import com.doduohor.repository.IncidentRepository
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.IncidentId
import com.doduohor.domain.shared.MeasurementId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.temporal.ChronoUnit

class PostgresIncidentRepository(
    private val database: Database,
    private val clock: Clock
) : IncidentRepository {
    override fun create(
        facilityId: FacilityId,
        equipmentId: EquipmentId,
        measurementId: MeasurementId,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double
    ): IncidentCreationResult<Incident> = transaction(database) {
        if (!value.isFinite()) return@transaction IncidentCreationResult.InvalidValue
        val createdAt = clock.now().truncatedTo(ChronoUnit.MICROS)
        val insertedRow = IncidentTable.insert {
            it[IncidentTable.facilityId] = facilityId.value
            it[IncidentTable.equipmentId] = equipmentId.value
            it[IncidentTable.measurementId] = measurementId.value
            it[IncidentTable.type] = type.name
            it[IncidentTable.severity] = severity.name
            it[IncidentTable.status] = IncidentStatus.OPEN.name
            it[IncidentTable.measurementType] = measurementType.name
            it[IncidentTable.measurementUnit] = measurementUnit.name
            it[IncidentTable.value] = value
            it[IncidentTable.createdAt] = createdAt
            it[IncidentTable.statusChangedAt] = createdAt
        }

        Incident.createNew(
            incidentId = IncidentId(insertedRow[IncidentTable.id]),
            facilityId = facilityId,
            equipmentId = equipmentId,
            measurementId = measurementId,
            type = type,
            severity = severity,
            measurementType = measurementType,
            measurementUnit = measurementUnit,
            value = value,
            createdAt = createdAt
        )
    }

    override fun save(incident: Incident): Incident?  = transaction(database) {
        val updatedRows = IncidentTable.update( { IncidentTable.id eq incident.id.value } ){
            it[IncidentTable.status] = incident.status.name
            it[IncidentTable.statusChangedAt] = incident.statusChangedAt
        }
        if(updatedRows == 0) return@transaction null
        incident
    }

    override fun findByIncidentId(incidentId: IncidentId): Incident? = transaction(database) {
        val foundRow = IncidentTable.selectAll()
            .where { IncidentTable.id eq incidentId.value }
            .singleOrNull()
            ?: return@transaction null

        toIncident(foundRow)
    }

    override fun findByFacilityId(facilityId: FacilityId): List<Incident> = transaction(database) {
        IncidentTable.selectAll()
            .where { IncidentTable.facilityId eq facilityId.value }
            .orderBy(IncidentTable.id)
            .map { row -> toIncident(row) }
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): List<Incident> = transaction(database) {
        IncidentTable.selectAll()
            .where { IncidentTable.equipmentId eq equipmentId.value }
            .orderBy(IncidentTable.id)
            .map { row -> toIncident(row) }
    }

    override fun findAll(): List<Incident> = transaction(database) {
        IncidentTable.selectAll().orderBy(IncidentTable.id).map { row -> toIncident(row) }
    }



    private fun toIncident(row: ResultRow): Incident =
        Incident.restore(
            id = IncidentId(row[IncidentTable.id]),
            facilityId = FacilityId(row[IncidentTable.facilityId]),
            equipmentId = EquipmentId(row[IncidentTable.equipmentId]),
            measurementId = MeasurementId(row[IncidentTable.measurementId]),
            type = IncidentType.valueOf(row[IncidentTable.type]),
            severity = IncidentSeverity.valueOf(row[IncidentTable.severity]),
            status = IncidentStatus.valueOf(row[IncidentTable.status]),
            measurementType = MeasurementType.valueOf(row[IncidentTable.measurementType]),
            measurementUnit = MeasurementUnit.valueOf(row[IncidentTable.measurementUnit]),
            value = row[IncidentTable.value],
            createdAt = row[IncidentTable.createdAt],
            statusChangedAt =  row[IncidentTable.statusChangedAt]
        )
}
