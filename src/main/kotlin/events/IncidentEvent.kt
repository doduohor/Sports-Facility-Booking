package com.doduohor.events

import com.doduohor.domain.model.Incident

fun Incident.toEventPayload(): IncidentEventPayload{
    return IncidentEventPayload(
        id = this.id,
        facilityId = this.facilityId,
        equipmentId = this.equipmentId,
        measurementId = this.measurementId,
        type = this.type.name.lowercase(),
        severity = this.severity,
        status = this.status.name.lowercase(),
        measurementType = this.measurementType.name.lowercase(),
        measurementUnit = this.measurementUnit.name.lowercase(),
        value = this.value,
        createdAt = this.createdAt.toString()
    )
}