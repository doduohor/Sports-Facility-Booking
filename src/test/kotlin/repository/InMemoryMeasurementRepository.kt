package com.doduohor.repository

import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.shared.Clock
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId

class InMemoryMeasurementRepository(
    private val clock: Clock
) : MeasurementRepository{
    private val measurements = mutableMapOf<MeasurementId, Measurement>()
    private var nextId = 400L

    override fun create(
        equipmentId: EquipmentId,
        measurementReading: MeasurementReading
    ): Measurement {
        val id = MeasurementId(nextId)
        nextId++

        val measurement = Measurement.create(
            id = id,
            equipmentId = equipmentId,
            measurementReading = measurementReading,
            createdAt = clock.now()
        )
        measurements[id] = measurement
        return measurement
    }

    override fun findByMeasurementId(measurementId: MeasurementId): Measurement? {
        return measurements[measurementId]
    }

    override fun findByEquipmentId(equipmentId: EquipmentId): List<Measurement> {
        return measurements.values.filter { it.equipmentId == equipmentId }
    }

    override fun findAll(): List<Measurement> {
        return measurements.values.toList()
    }

}
