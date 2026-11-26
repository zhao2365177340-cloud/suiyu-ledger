package com.suiyu.ledger.analytics

import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.ui.theme.ChartPaletteLight
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey
import kotlin.math.abs

/**
 * 自动分析文案：把「数字」翻译成「人话」。
 *
 * 设计要点：
 *  - 只陈述可被数字验证的事实，不做价值判断式的说教；
 *  - 每条洞察都带「下一步线索」，让用户知道该看哪里；
 *  - 语气克制、书面感，符合家庭财务报表的定位。
 */
enum class InsightTone { POSITIVE, NEUTRAL, NEGATIVE }

data class Insight(
    val title: String,
    val body: String,
    val tone: InsightTone = InsightTone.NEUTRAL,
    val emoji: String = "·",
)

object Narrative {

    /** 报表开头的一段总述 */
    fun overview(comparison: MonthComparison, familyName: String): String {
        val current = comparison.current
        val month = comparison.month
        if (current.isEmpty) {
            return "${month.label()}还没有记录。导入账单截图或手动记一笔，${familyName}的收支画像就会开始累积。"
        }
        val expenseMoM = comparison.expenseMoM
        val expenseYoY = comparison.expenseYoY
        val balance = current.balanceCents
        val builder = StringBuilder()
        builder.append("${month.label()}，${familyName}共记录 ${current.recordCount} 笔收支：收入 ")
            .append(Money.format(current.incomeCents))
            .append("，支出 ")
            .append(Money.format(current.expenseCents))
            .append("，结余 ")
            .append(Money.format(balance))
            .append("，结余率 ")
            .append(Money.percent(current.savingsRate))
            .append("。")
        if (expenseMoM.hasBase) {
            val word = if (expenseMoM.direction > 0) "增加" else if (expenseMoM.direction < 0) "减少" else "持平"
            if (expenseMoM.direction == 0) {
                builder.append("支出与上月基本持平。")
            } else {
                builder.append("支出环比")
                    .append(word)
                    .append(Money.format(abs(expenseMoM.diff)))
                    .append("（")
                    .append(Money.percent(expenseMoM.pct, signed = true))
                    .append("）。")
            }
        }
        if (expenseYoY.hasBase) {
            val word = if (expenseYoY.direction > 0) "高于" else if (expenseYoY.direction < 0) "低于" else "持平于"
            if (expenseYoY.direction == 0) {
                builder.append("与去年同月基本相当。")
            } else {
                builder.append("同比")
                    .append(word)
                    .append("去年同月的 ")
                    .append(Money.format(comparison.lastYear.expenseCents))
                    .append("（")
                    .append(Money.percent(expenseYoY.pct, signed = true))
                    .append("）。")
            }
        }
        return builder.toString()
    }

