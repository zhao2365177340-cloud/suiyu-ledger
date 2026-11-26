package com.suiyu.ledger.tax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 税务引擎单测。
 *
 * 期望值全部来自独立编写的 Python 参考实现（全整数分），不是「跑一遍引擎再抄结果」，
 * 因此这些断言能真正发现公式、税率档、速算扣除数、四舍五入上的错误。
 */
class TaxEngineTest {

    // ────────────────────────────────────────────────────────────
    // 1. 样例总校验（覆盖 7 个税率档 / 年终奖临界点 / 扣除边界 / 补退税）
    // ────────────────────────────────────────────────────────────

    @Test
    fun `all samples match independent reference implementation`() {
        assertTrue("样例数量应不少于 10 组", TaxSamples.all.size >= 10)
        TaxSamples.all.forEach { sample ->
            val actual = TaxEngine.compute(sample.input)
            val mismatches = sample.mismatches(actual)
            assertTrue("${sample.id} ${sample.title} 未通过：${mismatches.joinToString("；")}", mismatches.isEmpty())
            assertTrue("${sample.id} ${sample.title} passed 属性应一致", sample.passed)
        }
    }

    // ────────────────────────────────────────────────────────────
    // 2. 年度税率表：档位边界与速算扣除数
    // ────────────────────────────────────────────────────────────

    @Test
    fun `annual brackets hit correct rate and quick deduction`() {
        val cases = listOf(
            // 应纳税所得额（分） to (税率, 速算扣除数分, 税额分)
            3_000_000L to Triple(3, 0L, 90_000L),
            3_600_000L to Triple(3, 0L, 108_000L),
            3_600_100L to Triple(10, 252_000L, 108_010L),
            14_400_000L to Triple(10, 252_000L, 1_188_000L),
            14_400_100L to Triple(20, 1_692_000L, 1_188_020L),
            30_000_000L to Triple(20, 1_692_000L, 4_308_000L),
            30_000_100L to Triple(25, 3_192_000L, 4_308_025L),
            42_000_000L to Triple(25, 3_192_000L, 7_308_000L),
            42_000_100L to Triple(30, 5_292_000L, 7_308_030L),
            66_000_000L to Triple(30, 5_292_000L, 14_508_000L),
            66_000_100L to Triple(35, 8_592_000L, 14_508_035L),
            96_000_000L to Triple(35, 8_592_000L, 25_008_000L),
            96_000_100L to Triple(45, 18_192_000L, 25_008_045L),
        )
        cases.forEach { (taxable, expected) ->
            val bracket = TaxRules.bracketOf(taxable)
            assertEquals("税率档错误：$taxable", expected.first, bracket.ratePercent)
            assertEquals("速算扣除数错误：$taxable", expected.second, bracket.quickDeductionCents)
            assertEquals("税额错误：$taxable", expected.third, TaxRules.taxOf(taxable))
        }
    }

    @Test
    fun `taxable income just above bracket boundary stays continuous`() {
        // 税率跳档时税额应连续（速算扣除数修正），不能出现断层
        assertEquals(108_000L, TaxRules.taxOf(3_599_999L))
        assertEquals(108_000L, TaxRules.taxOf(3_600_000L))
        assertEquals(108_010L, TaxRules.taxOf(3_600_100L))
        assertEquals(1_188_000L, TaxRules.taxOf(14_399_999L))
        assertEquals(1_188_000L, TaxRules.taxOf(14_400_000L))
        assertEquals(1_188_020L, TaxRules.taxOf(14_400_100L))
    }

    @Test
    fun `zero and negative taxable income yields zero tax`() {
        assertEquals(0L, TaxRules.taxOf(0L))
        assertEquals(0L, TaxRules.taxOf(-1L))
        assertEquals(3, TaxRules.bracketOf(0L).ratePercent)
    }

