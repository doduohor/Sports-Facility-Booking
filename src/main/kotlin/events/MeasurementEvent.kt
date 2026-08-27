package com.doduohor.events

import com.doduohor.domain.model.Measurement

fun Measurement.toEventPayload(): MeasurementEventPayload{
    return MeasurementEventPayload(
        id = this.id.value,
        equipmentId = this.equipmentId.value,
        type = this.measurementReading.type.name.lowercase(),
        unit = this.measurementReading.unit.name.lowercase(),
        value = this.measurementReading.value,
        createdAt = this.createdAt.toString()
    )
}
