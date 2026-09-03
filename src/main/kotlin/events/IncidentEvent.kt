package com.doduohor.events

import com.doduohor.domain.model.Incident

fun Incident.toEventPayload(): IncidentEventPayload{
    return IncidentEventPayload(
        id = this.id.value,
        facilityId = this.facilityId.value,
        equipmentId = this.equipmentId.value,
        measurementId = this.measurementId.value,
        type = this.type.name.lowercase(),
        severity = this.severity,
        status = this.status.name.lowercase(),
        measurementType = this.measurementType.name.lowercase(),
        measurementUnit = this.measurementUnit.name.lowercase(),
        value = this.value,
        createdAt = this.createdAt.toString(),
        statusChangedAt = this.statusChangedAt.toString()
    )
}
