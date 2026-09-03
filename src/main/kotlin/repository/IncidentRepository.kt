package com.doduohor.repository

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentCreationResult
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.MeasurementId
import com.doduohor.domain.shared.IncidentId


interface IncidentRepository {
    fun create(
        facilityId: FacilityId,
        equipmentId: EquipmentId,
        measurementId: MeasurementId,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double,
    ): IncidentCreationResult<Incident>

    fun save(incident: Incident): Incident?

    fun findByIncidentId(incidentId: IncidentId): Incident?
    fun findByFacilityId(facilityId: FacilityId): List<Incident>
    fun findByEquipmentId(equipmentId: EquipmentId): List<Incident>
    fun findAll(): List<Incident>
}