    /** 支出结构洞察 */
    fun categoryInsights(
        slices: List<CategorySlice>,
        changes: List<CategoryChange>,
        limit: Int = 3,
    ): List<Insight> {
        if (slices.isEmpty()) return emptyList()
        val result = ArrayList<Insight>()
        val top = slices.first()
        val topText = "${top.name}是本月最大支出项：${Money.format(top.totalCents)}，占家庭支出的 ${(top.ratio * 100).toInt()}%"
        val topChange = if (top.baseCents > 0L) {
            val pct = (top.totalCents - top.baseCents).toDouble() / top.baseCents.toDouble()
            "，较上月${if (pct >= 0) "上升" else "下降"} ${Money.percent(abs(pct))}"
        } else {
            ""
        }
        result += Insight(
            title = "结构：${top.name}占比最高",
            body = topText + topChange + "。",
            tone = if (top.ratio > 0.35) InsightTone.NEGATIVE else InsightTone.NEUTRAL,
            emoji = top.emoji,
        )

        // 变化最大的分类（排除占比第一的，避免重复）
        val biggest = changes.filter { it.categoryId != top.categoryId }.maxByOrNull { abs(it.change.diff) }
        if (biggest != null && biggest.currentCents + biggest.baseCents > 0L) {
            val diff = biggest.change.diff
            val verb = if (diff > 0) "新增" else "节省"
            val body = if (biggest.baseCents == 0L) {
                "本月${biggest.name}支出 ${Money.format(biggest.currentCents)}，上月没有同类记录。"
            } else if (biggest.currentCents == 0L) {
                "本月没有${biggest.name}支出，上月为 ${Money.format(biggest.baseCents)}。"
            } else {
                "${biggest.name}较上月${if (diff >= 0) "多花" else "少花"} ${Money.format(abs(diff))}（${Money.percent(biggest.change.pct, signed = true)}），本月为 ${Money.format(biggest.currentCents)}。"
            }
            result += Insight(
                title = "波动：${biggest.name}${verb}最多",
                body = body,
                tone = if (diff > 0) InsightTone.NEUTRAL else InsightTone.POSITIVE,
                emoji = biggest.emoji,
            )
        }

        val topThreeRatio = slices.take(3).sumOf { it.ratio }
        if (slices.size >= 3) {
            result += Insight(
                title = "集中度：前三类占 ${(topThreeRatio * 100).toInt()}%",
                body = "支出前三位为 ${slices.take(3).joinToString("、") { it.name }}。前三个分类越集中，预算越好抓。",
                tone = InsightTone.NEUTRAL,
                emoji = "📊",
            )
        }
        return result.take(limit)
    }

    /** 成员维度洞察 */
    fun memberInsights(slices: List<MemberSlice>, summary: MonthSummary, limit: Int = 2): List<Insight> {
        val active = slices.filter { it.count > 0 }
        if (active.isEmpty()) return emptyList()
        val result = ArrayList<Insight>()
        val top = active.maxByOrNull { it.expenseCents }
        if (top != null && top.expenseCents > 0) {
            result += Insight(
                title = "成员：${top.name}支出最高",
                body = "${top.name}本月支出 ${Money.format(top.expenseCents)}，占家庭支出的 ${(top.expenseRatio * 100).toInt()}%；收入 ${Money.format(top.incomeCents)}，个人结余 ${Money.format(top.balanceCents)}。",
                tone = InsightTone.NEUTRAL,
                emoji = top.emoji,
            )
        }
        val selfOnly = active.filter { it.expenseCents > 0 }
        if (selfOnly.size >= 2) {
            val avg = selfOnly.sumOf { it.expenseCents } / selfOnly.size
            val max = selfOnly.maxOf { it.expenseCents }
            val min = selfOnly.minOf { it.expenseCents }
            val gap = if (avg > 0) (max - min).toDouble() / avg.toDouble() else 0.0
            result += Insight(
                title = "均衡：成员支出差异 ${(gap * 100).toInt()}%",
                body = "家庭可分析成员人均支出 ${Money.format(avg)}，最高与最低相差 ${Money.format(max - min)}。若差距主要来自一次性大额支出，可在成员分析里查看具体明细。",
                tone = InsightTone.NEUTRAL,
                emoji = "👨‍👩‍👧",
            )
        }
        return result.take(limit)
    }

