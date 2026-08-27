package com.doduohor

import com.doduohor.domain.model.FacilityType
import com.doduohor.repository.InMemoryFacilityRepository
import com.doduohor.service.CreateFacilityResult
import com.doduohor.service.FacilityService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FacilityServiceTest {
    @Test
    fun `create facility accepts typed facility type`() {
        val service = FacilityService(InMemoryFacilityRepository())

        val result = service.createFacility("Central Pool", FacilityType.POOL)

        val success = assertIs<CreateFacilityResult.Success>(result)
        assertEquals("Central Pool", success.facility.name)
        assertEquals(FacilityType.POOL, success.facility.type)
    }

    @Test
    fun `create facility rejects blank name`() {
        val service = FacilityService(InMemoryFacilityRepository())

        val result = service.createFacility("   ", FacilityType.POOL)

        assertIs<CreateFacilityResult.InvalidName>(result)
    }
}
