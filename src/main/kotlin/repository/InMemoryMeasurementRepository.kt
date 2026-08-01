package com.doduohor.repository

import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit

class InMemoryMeasurementRepository : MeasurementRepository{
    private val measurements = mutableMapOf<Long, Measurement>()
    private var nextId = 400L

    override fun create(
        equipmentId: Long,
        type: MeasurementType,
        unit: MeasurementUnit,
        value: Double
    ): Measurement {
        val id = nextId
        nextId++

        val measurement = Measurement.create(
            id = id,
            equipmentId = equipmentId,
            type = type,
            unit = unit,
            value = value
        )
        measurements[id] = measurement
        return measurement
    }

    override fun findByMeasurementId(measurementId: Long): Measurement? {
        return measurements[measurementId]
    }

    override fun findByEquipmentId(equipmentId: Long): List<Measurement> {
        return measurements.values.filter { it.equipmentId == equipmentId }
    }

    override fun findAll(): List<Measurement> {
        return measurements.values.toList()
    }

}
