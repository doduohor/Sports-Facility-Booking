package com.doduohor.repository.postgres

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityPrepare
import com.doduohor.domain.model.FacilityStatus
import com.doduohor.domain.model.FacilityType
import com.doduohor.infrastructure.database.FacilityTable
import com.doduohor.repository.FacilityRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class PostgresFacilityRepository(private val database: Database): FacilityRepository {
    override fun create(
        facilityName: String,
        facilityType: FacilityType
    ): Facility = transaction(database) {

            val prepareFacility = FacilityPrepare.prepareNew(
                name = facilityName,
                type = facilityType
            )

            val insertedRow = FacilityTable.insert {
                it[FacilityTable.name] = prepareFacility.name
                it[FacilityTable.type] = prepareFacility.type.name
                it[FacilityTable.status] = Facility.DEFAULT_STATUS.name
            }

            val generatedId = insertedRow[FacilityTable.id]

            Facility.createNew(
                id = generatedId,
                name = prepareFacility.name,
                type = prepareFacility.type
            )
        }


    override fun save(facility: Facility) = transaction(database) {
        val updatedRows = FacilityTable.update({ FacilityTable.id eq facility.id }) {
            it[FacilityTable.name] = facility.name
            it[FacilityTable.type] = facility.type.name
            it[FacilityTable.status] = facility.status.name
        }
        if (updatedRows == 0) return@transaction null
        facility
    }

    override fun findById(id: Long) = transaction(database)  {
        val foundRow = FacilityTable.selectAll().where { FacilityTable.id eq id }.singleOrNull()
            if(foundRow == null)
                return@transaction null
            Facility(
                id = foundRow[FacilityTable.id],
                name = foundRow[FacilityTable.name],
                type = FacilityType.valueOf(foundRow[FacilityTable.type]),
                status = FacilityStatus.valueOf(foundRow[FacilityTable.status])
            )
    }

    override fun findAll() = transaction(database) {
        val foundedAllRows = FacilityTable.selectAll().toList()
        val result = foundedAllRows.map { it -> Facility(
            id = it[FacilityTable.id],
            name = it[FacilityTable.name],
            type = FacilityType.valueOf(it[FacilityTable.type]),
            status = FacilityStatus.valueOf(it[FacilityTable.status])
        ) }
        result
    }
}