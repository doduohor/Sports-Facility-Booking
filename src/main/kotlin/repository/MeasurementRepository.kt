package com.doduohor.repository

import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId

interface MeasurementRepository {
    fun create(equipmentId: EquipmentId, measurementReading: MeasurementReading): Measurement
    fun findByMeasurementId(measurementId: MeasurementId): Measurement?
    fun findByEquipmentId(equipmentId: EquipmentId): List<Measurement>
    fun findAll(): List<Measurement>
}