    // ────────────────────────────────────────────────────────────
    // 3. 收入额折算：劳务 80%、稿酬 56%、特许权 80%
    // ────────────────────────────────────────────────────────────

    @Test
    fun `income amount conversions follow statutory rules`() {
        val income = IncomeInput(
            salaryCents = 10_000_000L,
            laborCents = 10_000_000L,
            authorCents = 10_000_000L,
            royaltyCents = 10_000_000L,
        )
        val result = TaxEngine.compute(TaxInput(income = income))
        assertEquals(10_000_000L, result.wageIncomeAmountCents)
        assertEquals(8_000_000L, result.laborIncomeAmountCents)
        assertEquals(5_600_000L, result.authorIncomeAmountCents)
        assertEquals(8_000_000L, result.royaltyIncomeAmountCents)
        assertEquals(31_600_000L, result.comprehensiveIncomeAmountCents)
        assertEquals(31_600_000L, TaxEngine.comprehensiveIncomeAmountOf(income))
    }

    @Test
    fun `author income rounds 80 percent then 70 percent`() {
        // 123.45 元 → 80% = 98.76 元 → 70% = 69.132 元 → 四舍五入 69.13 元
        val result = TaxEngine.compute(TaxInput(income = IncomeInput(authorCents = 12_345L)))
        assertEquals(6_913L, result.authorIncomeAmountCents)
    }

    // ────────────────────────────────────────────────────────────
    // 4. 基本减除费用与应纳税所得额
    // ────────────────────────────────────────────────────────────

    @Test
    fun `basic deduction is 60000 yuan per year`() {
        assertEquals(6_000_000L, TaxRules.BASIC_DEDUCTION_ANNUAL_CENTS)
        val result = TaxEngine.compute(TaxInput(income = IncomeInput(salaryCents = 9_000_000L)))
        assertEquals(3_000_000L, result.taxableIncomeCents)
        assertEquals(90_000L, result.comprehensiveTaxCents)
    }

    @Test
    fun `no income returns zeros without crashing`() {
        val result = TaxEngine.compute(TaxInput())
        assertEquals(0L, result.comprehensiveIncomeAmountCents)
        assertEquals(0L, result.taxableIncomeCents)
        assertEquals(0L, result.totalTaxCents)
        assertEquals(0L, result.totalWithheldCents)
        assertEquals(0L, result.payableCents)
        assertEquals(0L, result.refundCents)
        assertEquals(0, result.taxBurdenBasisPoints)
        assertEquals(12, result.withholdingRows.size)
        assertNull(result.bonusComparison)
        assertNull(result.recommendedScheme)
        assertFalse(result.hasIncome)
        assertTrue(result.notices.any { it.level == TaxNoticeLevel.INFO })
    }

    // ────────────────────────────────────────────────────────────
    // 5. 专项附加扣除：标准、分摊、封顶与互斥
    // ────────────────────────────────────────────────────────────

    @Test
    fun `child and infant education deductions respect share percentage`() {
        fun childAnnual(count: Int, share: Int): Long =
            TaxEngine.additionalDeductions(
                AdditionalDeductionInput(childCount = count, childSharePercent = share),
            ).first { it.key == TaxEngine.KEY_CHILD }.annualCents

        assertEquals(2_400_000L, childAnnual(1, 100))
        assertEquals(1_200_000L, childAnnual(1, 50))
        assertEquals(2_400_000L, childAnnual(2, 50))
        assertEquals(4_800_000L, childAnnual(2, 100))

        val infant = TaxEngine.additionalDeductions(
            AdditionalDeductionInput(infantCount = 1, infantSharePercent = 50),
        ).first { it.key == TaxEngine.KEY_INFANT }
        assertEquals(1_200_000L, infant.annualCents)
    }

