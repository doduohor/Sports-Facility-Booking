package com.doduohor.domain.model

import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId
import java.time.Instant

enum class MeasurementType{
    TEMPERATURE,
    HUMIDITY,
    CO2,
    SMOKE,

    ;

    companion object {
        fun fromString(value: String): MeasurementType? =
            entries.firstOrNull { it.name.equals(value.trim(), true) }
    }
}

enum class MeasurementUnit{
    CELSIUS,
    PERCENT,
    PPM;

    companion object {
        fun fromString(value: String): MeasurementUnit? =
            entries.firstOrNull { it.name.equals(value.trim(), true) }
    }
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
    val id: MeasurementId,
    val equipmentId: EquipmentId,
    val measurementReading: MeasurementReading,
    val createdAt: Instant
) {
    companion object{
        fun create(id: MeasurementId, equipmentId: EquipmentId, measurementReading: MeasurementReading, createdAt: Instant): Measurement{
            return Measurement(
                id = id,
                equipmentId = equipmentId,
                measurementReading = measurementReading,
                createdAt = createdAt
            )
        }
    }
}
