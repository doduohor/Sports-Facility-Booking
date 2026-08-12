package com.doduohor.events

import com.doduohor.domain.model.Measurement

fun Measurement.toEventPayload(): MeasurementEventPayload{
    return MeasurementEventPayload(
        id = this.id,
        equipmentId = this.equipmentId,
        type = this.type.name.lowercase(),
        unit = this.unit.name.lowercase(),
        value = this.value,
        createdAt = this.createdAt.toString()
    )
}