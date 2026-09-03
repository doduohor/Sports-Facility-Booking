package com.doduohor.domain.shared

@JvmInline
value class CustomerId(val value: Int){
    init{
        require(value > 0) { "CustomerId must be positive" }
    }
}
