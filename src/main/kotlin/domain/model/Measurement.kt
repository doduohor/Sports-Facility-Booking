package com.doduohor.domain.model

import java.time.Instant

enum class MeasurementType{
    TEMPERATURE,
    HUMIDITY,
    CO2,
    SMOKE,
}

enum class MeasurementUnit{
    CELSIUS,
    PERCENT,
    PPM
}


data class ValueRange(
    val min: Double,
    val max: Double
){
    init {
        require(min < max) { "Incorrect range" }
    }
}

fun ValueRange.contains(value: Double): Boolean = value in min..max


data class Measurement(
    val id: Long,
    val equipmentId: Long,
    val type: MeasurementType,
    val unit: MeasurementUnit,
    val value: Double,
    val createdAt: Instant
) {
    companion object{
        fun create(id: Long, equipmentId: Long, type: MeasurementType, unit: MeasurementUnit, value: Double): Measurement{
            return Measurement(
                id = id,
                equipmentId = equipmentId,
                type = type,
                unit = unit,
                value = value,
                createdAt = Instant.now()
            )
        }
    }
}
