package com.doduohor.repository.postgres

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.infrastructure.database.postgres.EquipmentTable
import com.doduohor.repository.EquipmentRepository
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.service.CreateEquipmentResult
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class PostgresEquipmentRepository(private val database: Database) : EquipmentRepository {
    override fun create(facilityId: FacilityId, name: String, type: EquipmentType): CreateEquipmentResult = transaction(database) {
        if (name.isBlank()) return@transaction CreateEquipmentResult.InvalidName
        val normalizedName = name.trim()
        val insertedRow = EquipmentTable.insert {
            it[EquipmentTable.facilityId] = facilityId.value
            it[EquipmentTable.name] = normalizedName
            it[EquipmentTable.type] = type.name
            it[EquipmentTable.status] = Equipment.DEFAULT_STATUS.name
        }

        val equipment = Equipment(
            id = EquipmentId(insertedRow[EquipmentTable.id]),
            facilityId = facilityId,
            name = normalizedName,
            type = type,
            status = Equipment.DEFAULT_STATUS
        )
        CreateEquipmentResult.Success(equipment)
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): Equipment? = transaction(database) {
        val foundRow = EquipmentTable.selectAll()
            .where { EquipmentTable.id eq equipmentId.value }
            .singleOrNull()
            ?: return@transaction null

        toEquipment(foundRow)
    }

    override fun findByFacilityId(facilityId: FacilityId): List<Equipment> = transaction(database) {
        EquipmentTable.selectAll()
            .where { EquipmentTable.facilityId eq facilityId.value }
            .orderBy(EquipmentTable.id)
            .map { row -> toEquipment(row) }
    }

    override fun findAll(): List<Equipment> = transaction(database) {
        EquipmentTable.selectAll().orderBy(EquipmentTable.id).map { row -> toEquipment(row) }
    }

    private fun toEquipment(row: ResultRow): Equipment =
        Equipment(
            id = EquipmentId(row[EquipmentTable.id]),
            facilityId = FacilityId(row[EquipmentTable.facilityId]),
            name = row[EquipmentTable.name],
            type = EquipmentType.valueOf(row[EquipmentTable.type]),
            status = EquipmentStatus.valueOf(row[EquipmentTable.status])
        )
}
