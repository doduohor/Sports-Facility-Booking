package com.doduohor.repository

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityCreationResult
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.shared.FacilityId

interface FacilityRepository {
    fun create(facilityName: String, facilityType: FacilityType): FacilityCreationResult<Facility>
    fun save(facility: Facility): Facility?
    fun findById(id: FacilityId): Facility?
    fun findAll(): List<Facility>
}
