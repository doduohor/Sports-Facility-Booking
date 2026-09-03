package com.doduohor.service

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import com.doduohor.domain.shared.FacilityId
import com.doduohor.infrastructure.time.FixedClock
import com.doduohor.repository.InMemoryEquipmentRepository
import com.doduohor.repository.InMemoryMeasurementRepository
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class MeasurementProcessorTest {
    @Test
    fun `process delegates typed command and returns created measurement`() {
        val equipmentRepository = InMemoryEquipmentRepository()
        val equipment = assertIs<CreateEquipmentResult.Success>(
            equipmentRepository.create(FacilityId(1L), "Boiler", EquipmentType.HEATING)
        ).equipment
        val measurementRepository = InMemoryMeasurementRepository(FixedClock(Instant.parse("2026-09-03T10:00:00Z")))
        val processor = MeasurementProcessor(MeasurementService(measurementRepository, equipmentRepository))
        val command = ProcessMeasurementCommand(
            equipmentId = equipment.id.value,
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 24.5
        )

        val result = processor.process(command)

        val success = assertIs<MeasurementProcessResult.Success>(result)
        assertEquals(command.equipmentId, success.measurement.equipmentId.value)
        assertEquals(command.type, success.measurement.measurementReading.type)
        assertEquals(command.unit, success.measurement.measurementReading.unit)
        assertEquals(command.value, success.measurement.measurementReading.value)
        assertEquals(listOf(success.measurement), measurementRepository.findAll())
    }

    @Test
    fun `process preserves measurement service failure contract`() {
        val processor = MeasurementProcessor(
            MeasurementService(
                InMemoryMeasurementRepository(FixedClock(Instant.parse("2026-09-03T10:00:00Z"))),
                InMemoryEquipmentRepository()
            )
        )
        val command = ProcessMeasurementCommand(
            equipmentId = 0L,
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 24.5
        )

        val result = processor.process(command)

        val failure = assertIs<MeasurementProcessResult.Failure>(result)
        assertSame(CreateMeasurementResult.InvalidEquipmentId, failure.measurementResult)
    }
}
