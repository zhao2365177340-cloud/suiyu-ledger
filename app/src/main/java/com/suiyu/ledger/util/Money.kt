package com.suiyu.ledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 金额格式化与解析。所有金额以「分」为单位传递，展示时再格式化。
 */
object Money {

    private val decimalFormatter = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale.CHINA))

    /** 例：1234567 分 → "¥12,345.67"；[sign] 为 true 时支出加 "-" */
    fun format(
        cents: Long,
        symbol: Boolean = true,
        forceDecimals: Boolean = false,
        sign: Boolean = false,
        type: TxTypeSign = TxTypeSign.NONE,
    ): String {
        val negative = cents < 0
        val absCents = abs(cents)
        val yuan = absCents / 100
        val fen = absCents % 100
        val grouped = groupDigits(yuan)
        val body = when {
            forceDecimals -> "$grouped.${fen.toString().padStart(2, '0')}"
            fen != 0L -> "$grouped.${fen.toString().padStart(2, '0')}"
            else -> grouped
        }
        val prefix = when {
            negative -> "-"
            sign || type == TxTypeSign.EXPENSE -> if (type == TxTypeSign.EXPENSE) "-" else "+"
            else -> ""
        }
        return prefix + (if (symbol) "¥" else "") + body
    }

    /** 无符号金额，用于输入框回填 / 统计表 */
    fun plain(cents: Long): String {
        val absCents = abs(cents)
        val yuan = absCents / 100
        val fen = absCents % 100
        return if (fen == 0L) groupDigits(yuan) else "${groupDigits(yuan)}.${fen.toString().padStart(2, '0')}"
    }

    /** 紧凑金额：12345 元 → "1.2万"；仅用于图表轴标签 */
    fun compact(cents: Long): String {
        val yuan = cents.toDouble() / 100.0
        val absYuan = abs(yuan)
        return when {
            absYuan >= 100_000_000 -> trimZero(yuan / 100_000_000.0) + "亿"
            absYuan >= 10_000 -> trimZero(yuan / 10_000.0) + "万"
            absYuan >= 1_000 -> "${(yuan / 1_000).toInt()}k"
            else -> yuan.toInt().toString()
        }
    }

    private fun trimZero(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toInt().toString() else rounded.toString()
    }

    private fun groupDigits(value: Long): String {
        val raw = value.toString()
        if (raw.length <= 3) return raw
        val builder = StringBuilder()
        var count = 0
        for (index in raw.length - 1 downTo 0) {
            builder.append(raw[index])
            count++
            if (count % 3 == 0 && index != 0) builder.append(',')
        }
        return builder.reverse().toString()
    }

    /**
     * 解析 OCR / 手工输入的金额文本为「分」。
     * 支持："¥1,234.56"、"-25.00"、"25"、"25.5"、"1 234.56"、"25元"
     */
    fun parseCents(text: String): Long? {
        if (text.isBlank()) return null
        val cleaned = text
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(",", "")
            .replace(" ", "")
            .replace("−", "-")
            .replace("，", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val negative = cleaned.startsWith("-")
        val numeric = cleaned.removePrefix("-").removePrefix("+")
        if (numeric.isEmpty() || numeric.count { it == '.' } > 1) return null
        val parts = numeric.split(".")
        val yuanPart = parts[0].ifEmpty { "0" }
        if (!yuanPart.all { it.isDigit() }) return null
        val fenPartRaw = if (parts.size == 2) parts[1] else "0"
        if (!fenPartRaw.all { it.isDigit() }) return null
        // 超过两位小数按分四舍五入
        val fenPart = when {
            fenPartRaw.length <= 2 -> fenPartRaw.padEnd(2, '0')
            else -> fenPartRaw.substring(0, 2) + if (fenPartRaw[2] >= '5') "1" else "0"
        }
        val yuan = yuanPart.toLongOrNull() ?: return null
        val fen = fenPart.toLongOrNull() ?: return null
        val total = yuan * 100 + fen
        return if (negative) -total else total
    }

    /** 百分比展示，例：0.1234 → "12.3%"；[signed] 时正数显示 "+" */
    fun percent(ratio: Double?, signed: Boolean = false, decimals: Int = 1): String {
        if (ratio == null || ratio.isNaN() || ratio.isInfinite()) return "—"
        val pct = ratio * 100.0
        val formatted = String.format(Locale.CHINA, "%.${decimals}f", abs(pct))
        val sign = when {
            pct > 0 && signed -> "+"
            pct < 0 -> "-"
            else -> ""
        }
        return "$sign$formatted%"
    }
}

enum class TxTypeSign { NONE, EXPENSE, INCOME }
