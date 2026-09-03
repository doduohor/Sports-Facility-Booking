package com.doduohor.api.mapper

import com.doduohor.api.dto.IncidentResponse
import com.doduohor.domain.model.Incident

fun Incident.toResponse(): IncidentResponse = IncidentResponse(
    id = id.value,
    facilityId = facilityId.value,
    equipmentId = equipmentId.value,
    measurementId = measurementId.value,
    type = type.toString().lowercase(),
    severity = severity.toString().lowercase(),
    status = status.toString().lowercase(),
    measurementType = measurementType.toString().lowercase(),
    measurementUnit = measurementUnit.toString().lowercase(),
    value = value,
    createdAt = createdAt.toString(),
    statusChangedAt = statusChangedAt.toString()
)