    /** 财务健康 + 建议 */
    fun healthInsights(health: HealthMetrics, summary: MonthSummary, limit: Int = 3): List<Insight> {
        if (summary.isEmpty) return emptyList()
        val result = ArrayList<Insight>()
        val rate = health.savingsRate
        val (tone, comment) = when {
            rate >= 0.30 -> InsightTone.POSITIVE to "结余率处于健康区间，家庭现金流的缓冲垫在变厚。"
            rate >= 0.10 -> InsightTone.NEUTRAL to "结余率尚可，若想更快积累，可以从弹性支出里再挤一点空间。"
            rate >= 0.0 -> InsightTone.NEGATIVE to "结余率偏低，收入几乎被支出吃掉，建议先给固定支出做一次体检。"
            else -> InsightTone.NEGATIVE to "本月支出超过收入，动用储蓄或负债在增加，值得留意。"
        }
        result += Insight(
            title = "健康：结余率 ${Money.percent(rate)}",
            body = comment,
            tone = tone,
            emoji = "🌱",
        )
        if (health.fixedExpenseRatio > 0) {
            result += Insight(
                title = "刚性：固定支出占 ${(health.fixedExpenseRatio * 100).toInt()}%",
                body = "居住、通讯、教育等刚性支出为 ${Money.format((summary.expenseCents * health.fixedExpenseRatio).toLong())}。这部分越稳定，越适合用预算而不是靠意志力控制。",
                tone = InsightTone.NEUTRAL,
                emoji = "🏠",
            )
        }
        if (health.noSpendDays > 0) {
            result += Insight(
                title = "节奏：${health.noSpendDays} 天「零支出」",
                body = "本月有 ${health.noSpendDays} 天没有支出记录，日均支出 ${Money.format(health.avgDailyExpenseCents)}。零支出日越多，说明消费越集中在计划内。",
                tone = InsightTone.POSITIVE,
                emoji = "🗓️",
            )
        }
        return result.take(limit)
    }

    /** 给首页的一句话摘要 */
    fun homeHeadline(comparison: MonthComparison, topCategory: CategorySlice?): String {
        val summary = comparison.current
        if (summary.isEmpty) return "这个月还没有记录，先从一张账单截图开始吧"
        val moM = comparison.expenseMoM
        val trend = when {
            !moM.hasBase -> "本月支出 ${Money.format(summary.expenseCents)}"
            moM.direction > 0 -> "支出比上月多 ${Money.percent(moM.pct, signed = true)}"
            moM.direction < 0 -> "支出比上月少 ${Money.percent(abs(moM.pct ?: 0.0))}"
            else -> "支出与上月持平"
        }
        val top = topCategory?.let { "，${it.name}占 ${(it.ratio * 100).toInt()}%" } ?: ""
        return "$trend$top"
    }

    /** 生成可分享的纯文本月报（用于系统分享） */
    fun reportText(
        comparison: MonthComparison,
        categories: List<CategorySlice>,
        members: List<MemberSlice>,
        health: HealthMetrics,
        familyName: String,
    ): String {
        val builder = StringBuilder()
        builder.append("岁余 · ${familyName} ${comparison.month.label()}财务报表\n")
        builder.append("────────────────\n")
        builder.append(overview(comparison, familyName)).append("\n\n")
        builder.append("【收支结构】\n")
        categories.take(6).forEach { slice ->
            builder.append("· ${slice.name}：${Money.format(slice.totalCents)}（${(slice.ratio * 100).toInt()}%）\n")
        }
        builder.append("\n【成员分账】\n")
        members.filter { it.count > 0 }.forEach { slice ->
            builder.append("· ${slice.name}：支出 ${Money.format(slice.expenseCents)}，收入 ${Money.format(slice.incomeCents)}，结余 ${Money.format(slice.balanceCents)}\n")
        }
        builder.append("\n【家庭财务健康】\n")
        builder.append("· 结余率：${Money.percent(health.savingsRate)}\n")
        builder.append("· 日均支出：${Money.format(health.avgDailyExpenseCents)}\n")
        builder.append("· 零支出天数：${health.noSpendDays} 天\n")
        health.largestExpense?.let { record ->
            builder.append("· 最大单笔支出：${Money.format(record.amountCents)}${if (record.merchant.isNotBlank()) "（${record.merchant}）" else ""}\n")
        }
        builder.append("\n由「岁余」自动生成 · 日计不足，岁计有余")
        return builder.toString()
    }

    /** 分类配色（与图表保持同一套色板） */
    fun paletteColor(index: Int): androidx.compose.ui.graphics.Color =
        ChartPaletteLight[index % ChartPaletteLight.size]
}
