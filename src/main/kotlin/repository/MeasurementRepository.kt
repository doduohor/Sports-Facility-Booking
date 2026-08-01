package com.doduohor.repository

import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit

interface MeasurementRepository {
    fun create(equipmentId: Long, type: MeasurementType, unit: MeasurementUnit, value: Double): Measurement
    fun findByMeasurementId(measurementId: Long): Measurement?
    fun findByEquipmentId(equipmentId: Long): List<Measurement>
    fun findAll(): List<Measurement>
}