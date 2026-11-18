package com.arash.daftar

import java.util.Calendar
import java.util.Locale
import java.util.UUID

object Fa {
    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    fun toEn(s: String): String = buildString(s.length) {
        for (c in s) {
            val i = FA.indexOf(c)
            val j = AR.indexOf(c)
            append(
                when {
                    i >= 0 -> '0' + i
                    j >= 0 -> '0' + j
                    else -> c
                }
            )
        }
    }

    fun toFa(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') FA[c - '0'] else c)
    }

    /** ۱۲,۳۴۵,۰۰۰ (بدون کلمه‌ی تومان) */
    fun num(v: Long): String = toFa(String.format(Locale.US, "%,d", v))

    fun toman(v: Long): String = num(v) + " تومان"

    /** فقط رقم‌ها (فارسی یا انگلیسی) را برمی‌دارد */
    fun digits(s: String): String = toEn(s).filter { it in '0'..'9' }

    /** برای مقایسه و جستجوی اسم: ی/ک عربی، فاصله‌های اضافه و نیم‌فاصله یکسان می‌شوند */
    fun norm(s: String): String = s.trim()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace('‌', ' ')
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

    /** نام برای ذخیره: فقط تمیز کردن فاصله‌ها و حروف عربی */
    fun cleanName(s: String): String = s.trim()
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace(Regex("\\s+"), " ")
}

object Jalali {
    private val months = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun monthName(m: Int) = months[m - 1]

    fun fromGregorian(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return intArrayOf(jy, jm, jd)
    }

    fun toGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy2 = jy + 1595
        var days = -355668 + 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    fun fromMillis(ms: Long): IntArray {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> {
            // اسفند کبیسه: اول فروردین سال بعد ۳۰ روز بعد از اول اسفند است
            val a = toGregorian(jy, 12, 1)
            val b = toGregorian(jy + 1, 1, 1)
            val ca = Calendar.getInstance().apply { clear(); set(a[0], a[1] - 1, a[2]) }
            val cb = Calendar.getInstance().apply { clear(); set(b[0], b[1] - 1, b[2]) }
            Math.round((cb.timeInMillis - ca.timeInMillis) / 86_400_000.0).toInt()
        }
    }

    fun toMillis(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int): Long {
        val g = toGregorian(jy, jm, jd)
        return Calendar.getInstance().apply {
            clear()
            set(g[0], g[1] - 1, g[2], hour, minute)
        }.timeInMillis
    }

    /** ۱۴۰۵/۰۸/۲۷ */
    fun date(ms: Long): String {
        val j = fromMillis(ms)
        return Fa.toFa(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]))
    }

    /** ۲۷ آبان ۱۴۰۵ */
    fun longDate(ms: Long): String {
        val j = fromMillis(ms)
        return Fa.toFa("${j[2]} ") + months[j[1] - 1] + Fa.toFa(" ${j[0]}")
    }

    fun time(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return Fa.toFa(String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
    }

    fun isToday(ms: Long): Boolean = fromMillis(ms).contentEquals(fromMillis(System.currentTimeMillis()))
}

fun newId(): String = UUID.randomUUID().toString()
