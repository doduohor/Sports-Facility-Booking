package com.doduohor.domain.shared

@JvmInline
value class FacilityId(val value: Long) {
    init {
        require(value > 0) { "FacilityId must be positive" }
    }
}