package com.doduohor.service

import com.doduohor.domain.model.Measurement

class MeasurementProcessor(
    private val measurementService: MeasurementService
) {
    fun process(command: ProcessMeasurementCommand): MeasurementProcessResult =
        when (
            val result = measurementService.create(
                equipmentId = command.equipmentId,
                type = command.type,
                unit = command.unit,
                value = command.value
            )
        ) {
            is CreateMeasurementResult.Success -> MeasurementProcessResult.Success(result.measurement)
            else -> MeasurementProcessResult.Failure(result)
        }
}

sealed interface MeasurementProcessResult {
    data class Success(val measurement: Measurement) : MeasurementProcessResult
    data class Failure(val measurementResult: CreateMeasurementResult) : MeasurementProcessResult
}
