package com.doduohor.repository

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentStatus
import com.doduohor.domain.model.EquipmentType

class InMemoryEquipmentRepository: EquipmentRepository {
    private val equipments = mutableMapOf<Long, Equipment>()
    private var nextId = 200L

    override fun create(facilityId: Long, name: String, type: EquipmentType): Equipment {
        val id = nextId
        nextId++

        val equipment = Equipment.create(
            id = id,
            facilityId = facilityId,
            name = name,
            type = type,
            status = EquipmentStatus.DISABLED
        )

        equipments[id] = equipment

        return equipment
    }

    override fun findAll(): List<Equipment> {
        return equipments.values.toList()
    }

    override fun findByEquipmentId(equipmentId: Long): Equipment? {
        return equipments[equipmentId]
    }

    override fun findByFacilityId(facilityId: Long): List<Equipment> {
        return equipments.values.filter { it.facilityId == facilityId }
    }
}