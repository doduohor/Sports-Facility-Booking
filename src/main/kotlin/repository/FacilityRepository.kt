package com.doduohor.repository

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityType

interface FacilityRepository {
    fun create(facilityName: String, facilityType: FacilityType): Facility
    fun save(facility: Facility): Facility?
    fun findById(id: Long): Facility?
    fun findAll(): List<Facility>
}
