package com.doduohor.service

import com.doduohor.domain.model.Equipment
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.policy.MeasurementCompatibilityPolicy
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.MeasurementRepository
import com.doduohor.domain.shared.EquipmentId
import com.doduohor.domain.shared.MeasurementId

class MeasurementService(private val measurementRepository: MeasurementRepository, private val equipmentRepository: EquipmentRepository) {

    fun create(equipmentId: Long, type: MeasurementType, unit: MeasurementUnit, value: Double): CreateMeasurementResult {
        if(equipmentId <= 0) return CreateMeasurementResult.InvalidEquipmentId
        val equipmentIdTyped = EquipmentId(equipmentId)
        val equipment = equipmentRepository.findByEquipmentId(equipmentIdTyped) ?: return CreateMeasurementResult.NotFindEquipmentId
        val measurementReading = MeasurementReading(type, unit, value)
        return when(MeasurementCompatibilityPolicy.supports(equipment.type, measurementReading)){
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidMeasurementValueRange -> CreateMeasurementResult.InvalidValue
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidTypeUnitMapping -> CreateMeasurementResult.InvalidMappingTypeAndUnit
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.NotFindMeasurementTypeForUnit -> CreateMeasurementResult.NotFindMeasurementType
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.NotFindMeasurementTypeForValueRange -> CreateMeasurementResult.MeasurementRangeNotConfigured
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.Success -> CreateMeasurementResult.Success(measurementRepository.create(equipmentIdTyped, measurementReading))
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.UnsupportedType -> CreateMeasurementResult.InvalidMeasurementType
        }
    }

    fun findByMeasurementId(measurementId: Long): Measurement? {
        if (measurementId <= 0) return null
        return measurementRepository.findByMeasurementId(MeasurementId(measurementId))
    }

    fun findByEquipmentId(equipmentId: Long): FindEquipmentIdResult {
        if(equipmentId <= 0) return FindEquipmentIdResult.InvalidEquipmentId

        val equipmentIdTyped = EquipmentId(equipmentId)
        val equipment = equipmentRepository.findByEquipmentId(equipmentIdTyped) ?: return FindEquipmentIdResult.NotFindEquipmentId
        return FindEquipmentIdResult.Success(measurementRepository.findByEquipmentId(equipment.id))
    }

    fun findAll(): List<Measurement> {
        return measurementRepository.findAll()
    }

}

sealed interface CreateMeasurementResult {
    data class Success(val measurement: Measurement) : CreateMeasurementResult
    data object InvalidEquipmentId: CreateMeasurementResult
    data object NotFindEquipmentId: CreateMeasurementResult
    data object NotFindMeasurementType: CreateMeasurementResult
    data object InvalidMappingTypeAndUnit: CreateMeasurementResult
    data object NotSupportedEquipmentType: CreateMeasurementResult
    data object InvalidMeasurementType: CreateMeasurementResult
    data object InvalidValue: CreateMeasurementResult
    data object MeasurementRangeNotConfigured: CreateMeasurementResult
}

sealed interface FindEquipmentIdResult {
    data class Success(val measurements: List<Measurement>): FindEquipmentIdResult
    data object InvalidEquipmentId: FindEquipmentIdResult
    data object NotFindEquipmentId: FindEquipmentIdResult
}
