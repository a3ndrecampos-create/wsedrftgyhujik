package com.promoapp.notifications

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyDealSchedulerTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, 28, hour, minute, 0, 0, zone)

    @Test
    fun `before the notify hour it waits until today`() {
        assertEquals(Duration.ofHours(1), DailyDealScheduler.delayUntilNext(9, at(8)))
    }

    @Test
    fun `exactly at the notify hour it waits until tomorrow`() {
        assertEquals(Duration.ofHours(24), DailyDealScheduler.delayUntilNext(9, at(9)))
    }

    @Test
    fun `after the notify hour it waits until tomorrow`() {
        assertEquals(Duration.ofMinutes(22 * 60 + 30), DailyDealScheduler.delayUntilNext(9, at(10, 30)))
    }

    @Test
    fun `late at night it waits a few hours`() {
        assertEquals(Duration.ofHours(10), DailyDealScheduler.delayUntilNext(9, at(23)))
    }
}
