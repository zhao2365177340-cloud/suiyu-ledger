package com.suiyu.ledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 月份键：以「年 + 月」表示统计周期，不使用字符串排序，避免跨年比较出错。
 */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {

    init {
        require(month in 1..12) { "month must be 1..12, but was $month" }
    }

    fun plusMonths(n: Int): MonthKey {
        val total = year * 12 + (month - 1) + n
        return MonthKey(Math.floorDiv(total, 12), Math.floorMod(total, 12) + 1)
    }

    /** 该月第一天的 00:00（本地时区） */
    fun startMillis(): Long =
        LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 下个月第一天 00:00，作为左闭右开区间的右端点 */
    fun endMillis(): Long = plusMonths(1).startMillis()

    fun contains(epochMillis: Long): Boolean = epochMillis >= startMillis() && epochMillis < endMillis()

    fun label(): String = "${year}年${month}月"

    fun short(): String = "${month}月"

    fun code(): String = "%04d-%02d".format(year, month)

    /** 与当前的关系：本月 / 上月 / 更早 */
    fun isCurrent(): Boolean = this == current()

    override fun compareTo(other: MonthKey): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    override fun toString(): String = code()

    companion object {
        fun current(): MonthKey = of(System.currentTimeMillis())

        fun of(epochMillis: Long): MonthKey {
            val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            return MonthKey(date.year, date.monthValue)
        }

        fun parse(code: String): MonthKey? {
            val parts = code.split("-")
            if (parts.size != 2) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            return if (month in 1..12) MonthKey(year, month) else null
        }

        fun range(start: MonthKey, end: MonthKey): List<MonthKey> {
            if (start > end) return emptyList()
            val result = ArrayList<MonthKey>()
            var cursor = start
            while (cursor <= end) {
                result += cursor
                cursor = cursor.plusMonths(1)
            }
            return result
        }
    }
}
