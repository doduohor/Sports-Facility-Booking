package com.doduohor.repository

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.Clock
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.FacilityId
import com.doduohor.domain.shared.IncidentId
import com.doduohor.domain.shared.MeasurementId

class InMemoryIncidentRepository(
    private val clock: Clock
): IncidentRepository {
    private val incidents = mutableMapOf<IncidentId, Incident>()
    private var nextId = 500L

    override fun create(
        facilityId: FacilityId,
        equipmentId: EquipmentId,
        measurementId: MeasurementId,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double
    ): Incident {
        val id = IncidentId(nextId)
        nextId++

        val incident = Incident(
            id = id,
            facilityId = facilityId,
            equipmentId = equipmentId,
            measurementId = measurementId,
            type = type,
            severity = severity,
            status = IncidentStatus.OPEN,
            measurementType = measurementType,
            measurementUnit = measurementUnit,
            value = value,
            createdAt = clock.now()
        )
        incidents[id] = incident
        return incident
    }

    override fun findByIncidentId(incidentId: IncidentId): Incident? {
        return incidents[incidentId]
    }

    override fun findByFacilityId(facilityId: FacilityId): List<Incident> {
        return incidents.values.filter { it.facilityId == facilityId }
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): List<Incident> {
        return incidents.values.filter { it.equipmentId == equipmentId }
    }

    override fun findAll(): List<Incident> {
        return incidents.values.toList()
    }

}