    @Test
    fun `continuing education supports degree months and certificate`() {
        fun continuing(input: AdditionalDeductionInput): Long =
            TaxEngine.additionalDeductions(input).first { it.key == TaxEngine.KEY_CONTINUING }.annualCents

        assertEquals(480_000L, continuing(AdditionalDeductionInput(continuingDegreeMonths = 12)))
        assertEquals(360_000L, continuing(AdditionalDeductionInput(continuingCertificate = true)))
        // 学历继续教育与职业资格继续教育可以同时享受
        assertEquals(
            840_000L,
            continuing(AdditionalDeductionInput(continuingDegreeMonths = 12, continuingCertificate = true)),
        )
        // 年内月数上限 12，超出不叠加
        assertEquals(480_000L, continuing(AdditionalDeductionInput(continuingDegreeMonths = 18)))
    }

    @Test
    fun `housing loan and housing rent are mutually exclusive`() {
        val loanAnnual = TaxEngine.additionalDeductions(
            AdditionalDeductionInput(housingLoan = true, housingLoanMonths = 12),
        ).first { it.key == TaxEngine.KEY_HOUSING_LOAN }.annualCents
        assertEquals(1_200_000L, loanAnnual)

        val both = AdditionalDeductionInput(housingLoan = true, housingRentLevel = HousingRentLevel.FIRST)
        val items = TaxEngine.additionalDeductions(both)
        assertEquals(0L, items.first { it.key == TaxEngine.KEY_HOUSING_LOAN }.annualCents)
        assertEquals(1_800_000L, items.first { it.key == TaxEngine.KEY_HOUSING_RENT }.annualCents)

        val result = TaxEngine.compute(TaxInput(additional = both))
        assertTrue(result.notices.any { it.level == TaxNoticeLevel.WARNING && it.message.contains("互斥") })
    }

    @Test
    fun `housing rent has three tiers`() {
        fun rentAnnual(level: HousingRentLevel): Long =
            TaxEngine.additionalDeductions(
                AdditionalDeductionInput(housingRentLevel = level, housingRentMonths = 12),
            ).first { it.key == TaxEngine.KEY_HOUSING_RENT }.annualCents

        assertEquals(1_800_000L, rentAnnual(HousingRentLevel.FIRST))
        assertEquals(1_320_000L, rentAnnual(HousingRentLevel.SECOND))
        assertEquals(960_000L, rentAnnual(HousingRentLevel.THIRD))
    }

    @Test
    fun `elderly care respects only child and shared cap`() {
        fun elderlyAnnual(input: AdditionalDeductionInput): Long =
            TaxEngine.additionalDeductions(input).first { it.key == TaxEngine.KEY_ELDERLY }.annualCents

        assertEquals(3_600_000L, elderlyAnnual(AdditionalDeductionInput(elderlyOnlyChild = true)))
        assertEquals(1_200_000L, elderlyAnnual(AdditionalDeductionInput(elderlySharedMonthlyCents = 100_000L)))
        // 非独生每人每月不超过 1,500 元，超出部分封顶
        assertEquals(1_800_000L, elderlyAnnual(AdditionalDeductionInput(elderlySharedMonthlyCents = 200_000L)))
    }

    @Test
    fun `serious illness starts at 15000 and caps at 80000`() {
        fun seriousAnnual(selfPaidCents: Long): Long =
            TaxEngine.additionalDeductions(
                AdditionalDeductionInput(seriousIllnessSelfPaidCents = selfPaidCents),
            ).first { it.key == TaxEngine.KEY_SERIOUS_ILLNESS }.annualCents

        assertEquals(0L, seriousAnnual(0L))
        assertEquals(0L, seriousAnnual(1_500_000L)) // 正好 15,000 元不扣
        assertEquals(100L, seriousAnnual(1_500_100L)) // 超 1 元扣 1 元
        assertEquals(3_500_000L, seriousAnnual(5_000_000L)) // 自付 5 万扣 3.5 万
        assertEquals(8_000_000L, seriousAnnual(9_500_000L)) // 自付 9.5 万触顶 8 万
        assertEquals(8_000_000L, seriousAnnual(30_000_000L))
    }

