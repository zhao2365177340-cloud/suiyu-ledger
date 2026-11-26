package com.suiyu.ledger.analytics

import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.AssetCategory
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 家庭财务体检引擎（v1.2）。
 *
 * 输入：用户自填资产、负债、工作与家庭情况 + 近 6 个月流水。
 * 输出：净资产、现金流基线、6 个风险维度评分、综合分与评级、可执行建议。
 *
 * 设计原则：
 *  - 纯 Kotlin，可被 JUnit 独立验证；金额全用「分」，比率分母为 0 时返回 null / 0；
 *  - 不制造「理财建议」式承诺，只做基于数字的体检与缺口提示；
 *  - 流水不足时允许用户手动填写固定收入 / 固定支出，保证没有账本也能体检。
 */
enum class RiskGrade(val label: String) {
    EXCELLENT("稳健"),
    HEALTHY("健康"),
    WATCH("需关注"),
    RISK("高风险");

    companion object {
        fun ofScore(score: Int): RiskGrade = when {
            score >= 85 -> EXCELLENT
            score >= 70 -> HEALTHY
            score >= 55 -> WATCH
            else -> RISK
        }
    }
}

data class CashflowBaseline(
    /** 近 6 个月平均月总收入（含所有收入） */
    val monthlyIncomeCents: Long,
    /** 固定收入：工资类收入；手动填写时覆盖 */
    val monthlyFixedIncomeCents: Long,
    val monthlyExpenseCents: Long,
    /** 固定支出：居住 + 通讯 + 教育（不含负债月供）；手动填写时覆盖 */
    val monthlyFixedExpenseCents: Long,
    /** 全部负债月供之和 */
    val monthlyDebtPaymentCents: Long,
    val monthlySurplusCents: Long,
    val savingsRate: Double,
    val monthsUsed: Int,
    val incomeFromManual: Boolean,
    val expenseFromManual: Boolean,
) {
    /** 刚性月支出：固定支出 + 月供，应急金覆盖的目标 */
    val essentialOutflowCents: Long get() = monthlyFixedExpenseCents + monthlyDebtPaymentCents
}

data class AssetAllocation(
    val label: String,
    val cents: Long,
    val ratio: Double,
)

data class RiskDimension(
    val key: String,
    val label: String,
    /** 0..100，越高越健康 */
    val score: Int,
    val valueText: String,
    val hint: String,
)

data class RiskMetric(
    val key: String,
    val label: String,
    val valueText: String,
    val score: Int,
    val grade: RiskGrade,
)

data class RiskAdvice(
    val title: String,
    val detail: String,
    val tone: InsightTone = InsightTone.NEUTRAL,
)

data class FinancialHealthReport(
    val totalAssetsCents: Long,
    val totalLiabilitiesCents: Long,
    val netWorthCents: Long,
    val liquidAssetsCents: Long,
    val financialAssetsCents: Long,
    val propertyAssetsCents: Long,
    val otherAssetsCents: Long,
    /** 应急储备金可覆盖的月数；刚性支出为 0 时显示 0 并在指标里标注待补充 */
    val emergencyMonths: Double,
    /** 总负债 / 总资产；无资产且有负债时按 100% 计 */
    val debtRatio: Double,
    /** 月供收入比（DSR）：刚性月支出 / 固定月收入；收入为 0 时为 null */
    val debtServiceRatio: Double?,
    val cashRatio: Double,
    val investmentRatio: Double,
    val score: Int,
    val grade: RiskGrade,
    val headline: String,
    val cashflow: CashflowBaseline,
    val metrics: List<RiskMetric>,
    val dimensions: List<RiskDimension>,
    val allocations: List<AssetAllocation>,
    val advice: List<RiskAdvice>,
    val hasAnyData: Boolean,
    val hasLedgerData: Boolean,
) {
    val hasAssets: Boolean get() = totalAssetsCents > 0L
    val hasLiabilities: Boolean get() = totalLiabilitiesCents > 0L
}

object FinancialHealth {

    const val WINDOW_MONTHS = 6
    private const val SALARY_CATEGORY = "cat_salary"
    private val FIXED_EXPENSE_CATEGORIES = setOf("cat_housing", "cat_telecom", "cat_education")

