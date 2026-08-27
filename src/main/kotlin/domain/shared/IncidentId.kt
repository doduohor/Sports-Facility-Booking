package com.doduohor.domain.shared

@JvmInline
value class IncidentId(val value: Long) {
    init {
        require(value > 0) { "IncidentId must be positive" }
    }
}