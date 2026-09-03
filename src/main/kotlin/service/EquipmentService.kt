package com.doduohor.service

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId

class EquipmentService(private val equipmentRepository: EquipmentRepository, private val facilityRepository: FacilityRepository) {
    fun create(facilityId: Long, name: String, type: EquipmentType): CreateEquipmentResult {
        if(facilityId <= 0) return CreateEquipmentResult.InvalidFacilityId
        if(name.isBlank()) return CreateEquipmentResult.InvalidName

        val facilityIdTyped = FacilityId(facilityId)
        val facilityIdSearch = facilityRepository.findById(facilityIdTyped) ?: return CreateEquipmentResult.NotFindFacilityId
        return when(val equipment = equipmentRepository.create(facilityIdSearch.id, name, type)){
            CreateEquipmentResult.InvalidFacilityId -> CreateEquipmentResult.InvalidFacilityId
            CreateEquipmentResult.InvalidName -> CreateEquipmentResult.InvalidName
            CreateEquipmentResult.NotFindFacilityId -> CreateEquipmentResult.NotFindFacilityId
            is CreateEquipmentResult.Success -> CreateEquipmentResult.Success(equipment.equipment)
        }
    }

    fun findByEquipmentId(equipmentId: Long): Equipment? {
        if (equipmentId <= 0) return null
        return equipmentRepository.findByEquipmentId(EquipmentId(equipmentId))
    }

    fun findByFacilityId(facilityId: Long): FindEquipmentsByFacilityIdResult {
        if(facilityId <= 0) return FindEquipmentsByFacilityIdResult.InvalidFacilityId

        val facilityIdTyped = FacilityId(facilityId)
        val facility = facilityRepository.findById(facilityIdTyped) ?: return FindEquipmentsByFacilityIdResult.NotFindFacilityId
        return FindEquipmentsByFacilityIdResult.Success(equipmentRepository.findByFacilityId(facility.id))
    }

    fun findAll(): List<Equipment> {
        return equipmentRepository.findAll()
    }

}

sealed interface CreateEquipmentResult {
    data class Success(val equipment: Equipment) : CreateEquipmentResult
    data object InvalidFacilityId: CreateEquipmentResult
    data object NotFindFacilityId: CreateEquipmentResult
    data object InvalidName: CreateEquipmentResult
}

sealed interface FindEquipmentsByFacilityIdResult {
    data class Success(val equipments: List<Equipment>) : FindEquipmentsByFacilityIdResult
    data object InvalidFacilityId: FindEquipmentsByFacilityIdResult
    data object NotFindFacilityId: FindEquipmentsByFacilityIdResult
}
