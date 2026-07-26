package com.doduohor.repository

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityType

class InMemoryFacilityRepository : FacilityRepository {
    private val facilities = mutableMapOf<Long, Facility>()
    private var nextId = 1L

    override fun create(name: String, type: FacilityType): Facility {
        val id = nextId
        nextId++

        val facility = Facility.createNew(id = id, name = name, type = type)

        facilities[id] = facility
        return facility
    }

    override fun findById(id: Long): Facility? = facilities[id]

    override fun findAll(): List<Facility> = facilities.values.toList()
}
