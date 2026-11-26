package com.suiyu.ledger.tax

/**
 * 税务模块的领域模型。
 *
 * 设计约定：
 *  - 金额一律使用「分」为单位的 Long，税率、速算扣除数也在分域内运算，全程不经过 Double，
 *    避免 0.1 + 0.2 式的浮点漂移（税务金额差一分都是错误）；
 *  - 输入模型按「年度汇算申报表」的逻辑组织：收入 → 三险一金 → 专项附加扣除 → 其他扣除；
 *  - 结果模型同时保留「计算过程字段」和「展示字段」，页面只做格式化，不重复算税。
 */

/** 综合所得四类收入 + 全年一次性奖金（原始收入额，单位：分） */
data class IncomeInput(
    val salaryCents: Long = 0L,
    val bonusCents: Long = 0L,
    val laborCents: Long = 0L,
    val authorCents: Long = 0L,
    val royaltyCents: Long = 0L,
) {
    /** 报表口径的「全部收入」，只用于展示与校验，不参与税额计算 */
    val totalCents: Long get() = salaryCents + bonusCents + laborCents + authorCents + royaltyCents
}

/** 三险一金：个人月度缴纳额（单位：分），年度合计 = 月缴 × 12 */
data class SocialInsuranceInput(
    val pensionCents: Long = 0L,
    val medicalCents: Long = 0L,
    val unemploymentCents: Long = 0L,
    val housingFundCents: Long = 0L,
) {
    val monthlyTotalCents: Long get() = pensionCents + medicalCents + unemploymentCents + housingFundCents
    val annualTotalCents: Long get() = monthlyTotalCents * 12
}

/** 住房租金三档（与住房贷款利息互斥） */
enum class HousingRentLevel(val label: String, val monthlyCents: Long, val description: String) {
    FIRST(
        "一类城市",
        150_000L,
        "直辖市、省会（首府）城市、计划单列市及国务院确定的其他城市：1,500 元/月",
    ),
    SECOND(
        "二类城市",
        110_000L,
        "市辖区户籍人口超过 100 万的城市：1,100 元/月",
    ),
    THIRD(
        "三类城市",
        80_000L,
        "市辖区户籍人口不超过 100 万的城市：800 元/月",
    ),
}

/**
 * 专项附加扣除输入。
 * 月度类扣除默认按全年 12 个月计算；继续教育、住房贷款按本年度实际可扣月数传入。
 */
data class AdditionalDeductionInput(
    val childCount: Int = 0,
    /** 本人扣除比例：50 或 100（父母可各 50%，也可一方 100%） */
    val childSharePercent: Int = 100,
    val infantCount: Int = 0,
    /** 3 岁以下婴幼儿照护的本人扣除比例：50 或 100 */
    val infantSharePercent: Int = 100,
    /** 学历（学位）继续教育本年度可扣月数 0-12，标准 400 元/月，最长累计 48 个月 */
    val continuingDegreeMonths: Int = 0,
    /** 职业资格继续教育：取证当年一次性扣 3,600 元 */
    val continuingCertificate: Boolean = false,
    val housingLoan: Boolean = false,
    /** 住房贷款利息本年度可扣月数 0-12，标准 1,000 元/月，最长累计 240 个月 */
    val housingLoanMonths: Int = 12,
    /** 住房租金档位；与住房贷款利息互斥，同时填写时只算租金 */
    val housingRentLevel: HousingRentLevel? = null,
    val housingRentMonths: Int = 12,
    /** 赡养老人：独生子女 3,000 元/月；非独生子女与兄弟姐妹分摊 3,000 元/月总额、每人不超过 1,500 元/月 */
    val elderlyOnlyChild: Boolean = false,
    val elderlySharedMonthlyCents: Long = 0L,
    /** 大病医疗：医保报销后个人自付全年累计（分），超过 15,000 元的部分扣除，年限额 80,000 元 */
    val seriousIllnessSelfPaidCents: Long = 0L,
)

/** 其他扣除：个人养老金、税优健康险、公益慈善捐赠（单位：分，年度） */
data class OtherDeductionInput(
    val personalPensionCents: Long = 0L,
    val commercialHealthCents: Long = 0L,
    /** 公益慈善捐赠：可按输入额扣除，但需符合 30% 限额等条件，引擎会给出超限提示 */
    val charityDonationCents: Long = 0L,
)

/** 全年一次性奖金计税方案 */
enum class BonusScheme(val label: String) {
    SEPARATE("单独计税"),
    COMBINED("并入综合所得"),
}

enum class TaxNoticeLevel { INFO, WARNING }

/** 计算过程中的政策提示（如扣除封顶、互斥、临界点） */
data class TaxNotice(val level: TaxNoticeLevel, val message: String)

/** 单项扣除的计算结果（页面据此展示金额、说明与政策依据） */
data class DeductionItem(
    val key: String,
    val title: String,
    val annualCents: Long,
    val detail: String,
    val policy: String,
)

/** 税率表的一档：upperCents 为该档应纳税所得额上限（含），最高档为 Long.MAX_VALUE */
data class TaxBracket(
    val upperCents: Long,
    val ratePercent: Int,
    val quickDeductionCents: Long,
)

/** 工资薪金累计预扣法明细（第 month 月一行，含累计值） */
data class WithholdingRow(
    val month: Int,
    val cumulativeIncomeCents: Long,
    val cumulativeBasicDeductionCents: Long,
    val cumulativeSpecialDeductionCents: Long,
    val cumulativeAdditionalDeductionCents: Long,
    val cumulativeOtherDeductionCents: Long,
    val cumulativeTaxableCents: Long,
    val cumulativeTaxCents: Long,
    val monthlyWithheldCents: Long,
)

