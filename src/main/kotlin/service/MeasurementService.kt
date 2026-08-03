package com.doduohor.service

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.Measurement
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.model.ValueRange
import com.doduohor.domain.model.contains
import com.doduohor.repository.EquipmentRepository
import com.doduohor.repository.MeasurementRepository

class MeasurementService(private val measurementRepository: MeasurementRepository, private val equipmentRepository: EquipmentRepository) {
    private val typeUnitMapping = mapOf<MeasurementType, MeasurementUnit>(
        MeasurementType.TEMPERATURE to MeasurementUnit.CELSIUS,
        MeasurementType.HUMIDITY to MeasurementUnit.PERCENT,
        MeasurementType.SMOKE to MeasurementUnit.PERCENT,
        MeasurementType.CO2 to MeasurementUnit.PPM,
    )

    private val equipmentMeasurementMap = mapOf<EquipmentType, Set<MeasurementType>>(
        EquipmentType.VENTILATION to setOf(MeasurementType.TEMPERATURE, MeasurementType.HUMIDITY, MeasurementType.CO2),
        EquipmentType.HEATING to setOf(MeasurementType.TEMPERATURE),
        EquipmentType.WATER_SUPPLY to setOf(MeasurementType.TEMPERATURE),
        EquipmentType.FIRE_ALARM to setOf(MeasurementType.SMOKE, MeasurementType.TEMPERATURE)
    )

    private val measurementValueRanges = mapOf<MeasurementType, ValueRange>(
        MeasurementType.TEMPERATURE to ValueRange(-50.0, 100.0),
        MeasurementType.CO2 to ValueRange(0.0, 10000.0),
        MeasurementType.HUMIDITY to ValueRange(0.0, 100.0),
        MeasurementType.SMOKE to ValueRange(0.0, 100.0)
    )

    fun create(equipmentId: Long, type: String, unit: String, value: Double): CreateMeasurementResult {
        if(equipmentId <= 0) return CreateMeasurementResult.InvalidEquipmentId
        if(type.isBlank()) return CreateMeasurementResult.InvalidType
        if(unit.isBlank()) return CreateMeasurementResult.InvalidUnit

        val measurementType = typeIsValid(type) ?: return CreateMeasurementResult.InvalidType
        val measurementUnit = unitIsValid(unit) ?: return CreateMeasurementResult.InvalidUnit
        val measurementValueRange = measurementValueRanges[measurementType] ?: return CreateMeasurementResult.MeasurementRangeNotConfigured
        val equipment = equipmentRepository.findByEquipmentId(equipmentId) ?: return CreateMeasurementResult.NotFindEquipmentId
        val approveMeasurementType = equipmentMeasurementMap[equipment.type] ?: return CreateMeasurementResult.NotSupportedEquipmentType

        if(!measurementValueRange.contains(value)) return CreateMeasurementResult.InvalidValue
        if(typeUnitMapping[measurementType] != measurementUnit) return CreateMeasurementResult.InvalidMappingTypeAndUnit
        if(measurementType !in approveMeasurementType) return CreateMeasurementResult.InvalidMeasurementType

        return CreateMeasurementResult.Success(measurementRepository.create(equipmentId, measurementType, measurementUnit, value))
    }

    fun findByMeasurementId(measurementId: Long): Measurement? {
        return measurementRepository.findByMeasurementId(measurementId)
    }

    fun findByEquipmentId(equipmentId: Long): FindEquipmentIdResult {
        if(equipmentId <= 0) return FindEquipmentIdResult.InvalidEquipmentId

        val equipment = equipmentRepository.findByEquipmentId(equipmentId) ?: return FindEquipmentIdResult.NotFindEquipmentId
        return FindEquipmentIdResult.Success(measurementRepository.findByEquipmentId(equipment.id))
    }

    fun findAll(): List<Measurement> {
        return measurementRepository.findAll()
    }

    private fun typeIsValid(type: String): MeasurementType? {
        return MeasurementType.entries.find { it.name == type.uppercase() }
    }

    private fun unitIsValid(unit: String): MeasurementUnit? {
        return MeasurementUnit.entries.find { it.name == unit.uppercase() }
    }
}

sealed interface CreateMeasurementResult {
    data class Success(val measurement: Measurement) : CreateMeasurementResult
    data object InvalidEquipmentId: CreateMeasurementResult
    data object NotFindEquipmentId: CreateMeasurementResult
    data object InvalidMappingTypeAndUnit: CreateMeasurementResult
    data object NotSupportedEquipmentType: CreateMeasurementResult
    data object InvalidMeasurementType: CreateMeasurementResult
    data object InvalidType: CreateMeasurementResult
    data object InvalidUnit: CreateMeasurementResult
    data object InvalidValue: CreateMeasurementResult
    data object MeasurementRangeNotConfigured: CreateMeasurementResult
}

sealed interface FindEquipmentIdResult {
    data class Success(val measurements: List<Measurement>): FindEquipmentIdResult
    data object InvalidEquipmentId: FindEquipmentIdResult
    data object NotFindEquipmentId: FindEquipmentIdResult
}
