package com.doduohor.service

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityActivateResult
import com.doduohor.domain.model.FacilityCreationResult
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.shared.FacilityId
import com.doduohor.repository.FacilityRepository

class FacilityService(private val facilityRepository: FacilityRepository) {
    fun createFacility(name: String, type: FacilityType): CreateFacilityResult {
        return when(val facility = facilityRepository.create(name, type)){
            is FacilityCreationResult.InvalidName -> CreateFacilityResult.InvalidName
            is FacilityCreationResult.Success -> CreateFacilityResult.Success(facility.value)
        }
    }

    fun getFacilityById(id: Long): Facility? {
        if (id <= 0) return null
        return facilityRepository.findById(FacilityId(id))
    }

    fun getFacilities(): List<Facility> {
        return facilityRepository.findAll()
    }

    fun activateFacility(id: Long): ActivateFacilityResult {
        if (id <= 0) return ActivateFacilityResult.NotFound
        val facility = facilityRepository.findById(FacilityId(id)) ?: return ActivateFacilityResult.NotFound
        return when (val facilityResult = facility.activate()) {
            FacilityActivateResult.AlreadyActive -> ActivateFacilityResult.AlreadyActive
            FacilityActivateResult.InvalidStatus -> ActivateFacilityResult.InvalidStatus
            is FacilityActivateResult.Success -> {
                val savedFacility = facilityRepository.save(facilityResult.facility)
                if (savedFacility == null) {
                    ActivateFacilityResult.NotFound
                } else {
                    ActivateFacilityResult.Success(savedFacility)
                }
            }
        }

    }
}

sealed interface CreateFacilityResult {
    data class Success(val facility: Facility) : CreateFacilityResult
    data object InvalidName: CreateFacilityResult
}

sealed interface ActivateFacilityResult {
    data class Success(val facility: Facility) : ActivateFacilityResult
    data object NotFound : ActivateFacilityResult
    data object InvalidStatus : ActivateFacilityResult
    data object AlreadyActive : ActivateFacilityResult
}
