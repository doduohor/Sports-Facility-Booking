package com.doduohor.domain.policy

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.MeasurementType
import kotlin.collections.Set

object EquipmentCapabilityPolicy {
    private val equipmentMeasurementMap = mapOf<EquipmentType, Set<MeasurementType>>(
        EquipmentType.VENTILATION to setOf(MeasurementType.TEMPERATURE, MeasurementType.HUMIDITY, MeasurementType.CO2),
        EquipmentType.HEATING to setOf(MeasurementType.TEMPERATURE),
        EquipmentType.WATER_SUPPLY to setOf(MeasurementType.TEMPERATURE),
        EquipmentType.FIRE_ALARM to setOf(MeasurementType.SMOKE, MeasurementType.TEMPERATURE)
    )
    fun supports(equipmentType: EquipmentType, measurementType: MeasurementType): Boolean =
    equipmentMeasurementMap[equipmentType]?.contains(measurementType) == true
}