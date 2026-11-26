package com.suiyu.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyAndDatesTest {

    @Test
    fun `parse common bill amount formats`() {
        assertEquals(123_456L, Money.parseCents("¥1,234.56"))
        assertEquals(123_456L, Money.parseCents("￥1,234.56"))
        assertEquals(-2_500L, Money.parseCents("-25.00"))
        assertEquals(2_500L, Money.parseCents("25元"))
        assertEquals(2_550L, Money.parseCents("25.5"))
        assertEquals(2_000L, Money.parseCents("20"))
        assertEquals(2_003L, Money.parseCents("20.03"))
        assertNull(Money.parseCents(""))
        assertNull(Money.parseCents("不是金额"))
        assertNull(Money.parseCents("12.3.4"))
    }

    @Test
    fun `format amount with grouping and two decimals`() {
        assertEquals("¥1,234.56", Money.format(123_456L))
        assertEquals("¥1,234", Money.format(123_400L))
        assertEquals("¥1,234.00", Money.format(123_400L, forceDecimals = true))
        assertEquals("¥0.05", Money.format(5L))
        assertEquals("-¥25", Money.format(2_500L, type = TxTypeSign.EXPENSE))
        assertEquals("-¥25.00", Money.format(2_500L, forceDecimals = true, type = TxTypeSign.EXPENSE))
        assertEquals("+¥25", Money.format(2_500L, sign = true, type = TxTypeSign.INCOME))
        assertEquals("+¥25.00", Money.format(2_500L, forceDecimals = true, sign = true, type = TxTypeSign.INCOME))
        assertEquals("12,345.60", Money.plain(1_234_560L))
    }

    @Test
    fun `percent handles zero denominator and signing`() {
        assertEquals("—", Money.percent(null))
        assertEquals("12.3%", Money.percent(0.1234))
        assertEquals("+12.3%", Money.percent(0.1234, signed = true))
        assertEquals("-8.0%", Money.percent(-0.08, signed = true))
    }

    @Test
    fun `month key arithmetic crosses years`() {
        assertEquals(MonthKey(2026, 1), MonthKey(2025, 12).plusMonths(1))
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).plusMonths(-1))
        assertEquals(MonthKey(2027, 2), MonthKey(2026, 11).plusMonths(3))
        assertEquals("2026年11月", MonthKey(2026, 11).label())
        assertTrue(MonthKey(2026, 11).contains(Dates.fromLocalDate(LocalDate.of(2026, 11, 15), 12, 0)))
        assertTrue(!MonthKey(2026, 11).contains(Dates.fromLocalDate(LocalDate.of(2026, 12, 1), 0, 0)))
    }

    @Test
    fun `day labels are relative`() {
        val now = System.currentTimeMillis()
        assertEquals("今天", Dates.dayLabel(now))
        assertEquals("昨天", Dates.dayLabel(now - 86_400_000L))
        assertEquals("前天", Dates.dayLabel(now - 2 * 86_400_000L))
    }
}