    @Test
    fun `serious illness is only deducted at settlement not in withholding`() {
        val input = TaxInput(
            income = IncomeInput(salaryCents = 30_000_000L),
            additional = AdditionalDeductionInput(seriousIllnessSelfPaidCents = 10_000_000L),
        )
        val result = TaxEngine.compute(input)
        // 预扣阶段不扣大病医疗：全年预扣 = 工资 300,000 元（无其他扣除）的税额
        assertEquals(3_108_000L, result.wageWithheldCents)
        assertEquals(1_508_000L, result.totalTaxCents)
        assertEquals(1_600_000L, result.refundCents)
        assertEquals(0L, result.payableCents)
    }

    // ────────────────────────────────────────────────────────────
    // 6. 其他扣除：限额与捐赠提示
    // ────────────────────────────────────────────────────────────

    @Test
    fun `other deductions are capped by annual limits`() {
        val items = TaxEngine.otherDeductions(
            OtherDeductionInput(
                personalPensionCents = 1_500_000L,
                commercialHealthCents = 300_000L,
                charityDonationCents = 1_000_000L,
            ),
        )
        assertEquals(1_200_000L, items.first { it.key == TaxEngine.KEY_PENSION }.annualCents)
        assertEquals(240_000L, items.first { it.key == TaxEngine.KEY_HEALTH_INSURANCE }.annualCents)
        assertEquals(1_000_000L, items.first { it.key == TaxEngine.KEY_CHARITY }.annualCents)

        val result = TaxEngine.compute(
            TaxInput(
                income = IncomeInput(salaryCents = 24_000_000L),
                other = OtherDeductionInput(
                    personalPensionCents = 1_500_000L,
                    commercialHealthCents = 300_000L,
                    charityDonationCents = 1_000_000L,
                ),
            ),
        )
        assertEquals(2_440_000L, result.otherDeductionCents)
        assertEquals(15_560_000L, result.taxableIncomeCents)
        assertEquals(1_420_000L, result.totalTaxCents)
        assertTrue(result.notices.any { it.message.contains("个人养老金") })
        assertTrue(result.notices.any { it.message.contains("税优健康险") })
    }

    @Test
    fun `charity donation over 30 percent limit produces warning`() {
        // 年工资 20 万，捐赠 6 万：扣除限额 = (200000-60000)×30% = 42,000 元
        val result = TaxEngine.compute(
            TaxInput(
                income = IncomeInput(salaryCents = 20_000_000L),
                other = OtherDeductionInput(charityDonationCents = 6_000_000L),
            ),
        )
        assertTrue(result.notices.any { it.level == TaxNoticeLevel.WARNING && it.message.contains("30%") })
        // 需求要求按用户输入额直接扣，因此仍按 6 万扣除
        assertEquals(6_000_000L, result.otherDeductionCents)
        assertEquals(8_000_000L, result.taxableIncomeCents)
        assertEquals(548_000L, result.totalTaxCents)
    }

    // ────────────────────────────────────────────────────────────
    // 7. 三险一金估算
    // ────────────────────────────────────────────────────────────

    @Test
    fun `social insurance estimate uses standard rates`() {
        val estimate = TaxRules.estimateSocialInsurance(monthlySalaryCents = 2_500_000L, housingFundPercent = 12)
        assertEquals(200_000L, estimate.pensionCents) // 8%
        assertEquals(50_000L, estimate.medicalCents) // 2%
        assertEquals(12_500L, estimate.unemploymentCents) // 0.5%
        assertEquals(300_000L, estimate.housingFundCents) // 12%
        assertEquals(562_500L, estimate.monthlyTotalCents)
        assertEquals(6_750_000L, estimate.annualTotalCents)

        // 公积金比例限制在 5%-12%
        assertEquals(125_000L, TaxRules.estimateSocialInsurance(2_500_000L, 1).housingFundCents)
        assertEquals(300_000L, TaxRules.estimateSocialInsurance(2_500_000L, 99).housingFundCents)
    }

