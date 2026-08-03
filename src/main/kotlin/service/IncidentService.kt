package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.repository.IncidentRepository

class IncidentService(
    private val facilityRepository: FacilityRepository,
    private val equipmentRepository: EquipmentRepository,
    private val incidentRepository: IncidentRepository
){
    fun create(
        facilityId: Long,
        equipmentId: Long,
        measurementId: Long,
        type: String,
        severity: String,
        measurementType: String,
        measurementUnit: String,
        value: Double,
    ): IncidentServiceResult{
        if(facilityId <= 0) return IncidentServiceResult.InvalidFacilityId
        if(equipmentId <= 0) return IncidentServiceResult.InvalidEquipmentId
        if(measurementId <= 0) return IncidentServiceResult.InvalidMeasurementId
        if(type.isBlank()) return IncidentServiceResult.InvalidType
        if(severity.isBlank()) return IncidentServiceResult.InvalidSeverity
        if(measurementType.isBlank()) return IncidentServiceResult.InvalidMeasurementType
        if(measurementUnit.isBlank()) return IncidentServiceResult.InvalidMeasurementUnit

        val incidentType = typeIsValid(type) ?: return IncidentServiceResult.InvalidType
        val incidentSeverity = severityIsValid(severity) ?: return IncidentServiceResult.InvalidSeverity
        val incidentMeasurementType = measurementTypeIsValid(measurementType) ?: return IncidentServiceResult.InvalidMeasurementType
        val incidentMeasurementUnit = measurementUnitIsValid(measurementUnit) ?: return IncidentServiceResult.InvalidMeasurementUnit
        val facility = facilityRepository.findById(facilityId) ?: return IncidentServiceResult.NotFindFacilityId
        val equipment = equipmentRepository.findByEquipmentId(equipmentId) ?: return IncidentServiceResult.NotFindEquipmentId

        if(equipment.facilityId != facility.id) return IncidentServiceResult.EquipmentDoesNotBelongToFacility

        return IncidentServiceResult.Success(
            incidentRepository.create(
                facilityId = facility.id,
                equipmentId = equipment.id,
                measurementId = measurementId,
                type = incidentType,
                severity = incidentSeverity,
                measurementType = incidentMeasurementType,
                measurementUnit = incidentMeasurementUnit,
                value = value
            )
        )
    }

    fun findByIncidentId(incidentId: Long): Incident?{
        return incidentRepository.findByIncidentId(incidentId)
    }
    
    fun findByFacilityId(facilityId: Long): FindIncidentsByFacilityIdResult{
        if(facilityId <= 0) return FindIncidentsByFacilityIdResult.InvalidFacilityId
        val facility = facilityRepository.findById(facilityId) ?: return FindIncidentsByFacilityIdResult.NotFindFacilityId
        return FindIncidentsByFacilityIdResult.Success(incidentRepository.findByFacilityId(facility.id))
    }
    
    fun findByEquipmentId(equipmentId: Long): FindIncidentsByEquipmentIdResult{
        if(equipmentId <= 0) return FindIncidentsByEquipmentIdResult.InvalidEquipmentId

        val equipment = equipmentRepository.findByEquipmentId(equipmentId) ?: return FindIncidentsByEquipmentIdResult.NotFindEquipmentId
        return FindIncidentsByEquipmentIdResult.Success(incidentRepository.findByEquipmentId(equipment.id))
    }
    fun findAll(): List<Incident>{
        return incidentRepository.findAll()
    }

    private fun typeIsValid(type: String): IncidentType?{
        return IncidentType.entries.find { it.name == type.uppercase() }
    }

    private fun severityIsValid(severity: String): IncidentSeverity?{
        return IncidentSeverity.entries.find { it.name == severity.uppercase() }
    }

    private fun measurementTypeIsValid(measurementType: String): MeasurementType?{
        return MeasurementType.entries.find { it.name ==  measurementType.uppercase()}
    }

    private fun measurementUnitIsValid(measurementUnit: String): MeasurementUnit?{
        return MeasurementUnit.entries.find { it.name == measurementUnit.uppercase() }
    }
}

sealed interface FindIncidentsByFacilityIdResult{
    data class Success(val incidents: List<Incident>): FindIncidentsByFacilityIdResult
    data object InvalidFacilityId: FindIncidentsByFacilityIdResult
    data object NotFindFacilityId: FindIncidentsByFacilityIdResult
}

sealed interface FindIncidentsByEquipmentIdResult{
    data class Success(val incidents: List<Incident>): FindIncidentsByEquipmentIdResult
    data object InvalidEquipmentId: FindIncidentsByEquipmentIdResult
    data object NotFindEquipmentId: FindIncidentsByEquipmentIdResult
}

sealed interface IncidentServiceResult{
    data class Success(val incident: Incident): IncidentServiceResult
    data object InvalidFacilityId: IncidentServiceResult
    data object InvalidEquipmentId: IncidentServiceResult
    data object InvalidMeasurementId: IncidentServiceResult
    data object InvalidType: IncidentServiceResult
    data object InvalidSeverity: IncidentServiceResult
    data object InvalidMeasurementType: IncidentServiceResult
    data object InvalidMeasurementUnit: IncidentServiceResult
    data object NotFindFacilityId: IncidentServiceResult
    data object NotFindEquipmentId: IncidentServiceResult
    data object EquipmentDoesNotBelongToFacility: IncidentServiceResult
}
