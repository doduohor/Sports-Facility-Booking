package com.doduohor.domain

import com.doduohor.domain.model.Facility
import com.doduohor.domain.model.FacilityActivateResult
import com.doduohor.domain.model.FacilityCreationResult
import com.doduohor.domain.model.FacilityStatus
import com.doduohor.domain.model.FacilityType
import com.doduohor.domain.shared.FacilityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FacilityTest {
    @Test
    fun `create new rejects blank name`() {
        val result = Facility.createNew(FacilityId(1), "   ", FacilityType.POOL)

        assertIs<FacilityCreationResult.InvalidName>(result)
    }

    @Test
    fun `create new trims name and applies inactive status`() {
        val result = Facility.createNew(FacilityId(1), "  Central Pool  ", FacilityType.POOL)

        val success = assertIs<FacilityCreationResult.Success<Facility>>(result)
        assertEquals("Central Pool", success.value.name)
        assertEquals(FacilityType.POOL, success.value.type)
        assertEquals(FacilityStatus.INACTIVE, success.value.status)
    }

    @Test
    fun `inactive facility can be activated`() {
        val facility = facilityWithStatus(FacilityStatus.INACTIVE)

        val result = facility.activate()

        val success = assertIs<FacilityActivateResult.Success>(result)
        assertEquals(FacilityStatus.ACTIVE, success.facility.status)
    }

    @Test
    fun `active facility reports already active when activated again`() {
        val facility = facilityWithStatus(FacilityStatus.ACTIVE)

        val result = facility.activate()

        assertIs<FacilityActivateResult.AlreadyActive>(result)
    }

    @Test
    fun `maintenance facility cannot be activated`() {
        val facility = facilityWithStatus(FacilityStatus.MAINTENANCE)

        val result = facility.activate()

        assertIs<FacilityActivateResult.InvalidStatus>(result)
    }

    @Test
    fun `only active facility can accept booking`() {
        assertTrue(facilityWithStatus(FacilityStatus.ACTIVE).canAcceptBooking())
        assertFalse(facilityWithStatus(FacilityStatus.INACTIVE).canAcceptBooking())
        assertFalse(facilityWithStatus(FacilityStatus.MAINTENANCE).canAcceptBooking())
    }

    private fun facilityWithStatus(status: FacilityStatus): Facility {
        return Facility(
            id = FacilityId(1),
            name = "Central Pool",
            type = FacilityType.POOL,
            status = status
        )
    }
}