    fun evaluate(
        records: List<TxRecord>,
        assets: List<Asset>,
        liabilities: List<Liability>,
        profile: FinancialProfile,
        nowMillis: Long = System.currentTimeMillis(),
    ): FinancialHealthReport {
        val totalAssets = assets.sumOf { it.valueCents }
        val totalLiabilities = liabilities.sumOf { it.balanceCents }
        val netWorth = totalAssets - totalLiabilities
        val liquidAssets = assets.filter { it.category.isCashLike }.sumOf { it.valueCents }
        val financialAssets = assets.filter { it.category.isFinancial }.sumOf { it.valueCents }
        val propertyAssets = assets.filter { it.category.group == "房产" }.sumOf { it.valueCents }
        val otherAssets = (totalAssets - financialAssets - propertyAssets).coerceAtLeast(0L)

        val cashflow = baseline(records, liabilities, profile, nowMillis)
        val essential = cashflow.essentialOutflowCents
        val debtPayment = cashflow.monthlyDebtPaymentCents
        val fixedIncome = cashflow.monthlyFixedIncomeCents

        val emergencyMonths = when {
            essential > 0L -> liquidAssets.toDouble() / essential.toDouble()
            liquidAssets > 0L -> 24.0
            else -> 0.0
        }
        val debtRatio = when {
            totalAssets > 0L -> totalLiabilities.toDouble() / totalAssets.toDouble()
            totalLiabilities > 0L -> 1.0
            else -> 0.0
        }
        val dsr = if (fixedIncome > 0L) essential.toDouble() / fixedIncome.toDouble() else null
        val cashRatio = if (totalAssets > 0L) liquidAssets.toDouble() / totalAssets.toDouble() else 0.0
        val investmentRatio = if (totalAssets > 0L) financialAssets.toDouble() / totalAssets.toDouble() else 0.0

        // ── 六个维度 ────────────────────────────────────────────
        val liquidityScore = when {
            essential <= 0L -> if (liquidAssets > 0L) 50 else 0
            emergencyMonths >= 12 -> 100
            emergencyMonths >= 6 -> 85
            emergencyMonths >= 4 -> 70
            emergencyMonths >= 3 -> 55
            emergencyMonths >= 2 -> 40
            emergencyMonths >= 1 -> 20
            else -> 5
        }
        val debtScore = debtScore(debtRatio, dsr, totalLiabilities, totalAssets)
        val incomeScore = incomeStabilityScore(records, profile, nowMillis, fixedIncome)
        val savingsScore = savingsScore(cashflow.savingsRate, cashflow.monthsUsed)
        val insuranceScore = insuranceScore(profile, essential, totalLiabilities)
        val allocationScore = allocationScore(assets, totalAssets, financialAssets, liquidAssets)

        val totalScore = (
            liquidityScore * 0.25 +
                debtScore * 0.25 +
                incomeScore * 0.20 +
                savingsScore * 0.15 +
                insuranceScore * 0.10 +
                allocationScore * 0.05
            ).toInt().coerceIn(0, 100)
        val grade = RiskGrade.ofScore(totalScore)

        val emergencyGrade = RiskGrade.ofScore(liquidityScore)
        val metrics = listOf(
            RiskMetric(
                key = "emergency",
                label = "应急储备金",
                valueText = if (essential > 0L) String.format(java.util.Locale.CHINA, "%.1f 个月", emergencyMonths) else "—",
                score = liquidityScore,
                grade = emergencyGrade,
            ),
            RiskMetric(
                key = "debt",
                label = "资产负债率",
                valueText = if (totalAssets > 0L || totalLiabilities > 0L) Money.percent(debtRatio, false) else "—",
                score = debtScore,
                grade = RiskGrade.ofScore(debtScore),
            ),
            RiskMetric(
                key = "dsr",
                label = "月供收入比",
                valueText = dsr?.let { Money.percent(it, false) } ?: "—",
                score = (100 - (dsr ?: 0.0).times(100).toInt()).coerceIn(0, 100),
                grade = RiskGrade.ofScore((100 - (dsr ?: 0.0).times(100).toInt()).coerceIn(0, 100)),
            ),
            RiskMetric(
                key = "savings",
                label = "近 6 个月储蓄率",
                valueText = if (cashflow.monthlyIncomeCents > 0L) Money.percent(cashflow.savingsRate, false) else "—",
                score = savingsScore,
                grade = RiskGrade.ofScore(savingsScore),
            ),
            RiskMetric(
                key = "income",
                label = "收入稳定性",
                valueText = incomeLabel(profile, incomeScore),
                score = incomeScore,
                grade = RiskGrade.ofScore(incomeScore),
            ),
            RiskMetric(
                key = "insurance",
                label = "保障覆盖",
                valueText = insuranceLabel(profile),
                score = insuranceScore,
                grade = RiskGrade.ofScore(insuranceScore),
            ),
        )

        val dimensions = listOf(
            RiskDimension(
                key = "liquidity",
                label = "流动性",
                score = liquidityScore,
                valueText = if (essential > 0L) String.format(java.util.Locale.CHINA, "应急金 %.1f 个月", emergencyMonths) else "待补充支出",
                hint = "流动类资产（现金 / 活期 / 定期 / 货基）可覆盖刚性月支出的月数",
            ),
            RiskDimension(
                key = "debt",
                label = "负债健康",
                score = debtScore,
                valueText = "负债率 ${Money.percent(debtRatio, false)}",
                hint = "总负债 / 总资产；有房贷时看长期偿还能力与月供压力",
            ),
            RiskDimension(
                key = "income",
                label = "收入稳定",
                score = incomeScore,
                valueText = incomeLabel(profile, incomeScore),
                hint = "单位性质、工作年限、收入波动与双收入结构",
            ),
            RiskDimension(
                key = "savings",
                label = "储蓄能力",
                score = savingsScore,
                valueText = if (cashflow.monthlyIncomeCents > 0L) Money.percent(cashflow.savingsRate, false) else "待补充收入",
                hint = "近 6 个月「收入 − 支出」占收入的比例",
            ),
            RiskDimension(
                key = "insurance",
                label = "保障覆盖",
                score = insuranceScore,
                valueText = insuranceLabel(profile),
                hint = "医疗 / 重疾 / 寿险的配置与保额相对家庭责任是否足够",
            ),
            RiskDimension(
                key = "allocation",
                label = "配置均衡",
                score = allocationScore,
                valueText = "现金类 ${Money.percent(cashRatio, false)} · 投资类 ${Money.percent(investmentRatio, false)}",
                hint = "现金与投资类资产占比、分类分散度与单一资产集中度",
            ),
        )

        val allocations = buildAllocations(assets, totalAssets)
        val hasLedgerData = records.any { it.includeInStats }
        val hasAnyData = totalAssets > 0L || totalLiabilities > 0L ||
            profile.occupation.isNotBlank() || hasLedgerData

        return FinancialHealthReport(
            totalAssetsCents = totalAssets,
            totalLiabilitiesCents = totalLiabilities,
            netWorthCents = netWorth,
            liquidAssetsCents = liquidAssets,
            financialAssetsCents = financialAssets,
            propertyAssetsCents = propertyAssets,
            otherAssetsCents = otherAssets,
            emergencyMonths = emergencyMonths,
            debtRatio = debtRatio,
            debtServiceRatio = dsr,
            cashRatio = cashRatio,
            investmentRatio = investmentRatio,
            score = totalScore,
            grade = grade,
            headline = headline(totalScore, grade, netWorth),
            cashflow = cashflow,
            metrics = metrics,
            dimensions = dimensions,
            allocations = allocations,
            advice = buildAdvice(
                report = null,
                liquidityScore = liquidityScore,
                debtScore = debtScore,
                incomeScore = incomeScore,
                savingsScore = savingsScore,
                insuranceScore = insuranceScore,
                allocationScore = allocationScore,
                emergencyMonths = emergencyMonths,
                essentialOutflowCents = essential,
                liquidAssetsCents = liquidAssets,
                debtRatio = debtRatio,
                dsr = dsr,
                totalLiabilities = totalLiabilities,
                cashflow = cashflow,
                assets = assets,
                profile = profile,
            ),
            hasAnyData = hasAnyData,
            hasLedgerData = hasLedgerData,
        )
    }

