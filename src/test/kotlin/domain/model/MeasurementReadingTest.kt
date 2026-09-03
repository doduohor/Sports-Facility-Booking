package com.doduohor.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeasurementReadingTest {
    @Test
    fun `valid reading preserves type unit and value`() {
        val reading = MeasurementReading(
            type = MeasurementType.TEMPERATURE,
            unit = MeasurementUnit.CELSIUS,
            value = 20.0
        )

        assertEquals(MeasurementType.TEMPERATURE, reading.type)
        assertEquals(MeasurementUnit.CELSIUS, reading.unit)
        assertEquals(20.0, reading.value)
    }

    @Test
    fun `reading rejects NaN value`() {
        assertFailsWith<IllegalArgumentException> {
            MeasurementReading(
                type = MeasurementType.TEMPERATURE,
                unit = MeasurementUnit.CELSIUS,
                value = Double.NaN
            )
        }
    }

    @Test
    fun `reading rejects positive infinity value`() {
        assertFailsWith<IllegalArgumentException> {
            MeasurementReading(
                type = MeasurementType.TEMPERATURE,
                unit = MeasurementUnit.CELSIUS,
                value = Double.POSITIVE_INFINITY
            )
        }
    }

    @Test
    fun `reading rejects negative infinity value`() {
        assertFailsWith<IllegalArgumentException> {
            MeasurementReading(
                type = MeasurementType.TEMPERATURE,
                unit = MeasurementUnit.CELSIUS,
                value = Double.NEGATIVE_INFINITY
            )
        }
    }
}
