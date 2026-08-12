package com.doduohor.repository.postgres

import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.infrastructure.database.MeasurementTable
import com.doduohor.repository.MeasurementRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit

class PostgresMeasurementRepository(private val database: Database) : MeasurementRepository {
    override fun create(
        equipmentId: Long,
        type: MeasurementType,
        unit: MeasurementUnit,
        value: Double
    ): Measurement = transaction(database) {
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val insertedRow = MeasurementTable.insert {
            it[MeasurementTable.equipmentId] = equipmentId
            it[MeasurementTable.type] = type.name
            it[MeasurementTable.unit] = unit.name
            it[MeasurementTable.value] = value
            it[MeasurementTable.createdAt] = createdAt
        }

        Measurement(
            id = insertedRow[MeasurementTable.id],
            equipmentId = equipmentId,
            type = type,
            unit = unit,
            value = value,
            createdAt = createdAt
        )
    }

    override fun findByMeasurementId(measurementId: Long): Measurement? = transaction(database) {
        val foundRow = MeasurementTable.selectAll()
            .where { MeasurementTable.id eq measurementId }
            .singleOrNull()
            ?: return@transaction null

        toMeasurement(foundRow)
    }

    override fun findByEquipmentId(equipmentId: Long): List<Measurement> = transaction(database) {
        MeasurementTable.selectAll()
            .where { MeasurementTable.equipmentId eq equipmentId }
            .map { row -> toMeasurement(row) }
    }

    override fun findAll(): List<Measurement> = transaction(database) {
        MeasurementTable.selectAll().map { row -> toMeasurement(row) }
    }

    private fun toMeasurement(row: ResultRow): Measurement =
        Measurement(
            id = row[MeasurementTable.id],
            equipmentId = row[MeasurementTable.equipmentId],
            type = MeasurementType.valueOf(row[MeasurementTable.type]),
            unit = MeasurementUnit.valueOf(row[MeasurementTable.unit]),
            value = row[MeasurementTable.value],
            createdAt = row[MeasurementTable.createdAt]
        )
}