    // ────────────────────────── 现金流基线 ──────────────────────────

    private fun baseline(
        records: List<TxRecord>,
        liabilities: List<Liability>,
        profile: FinancialProfile,
        nowMillis: Long,
    ): CashflowBaseline {
        val current = MonthKey.of(nowMillis)
        val window = (WINDOW_MONTHS - 1 downTo 0).map { current.plusMonths(-it) }
        val perMonth = window.map { month -> Analytics.ofMonth(records, month) }
        val rawMonths = perMonth.count { it.isNotEmpty() }
        val monthsWithData = rawMonths.coerceAtLeast(1)
        val divisor = monthsWithData.toLong()

        val salaryTotal = perMonth.sumOf { list ->
            list.filter { it.type == TxType.INCOME && it.categoryId == SALARY_CATEGORY }.sumOf { it.amountCents }
        }
        val incomeTotal = perMonth.sumOf { list ->
            list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
        }
        val expenseTotal = perMonth.sumOf { list ->
            list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
        }
        val fixedExpenseTotal = perMonth.sumOf { list ->
            list.filter { it.type == TxType.EXPENSE && it.categoryId in FIXED_EXPENSE_CATEGORIES }
                .sumOf { it.amountCents }
        }
        val debtPayment = liabilities.sumOf { it.monthlyPaymentCents }

        val autoFixedIncome = if (monthsWithData > 0) salaryTotal / divisor else 0L
        val autoIncome = if (monthsWithData > 0) incomeTotal / divisor else 0L
        val autoExpense = if (monthsWithData > 0) expenseTotal / divisor else 0L
        val autoFixedExpense = if (monthsWithData > 0) fixedExpenseTotal / divisor else 0L

        val incomeFromManual = profile.manualMonthlyIncomeCents > 0L
        val expenseFromManual = profile.manualMonthlyFixedExpenseCents > 0L
        val fixedIncome = if (incomeFromManual) profile.manualMonthlyIncomeCents else autoFixedIncome
        val fixedExpense = if (expenseFromManual) profile.manualMonthlyFixedExpenseCents else autoFixedExpense
        val income = if (incomeFromManual) maxOf(profile.manualMonthlyIncomeCents, autoIncome) else autoIncome
        // 手动固定支出不含月供；总支出用于储蓄率，若手动填写则「固定 + 其余消费」保守取较大值
        val expense = if (expenseFromManual) maxOf(autoExpense, fixedExpense) else autoExpense
        val surplus = income - expense
        val savingsRate = if (income > 0L) surplus.toDouble() / income.toDouble() else 0.0

        return CashflowBaseline(
            monthlyIncomeCents = income,
            monthlyFixedIncomeCents = fixedIncome,
            monthlyExpenseCents = expense,
            monthlyFixedExpenseCents = fixedExpense,
            monthlyDebtPaymentCents = debtPayment,
            monthlySurplusCents = surplus,
            savingsRate = savingsRate,
            monthsUsed = rawMonths,
            incomeFromManual = incomeFromManual,
            expenseFromManual = expenseFromManual,
        )
    }

