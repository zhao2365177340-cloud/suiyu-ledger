package com.suiyu.ledger.tax

import kotlin.math.abs

/**
 * 个人所得税规则常量与整数运算工具。
 *
 * 为什么全部用 Long「分」：
 *  1. 税额 = 应纳税所得额 × 税率 − 速算扣除数，税法要求四舍五入到分；
 *  2. 用 Double 时 0.1 元级别的误差会在汇算结果里变成 1 分钱的偏差；
 *  3. 因此这里提供 [roundDivHalfUp] / [percentOf] / [perMilleOf] 三个显式整数运算入口，
 *     所有税率表命中与四舍五入都集中在本文件，便于单测与独立复算（Python）对齐。
 *
 * 政策时点：2026 年度综合所得汇算。专项附加扣除按 2023 年提高后的标准执行（2026 年延续），
 * 全年一次性奖金单独计税政策延续至 2027 年 12 月 31 日。
 */
object TaxRules {

    const val POLICY_YEAR = 2026

    const val POLICY_BASIS =
        "税制依据：2019 年起施行的《中华人民共和国个人所得税法》及其实施条例、" +
            "《个人所得税专项附加扣除暂行办法》；专项附加扣除按 2023 年提高后的标准执行，" +
            "2026 年度继续适用；全年一次性奖金单独计税政策延续至 2027 年 12 月 31 日。"

    // ── 金额上限：防止输入异常导致 Long 溢出（1 亿元）──
    const val MAX_INPUT_CENTS = 10_000_000_000L

    // ── 基本减除费用 ──
    const val BASIC_DEDUCTION_MONTHLY_CENTS = 500_000L
    const val BASIC_DEDUCTION_ANNUAL_CENTS = BASIC_DEDUCTION_MONTHLY_CENTS * 12

    // ── 专项附加扣除标准（月度金额，分）──
    const val CHILD_EDUCATION_MONTHLY_CENTS = 200_000L
    const val INFANT_CARE_MONTHLY_CENTS = 200_000L
    const val CONTINUING_DEGREE_MONTHLY_CENTS = 40_000L
    const val CONTINUING_CERTIFICATE_ANNUAL_CENTS = 360_000L
    const val HOUSING_LOAN_MONTHLY_CENTS = 100_000L
    const val ELDERLY_ONLY_CHILD_MONTHLY_CENTS = 300_000L
    const val ELDERLY_SHARED_MAX_MONTHLY_CENTS = 150_000L

    // ── 专项附加扣除边界 ──
    const val SERIOUS_ILLNESS_THRESHOLD_CENTS = 1_500_000L // 15,000 元起付
    const val SERIOUS_ILLNESS_CAP_CENTS = 8_000_000L // 年限额 80,000 元

    // ── 其他扣除限额 ──
    const val PERSONAL_PENSION_CAP_CENTS = 1_200_000L // 12,000 元/年
    const val COMMERCIAL_HEALTH_CAP_CENTS = 240_000L // 2,400 元/年
    const val CHARITY_LIMIT_PERCENT = 30 // 公益捐赠：应纳税所得额 30% 限额

    // ── 三险一金估算比例（个人部分） ──
    const val PENSION_RATE_PERCENT = 8
    const val MEDICAL_RATE_PERCENT = 2
    const val UNEMPLOYMENT_RATE_PERMILLE = 5 // 0.5%
    const val HOUSING_FUND_MIN_PERCENT = 5
    const val HOUSING_FUND_MAX_PERCENT = 12

    /**
     * 居民个人综合所得年度税率表。
     * 每一档：应纳税所得额上限（含）、税率、速算扣除数（分）。
     */
    val ANNUAL_BRACKETS: List<TaxBracket> = listOf(
        TaxBracket(3_600_000L, 3, 0L),
        TaxBracket(14_400_000L, 10, 252_000L),
        TaxBracket(30_000_000L, 20, 1_692_000L),
        TaxBracket(42_000_000L, 25, 3_192_000L),
        TaxBracket(66_000_000L, 30, 5_292_000L),
        TaxBracket(96_000_000L, 35, 8_592_000L),
        TaxBracket(Long.MAX_VALUE, 45, 18_192_000L),
    )

    /**
     * 按月换算后的综合所得税率表，用于全年一次性奖金单独计税。
     * 档位边界与速算扣除数均为「月度」口径，不能与年度表混用。
     */
    val MONTHLY_BRACKETS: List<TaxBracket> = listOf(
        TaxBracket(300_000L, 3, 0L),
        TaxBracket(1_200_000L, 10, 21_000L),
        TaxBracket(2_500_000L, 20, 141_000L),
        TaxBracket(3_500_000L, 25, 266_000L),
        TaxBracket(5_500_000L, 30, 441_000L),
        TaxBracket(8_000_000L, 35, 716_000L),
        TaxBracket(Long.MAX_VALUE, 45, 1_516_000L),
    )

