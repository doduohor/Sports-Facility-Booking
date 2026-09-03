package com.doduohor.api.mapper

import com.doduohor.api.dto.MeasurementResponse
import com.doduohor.domain.model.Measurement

fun Measurement.toResponse(): MeasurementResponse = MeasurementResponse(
    id = id.value,
    equipmentId = equipmentId.value,
    type = measurementReading.type.toString().lowercase(),
    unit = measurementReading.unit.toString().lowercase(),
    value = measurementReading.value,
    createdAt = createdAt.toString()
)