    // ────────────────────────── 评分 ──────────────────────────

    private fun debtScore(debtRatio: Double, dsr: Double?, liability: Long, asset: Long): Int {
        if (liability <= 0L) return 100
        var score = when {
            debtRatio > 0.70 -> 25
            debtRatio > 0.50 -> 50
            debtRatio > 0.30 -> 75
            debtRatio > 0.15 -> 88
            else -> 100
        }
        if (dsr != null) {
            score = when {
                dsr > 0.70 -> minOf(score, 20)
                dsr > 0.50 -> minOf(score, 40)
                dsr > 0.40 -> minOf(score, 60)
                dsr > 0.30 -> minOf(score, 78)
                else -> score
            }
        }
        // 资不抵债直接压到底
        if (asset > 0L && liability > asset) score = minOf(score, 15)
        return score.coerceIn(0, 100)
    }

    private fun incomeStabilityScore(
        records: List<TxRecord>,
        profile: FinancialProfile,
        nowMillis: Long,
        fixedIncome: Long,
    ): Int {
        var score = when (profile.employmentType) {
            com.suiyu.ledger.model.EmploymentType.GOVERNMENT -> 85
            com.suiyu.ledger.model.EmploymentType.FOREIGN -> 75
            com.suiyu.ledger.model.EmploymentType.PRIVATE -> 68
            com.suiyu.ledger.model.EmploymentType.RETIRED -> 90
            com.suiyu.ledger.model.EmploymentType.FREELANCE -> 42
            com.suiyu.ledger.model.EmploymentType.OTHER -> 55
        }
        score += when (profile.incomeStability) {
            1 -> -15
            3 -> 10
            else -> 0
        }
        score += when {
            profile.jobYears >= 5 -> 10
            profile.jobYears >= 2 -> 5
            else -> -5
        }
        score += if (profile.spouseWorks) 5 else -5
        if (fixedIncome <= 0L) score -= 10

        // 流水验证：近 6 个月收入的变异系数
        val current = MonthKey.of(nowMillis)
        val monthlyIncome = (WINDOW_MONTHS - 1 downTo 0).map { offset ->
            Analytics.ofMonth(records, current.plusMonths(-offset))
                .filter { it.type == TxType.INCOME }
                .sumOf { it.amountCents }
        }.filter { it > 0L }
        if (monthlyIncome.size >= 3) {
            val mean = monthlyIncome.average()
            val variance = monthlyIncome.sumOf { value -> (value - mean) * (value - mean) } / monthlyIncome.size
            val cv = if (mean > 0) sqrt(variance) / mean else 0.0
            score += when {
                cv < 0.15 -> 10
                cv < 0.30 -> 5
                cv < 0.50 -> -5
                else -> -12
            }
        }
        return score.coerceIn(0, 100)
    }

