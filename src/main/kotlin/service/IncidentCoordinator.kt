package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.policy.IncidentPolicy
import com.doduohor.domain.policy.IncidentPolicyResult
import com.doduohor.repository.EquipmentRepository

class IncidentCoordinator(
    private val incidentPolicy: IncidentPolicy,
    private val equipmentRepository: EquipmentRepository,
    private val incidentService: IncidentService
) {
    fun coordinate(measurement: Measurement): IncidentCoordinationResult {
        return when (val policyResult = incidentPolicy.detect(measurement)) {
            IncidentPolicyResult.NotIncident -> IncidentCoordinationResult.NoIncident
            is IncidentPolicyResult.NeedIncident -> {
                val equipment = equipmentRepository.findByEquipmentId(measurement.equipmentId)
                    ?: return IncidentCoordinationResult.EquipmentContextLost(measurement)
                when (val incidentResult = incidentService.create(
                    facilityId = equipment.facilityId.value,
                    equipmentId = equipment.id.value,
                    measurementId = measurement.id.value,
                    type = policyResult.incidentRequired.type,
                    severity = policyResult.incidentRequired.severity,
                    measurementType = measurement.measurementReading.type,
                    measurementUnit = measurement.measurementReading.unit,
                    value = measurement.measurementReading.value
                )) {
                    is IncidentServiceResult.Success -> IncidentCoordinationResult.Created(incidentResult.incident)
                    else -> IncidentCoordinationResult.IncidentCreateError(measurement, incidentResult)
                }
            }
        }
    }
}

sealed interface IncidentCoordinationResult {
    data object NoIncident : IncidentCoordinationResult
    data class Created(val incident: Incident) : IncidentCoordinationResult
    data class EquipmentContextLost(val measurement: Measurement) : IncidentCoordinationResult
    data class IncidentCreateError(
        val measurement: Measurement,
        val incidentResult: IncidentServiceResult
    ) : IncidentCoordinationResult
}
