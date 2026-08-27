package com.doduohor.domain.booking

import com.doduohor.domain.model.BookingTimeInterval
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BookingTimeIntervalTest {
    @Test
    fun `creates interval when start is before end`() {
        val start = Instant.parse("2026-08-12T07:00:00Z")
        val end = Instant.parse("2026-08-12T08:00:00Z")

        val interval = BookingTimeInterval(start, end)

        assertEquals(start, interval.startTime)
        assertEquals(end, interval.endTime)
    }

    @Test
    fun `rejects interval with equal start and end`() {
        val time = Instant.parse("2026-08-12T07:00:00Z")

        assertFailsWith<IllegalArgumentException> {
            BookingTimeInterval(time, time)
        }
    }

    @Test
    fun `rejects interval when start is after end`() {
        assertFailsWith<IllegalArgumentException> {
            BookingTimeInterval(
                startTime = Instant.parse("2026-08-12T08:00:00Z"),
                endTime = Instant.parse("2026-08-12T07:00:00Z")
            )
        }
    }

    @Test
    fun `overlaps uses start inclusive and end exclusive interval semantics`() {
        val existing = bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T12:00:00Z")

        assertFalse(existing.overlaps(bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T10:00:00Z")))
        assertTrue(existing.overlaps(bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T11:00:00Z")))
        assertTrue(existing.overlaps(bookingInterval("2026-08-12T11:00:00Z", "2026-08-12T12:00:00Z")))
        assertFalse(existing.overlaps(bookingInterval("2026-08-12T12:00:00Z", "2026-08-12T13:00:00Z")))
        assertTrue(existing.overlaps(bookingInterval("2026-08-12T09:00:00Z", "2026-08-12T13:00:00Z")))
    }

    @Test
    fun `overlaps is symmetric`() {
        val first = bookingInterval("2026-08-12T10:00:00Z", "2026-08-12T12:00:00Z")
        val second = bookingInterval("2026-08-12T11:00:00Z", "2026-08-12T13:00:00Z")

        assertTrue(first.overlaps(second))
        assertTrue(second.overlaps(first))
    }

    private fun bookingInterval(startTime: String, endTime: String) =
        BookingTimeInterval(
            startTime = Instant.parse(startTime),
            endTime = Instant.parse(endTime)
        )
}