    private fun savingsScore(savingsRate: Double, monthsUsed: Int): Int {
        if (monthsUsed <= 0) return 50
        return when {
            savingsRate >= 0.50 -> 100
            savingsRate >= 0.40 -> 90
            savingsRate >= 0.30 -> 80
            savingsRate >= 0.20 -> 65
            savingsRate >= 0.10 -> 45
            savingsRate >= 0.0 -> 25
            else -> 5
        }
    }

    private fun insuranceScore(profile: FinancialProfile, essentialOutflow: Long, liabilities: Long): Int {
        var score = 0
        if (profile.hasMedicalInsurance) score += 30
        if (profile.hasCriticalIllnessInsurance) score += 25
        if (profile.hasLifeInsurance) score += 15
        val annualResponsibility = essentialOutflow * 12 + liabilities
        if (annualResponsibility > 0L && profile.insuranceCoverageCents > 0L) {
            val coverageRatio = profile.insuranceCoverageCents.toDouble() / annualResponsibility.toDouble()
            score += when {
                coverageRatio >= 1.0 -> 30
                coverageRatio >= 0.6 -> 20
                coverageRatio >= 0.3 -> 12
                coverageRatio > 0.0 -> 6
                else -> 0
            }
        }
        return score.coerceIn(0, 100)
    }

    private fun allocationScore(
        assets: List<Asset>,
        totalAssets: Long,
        financialAssets: Long,
        liquidAssets: Long,
    ): Int {
        if (totalAssets <= 0L) return 0
        if (financialAssets <= 0L) return 25
        val financial = assets.filter { it.category.isFinancial && it.valueCents > 0L }
        val categories = financial.map { it.category }.distinct().size
        val investRatio = financial.filter { it.category == AssetCategory.INVEST }.sumOf { it.valueCents }
            .toDouble() / financialAssets.toDouble()
        val cashRatio = liquidAssets.toDouble() / financialAssets.toDouble()
        var score = 40
        if (categories >= 3) score += 20
        if (investRatio in 0.10..0.60) score += 25
        if (cashRatio in 0.20..0.80) score += 15
        // 单一资产（非自住）过度集中
        val topOther = assets.filter { it.category != AssetCategory.PROPERTY_HOME }
            .maxOfOrNull { it.valueCents } ?: 0L
        if (totalAssets > 0 && topOther.toDouble() / totalAssets.toDouble() > 0.70) score -= 15
        return score.coerceIn(0, 100)
    }

    // ────────────────────────── 文案 ──────────────────────────

    private fun headline(score: Int, grade: RiskGrade, netWorth: Long): String {
        val net = Money.format(netWorth)
        return "净资产 $net，综合 ${score} 分，风险等级：${grade.label}"
    }

    private fun incomeLabel(profile: FinancialProfile, score: Int): String = when {
        score >= 80 -> "稳定"
        score >= 60 -> "较稳定"
        score >= 45 -> "一般"
        else -> "波动较大"
    } + " · " + profile.employmentType.label

