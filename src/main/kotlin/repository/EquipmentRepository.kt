package com.doduohor.repository

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.service.CreateEquipmentResult

interface EquipmentRepository {
    fun create(facilityId: FacilityId, name: String, type: EquipmentType) : CreateEquipmentResult
    fun findByEquipmentId(equipmentId: EquipmentId): Equipment?
    fun findByFacilityId(facilityId: FacilityId): List<Equipment>
    fun findAll(): List<Equipment>
}