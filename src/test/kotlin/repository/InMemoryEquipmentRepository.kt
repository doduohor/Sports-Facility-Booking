package com.doduohor.repository

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentCreateResult
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.service.CreateEquipmentResult

class InMemoryEquipmentRepository: EquipmentRepository {
    private val equipments = mutableMapOf<EquipmentId, Equipment>()
    private var nextId = 200L

    override fun create(facilityId: FacilityId, name: String, type: EquipmentType): CreateEquipmentResult {
        val id = EquipmentId(nextId)
        nextId++

        val result = Equipment.createNew(
            id = id,
            facilityId = facilityId,
            name = name,
            type = type
        )

        val equipment = when (result) {
            EquipmentCreateResult.InvalidName -> return CreateEquipmentResult.InvalidName
            is EquipmentCreateResult.Success -> result.equipment
        }

        equipments[id] = equipment

        return CreateEquipmentResult.Success(equipment)
    }

    override fun findAll(): List<Equipment> {
        return equipments.values.toList()
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): Equipment? {
        return equipments[equipmentId]
    }

    override fun findByFacilityId(facilityId: FacilityId): List<Equipment> {
        return equipments.values.filter { it.facilityId == facilityId }
    }
}
