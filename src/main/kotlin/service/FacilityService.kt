package com.doduohor.service

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityType
import com.doduohor.repository.FacilityRepository

class FacilityService(private val facilityRepository: FacilityRepository) {
    fun createFacility(name: String, type: String): CreateFacilityResult {
        if (name.isBlank()) {
            return CreateFacilityResult.InvalidName
        }

        val facilityType = strToFacilityType(type) ?: return CreateFacilityResult.InvalidType
        val facility = facilityRepository.create(name, facilityType)

        return CreateFacilityResult.Success(facility)
    }

    fun getFacilityById(id: Long): Facility? {
        return facilityRepository.findById(id)
    }

    fun getFacilities(): List<Facility> {
        return facilityRepository.findAll()
    }

    private fun strToFacilityType(input: String): FacilityType? {
        val facilityTypes = FacilityType.entries.associateBy { it.name.lowercase() }
        return facilityTypes[input.lowercase()]
    }
}

sealed interface CreateFacilityResult {
    data class Success(val facility: Facility) : CreateFacilityResult
    data object InvalidName : CreateFacilityResult
    data object InvalidType : CreateFacilityResult
}
