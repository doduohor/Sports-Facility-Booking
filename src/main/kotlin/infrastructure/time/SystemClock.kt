package com.doduohor.infrastructure.time

import com.doduohor.domain.shared.Clock
import java.time.Instant

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}