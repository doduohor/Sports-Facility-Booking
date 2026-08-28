package com.doduohor.repository.postgres

import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.Clock
import com.doduohor.infrastructure.database.postgres.MeasurementTable
import com.doduohor.repository.MeasurementRepository
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostgresMeasurementRepository(
    private val database: Database,
    private val clock: Clock
    ) : MeasurementRepository {
    override fun create(
        equipmentId: EquipmentId,
        measurementReading: MeasurementReading
    ): Measurement = transaction(database) {
        val createdAt = clock.now()
        val insertedRow = MeasurementTable.insert {
            it[MeasurementTable.equipmentId] = equipmentId.value
            it[MeasurementTable.type] = measurementReading.type.name
            it[MeasurementTable.unit] = measurementReading.unit.name
            it[MeasurementTable.value] = measurementReading.value
            it[MeasurementTable.createdAt] = createdAt
        }

        Measurement.create(
            id = MeasurementId(insertedRow[MeasurementTable.id]),
            equipmentId = equipmentId,
            measurementReading = measurementReading,
            createdAt = createdAt
        )
    }

    override fun findByMeasurementId(measurementId: MeasurementId): Measurement? = transaction(database) {
        val foundRow = MeasurementTable.selectAll()
            .where { MeasurementTable.id eq measurementId.value }
            .singleOrNull()
            ?: return@transaction null

        toMeasurement(foundRow)
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): List<Measurement> = transaction(database) {
        MeasurementTable.selectAll()
            .where { MeasurementTable.equipmentId eq equipmentId.value }
            .orderBy(MeasurementTable.id)
            .map { row -> toMeasurement(row) }
    }

    override fun findAll(): List<Measurement> = transaction(database) {
        MeasurementTable.selectAll().orderBy(MeasurementTable.id).map { row -> toMeasurement(row) }
    }

    private fun toMeasurement(row: ResultRow): Measurement =
        Measurement(
            id = MeasurementId(row[MeasurementTable.id]),
            equipmentId = EquipmentId(row[MeasurementTable.equipmentId]),
            measurementReading = MeasurementReading(
                type = MeasurementType.valueOf(row[MeasurementTable.type]),
                unit = MeasurementUnit.valueOf(row[MeasurementTable.unit]),
                value = row[MeasurementTable.value]
            ),
            createdAt = row[MeasurementTable.createdAt]
        )
}
