package com.suiyu.ledger.analytics

import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.MonthKey
import java.time.LocalDate

/**
 * 统计分析引擎：所有页面（首页 / 统计 / 报表 / 成员分析 / 税务）共用同一套口径，
 * 保证「同一笔钱在任何页面看到的数字一致」。
 *
 * 口径约定：
 *  - 仅统计 includeInStats = true 的流水；
 *  - 时间按设备本地时区、自然月划分；
 *  - 比率的分母为 0 时返回 null / 0，绝不产生 NaN 展示。
 */

data class MonthSummary(
    val month: MonthKey,
    val incomeCents: Long,
    val expenseCents: Long,
    val recordCount: Int,
    val incomeCount: Int,
    val expenseCount: Int,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
    val savingsRate: Double get() = if (incomeCents <= 0L) 0.0 else balanceCents.toDouble() / incomeCents.toDouble()
    val expenseToIncome: Double get() = if (incomeCents <= 0L) 0.0 else expenseCents.toDouble() / incomeCents.toDouble()
    val avgDailyExpenseCents: Long
        get() {
            val days = when {
                month == MonthKey.current() -> Dates.toLocalDate(System.currentTimeMillis()).dayOfMonth
                else -> Dates.lengthOfMonth(month)
            }.coerceAtLeast(1)
            return expenseCents / days
        }
    val isEmpty: Boolean get() = recordCount == 0
}

data class Change(val current: Long, val base: Long) {
    val diff: Long get() = current - base
    val pct: Double? get() = if (base == 0L) null else diff.toDouble() / base.toDouble()
    val direction: Int get() = diff.compareTo(0L)
    val hasBase: Boolean get() = base != 0L
}

data class CategorySlice(
    val categoryId: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val totalCents: Long,
    val count: Int,
    val ratio: Double,
    val baseCents: Long = 0L,
) {
    val change: Change get() = Change(totalCents, baseCents)
}

