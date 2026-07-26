package com.doduohor.repository

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityType

interface FacilityRepository {
    fun create(name: String, type: FacilityType): Facility
    fun findById(id: Long): Facility?
    fun findAll(): List<Facility>
}
