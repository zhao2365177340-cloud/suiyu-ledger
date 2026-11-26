package com.suiyu.ledger.tax

/**
 * 计算器自检样例。
 *
 * 每个样例的期望值都由独立编写的 Python 参考实现逐项复算得到（全整数分），
 * 覆盖：7 个税率档、年终奖 36,000 / 144,000 临界点、专项附加扣除边界、
 * 预扣与汇算差异（退税 / 补税）、大病医疗汇算扣除、其他扣除限额。
 * 页面「计算器自检」逐条重算并与期望值比对，单测同样逐条断言。
 */
data class TaxSample(
    val id: String,
    val title: String,
    val scenario: String,
    val input: TaxInput,
    val expectedTaxableCents: Long,
    val expectedComprehensiveTaxCents: Long,
    val expectedTotalTaxCents: Long,
    val expectedWithheldCents: Long,
    /** 汇算结果：正 = 应补税，负 = 应退税 */
    val expectedBalanceCents: Long,
    val expectedScheme: BonusScheme?,
) {
    /** 与实际测算结果比对，返回不一致的字段名列表（空 = 通过） */
    fun mismatches(actual: TaxResult): List<String> = buildList {
        if (actual.taxableIncomeCents != expectedTaxableCents) {
            add("应纳税所得额：期望 ${TaxRules.yuanText(expectedTaxableCents)}，实际 ${TaxRules.yuanText(actual.taxableIncomeCents)}")
        }
        if (actual.comprehensiveTaxCents != expectedComprehensiveTaxCents) {
            add("综合所得税额：期望 ${TaxRules.yuanText(expectedComprehensiveTaxCents)}，实际 ${TaxRules.yuanText(actual.comprehensiveTaxCents)}")
        }
        if (actual.totalTaxCents != expectedTotalTaxCents) {
            add("全年应纳税额：期望 ${TaxRules.yuanText(expectedTotalTaxCents)}，实际 ${TaxRules.yuanText(actual.totalTaxCents)}")
        }
        if (actual.totalWithheldCents != expectedWithheldCents) {
            add("已预缴：期望 ${TaxRules.yuanText(expectedWithheldCents)}，实际 ${TaxRules.yuanText(actual.totalWithheldCents)}")
        }
        val actualBalance = actual.payableCents - actual.refundCents
        if (actualBalance != expectedBalanceCents) {
            add("应补/退：期望 ${TaxRules.yuanText(expectedBalanceCents)}，实际 ${TaxRules.yuanText(actualBalance)}")
        }
        if (actual.recommendedScheme != expectedScheme) {
            add("年终奖方案：期望 ${expectedScheme?.label ?: "无"}，实际 ${actual.recommendedScheme?.label ?: "无"}")
        }
    }

    val passed: Boolean get() = mismatches(TaxEngine.compute(input)).isEmpty()

    val balanceLabel: String
        get() = when {
            expectedBalanceCents > 0L -> "补 ${TaxRules.yuanText(expectedBalanceCents)}"
            expectedBalanceCents < 0L -> "退 ${TaxRules.yuanText(-expectedBalanceCents)}"
            else -> "不退不补"
        }
}

object TaxSamples {

    private fun sample(
        id: String,
        title: String,
        scenario: String,
        input: TaxInput,
        expectedTaxableCents: Long,
        expectedComprehensiveTaxCents: Long,
        expectedTotalTaxCents: Long,
        expectedWithheldCents: Long,
        expectedBalanceCents: Long,
        expectedScheme: BonusScheme? = null,
    ): TaxSample = TaxSample(
        id = id,
        title = title,
        scenario = scenario,
        input = input,
        expectedTaxableCents = expectedTaxableCents,
        expectedComprehensiveTaxCents = expectedComprehensiveTaxCents,
        expectedTotalTaxCents = expectedTotalTaxCents,
        expectedWithheldCents = expectedWithheldCents,
        expectedBalanceCents = expectedBalanceCents,
        expectedScheme = expectedScheme,
    )

