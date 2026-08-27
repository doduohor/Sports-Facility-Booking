package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.FacilityRepository
import com.doduohor.repository.IncidentRepository
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.IncidentId
import com.doduohor.domain.shared.MeasurementId

class IncidentService(
    private val facilityRepository: FacilityRepository,
    private val equipmentRepository: EquipmentRepository,
    private val incidentRepository: IncidentRepository
){
    fun create(
        facilityId: Long,
        equipmentId: Long,
        measurementId: Long,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double,
    ): IncidentServiceResult{
        if(facilityId <= 0) return IncidentServiceResult.InvalidFacilityId
        if(equipmentId <= 0) return IncidentServiceResult.InvalidEquipmentId
        if(measurementId <= 0) return IncidentServiceResult.InvalidMeasurementId
        val facilityIdTyped = FacilityId(facilityId)
        val equipmentIdTyped = EquipmentId(equipmentId)
        val measurementIdTyped = MeasurementId(measurementId)
        val facility = facilityRepository.findById(facilityIdTyped) ?: return IncidentServiceResult.NotFindFacilityId
        val equipment = equipmentRepository.findByEquipmentId(equipmentIdTyped) ?: return IncidentServiceResult.NotFindEquipmentId

        if(equipment.facilityId != facility.id) return IncidentServiceResult.EquipmentDoesNotBelongToFacility

        return IncidentServiceResult.Success(
            incidentRepository.create(
                facilityId = facility.id,
                equipmentId = equipment.id,
                measurementId = measurementIdTyped,
                type = type,
                severity = severity,
                measurementType = measurementType,
                measurementUnit = measurementUnit,
                value = value
            )
        )
    }

    fun findByIncidentId(incidentId: Long): Incident?{
        if (incidentId <= 0) return null
        return incidentRepository.findByIncidentId(IncidentId(incidentId))
    }
    
    fun findByFacilityId(facilityId: Long): FindIncidentsByFacilityIdResult{
        if(facilityId <= 0) return FindIncidentsByFacilityIdResult.InvalidFacilityId
        val facility = facilityRepository.findById(FacilityId(facilityId)) ?: return FindIncidentsByFacilityIdResult.NotFindFacilityId
        return FindIncidentsByFacilityIdResult.Success(incidentRepository.findByFacilityId(facility.id))
    }
    
    fun findByEquipmentId(equipmentId: Long): FindIncidentsByEquipmentIdResult{
        if(equipmentId <= 0) return FindIncidentsByEquipmentIdResult.InvalidEquipmentId

        val equipment = equipmentRepository.findByEquipmentId(EquipmentId(equipmentId)) ?: return FindIncidentsByEquipmentIdResult.NotFindEquipmentId
        return FindIncidentsByEquipmentIdResult.Success(incidentRepository.findByEquipmentId(equipment.id))
    }
    fun findAll(): List<Incident>{
        return incidentRepository.findAll()
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
    data object NotFindFacilityId: IncidentServiceResult
    data object NotFindEquipmentId: IncidentServiceResult
    data object EquipmentDoesNotBelongToFacility: IncidentServiceResult
}
