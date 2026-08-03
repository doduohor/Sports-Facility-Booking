package com.doduohor.api.mapper

import com.doduohor.api.dto.IncidentResponse
import com.doduohor.domain.model.Incident

fun Incident.toResponse(): IncidentResponse = IncidentResponse(
    id = id,
    facilityId = facilityId,
    equipmentId = equipmentId,
    measurementId = measurementId,
    type = type.toString().lowercase(),
    severity = severity.toString().lowercase(),
    status = status.toString().lowercase(),
    measurementType = measurementType.toString().lowercase(),
    measurementUnit = measurementUnit.toString().lowercase(),
    value = value,
    createdAt = createdAt.toString()
)
