package com.doduohor.domain.booking

import com.doduohor.domain.policy.BookingPolicy
import com.doduohor.domain.shared.CustomerId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookingPolicyTest {
    @Test
    fun `customer id is valid at lower boundary`() {
        assertTrue(BookingPolicy.isValidCustomerId(CustomerId(900)))
    }

    @Test
    fun `customer id is valid at upper boundary`() {
        assertTrue(BookingPolicy.isValidCustomerId(CustomerId(1000)))
    }

    @Test
    fun `customer id below lower boundary is invalid`() {
        assertFalse(BookingPolicy.isValidCustomerId(CustomerId(899)))
    }

    @Test
    fun `customer id above upper boundary is invalid`() {
        assertFalse(BookingPolicy.isValidCustomerId(CustomerId(1001)))
    }
}
