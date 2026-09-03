package com.doduohor.domain.policy

import com.doduohor.domain.shared.CustomerId

object BookingPolicy {
    fun isValidCustomerId(customerId: CustomerId ): Boolean = customerId.value in 900..1000
}