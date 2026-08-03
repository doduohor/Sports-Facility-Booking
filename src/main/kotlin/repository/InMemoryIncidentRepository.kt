package com.doduohor.repository

import com.doduohor.domain.model.Incident
import com.doduohor.domain.model.IncidentSeverity
import com.doduohor.domain.model.IncidentStatus
import com.doduohor.domain.model.IncidentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import java.time.Instant

class InMemoryIncidentRepository: IncidentRepository {
    private val incidents = mutableMapOf<Long, Incident>()
    private var nextId = 500L

    override fun create(
        facilityId: Long,
        equipmentId: Long,
        measurementId: Long,
        type: IncidentType,
        severity: IncidentSeverity,
        measurementType: MeasurementType,
        measurementUnit: MeasurementUnit,
        value: Double
    ): Incident {
        val id = nextId
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
            createdAt = Instant.now()
        )
        incidents[id] = incident
        return incident
    }

    override fun findByIncidentId(incidentId: Long): Incident? {
        return incidents[incidentId]
    }

    override fun findByFacilityId(facilityId: Long): List<Incident> {
        return incidents.values.filter { it.facilityId == facilityId }
    }

    override fun findByEquipmentId(equipmentId: Long): List<Incident> {
        return incidents.values.filter { it.equipmentId == equipmentId }
    }

    override fun findAll(): List<Incident> {
        return incidents.values.toList()
    }

}