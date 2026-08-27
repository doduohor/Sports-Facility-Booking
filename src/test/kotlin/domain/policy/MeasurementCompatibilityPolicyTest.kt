package com.doduohor.domain.policy

import com.doduohor.domain.model.EquipmentType
import com.doduohor.domain.model.MeasurementReading
import com.doduohor.domain.model.MeasurementType
import com.doduohor.domain.model.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementCompatibilityPolicyTest {
    @Test
    fun `accepts reading supported by equipment with matching unit and value`() {
        val reading = MeasurementReading(
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 20.0
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.Success,
            result
        )
    }

    @Test
    fun `rejects reading with incorrect unit`() {
        val reading = MeasurementReading(
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.PERCENT,
            value = 20.0
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidTypeUnitMapping,
            result
        )
    }

    @Test
    fun `rejects reading below allowed value range`() {
        val reading = MeasurementReading(
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = -50.1
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidMeasurementValueRange,
            result
        )
    }

    @Test
    fun `rejects reading above allowed value range`() {
        val reading = MeasurementReading(
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 100.1
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidMeasurementValueRange,
            result
        )
    }

    @Test
    fun `accepts measurement type supported by equipment`() {
        val reading = MeasurementReading(
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.PERCENT,
            value = 10.0
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.FIRE_ALARM, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.Success,
            result
        )
    }

    @Test
    fun `rejects measurement type unsupported by equipment`() {
        val reading = MeasurementReading(
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.PERCENT,
            value = 10.0
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.UnsupportedType,
            result
        )
    }

    @Test
    fun `reports invalid unit before unsupported equipment type`() {
        val reading = MeasurementReading(
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.CELSIUS,
            value = 10.0
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidTypeUnitMapping,
            result
        )
    }

    @Test
    fun `reports value outside range before unsupported equipment type`() {
        val reading = MeasurementReading(
            type = MeasurementType.SMOKE,
            unit = MeasurementUnit.PERCENT,
            value = 100.1
        )

        val result = MeasurementCompatibilityPolicy.supports(EquipmentType.HEATING, reading)

        assertEquals(
            MeasurementCompatibilityPolicy.MeasurementCompatibilityResult.InvalidMeasurementValueRange,
            result
        )
    }
}
