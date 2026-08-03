package com.doduohor.service

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository

class EquipmentService(private val equipmentRepository: EquipmentRepository, private val facilityRepository: FacilityRepository) {
    fun create(facilityId: Long, name: String, type: String): CreateEquipmentResult {
        if(facilityId <= 0) return CreateEquipmentResult.InvalidFacilityId
        if(name.isBlank()) return CreateEquipmentResult.InvalidName
        if(type.isBlank()) return CreateEquipmentResult.InvalidType

        val equipmentType = typeIsValid(type) ?: return CreateEquipmentResult.InvalidType
        val facilityIdSearch = facilityRepository.findById(facilityId) ?: return CreateEquipmentResult.NotFindFacilityId

        return CreateEquipmentResult.Success(equipmentRepository.create(facilityIdSearch.id, name, equipmentType))
    }

    fun findByEquipmentId(equipmentId: Long): Equipment? {
        return equipmentRepository.findByEquipmentId(equipmentId)
    }

    fun findByFacilityId(facilityId: Long): FindEquipmentsByFacilityIdResult {
        if(facilityId <= 0) return FindEquipmentsByFacilityIdResult.InvalidFacilityId

        val facility = facilityRepository.findById(facilityId) ?: return FindEquipmentsByFacilityIdResult.NotFindFacilityId
        return FindEquipmentsByFacilityIdResult.Success(equipmentRepository.findByFacilityId(facility.id))
    }

    fun findAll(): List<Equipment> {
        return equipmentRepository.findAll()
    }

    private fun typeIsValid(type: String): EquipmentType? {
        return EquipmentType.entries.find { it.name == type.uppercase() }
    }
}

sealed interface CreateEquipmentResult {
    data class Success(val equipment: Equipment) : CreateEquipmentResult
    data object InvalidFacilityId: CreateEquipmentResult
    data object NotFindFacilityId: CreateEquipmentResult
    data object InvalidName: CreateEquipmentResult
    data object InvalidType: CreateEquipmentResult
}

sealed interface FindEquipmentsByFacilityIdResult {
    data class Success(val equipments: List<Equipment>) : FindEquipmentsByFacilityIdResult
    data object InvalidFacilityId: FindEquipmentsByFacilityIdResult
    data object NotFindFacilityId: FindEquipmentsByFacilityIdResult
}
