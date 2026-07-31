package com.doduohor.repository

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType

interface EquipmentRepository {
    fun create(facilityId: Long, name: String, type: EquipmentType) : Equipment
    fun findByEquipmentId(equipmentId: Long): Equipment?
    fun findByFacilityId(facilityId: Long): List<Equipment>
    fun findAll(): List<Equipment>
}