    /** 10 组以上样例：前 7 组覆盖综合所得税率表 7 档，其余覆盖临界点与汇算差异 */
    val all: List<TaxSample> = listOf(
        sample(
            id = "S1",
            title = "3% 档 · 单一工资",
            scenario = "年工资 90,000 元，仅基本减除费用",
            input = TaxInput(income = IncomeInput(salaryCents = 9_000_000L)),
            expectedTaxableCents = 3_000_000L,
            expectedComprehensiveTaxCents = 90_000L,
            expectedTotalTaxCents = 90_000L,
            expectedWithheldCents = 90_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S2",
            title = "10% 档 · 单一工资",
            scenario = "年工资 150,000 元，仅基本减除费用",
            input = TaxInput(income = IncomeInput(salaryCents = 15_000_000L)),
            expectedTaxableCents = 9_000_000L,
            expectedComprehensiveTaxCents = 648_000L,
            expectedTotalTaxCents = 648_000L,
            expectedWithheldCents = 648_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S3",
            title = "20% 档 · 三险一金估算",
            scenario = "月薪 25,000 元；养老 8% / 医疗 2% / 失业 0.5% / 公积金 12%",
            input = TaxInput(
                income = IncomeInput(salaryCents = 30_000_000L),
                socialInsurance = SocialInsuranceInput(
                    pensionCents = 200_000L,
                    medicalCents = 50_000L,
                    unemploymentCents = 12_500L,
                    housingFundCents = 300_000L,
                ),
            ),
            expectedTaxableCents = 17_250_000L,
            expectedComprehensiveTaxCents = 1_758_000L,
            expectedTotalTaxCents = 1_758_000L,
            expectedWithheldCents = 1_758_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S4",
            title = "25% 档 · 三险一金",
            scenario = "年工资 500,000 元，三险一金月缴 5,000 元",
            input = TaxInput(
                income = IncomeInput(salaryCents = 50_000_000L),
                socialInsurance = SocialInsuranceInput(pensionCents = 500_000L),
            ),
            expectedTaxableCents = 38_000_000L,
            expectedComprehensiveTaxCents = 6_308_000L,
            expectedTotalTaxCents = 6_308_000L,
            expectedWithheldCents = 6_308_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S5",
            title = "30% 档 · 专项附加扣除",
            scenario = "年工资 660,000 元；1 孩子女教育 100% + 赡养老人独生 + 房贷利息全年",
            input = TaxInput(
                income = IncomeInput(salaryCents = 66_000_000L),
                socialInsurance = SocialInsuranceInput(pensionCents = 500_000L),
                additional = AdditionalDeductionInput(
                    childCount = 1,
                    childSharePercent = 100,
                    housingLoan = true,
                    elderlyOnlyChild = true,
                ),
            ),
            expectedTaxableCents = 46_800_000L,
            expectedComprehensiveTaxCents = 8_748_000L,
            expectedTotalTaxCents = 8_748_000L,
            expectedWithheldCents = 8_748_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S6",
            title = "35% 档 · 多项扣除",
            scenario = "年工资 900,000 元；1 孩子女教育 50% + 非独生赡养老人 1,500 元/月 + 学历继续教育全年",
            input = TaxInput(
                income = IncomeInput(salaryCents = 90_000_000L),
                socialInsurance = SocialInsuranceInput(pensionCents = 500_000L),
                additional = AdditionalDeductionInput(
                    childCount = 1,
                    childSharePercent = 50,
                    continuingDegreeMonths = 12,
                    elderlySharedMonthlyCents = 150_000L,
                ),
            ),
            expectedTaxableCents = 74_520_000L,
            expectedComprehensiveTaxCents = 17_490_000L,
            expectedTotalTaxCents = 17_490_000L,
            expectedWithheldCents = 17_490_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S7",
            title = "45% 档 · 高收入",
            scenario = "年工资 1,500,000 元，三险一金月缴 5,000 元",
            input = TaxInput(
                income = IncomeInput(salaryCents = 150_000_000L),
                socialInsurance = SocialInsuranceInput(pensionCents = 500_000L),
            ),
            expectedTaxableCents = 138_000_000L,
            expectedComprehensiveTaxCents = 43_908_000L,
            expectedTotalTaxCents = 43_908_000L,
            expectedWithheldCents = 43_908_000L,
            expectedBalanceCents = 0L,
        ),
        sample(
            id = "S8",
            title = "年终奖临界点前 36,000",
            scenario = "年工资 120,000 元 + 年终奖 36,000 元，单独计税更优",
            input = TaxInput(
                income = IncomeInput(salaryCents = 12_000_000L, bonusCents = 3_600_000L),
            ),
            expectedTaxableCents = 6_000_000L,
            expectedComprehensiveTaxCents = 348_000L,
            expectedTotalTaxCents = 456_000L,
            expectedWithheldCents = 456_000L,
            expectedBalanceCents = 0L,
            expectedScheme = BonusScheme.SEPARATE,
        ),
        sample(
            id = "S9",
            title = "年终奖临界点后 36,001",
            scenario = "年终奖多发 1 元，月均 3,000.08 元使税率从 3% 跳到 10%",
            input = TaxInput(
                income = IncomeInput(salaryCents = 12_000_000L, bonusCents = 3_600_100L),
            ),
            expectedTaxableCents = 6_000_000L,
            expectedComprehensiveTaxCents = 348_000L,
            expectedTotalTaxCents = 687_010L,
            expectedWithheldCents = 687_010L,
            expectedBalanceCents = 0L,
            expectedScheme = BonusScheme.SEPARATE,
        ),
        sample(
            id = "S10",
            title = "年终奖临界点 144,000",
            scenario = "年工资 240,000 元 + 年终奖 144,000 元（月均正好 12,000 元）",
            input = TaxInput(
                income = IncomeInput(salaryCents = 24_000_000L, bonusCents = 14_400_000L),
            ),
            expectedTaxableCents = 18_000_000L,
            expectedComprehensiveTaxCents = 1_908_000L,
            expectedTotalTaxCents = 3_327_000L,
            expectedWithheldCents = 3_327_000L,
            expectedBalanceCents = 0L,
            expectedScheme = BonusScheme.SEPARATE,
        ),
        sample(
            id = "S11",
            title = "大病医疗 · 汇算退税",
            scenario = "年工资 300,000 元，医保后自付 100,000 元：汇算扣除 80,000 元，预扣未扣，产生退税",
            input = TaxInput(
                income = IncomeInput(salaryCents = 30_000_000L),
                additional = AdditionalDeductionInput(seriousIllnessSelfPaidCents = 10_000_000L),
            ),
            expectedTaxableCents = 16_000_000L,
            expectedComprehensiveTaxCents = 1_508_000L,
            expectedTotalTaxCents = 1_508_000L,
            expectedWithheldCents = 3_108_000L,
            expectedBalanceCents = -1_600_000L,
        ),
        sample(
            id = "S12",
            title = "劳务报酬 + 工资 · 汇算补税",
            scenario = "年工资 900,000 元 + 劳务报酬 300,000 元，劳务预扣 40% 仍不足以覆盖综合所得边际税率",
            input = TaxInput(
                income = IncomeInput(salaryCents = 90_000_000L, laborCents = 30_000_000L),
            ),
            expectedTaxableCents = 108_000_000L,
            expectedComprehensiveTaxCents = 30_408_000L,
            expectedTotalTaxCents = 30_408_000L,
            expectedWithheldCents = 29_708_000L,
            expectedBalanceCents = 700_000L,
        ),
        sample(
            id = "S13",
            title = "稿酬 · 收入额 56% 与退税",
            scenario = "仅稿酬 100,000 元，收入额 56,000 元未达 6 万，预扣税款可全额退",
            input = TaxInput(income = IncomeInput(authorCents = 10_000_000L)),
            expectedTaxableCents = 0L,
            expectedComprehensiveTaxCents = 0L,
            expectedTotalTaxCents = 0L,
            expectedWithheldCents = 1_120_000L,
            expectedBalanceCents = -1_120_000L,
        ),
        sample(
            id = "S14",
            title = "其他扣除限额",
            scenario = "年工资 240,000 元；个人养老金 15,000 元（限 12,000）、税优健康险 3,000 元（限 2,400）、捐赠 10,000 元",
            input = TaxInput(
                income = IncomeInput(salaryCents = 24_000_000L),
                other = OtherDeductionInput(
                    personalPensionCents = 1_500_000L,
                    commercialHealthCents = 300_000L,
                    charityDonationCents = 1_000_000L,
                ),
            ),
            expectedTaxableCents = 15_560_000L,
            expectedComprehensiveTaxCents = 1_420_000L,
            expectedTotalTaxCents = 1_420_000L,
            expectedWithheldCents = 1_420_000L,
            expectedBalanceCents = 0L,
        ),
    )

    /** 页面自检只展示前 10 组即可覆盖主要规则；此处全部提供 */
    val showcase: List<TaxSample> get() = all
}
