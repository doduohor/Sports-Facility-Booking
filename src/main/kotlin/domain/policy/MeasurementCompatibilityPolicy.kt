package com.doduohor.domain.policy

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.model.ValueRange
import com.doduohor.domain.model.contains

object MeasurementCompatibilityPolicy {
    private val typeUnitMapping = mapOf<MeasurementType, MeasurementUnit>(
        MeasurementType.TEMPERATURE to MeasurementUnit.CELSIUS,
        MeasurementType.HUMIDITY to MeasurementUnit.PERCENT,
        MeasurementType.SMOKE to MeasurementUnit.PERCENT,
        MeasurementType.CO2 to MeasurementUnit.PPM,
    )

    private val measurementValueRanges = mapOf<MeasurementType, ValueRange>(
        MeasurementType.TEMPERATURE to ValueRange(-50.0, 100.0),
        MeasurementType.CO2 to ValueRange(0.0, 10000.0),
        MeasurementType.HUMIDITY to ValueRange(0.0, 100.0),
        MeasurementType.SMOKE to ValueRange(0.0, 100.0)
    )

    fun supports(equipmentType: EquipmentType, measurementReading: MeasurementReading): MeasurementCompatibilityResult{
        return when(checkTypeUnitMapping(measurementReading.type, measurementReading.unit)){
            TypeUnitMappingResult.InvalidMapping -> MeasurementCompatibilityResult.InvalidTypeUnitMapping
            TypeUnitMappingResult.NotFindType -> MeasurementCompatibilityResult.NotFindMeasurementTypeForUnit
            TypeUnitMappingResult.Success -> {
                when(checkValueRanges(measurementReading.type, measurementReading.value)){
                    MeasurementValueRangeResult.InvalidValueRange -> MeasurementCompatibilityResult.InvalidMeasurementValueRange
                    MeasurementValueRangeResult.NotFindType -> MeasurementCompatibilityResult.NotFindMeasurementTypeForValueRange
                    MeasurementValueRangeResult.Success -> {
                        if (EquipmentCapabilityPolicy.supports(equipmentType, measurementReading.type)) {
                            MeasurementCompatibilityResult.Success
                        } else {
                            MeasurementCompatibilityResult.UnsupportedType
                        }
                    }
                }
            }
        }
    }

    private fun checkTypeUnitMapping(measurementType: MeasurementType, measurementUnit: MeasurementUnit): TypeUnitMappingResult {
        val unit = typeUnitMapping[measurementType] ?: return TypeUnitMappingResult.NotFindType
        return when(unit == measurementUnit){
            true -> TypeUnitMappingResult.Success
            false -> TypeUnitMappingResult.InvalidMapping
        }
    }

    private fun checkValueRanges(measurementType: MeasurementType, value: Double): MeasurementValueRangeResult {
        val valueRange = measurementValueRanges[measurementType] ?: return MeasurementValueRangeResult.NotFindType
        return when(valueRange.contains(value)){
            true -> MeasurementValueRangeResult.Success
            false -> MeasurementValueRangeResult.InvalidValueRange 
        } 
    }

    sealed interface MeasurementCompatibilityResult{
        data object Success: MeasurementCompatibilityResult
        data object UnsupportedType: MeasurementCompatibilityResult
        data object InvalidTypeUnitMapping: MeasurementCompatibilityResult
        data object InvalidMeasurementValueRange: MeasurementCompatibilityResult
        data object NotFindMeasurementTypeForUnit: MeasurementCompatibilityResult
        data object NotFindMeasurementTypeForValueRange: MeasurementCompatibilityResult
    }

    sealed interface TypeUnitMappingResult{
        data object Success: TypeUnitMappingResult
        data object NotFindType: TypeUnitMappingResult
        data object InvalidMapping: TypeUnitMappingResult
    }

    sealed interface MeasurementValueRangeResult{
        data object Success: MeasurementValueRangeResult
        data object NotFindType: MeasurementValueRangeResult
        data object InvalidValueRange: MeasurementValueRangeResult
    }
}
