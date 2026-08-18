package com.doduohor.repository.postgres

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.infrastructure.database.postgres.EquipmentTable
import com.doduohor.repository.EquipmentRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostgresEquipmentRepository(private val database: Database) : EquipmentRepository {
    override fun create(facilityId: Long, name: String, type: EquipmentType): Equipment = transaction(database) {
        val insertedRow = EquipmentTable.insert {
            it[EquipmentTable.facilityId] = facilityId
            it[EquipmentTable.name] = name
            it[EquipmentTable.type] = type.name
            it[EquipmentTable.status] = Equipment.DEFAULT_STATUS.name
        }

        Equipment.create(
            id = insertedRow[EquipmentTable.id],
            facilityId = facilityId,
            name = name,
            type = type,
            status = Equipment.DEFAULT_STATUS
        )
    }

    override fun findByEquipmentId(equipmentId: Long): Equipment? = transaction(database) {
        val foundRow = EquipmentTable.selectAll()
            .where { EquipmentTable.id eq equipmentId }
            .singleOrNull()
            ?: return@transaction null

        toEquipment(foundRow)
    }

    override fun findByFacilityId(facilityId: Long): List<Equipment> = transaction(database) {
        EquipmentTable.selectAll()
            .where { EquipmentTable.facilityId eq facilityId }
            .map { row -> toEquipment(row) }
    }

    override fun findAll(): List<Equipment> = transaction(database) {
        EquipmentTable.selectAll().map { row -> toEquipment(row) }
    }

    private fun toEquipment(row: ResultRow): Equipment =
        Equipment(
            id = row[EquipmentTable.id],
            facilityId = row[EquipmentTable.facilityId],
            name = row[EquipmentTable.name],
            type = EquipmentType.valueOf(row[EquipmentTable.type]),
            status = EquipmentStatus.valueOf(row[EquipmentTable.status])
        )
}
