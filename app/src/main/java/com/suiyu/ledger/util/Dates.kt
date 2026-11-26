package com.suiyu.ledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期与时间展示：账单场景下最常用「今天 / 昨天 / 前天 / 11月20日」这类相对表达。
 */
object Dates {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val fullFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    private val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun now(): Long = System.currentTimeMillis()

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalDateTime(epochMillis: Long): LocalDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()

    fun fromLocalDate(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(epochMillis: Long): Long =
        toLocalDate(epochMillis).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(epochMillis: Long): Long = startOfDay(epochMillis) + 86_400_000L

    fun time(epochMillis: Long): String = toLocalDateTime(epochMillis).format(timeFormatter)

    fun monthDay(epochMillis: Long): String = toLocalDate(epochMillis).format(monthDayFormatter)

    fun fullDate(epochMillis: Long): String = toLocalDate(epochMillis).format(fullFormatter)

    fun dateTime(epochMillis: Long): String = toLocalDateTime(epochMillis).format(dateTimeFormatter)

    fun isoDate(epochMillis: Long): String = toLocalDate(epochMillis).format(isoFormatter)

    fun parseIsoDate(text: String): Long? = runCatching {
        LocalDate.parse(text, isoFormatter).atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrNull()

    fun weekday(epochMillis: Long): String {
        val day = toLocalDate(epochMillis).dayOfWeek.value // 1 = Monday
        return weekdays[(day - 1).coerceIn(0, 6)]
    }

    /** 列表分组标题：今天 / 昨天 / 前天 / 11月20日 周四 */
    fun dayLabel(epochMillis: Long): String {
        val date = toLocalDate(epochMillis)
        val today = LocalDate.now(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(date, today)
        val base = when (days) {
            0L -> "今天"
            1L -> "昨天"
            2L -> "前天"
            else -> date.format(monthDayFormatter)
        }
        return base
    }

    /** 距离今天多少天（过去为正数） */
    fun daysAgo(epochMillis: Long): Long =
        java.time.temporal.ChronoUnit.DAYS.between(toLocalDate(epochMillis), LocalDate.now(zone))

    /** 2026年11月 */
    fun yearMonth(epochMillis: Long): String {
        val date = toLocalDate(epochMillis)
        return "${date.year}年${date.monthValue}月"
    }

    /** 一年中的第几天 */
    fun dayOfYear(epochMillis: Long): Int = toLocalDate(epochMillis).dayOfYear

    /** 该月天数 */
    fun lengthOfMonth(month: MonthKey): Int =
        LocalDate.of(month.year, month.month, 1).lengthOfMonth()

    /** 该月 1 号的星期（1 = 周一） */
    fun firstWeekdayOfMonth(month: MonthKey): Int =
        LocalDate.of(month.year, month.month, 1).dayOfWeek.value
}
