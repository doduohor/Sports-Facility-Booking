package com.doduohor.repository.postgres

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityCreationResult
import com.doduohor.domain.model.FacilityStatus
import com.doduohor.domain.model.FacilityType
import com.doduohor.infrastructure.database.postgres.FacilityTable
import com.doduohor.repository.FacilityRepository
import com.doduohor.domain.shared.FacilityId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class PostgresFacilityRepository(
    private val database: Database
    ): FacilityRepository {
    override fun create(
        facilityName: String,
        facilityType: FacilityType
    ): FacilityCreationResult<Facility> = transaction(database) {

            if (facilityName.isBlank()) {
                return@transaction FacilityCreationResult.InvalidName
            }
            val normalizedName = facilityName.trim()

            val insertedRow = FacilityTable.insert {
                it[FacilityTable.name] = normalizedName
                it[FacilityTable.type] = facilityType.name
                it[FacilityTable.status] = Facility.DEFAULT_STATUS.name
            }

            val generatedId = FacilityId(insertedRow[FacilityTable.id])

            Facility.createNew(
                id = generatedId,
                name = normalizedName,
                type = facilityType
            )
        }


    override fun save(facility: Facility) = transaction(database) {
        val updatedRows = FacilityTable.update({ FacilityTable.id eq facility.id.value }) {
            it[FacilityTable.name] = facility.name
            it[FacilityTable.type] = facility.type.name
            it[FacilityTable.status] = facility.status.name
        }
        if (updatedRows == 0) return@transaction null
        facility
    }

    override fun findById(id: FacilityId) = transaction(database)  {
        val foundRow = FacilityTable.selectAll().where { FacilityTable.id eq id.value }.singleOrNull()
            if(foundRow == null)
                return@transaction null
            Facility(
                id = FacilityId(foundRow[FacilityTable.id]),
                name = foundRow[FacilityTable.name],
                type = FacilityType.valueOf(foundRow[FacilityTable.type]),
                status = FacilityStatus.valueOf(foundRow[FacilityTable.status])
            )
    }

    override fun findAll() = transaction(database) {
        val foundedAllRows = FacilityTable.selectAll().orderBy(FacilityTable.id).toList()
        val result = foundedAllRows.map { it -> Facility(
            id = FacilityId(it[FacilityTable.id]),
            name = it[FacilityTable.name],
            type = FacilityType.valueOf(it[FacilityTable.type]),
            status = FacilityStatus.valueOf(it[FacilityTable.status])
        ) }
        result
    }
}
