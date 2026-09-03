package com.doduohor.domain.shared

@JvmInline
value class MeasurementId(val value: Long) {
    init {
        require(value > 0) { "MeasurementId must be positive" }
    }
}