    private fun insuranceLabel(profile: FinancialProfile): String {
        val items = mutableListOf<String>()
        if (profile.hasMedicalInsurance) items += "医疗"
        if (profile.hasCriticalIllnessInsurance) items += "重疾"
        if (profile.hasLifeInsurance) items += "寿险"
        if (items.isEmpty()) return "未配置"
        val coverage = if (profile.insuranceCoverageCents > 0L) " · 保额 ${Money.format(profile.insuranceCoverageCents)}" else ""
        return items.joinToString(" + ") + coverage
    }

    private fun buildAllocations(assets: List<Asset>, total: Long): List<AssetAllocation> {
        if (total <= 0L) return emptyList()
        val groups = listOf(
            "现金与存款" to listOf(AssetCategory.CASH, AssetCategory.DEPOSIT),
            "理财与货币基金" to listOf(AssetCategory.WEALTH),
            "股票 / 基金 / 债券" to listOf(AssetCategory.INVEST),
            "保单现金价值" to listOf(AssetCategory.INSURANCE),
            "房产" to listOf(AssetCategory.PROPERTY_HOME, AssetCategory.PROPERTY_INVEST),
            "车辆" to listOf(AssetCategory.VEHICLE),
            "其他资产" to listOf(AssetCategory.RECEIVABLE, AssetCategory.OTHER),
        )
        return groups.mapNotNull { (label, categories) ->
            val cents = assets.filter { it.category in categories }.sumOf { it.valueCents }
            if (cents <= 0L) null else AssetAllocation(label, cents, cents.toDouble() / total.toDouble())
        }
    }

    private fun buildAdvice(
        report: FinancialHealthReport?,
        liquidityScore: Int,
        debtScore: Int,
        incomeScore: Int,
        savingsScore: Int,
        insuranceScore: Int,
        allocationScore: Int,
        emergencyMonths: Double,
        essentialOutflowCents: Long,
        liquidAssetsCents: Long,
        debtRatio: Double,
        dsr: Double?,
        totalLiabilities: Long,
        cashflow: CashflowBaseline,
        assets: List<Asset>,
        profile: FinancialProfile,
    ): List<RiskAdvice> {
        val advice = ArrayList<RiskAdvice>()

        if (liquidityScore < 70) {
            val target = essentialOutflowCents * 6
            val gap = (target - liquidAssetsCents).coerceAtLeast(0L)
            if (essentialOutflowCents > 0L) {
                advice += RiskAdvice(
                    title = "应急储备金偏少",
                    detail = "目前可覆盖 ${String.format(java.util.Locale.CHINA, "%.1f", emergencyMonths)} 个月刚性支出。" +
                        "建议先补到 6 个月（约 ${Money.format(gap)}），放在活期 / 货基 / 短期存款里，随取随用。",
                    tone = InsightTone.NEGATIVE,
                )
            } else {
                advice += RiskAdvice(
                    title = "先补全支出基线",
                    detail = "账本与手动填写里都缺少固定月支出，应急金月数无法评估。到「家庭 → 财务资料」填写固定月支出，或先记录 1–2 个月流水。",
                    tone = InsightTone.NEUTRAL,
                )
            }
        }

        if (debtScore < 70) {
            advice += RiskAdvice(
                title = "负债压力偏高",
                detail = "资产负债率 ${Money.percent(debtRatio, false)}" +
                    (dsr?.let { "，月供收入比 ${Money.percent(it, false)}" } ?: "") +
                    "。优先偿还高息负债，避免新增消费贷；若月供占收入超过 40%，可考虑延长贷款期限或提前还一部分。",
                tone = InsightTone.NEGATIVE,
            )
        } else if (totalLiabilities > 0L) {
            advice += RiskAdvice(
                title = "负债在可控范围",
                detail = "资产负债率 ${Money.percent(debtRatio, false)}" +
                    (dsr?.let { "，月供收入比 ${Money.percent(it, false)}" } ?: "") +
                    "，保持当前还款节奏即可，不建议为投资新增高息负债。",
                tone = InsightTone.POSITIVE,
            )
        }

        if (incomeScore < 70) {
            advice += RiskAdvice(
                title = "收入稳定性偏弱",
                detail = "当前为「${profile.employmentType.label}」，工作 ${profile.jobYears} 年。" +
                    "建议把应急金目标提高到 9–12 个月，并保留可迁移的技能与副业收入；家庭双收入是最有效的抗风险结构。",
                tone = InsightTone.NEGATIVE,
            )
        }

        if (savingsScore < 70 && cashflow.monthsUsed > 0) {
            advice += RiskAdvice(
                title = "结余空间可以更大",
                detail = "近 ${cashflow.monthsUsed} 个月平均储蓄率 ${Money.percent(cashflow.savingsRate, false)}，" +
                    "月均结余 ${Money.format(cashflow.monthlySurplusCents)}。建议先把结余自动转入独立账户，再逐步提升到 30% 以上。",
                tone = InsightTone.NEGATIVE,
            )
        }

        if (insuranceScore < 75) {
            val detail = buildString {
                append("家庭责任包括月供与刚性支出。")
                if (!profile.hasMedicalInsurance) append("先补齐百万医疗；")
                if (!profile.hasCriticalIllnessInsurance) append("重疾险建议保额覆盖 3–5 年收入；")
                if (!profile.hasLifeInsurance) append("有房贷或子女教育责任时，加一份定期寿险，保额覆盖「负债 + 3–5 年支出」；")
                if (profile.insuranceCoverageCents <= 0L) append("当前未填写保额，录入后评分会更准确。")
                append("保费预算建议控制在年收入 5%–10%。")
            }
            advice += RiskAdvice(
                title = "保障覆盖有缺口",
                detail = detail,
                tone = InsightTone.NEGATIVE,
            )
        }

        if (allocationScore < 60 && assets.isNotEmpty()) {
            advice += RiskAdvice(
                title = "资产配置偏集中",
                detail = "现金类与投资类比例、资产类别分散度可以再均衡一些：保留 6–12 个月应急金后，" +
                    "把长期资金分散到 2–3 类低相关资产，避免单一资产占比过高。",
                tone = InsightTone.NEGATIVE,
            )
        }

        if (advice.isEmpty()) {
            advice += RiskAdvice(
                title = "整体状态良好",
                detail = "净资产、流动性、负债与保障均在健康区间，保持现有储蓄节奏并每年复核一次资产与负债即可。",
                tone = InsightTone.POSITIVE,
            )
        }
        if (!cashflow.incomeFromManual && cashflow.monthsUsed < 3) {
            advice += RiskAdvice(
                title = "样本还比较少",
                detail = "当前只用 ${cashflow.monthsUsed} 个月的流水估算固定收支；继续记账到 6 个月，体检结果会更准。也可以手动填写固定月收入 / 支出。",
                tone = InsightTone.NEUTRAL,
            )
        }
        return advice.take(5)
    }