    // ────────────────────────────────────────────────────────────
    // 8. 工资薪金累计预扣法
    // ────────────────────────────────────────────────────────────

    @Test
    fun `withholding rows are cumulative and end at annual wage tax`() {
        val result = TaxEngine.compute(
            TaxInput(
                income = IncomeInput(salaryCents = 30_000_000L),
                socialInsurance = SocialInsuranceInput(pensionCents = 500_000L),
            ),
        )
        val rows = result.withholdingRows
        assertEquals(12, rows.size)
        assertEquals(1, rows.first().month)
        assertEquals(12, rows.last().month)
        // 第 1 个月：收入 2.5 万，基本减除 5 千，三险一金 5 千 → 累计应税 1.5 万 → 3%
        assertEquals(2_500_000L, rows[0].cumulativeIncomeCents)
        assertEquals(500_000L, rows[0].cumulativeBasicDeductionCents)
        assertEquals(500_000L, rows[0].cumulativeSpecialDeductionCents)
        assertEquals(1_500_000L, rows[0].cumulativeTaxableCents)
        assertEquals(45_000L, rows[0].cumulativeTaxCents)
        assertEquals(45_000L, rows[0].monthlyWithheldCents)
        // 第 3 个月累计应税 4.5 万 → 10% 档
        assertEquals(4_500_000L, rows[2].cumulativeTaxableCents)
        assertEquals(198_000L, rows[2].cumulativeTaxCents)
        assertEquals(108_000L, rows[2].monthlyWithheldCents)
        // 年末累计等于全年工资预扣
        assertEquals(30_000_000L, rows.last().cumulativeIncomeCents)
        assertEquals(18_000_000L, rows.last().cumulativeTaxableCents)
        assertEquals(1_908_000L, rows.last().cumulativeTaxCents)
        assertEquals(1_908_000L, result.wageWithheldCents)
        assertEquals(1_908_000L, rows.sumOf { it.monthlyWithheldCents })
    }

    @Test
    fun `withholding includes special additional and other deductions`() {
        val input = TaxInput(
            income = IncomeInput(salaryCents = 30_000_000L),
            socialInsurance = SocialInsuranceInput(pensionCents = 500_000L),
            additional = AdditionalDeductionInput(childCount = 1, childSharePercent = 100),
            other = OtherDeductionInput(personalPensionCents = 1_200_000L),
        )
        val result = TaxEngine.compute(input)
        // 年度扣除：60,000 + 60,000 + 24,000 + 12,000 = 156,000 → 应税 144,000
        assertEquals(14_400_000L, result.taxableIncomeCents)
        assertEquals(1_188_000L, result.comprehensiveTaxCents)
        assertEquals(1_188_000L, result.wageWithheldCents)
        assertEquals(0L, result.payableCents)
        assertEquals(0L, result.refundCents)
    }

    // ────────────────────────────────────────────────────────────
    // 9. 年终奖：单独计税、临界点、两方案比较
    // ────────────────────────────────────────────────────────────

    @Test
    fun `bonus separate tax uses monthly bracket`() {
        assertEquals(108_000L, TaxRules.bonusTaxOf(3_600_000L)) // 36,000 元 → 3%
        assertEquals(1_419_000L, TaxRules.bonusTaxOf(14_400_000L)) // 144,000 元 → 10%
        assertEquals(5_859_000L, TaxRules.bonusTaxOf(30_000_000L)) // 300,000 元 → 20%
        assertEquals(10_234_000L, TaxRules.bonusTaxOf(42_000_000L)) // 420,000 元 → 25%
        assertEquals(19_359_000L, TaxRules.bonusTaxOf(66_000_000L)) // 660,000 元 → 30%
        assertEquals(32_884_000L, TaxRules.bonusTaxOf(96_000_000L)) // 960,000 元 → 35%
        assertEquals(0L, TaxRules.bonusTaxOf(0L))
    }

