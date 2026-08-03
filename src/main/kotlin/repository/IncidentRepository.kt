package com.doduohor.repository

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit


interface IncidentRepository {
    fun create(
        facilityId: Long,
        equipmentId: Long,
        measurementId: Long,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double,
    ): Incident

    fun findByIncidentId(incidentId: Long): Incident?
    fun findByFacilityId(facilityId: Long): List<Incident>
    fun findByEquipmentId(equipmentId: Long): List<Incident>
    fun findAll(): List<Incident>
}