data class MemberSlice(
    val memberId: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val role: String,
    val expenseCents: Long,
    val incomeCents: Long,
    val count: Int,
    val expenseRatio: Double,
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

data class MonthPoint(val month: MonthKey, val incomeCents: Long, val expenseCents: Long) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

data class DayPoint(val day: Int, val weekday: Int, val expenseCents: Long, val incomeCents: Long)

data class MonthComparison(
    val month: MonthKey,
    val current: MonthSummary,
    val previous: MonthSummary,
    val lastYear: MonthSummary,
) {
    val expenseMoM: Change get() = Change(current.expenseCents, previous.expenseCents)
    val expenseYoY: Change get() = Change(current.expenseCents, lastYear.expenseCents)
    val incomeMoM: Change get() = Change(current.incomeCents, previous.incomeCents)
    val incomeYoY: Change get() = Change(current.incomeCents, lastYear.incomeCents)
    val balanceMoM: Change get() = Change(current.balanceCents, previous.balanceCents)
    val savingsRateDelta: Double get() = current.savingsRate - previous.savingsRate
}

data class CategoryChange(
    val categoryId: String,
    val name: String,
    val emoji: String,
    val currentCents: Long,
    val baseCents: Long,
) {
    val change: Change get() = Change(currentCents, baseCents)
}

data class MerchantSlice(val merchant: String, val totalCents: Long, val count: Int, val ratio: Double)

data class HealthMetrics(
    val savingsRate: Double,
    val expenseToIncome: Double,
    val avgDailyExpenseCents: Long,
    val noSpendDays: Int,
    val largestExpense: TxRecord?,
    val topCategoryRatio: Double,
    val fixedExpenseRatio: Double,
    val passiveIncomeRatio: Double,
) {
    val savingsRating: Int
        get() = when {
            savingsRate >= 0.30 -> 3
            savingsRate >= 0.10 -> 2
            savingsRate >= 0.0 -> 1
            else -> 0
        }
}

object Analytics {

    private const val FALLBACK_COLOR = "#9A9487"

    fun ofMonth(records: List<TxRecord>, month: MonthKey): List<TxRecord> {
        val start = month.startMillis()
        val end = month.endMillis()
        return records.filter { it.includeInStats && it.occurredAt >= start && it.occurredAt < end }
    }

    fun summary(records: List<TxRecord>, month: MonthKey): MonthSummary {
        val list = ofMonth(records, month)
        val income = list.filter { it.type == TxType.INCOME }
        val expense = list.filter { it.type == TxType.EXPENSE }
        return MonthSummary(
            month = month,
            incomeCents = income.sumOf { it.amountCents },
            expenseCents = expense.sumOf { it.amountCents },
            recordCount = list.size,
            incomeCount = income.size,
            expenseCount = expense.size,
        )
    }

    fun comparison(records: List<TxRecord>, month: MonthKey): MonthComparison = MonthComparison(
        month = month,
        current = summary(records, month),
        previous = summary(records, month.plusMonths(-1)),
        lastYear = summary(records, MonthKey(month.year - 1, month.month)),
    )

    fun categorySlices(
        records: List<TxRecord>,
        categories: List<Category>,
        month: MonthKey,
        type: TxType = TxType.EXPENSE,
        memberId: String? = null,
        baseMonth: MonthKey? = null,
    ): List<CategorySlice> {
        val list = ofMonth(records, month).filter {
            it.type == type && (memberId == null || it.memberId == memberId)
        }
        val total = list.sumOf { it.amountCents }
        val baseMap = if (baseMonth != null) {
            ofMonth(records, baseMonth)
                .filter { it.type == type && (memberId == null || it.memberId == memberId) }
                .groupBy { it.categoryId }
                .mapValues { entry -> entry.value.sumOf { it.amountCents } }
        } else {
            emptyMap()
        }
        return list.groupBy { it.categoryId }
            .map { (categoryId, items) ->
                val category = categories.firstOrNull { it.id == categoryId }
                val sum = items.sumOf { it.amountCents }
                CategorySlice(
                    categoryId = categoryId,
                    name = category?.name ?: "未分类",
                    emoji = category?.emoji ?: "📎",
                    colorHex = category?.colorHex ?: FALLBACK_COLOR,
                    totalCents = sum,
                    count = items.size,
                    ratio = if (total > 0L) sum.toDouble() / total.toDouble() else 0.0,
                    baseCents = baseMap[categoryId] ?: 0L,
                )
            }
            .sortedByDescending { it.totalCents }
    }

    fun memberSlices(records: List<TxRecord>, members: List<Member>, month: MonthKey): List<MemberSlice> {
        val list = ofMonth(records, month)
        val totalExpense = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
        return members.map { member ->
            val own = list.filter { it.memberId == member.id }
            val expense = own.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
            MemberSlice(
                memberId = member.id,
                name = member.name,
                emoji = member.emoji,
                colorHex = member.colorHex,
                role = member.role,
                expenseCents = expense,
                incomeCents = own.filter { it.type == TxType.INCOME }.sumOf { it.amountCents },
                count = own.size,
                expenseRatio = if (totalExpense > 0L) expense.toDouble() / totalExpense.toDouble() else 0.0,
            )
        }.sortedByDescending { it.expenseCents }
    }

    fun monthPoints(records: List<TxRecord>, endMonth: MonthKey, count: Int): List<MonthPoint> {
        if (count <= 0) return emptyList()
        return (count - 1 downTo 0).map { offset ->
            val month = endMonth.plusMonths(-offset)
            val summary = summary(records, month)
            MonthPoint(month, summary.incomeCents, summary.expenseCents)
        }
    }

    fun yearPoints(records: List<TxRecord>, year: Int): List<MonthPoint> =
        (1..12).map { month ->
            val summary = summary(records, MonthKey(year, month))
            MonthPoint(summary.month, summary.incomeCents, summary.expenseCents)
        }

    fun dailyPoints(records: List<TxRecord>, month: MonthKey): List<DayPoint> {
        val list = ofMonth(records, month)
        val days = Dates.lengthOfMonth(month)
        val expenseMap = list.filter { it.type == TxType.EXPENSE }
            .groupBy { Dates.toLocalDate(it.occurredAt).dayOfMonth }
            .mapValues { entry -> entry.value.sumOf { it.amountCents } }
        val incomeMap = list.filter { it.type == TxType.INCOME }
            .groupBy { Dates.toLocalDate(it.occurredAt).dayOfMonth }
            .mapValues { entry -> entry.value.sumOf { it.amountCents } }
        return (1..days).map { day ->
            DayPoint(
                day = day,
                weekday = LocalDate.of(month.year, month.month, day).dayOfWeek.value,
                expenseCents = expenseMap[day] ?: 0L,
                incomeCents = incomeMap[day] ?: 0L,
            )
        }
    }

    fun categoryChanges(
        records: List<TxRecord>,
        categories: List<Category>,
        month: MonthKey,
        type: TxType = TxType.EXPENSE,
    ): List<CategoryChange> {
        val base = month.plusMonths(-1)
        val currentSlices = categorySlices(records, categories, month, type)
        val currentMap = currentSlices.associate { it.categoryId to it.totalCents }
        val baseSlices = categorySlices(records, categories, base, type)
        val baseMap = baseSlices.associate { it.categoryId to it.totalCents }
        val ids = (currentMap.keys + baseMap.keys).toSet()
        return ids.map { id ->
            val category = categories.firstOrNull { it.id == id }
            CategoryChange(
                categoryId = id,
                name = category?.name ?: "未分类",
                emoji = category?.emoji ?: "📎",
                currentCents = currentMap[id] ?: 0L,
                baseCents = baseMap[id] ?: 0L,
            )
        }.sortedByDescending { kotlin.math.abs(it.currentCents - it.baseCents) }
    }

    fun memberCategorySlices(
        records: List<TxRecord>,
        categories: List<Category>,
        memberId: String,
        month: MonthKey,
        type: TxType = TxType.EXPENSE,
    ): List<CategorySlice> = categorySlices(records, categories, month, type, memberId)

    fun topMerchants(
        records: List<TxRecord>,
        month: MonthKey,
        type: TxType = TxType.EXPENSE,
        limit: Int = 5,
    ): List<MerchantSlice> {
        val list = ofMonth(records, month).filter { it.type == type && it.merchant.isNotBlank() }
        val total = list.sumOf { it.amountCents }
        return list.groupBy { it.merchant.trim() }
            .map { (merchant, items) ->
                val sum = items.sumOf { it.amountCents }
                MerchantSlice(
                    merchant = merchant,
                    totalCents = sum,
                    count = items.size,
                    ratio = if (total > 0L) sum.toDouble() / total.toDouble() else 0.0,
                )
            }
            .sortedByDescending { it.totalCents }
            .take(limit)
    }

    fun sourceBreakdown(records: List<TxRecord>, month: MonthKey): Map<BillSource, Int> =
        ofMonth(records, month).groupBy { it.source }.mapValues { it.value.size }

    fun healthMetrics(
        records: List<TxRecord>,
        categories: List<Category>,
        month: MonthKey,
    ): HealthMetrics {
        val list = ofMonth(records, month)
        val summary = summary(records, month)
        val expenseList = list.filter { it.type == TxType.EXPENSE }
        val days = Dates.lengthOfMonth(month)
        val spendDays = expenseList.map { Dates.toLocalDate(it.occurredAt).dayOfMonth }.distinct().size
        val noSpendDays = (days - spendDays).coerceAtLeast(0)
        val fixedIds = setOf("cat_housing", "cat_telecom", "cat_education")
        val fixedExpense = expenseList.filter { it.categoryId in fixedIds }.sumOf { it.amountCents }
        val passiveIds = setOf("cat_invest", "cat_redpacket")
        val passiveIncome = list.filter { it.type == TxType.INCOME && it.categoryId in passiveIds }
            .sumOf { it.amountCents }
        val topRatio = categorySlices(records, categories, month).firstOrNull()?.ratio ?: 0.0
        return HealthMetrics(
            savingsRate = summary.savingsRate,
            expenseToIncome = summary.expenseToIncome,
            avgDailyExpenseCents = summary.avgDailyExpenseCents,
            noSpendDays = noSpendDays,
            largestExpense = expenseList.maxByOrNull { it.amountCents },
            topCategoryRatio = topRatio,
            fixedExpenseRatio = if (summary.expenseCents > 0L) {
                fixedExpense.toDouble() / summary.expenseCents.toDouble()
            } else {
                0.0
            },
            passiveIncomeRatio = if (summary.incomeCents > 0L) {
                passiveIncome.toDouble() / summary.incomeCents.toDouble()
            } else {
                0.0
            },
        )
    }

    /** 日累计支出曲线（用于「本月花了多少」的进度对比） */
    fun cumulativeExpense(records: List<TxRecord>, month: MonthKey): List<Long> {
        val points = dailyPoints(records, month)
        var sum = 0L
        return points.map { day ->
            sum += day.expenseCents
            sum
        }
    }

    /** 某成员在最近 N 个月的支出序列（成员单独分析用） */
    fun memberMonthPoints(
        records: List<TxRecord>,
        memberId: String,
        endMonth: MonthKey,
        count: Int,
    ): List<MonthPoint> = (count - 1 downTo 0).map { offset ->
        val month = endMonth.plusMonths(-offset)
        val list = ofMonth(records, month).filter { it.memberId == memberId }
        MonthPoint(
            month = month,
            incomeCents = list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents },
            expenseCents = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents },
        )
    }

    /** 全年累计 */
    fun yearSummary(records: List<TxRecord>, year: Int): MonthSummary {
        val start = MonthKey(year, 1).startMillis()
        val end = MonthKey(year + 1, 1).startMillis()
        val list = records.filter { it.includeInStats && it.occurredAt >= start && it.occurredAt < end }
        val income = list.filter { it.type == TxType.INCOME }
        val expense = list.filter { it.type == TxType.EXPENSE }
        return MonthSummary(
            month = MonthKey(year, 12),
            incomeCents = income.sumOf { it.amountCents },
            expenseCents = expense.sumOf { it.amountCents },
            recordCount = list.size,
            incomeCount = income.size,
            expenseCount = expense.size,
        )
    }
}
