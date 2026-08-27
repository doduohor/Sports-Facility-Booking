package com.doduohor.domain.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DomainIdsTest {
    @Test
    fun `domain ids preserve their numeric value`() {
        assertEquals(1L, FacilityId(1L).value)
        assertEquals(2L, EquipmentId(2L).value)
        assertEquals(3L, BookingId(3L).value)
        assertEquals(4L, MeasurementId(4L).value)
        assertEquals(5L, IncidentId(5L).value)
    }

    @Test
    fun `domain ids reject non-positive values`() {
        assertFailsWith<IllegalArgumentException> { FacilityId(0L) }
        assertFailsWith<IllegalArgumentException> { EquipmentId(-1L) }
        assertFailsWith<IllegalArgumentException> { BookingId(0L) }
        assertFailsWith<IllegalArgumentException> { MeasurementId(-1L) }
        assertFailsWith<IllegalArgumentException> { IncidentId(0L) }
    }
}