/** 年终奖「单独计税」与「并入综合所得」两方案对比 */
data class BonusComparison(
    /** 年终奖单独计税时，年终奖本身的税额（月度税率表） */
    val separateBonusTaxCents: Long,
    /** 方案 A：综合所得税额 + 年终奖单独税额 */
    val separateTotalTaxCents: Long,
    /** 方案 B：并入后的应纳税所得额、适用档位、全年税额 */
    val combinedTaxableIncomeCents: Long,
    val combinedBracket: TaxBracket,
    val combinedTotalTaxCents: Long,
    val recommended: BonusScheme,
    /** 两方案全年应纳税额差额（绝对值），即选推荐方案可少缴的金额 */
    val savingCents: Long,
    /** 方案 A 的应补（正）/应退（负）税额 */
    val separateBalanceCents: Long,
    /** 方案 B 的应补（正）/应退（负）税额 */
    val combinedBalanceCents: Long,
)

/** 测算输入 */
data class TaxInput(
    val income: IncomeInput = IncomeInput(),
    val socialInsurance: SocialInsuranceInput = SocialInsuranceInput(),
    val additional: AdditionalDeductionInput = AdditionalDeductionInput(),
    val other: OtherDeductionInput = OtherDeductionInput(),
)

/**
 * 测算结果。
 *
 * 税额相关字段有三种口径，页面不要混用：
 *  - [comprehensiveTaxCents]：不含年终奖的综合所得年度税额；
 *  - [bonusComparison]：年终奖两方案；
 *  - [totalTaxCents]：按推荐方案计算的全年应纳税额（无年终奖时等于综合所得税额）。
 */
data class TaxResult(
    val input: TaxInput,
    // ── 收入额（法定折算后的金额）──
    val wageIncomeAmountCents: Long,
    val laborIncomeAmountCents: Long,
    val authorIncomeAmountCents: Long,
    val royaltyIncomeAmountCents: Long,
    val comprehensiveIncomeAmountCents: Long,
    // ── 扣除 ──
    val basicDeductionCents: Long,
    val specialDeductionCents: Long,
    val additionalDeductionCents: Long,
    val additionalItems: List<DeductionItem>,
    val otherDeductionCents: Long,
    val otherItems: List<DeductionItem>,
    // ── 综合所得（不含年终奖）──
    val taxableIncomeCents: Long,
    val bracket: TaxBracket,
    val comprehensiveTaxCents: Long,
    // ── 年终奖与全年税额 ──
    val bonusComparison: BonusComparison?,
    val recommendedScheme: BonusScheme?,
    val totalTaxCents: Long,
    // ── 预缴与汇算 ──
    val wageWithheldCents: Long,
    val otherWithheldCents: Long,
    val bonusWithheldCents: Long,
    val totalWithheldCents: Long,
    val payableCents: Long,
    val refundCents: Long,
    /** 综合税负率，万分位（100 = 1.00%），整数存储避免浮点展示误差 */
    val taxBurdenBasisPoints: Int,
    val withholdingRows: List<WithholdingRow>,
    val notices: List<TaxNotice>,
) {
    /** 模拟申报「收入额」：推荐并入时含年终奖，单独计税时只含综合所得 */
    val filingIncomeAmountCents: Long
        get() = if (recommendedScheme == BonusScheme.COMBINED) {
            comprehensiveIncomeAmountCents + input.income.bonusCents
        } else {
            comprehensiveIncomeAmountCents
        }

    /** 模拟申报「应纳税所得额」 */
    val filingTaxableIncomeCents: Long
        get() = if (recommendedScheme == BonusScheme.COMBINED) {
            bonusComparison?.combinedTaxableIncomeCents ?: taxableIncomeCents
        } else {
            taxableIncomeCents
        }

    /** 模拟申报「适用税率档」 */
    val filingBracket: TaxBracket
        get() = if (recommendedScheme == BonusScheme.COMBINED) {
            bonusComparison?.combinedBracket ?: bracket
        } else {
            bracket
        }

    /** 模拟申报「应纳税额」：单独计税时只体现综合所得，年终奖单列；并入时含年终奖 */
    val filingTaxCents: Long
        get() = if (recommendedScheme == BonusScheme.COMBINED) {
            bonusComparison?.combinedTotalTaxCents ?: comprehensiveTaxCents
        } else {
            comprehensiveTaxCents
        }

    /** 模拟申报「已缴税额」：单独计税时不含年终奖单独预扣，与申报表口径一致 */
    val filingWithheldCents: Long
        get() = if (recommendedScheme == BonusScheme.COMBINED) {
            totalWithheldCents
        } else {
            (totalWithheldCents - bonusWithheldCents).coerceAtLeast(0L)
        }

    /** 模拟申报「应补（退）税额」：正数补税、负数退税 */
    val filingBalanceCents: Long get() = filingTaxCents - filingWithheldCents

    val deductionTotalCents: Long
        get() = basicDeductionCents + specialDeductionCents + additionalDeductionCents + otherDeductionCents

    val hasIncome: Boolean
        get() = comprehensiveIncomeAmountCents > 0L || input.income.bonusCents > 0L

    val hasBonus: Boolean get() = input.income.bonusCents > 0L
}
