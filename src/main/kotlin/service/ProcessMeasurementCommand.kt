package com.doduohor.service

import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit

data class ProcessMeasurementCommand(
    val equipmentId: Long,
    val type: MeasurementType,
    val unit: MeasurementUnit,
    val value: Double
)
