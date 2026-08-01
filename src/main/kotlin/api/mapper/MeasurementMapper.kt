package com.doduohor.api.mapper

import com.doduohor.api.dto.MeasurementResponse
import com.doduohor.domain.model.Measurement

fun Measurement.toResponse(): MeasurementResponse = MeasurementResponse(
    id = id,
    equipmentId = equipmentId,
    type = type.toString().lowercase(),
    unit = unit.toString().lowercase(),
    value = value,
    createdAt = createdAt.toString()
)