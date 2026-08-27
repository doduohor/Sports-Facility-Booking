package com.doduohor.repository

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityCreationResult
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.shared.FacilityId

class InMemoryFacilityRepository : FacilityRepository {
    private val facilities = mutableMapOf<FacilityId, Facility>()
    private var nextId = 1L

    override fun create(facilityName: String, facilityType: FacilityType): FacilityCreationResult<Facility> {
        val id = FacilityId(nextId)
        nextId++

        return when (val result = Facility.createNew(id = id, name = facilityName, type = facilityType)) {
            FacilityCreationResult.InvalidName -> result
            is FacilityCreationResult.Success -> {
                facilities[id] = result.value
                result
            }
        }
    }

    override fun save(facility: Facility): Facility {
        facilities[facility.id] = facility
        return facility
    }

    override fun findById(id: FacilityId): Facility? = facilities[id]

    override fun findAll(): List<Facility> = facilities.values.toList()
}
