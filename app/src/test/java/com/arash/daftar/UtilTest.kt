package com.arash.daftar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    @Test
    fun jalaliRoundTrip() {
        val j = Jalali.fromGregorian(2026, 11, 18)
        assertEquals(listOf(1405, 8, 27), j.toList())
        assertEquals(listOf(2026, 11, 18), Jalali.toGregorian(1405, 8, 27).toList())
        assertEquals(31, Jalali.monthLength(1405, 1))
        assertEquals(30, Jalali.monthLength(1403, 12)) // ۱۴۰۳ کبیسه
        assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test
    fun namesMatchRegardlessOfArabicLetters() {
        assertEquals(Fa.norm("علي  كريمي"), Fa.norm(" علی کریمی"))
        assertEquals("12500", Fa.digits("۱۲,۵۰۰"))
        assertEquals("۱,۲۵۰,۰۰۰ تومان", Fa.toman(1_250_000))
    }

    @Test
    fun sorting() {
        val a = CustomerSummary("1", "بهاره", 500, 0, 100, 300)
        val b = CustomerSummary("2", "آرزو", 900, 100, 50, 200)
        val c = CustomerSummary("3", "پریسا", 0, 50, null, null)
        assertEquals(listOf("2", "1", "3"), sortCustomers(listOf(a, b, c), SortBy.AMOUNT, true).map { it.id })
        assertEquals(listOf("2", "1", "3"), sortCustomers(listOf(a, b, c), SortBy.FIRST, false).map { it.id })
        assertEquals(listOf("1", "2", "3"), sortCustomers(listOf(a, b, c), SortBy.LAST, true).map { it.id })
        assertEquals(listOf("2", "1", "3"), sortCustomers(listOf(a, b, c), SortBy.NAME, false).map { it.id })
        assertTrue(c.balance < 0)
    }
}
