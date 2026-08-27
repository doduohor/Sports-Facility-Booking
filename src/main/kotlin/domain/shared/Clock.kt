package com.doduohor.domain.shared

import java.time.Instant

interface Clock {
    fun now(): Instant
}