    @Test
    fun `bonus just over 36000 and 144000 jumps in tax`() {
        assertEquals(108_000L, TaxRules.bonusTaxOf(3_600_000L))
        assertEquals(339_010L, TaxRules.bonusTaxOf(3_600_100L))
        assertEquals(231_010L, TaxRules.bonusTaxOf(3_600_100L) - TaxRules.bonusTaxOf(3_600_000L))

        assertEquals(1_419_000L, TaxRules.bonusTaxOf(14_400_000L))
        assertEquals(2_739_020L, TaxRules.bonusTaxOf(14_400_100L))
        assertEquals(1_320_020L, TaxRules.bonusTaxOf(14_400_100L) - TaxRules.bonusTaxOf(14_400_000L))
    }

    @Test
    fun `bonus comparison recommends separate scheme for typical case`() {
        val result = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 12_000_000L, bonusCents = 3_600_000L)),
        )
        assertNotNull(result.bonusComparison)
        val comparison = result.bonusComparison!!
        assertEquals(BonusScheme.SEPARATE, comparison.recommended)
        assertEquals(108_000L, comparison.separateBonusTaxCents)
        assertEquals(456_000L, comparison.separateTotalTaxCents)
        assertEquals(9_600_000L, comparison.combinedTaxableIncomeCents)
        assertEquals(708_000L, comparison.combinedTotalTaxCents)
        assertEquals(252_000L, comparison.savingCents)
        assertEquals(456_000L, result.totalTaxCents)
        assertEquals(108_000L, result.bonusWithheldCents)
        assertEquals(348_000L, result.wageWithheldCents)
        assertEquals(0L, result.payableCents)
        assertEquals(0L, result.refundCents)
    }

    @Test
    fun `bonus comparison can recommend combining into comprehensive income`() {
        // 工资 5 万 + 年终奖 3.6 万：并入后仍只用到 3% 档，比单独计税少缴 3 万元
        val result = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 5_000_000L, bonusCents = 3_600_000L)),
        )
        val comparison = result.bonusComparison!!
        assertEquals(BonusScheme.COMBINED, comparison.recommended)
        assertEquals(108_000L, comparison.separateTotalTaxCents)
        assertEquals(78_000L, comparison.combinedTotalTaxCents)
        assertEquals(30_000L, comparison.savingCents)
        assertEquals(78_000L, result.totalTaxCents)
        assertEquals(108_000L, result.totalWithheldCents)
        // 预缴按单独计税扣了 1,080 元，汇算按并入 780 元 → 退 300 元
        assertEquals(30_000L, result.refundCents)
        assertEquals(0L, result.payableCents)
    }

    @Test
    fun `bonus 144000 critical point sample`() {
        val result = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 24_000_000L, bonusCents = 14_400_000L)),
        )
        val comparison = result.bonusComparison!!
        assertEquals(BonusScheme.SEPARATE, comparison.recommended)
        assertEquals(1_419_000L, comparison.separateBonusTaxCents)
        assertEquals(3_327_000L, comparison.separateTotalTaxCents)
        assertEquals(32_400_000L, comparison.combinedTaxableIncomeCents)
        assertEquals(4_908_000L, comparison.combinedTotalTaxCents)
        assertEquals(1_581_000L, comparison.savingCents)
        assertEquals(3_327_000L, result.totalTaxCents)
    }

    // ────────────────────────────────────────────────────────────
    // 10. 汇算：退税与补税
    // ────────────────────────────────────────────────────────────

    @Test
    fun `refund case when only author income`() {
        val result = TaxEngine.compute(TaxInput(income = IncomeInput(authorCents = 10_000_000L)))
        assertEquals(0L, result.totalTaxCents)
        assertEquals(1_120_000L, result.otherWithheldCents)
        assertEquals(1_120_000L, result.refundCents)
        assertEquals(0L, result.payableCents)
        assertEquals(0L, result.wageWithheldCents)
    }

    @Test
    fun `payable case when labor income pushes marginal bracket`() {
        val result = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 90_000_000L, laborCents = 30_000_000L)),
        )
        assertEquals(108_000_000L, result.taxableIncomeCents)
        assertEquals(30_408_000L, result.comprehensiveTaxCents)
        assertEquals(20_808_000L, result.wageWithheldCents)
        assertEquals(8_900_000L, result.otherWithheldCents)
        assertEquals(700_000L, result.payableCents)
        assertEquals(0L, result.refundCents)
    }

    // ────────────────────────────────────────────────────────────
    // 11. 税负率、输入容错、申报口径
    // ────────────────────────────────────────────────────────────

    @Test
    fun `tax burden rate is stored in basis points`() {
        val low = TaxEngine.compute(TaxInput(income = IncomeInput(salaryCents = 9_000_000L)))
        assertEquals(100, low.taxBurdenBasisPoints) // 900 / 90,000 = 1.00%
        val mid = TaxEngine.compute(TaxInput(income = IncomeInput(salaryCents = 15_000_000L)))
        assertEquals(432, mid.taxBurdenBasisPoints) // 6,480 / 150,000 = 4.32%
        assertEquals(0, TaxEngine.compute(TaxInput()).taxBurdenBasisPoints)
    }

    @Test
    fun `negative and oversized inputs are normalized`() {
        val negative = TaxEngine.compute(
            TaxInput(
                income = IncomeInput(salaryCents = -1_000_000L, laborCents = -5L),
                socialInsurance = SocialInsuranceInput(pensionCents = -1L),
                other = OtherDeductionInput(personalPensionCents = -1L),
            ),
        )
        assertEquals(0L, negative.comprehensiveIncomeAmountCents)
        assertEquals(0L, negative.totalTaxCents)

        val oversized = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = TaxRules.MAX_INPUT_CENTS + 1L)),
        )
        // 1 亿元上限：应缴税额稳定为 (1亿 - 6万) × 45% - 181,920 = 44,791,080 元
        assertEquals(4_479_108_000L, oversized.totalTaxCents)
    }

    @Test
    fun `filing fields match recommended scheme`() {
        val separate = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 12_000_000L, bonusCents = 3_600_000L)),
        )
        // 单独计税：申报表只体现综合所得，年终奖不在申报表收入额与已缴税额中
        assertEquals(12_000_000L, separate.filingIncomeAmountCents)
        assertEquals(6_000_000L, separate.filingTaxableIncomeCents)
        assertEquals(10, separate.filingBracket.ratePercent)
        assertEquals(348_000L, separate.filingTaxCents)
        assertEquals(348_000L, separate.filingWithheldCents)
        assertEquals(0L, separate.filingBalanceCents)

        val combined = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 5_000_000L, bonusCents = 3_600_000L)),
        )
        // 并入综合所得：申报表收入额含年终奖，已缴税额含年终奖单独预扣
        assertEquals(8_600_000L, combined.filingIncomeAmountCents)
        assertEquals(2_600_000L, combined.filingTaxableIncomeCents)
        assertEquals(3, combined.filingBracket.ratePercent)
        assertEquals(78_000L, combined.filingTaxCents)
        assertEquals(108_000L, combined.filingWithheldCents)
        assertEquals(-30_000L, combined.filingBalanceCents)
    }

    @Test
    fun `bonus threshold crossing produces warning notice`() {
        val result = TaxEngine.compute(
            TaxInput(income = IncomeInput(salaryCents = 12_000_000L, bonusCents = 3_600_100L)),
        )
        assertTrue(result.notices.any { notice ->
            notice.level == TaxNoticeLevel.WARNING && notice.message.contains("临界点")
        })
    }
}
