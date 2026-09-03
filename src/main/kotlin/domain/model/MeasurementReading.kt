package com.doduohor.domain.model

data class MeasurementReading(
    val type: MeasurementType,
    val unit: MeasurementUnit,
    val value: Double
){
    init{
        require(!value.isNaN()) {"The value is not a number"}
        require(!value.isInfinite()) {"Value is positive or negative infinity"}
    }
}
