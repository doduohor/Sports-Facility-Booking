package com.doduohor.domain.shared

@JvmInline
value class BookingId(val value: Long){
    init {
        require(value > 0) {"BookingId must be positive"}
    }
}