    /** 可分享的纯文本体检报告 */
    fun toShareText(report: FinancialHealthReport, familyName: String): String {
        val builder = StringBuilder()
        builder.append("岁余 · $familyName 家庭财务体检\n")
        builder.append("────────────────\n")
        builder.append("综合评分：${report.score} 分 · ${report.grade.label}\n")
        builder.append("总资产 ${Money.format(report.totalAssetsCents)} · 总负债 ${Money.format(report.totalLiabilitiesCents)} · 净资产 ${Money.format(report.netWorthCents)}\n")
        builder.append("应急储备金：${String.format(java.util.Locale.CHINA, "%.1f", report.emergencyMonths)} 个月\n")
        builder.append("资产负债率：${Money.percent(report.debtRatio, false)}，月供收入比：${report.debtServiceRatio?.let { Money.percent(it, false) } ?: "—"}\n")
        builder.append("月固定收入 ${Money.format(report.cashflow.monthlyFixedIncomeCents)}，月固定支出 ${Money.format(report.cashflow.essentialOutflowCents)}\n")
        builder.append("月均结余 ${Money.format(report.cashflow.monthlySurplusCents)}，储蓄率 ${Money.percent(report.cashflow.savingsRate, false)}\n\n")
        builder.append("【六维评分】\n")
        report.dimensions.forEach { builder.append("· ${it.label}：${it.score} 分\n") }
        builder.append("\n【资产配置】\n")
        report.allocations.forEach { builder.append("· ${it.label}：${Money.format(it.cents)}（${Money.percent(it.ratio, false)}）\n") }
        builder.append("\n【建议】\n")
        report.advice.forEach { builder.append("· ${it.title}：${it.detail}\n") }
        builder.append("\n由「岁余」根据本机账本自动生成 · 仅供参考，不构成投资建议")
        return builder.toString()
    }
}
