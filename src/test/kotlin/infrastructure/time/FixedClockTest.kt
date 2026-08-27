package com.doduohor.infrastructure.time

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FixedClockTest {
    @Test
    fun `now always returns configured instant`() {
        val instant = Instant.parse("2026-08-20T12:00:00Z")
        val clock = FixedClock(instant)

        assertEquals(instant, clock.now())
        assertEquals(instant, clock.now())
    }
}
