package com.doduohor.infrastructure.time

import com.doduohor.domain.shared.Clock
import java.time.Instant

class FixedClock(
    private val fixedInstant: Instant
) : Clock {
    override fun now(): Instant = fixedInstant
}