    /** 年终奖单独计税的「多发一元多缴税」临界点（元转分） */
    val BONUS_THRESHOLD_CENTS: List<Long> = listOf(
        3_600_000L, // 36,000 元
        14_400_000L, // 144,000 元
        30_000_000L, // 300,000 元
        42_000_000L, // 420,000 元
        66_000_000L, // 660,000 元
        96_000_000L, // 960,000 元
    )

    /** 输入金额规整：负数按 0 处理，超过 1 亿元按上限截断，保证后续整数乘法不溢出 */
    fun normalize(cents: Long): Long = cents.coerceIn(0L, MAX_INPUT_CENTS)

    /** 四舍五入的整数除法：numerator / denominator（denominator > 0），显式处理正负号 */
    fun roundDivHalfUp(numerator: Long, denominator: Long): Long {
        require(denominator > 0L) { "分母必须为正数" }
        return if (numerator >= 0L) {
            (numerator + denominator / 2L) / denominator
        } else {
            -((-numerator + denominator / 2L) / denominator)
        }
    }

    /** cents 的 percent%（百分数整数，如 3 表示 3%），四舍五入到分 */
    fun percentOf(cents: Long, percent: Int): Long =
        roundDivHalfUp(cents * percent.toLong(), 100L)

    /** cents 的 perMille‰（千分数整数，如 5 表示 0.5%），四舍五入到分 */
    fun perMilleOf(cents: Long, perMille: Int): Long =
        roundDivHalfUp(cents * perMille.toLong(), 1_000L)

    /** 命中税率档（应纳税所得额为 0 时命中第一档 3%） */
    fun bracketOf(taxableCents: Long, table: List<TaxBracket> = ANNUAL_BRACKETS): TaxBracket {
        val taxable = taxableCents.coerceAtLeast(0L)
        return table.firstOrNull { taxable <= it.upperCents } ?: table.last()
    }

    /** 按税率表计算税额：round(应纳税所得额 × 税率) − 速算扣除数，最低为 0 */
    fun taxOf(taxableCents: Long, table: List<TaxBracket> = ANNUAL_BRACKETS): Long {
        val taxable = taxableCents.coerceAtLeast(0L)
        val bracket = bracketOf(taxable, table)
        return (percentOf(taxable, bracket.ratePercent) - bracket.quickDeductionCents).coerceAtLeast(0L)
    }

    /**
     * 全年一次性奖金单独计税：
     * 奖金 ÷ 12 命中月度税率表档位，税额 = 奖金 × 税率 − 月度速算扣除数。
     */
    fun bonusTaxOf(bonusCents: Long): Long {
        val bonus = normalize(bonusCents)
        if (bonus <= 0L) return 0L
        val monthlyEquivalent = bonus / 12L
        val bracket = bracketOf(monthlyEquivalent, MONTHLY_BRACKETS)
        return (percentOf(bonus, bracket.ratePercent) - bracket.quickDeductionCents).coerceAtLeast(0L)
    }

    /**
     * 按工资基数自动估算三险一金个人月缴额（仅估算，不含社保缴费基数上下限）。
     * 养老 8% / 医疗 2% / 失业 0.5% / 公积金 5%-12%（用户可选）。
     */
    fun estimateSocialInsurance(
        monthlySalaryCents: Long,
        housingFundPercent: Int = 12,
    ): SocialInsuranceInput {
        val base = normalize(monthlySalaryCents)
        val fundPercent = housingFundPercent.coerceIn(HOUSING_FUND_MIN_PERCENT, HOUSING_FUND_MAX_PERCENT)
        return SocialInsuranceInput(
            pensionCents = percentOf(base, PENSION_RATE_PERCENT),
            medicalCents = percentOf(base, MEDICAL_RATE_PERCENT),
            unemploymentCents = perMilleOf(base, UNEMPLOYMENT_RATE_PERMILLE),
            housingFundCents = percentOf(base, fundPercent),
        )
    }

    /** 无货币符号的元金额文本：1234567 → "12,345.67"；用于政策说明中的数字 */
    fun yuanText(cents: Long): String {
        val negative = cents < 0L
        val value = abs(cents)
        val yuan = value / 100L
        val fen = value % 100L
        val body = groupDigits(yuan) + if (fen != 0L) ".${fen.toString().padStart(2, '0')}" else ""
        return if (negative) "-$body" else body
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
}
