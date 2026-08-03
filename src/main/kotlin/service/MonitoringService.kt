package com.doduohor.service

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.Measurement
import com.doduohor.repository.EquipmentRepository

class MonitoringService(
    private val measurementService: MeasurementService,
    private val incidentService: IncidentService,
    private val equipmentRepository: EquipmentRepository,
    private val incidentPolicy: IncidentPolicy
){
    fun processMeasurement(equipmentId: Long, type: String, unit: String, value: Double): MonitoringServiceResult{
        val measurementResult = measurementService.create(equipmentId, type, unit, value)
        val measurement = when(measurementResult){
            is CreateMeasurementResult.Success -> measurementResult.measurement
            else -> return MonitoringServiceResult.MeasurementCreateError(measurementResult)
        }
        return when(val incidentPolicy = incidentPolicy.detect(measurement)){
            is IncidentPolicyResult.NeedIncident -> createIncident(measurement, incidentPolicy.incidentRequired)
            IncidentPolicyResult.NotIncident -> MonitoringServiceResult.SuccessWithoutIncident(measurement)
        }
    }


    private fun createIncident(measurement: Measurement, incidentRequired: IncidentRequired): MonitoringServiceResult{
        val equipment = equipmentRepository.findByEquipmentId(measurement.equipmentId) ?:
            return MonitoringServiceResult.EquipmentContextLost(measurement)
        val incident = incidentService.create(
            facilityId = equipment.facilityId,
            equipmentId = equipment.id,
            measurementId = measurement.id,
            type = incidentRequired.type.toString(),
            severity = incidentRequired.severity.toString(),
            measurementType = measurement.type.toString(),
            measurementUnit = measurement.unit.toString(),
            value = measurement.value
        )
        when(incident){
            is IncidentServiceResult.Success -> return MonitoringServiceResult.SuccessWithIncident(measurement,incident.incident)
            else -> return MonitoringServiceResult.IncidentCreateError(measurement, incident)
        }
    }
}

sealed interface MonitoringServiceResult{
    data class SuccessWithIncident(val measurement: Measurement,val incident: Incident): MonitoringServiceResult
    data class EquipmentContextLost(val measurement: Measurement): MonitoringServiceResult
    data class SuccessWithoutIncident(val measurement: Measurement): MonitoringServiceResult
    data class IncidentCreateError(val measurement: Measurement, val incidentResult: IncidentServiceResult): MonitoringServiceResult
    data class MeasurementCreateError(val measurementResult: CreateMeasurementResult): MonitoringServiceResult
}