package com.suiyu.ledger.tax

/**
 * 个人所得税计算引擎（纯 Kotlin，无 Android 依赖）。
 *
 * 输入 → 输出完全由 [TaxRules] 的整数规则决定，可被 JUnit 与外部脚本独立复算；
 * 页面只负责收集输入、展示结果，不重复任何税额公式。
 *
 * 计算主线：
 *  1. 收入额：工资薪金全额；劳务 ×80%；稿酬 ×80%×70%；特许权使用费 ×80%；
 *  2. 综合所得应纳税所得额 = 收入额 − 60,000 − 三险一金 − 专项附加扣除 − 其他扣除；
 *  3. 综合所得税额 = 应纳税所得额 × 年度税率 − 速算扣除数；
 *  4. 工资薪金按累计预扣法逐月计算全年已预缴；劳务/稿酬/特许权按单次支付规则估算预扣；
 *  5. 年终奖分别计算「单独计税」与「并入综合所得」，取低推荐并给出差额；
 *  6. 应补/退税额 = 全年应纳税额 − 全年已预缴。
 *
 * 重要口径：大病医疗专项附加扣除只能在年度汇算时办理，预扣预缴阶段不扣除，
 * 因此本引擎在 12 个月预扣表中把它剔除，汇算时再计入——这会产生真实的退税场景。
 */
object TaxEngine {

    const val KEY_CHILD = "child"
    const val KEY_INFANT = "infant"
    const val KEY_CONTINUING = "continuing"
    const val KEY_HOUSING_LOAN = "housingLoan"
    const val KEY_HOUSING_RENT = "housingRent"
    const val KEY_ELDERLY = "elderly"
    const val KEY_SERIOUS_ILLNESS = "seriousIllness"
    const val KEY_PENSION = "pension"
    const val KEY_HEALTH_INSURANCE = "healthInsurance"
    const val KEY_CHARITY = "charity"

    /** 月数规整：0..12 */
    private fun monthsOf(value: Int, max: Int = 12): Int = value.coerceIn(0, max)

    private fun sanitizeIncome(income: IncomeInput): IncomeInput = IncomeInput(
        salaryCents = TaxRules.normalize(income.salaryCents),
        bonusCents = TaxRules.normalize(income.bonusCents),
        laborCents = TaxRules.normalize(income.laborCents),
        authorCents = TaxRules.normalize(income.authorCents),
        royaltyCents = TaxRules.normalize(income.royaltyCents),
    )

    private fun sanitizeSocial(social: SocialInsuranceInput): SocialInsuranceInput = SocialInsuranceInput(
        pensionCents = TaxRules.normalize(social.pensionCents),
        medicalCents = TaxRules.normalize(social.medicalCents),
        unemploymentCents = TaxRules.normalize(social.unemploymentCents),
        housingFundCents = TaxRules.normalize(social.housingFundCents),
    )

    /** 计入综合所得的收入额（已按法定比例折算），供页面实时展示 */
    fun comprehensiveIncomeAmountOf(income: IncomeInput): Long {
        val value = sanitizeIncome(income)
        return value.salaryCents +
            TaxRules.percentOf(value.laborCents, 80) +
            TaxRules.percentOf(TaxRules.percentOf(value.authorCents, 80), 70) +
            TaxRules.percentOf(value.royaltyCents, 80)
    }

    // ────────────────────────────────────────────────────────────
    // 专项附加扣除、其他扣除：逐项计算 + 政策说明
    // ────────────────────────────────────────────────────────────

    /** 计算 7 项专项附加扣除的年度金额（全部 7 项都会返回，未填报为 0） */
    fun additionalDeductions(input: AdditionalDeductionInput): List<DeductionItem> {
        val childCount = input.childCount.coerceIn(0, 10)
        val childShare = input.childSharePercent.coerceIn(0, 100)
        val childAnnual = TaxRules.percentOf(
            TaxRules.CHILD_EDUCATION_MONTHLY_CENTS * 12L * childCount,
            childShare,
        )

        val infantCount = input.infantCount.coerceIn(0, 10)
        val infantShare = input.infantSharePercent.coerceIn(0, 100)
        val infantAnnual = TaxRules.percentOf(
            TaxRules.INFANT_CARE_MONTHLY_CENTS * 12L * infantCount,
            infantShare,
        )

        val degreeMonths = monthsOf(input.continuingDegreeMonths)
        val continuingAnnual = TaxRules.CONTINUING_DEGREE_MONTHLY_CENTS * degreeMonths +
            if (input.continuingCertificate) TaxRules.CONTINUING_CERTIFICATE_ANNUAL_CENTS else 0L
        val continuingDetail = buildList {
            if (degreeMonths > 0) add("学历（学位）继续教育 400 元/月 × $degreeMonths 个月")
            if (input.continuingCertificate) add("职业资格继续教育 3,600 元（取证当年）")
        }.joinToString("；").ifBlank { "未填报" }

        // 住房贷款利息与住房租金互斥：同时填写时以租金为准
        val loanMonths = monthsOf(input.housingLoanMonths)
        val loanAnnual = if (input.housingRentLevel == null && input.housingLoan) {
            TaxRules.HOUSING_LOAN_MONTHLY_CENTS * loanMonths
        } else {
            0L
        }
        val loanDetail = when {
            input.housingRentLevel != null && input.housingLoan -> "已选择住房租金，房贷利息不再重复扣除"
            loanAnnual > 0L -> "首套住房贷款利息 1,000 元/月 × $loanMonths 个月"
            else -> "未填报"
        }

        val rentMonths = monthsOf(input.housingRentMonths)
        val rentLevel = input.housingRentLevel
        val rentAnnual = rentLevel?.monthlyCents?.times(rentMonths.toLong()) ?: 0L
        val rentDetail = if (rentLevel == null) {
            "未填报"
        } else {
            "${rentLevel.label} ${TaxRules.yuanText(rentLevel.monthlyCents)} 元/月 × $rentMonths 个月"
        }

        val elderlyAnnual = if (input.elderlyOnlyChild) {
            TaxRules.ELDERLY_ONLY_CHILD_MONTHLY_CENTS * 12L
        } else {
            TaxRules.normalize(input.elderlySharedMonthlyCents)
                .coerceAtMost(TaxRules.ELDERLY_SHARED_MAX_MONTHLY_CENTS) * 12L
        }
        val elderlyDetail = if (input.elderlyOnlyChild) {
            "独生子女 3,000 元/月 × 12 个月"
        } else if (elderlyAnnual > 0L) {
            "非独生子女分摊 ${TaxRules.yuanText(elderlyAnnual / 12)} 元/月 × 12 个月（每人上限 1,500 元/月）"
        } else {
            "未填报"
        }

        val selfPaid = TaxRules.normalize(input.seriousIllnessSelfPaidCents)
        val seriousAnnual = (selfPaid - TaxRules.SERIOUS_ILLNESS_THRESHOLD_CENTS)
            .coerceIn(0L, TaxRules.SERIOUS_ILLNESS_CAP_CENTS)
        val seriousDetail = when {
            selfPaid <= 0L -> "未填报"
            selfPaid <= TaxRules.SERIOUS_ILLNESS_THRESHOLD_CENTS ->
                "个人自付 ${TaxRules.yuanText(selfPaid)} 元，未超过 15,000 元起付线"
            seriousAnnual >= TaxRules.SERIOUS_ILLNESS_CAP_CENTS ->
                "个人自付 ${TaxRules.yuanText(selfPaid)} 元，超过起付线部分按年限额 80,000 元扣除"
            else ->
                "个人自付 ${TaxRules.yuanText(selfPaid)} 元，超起付线部分 ${TaxRules.yuanText(seriousAnnual)} 元"
        }

        return listOf(
            DeductionItem(
                key = KEY_CHILD,
                title = "子女教育",
                annualCents = childAnnual,
                detail = if (childAnnual == 0L) "未填报" else "$childCount 个子女 × 2,000 元/月 × 12 个月 × $childShare%",
                policy = "每个子女 2,000 元/月，父母可选择各扣 50% 或由一方 100% 扣除，扣除至子女全日制学历教育结束。",
            ),
            DeductionItem(
                key = KEY_INFANT,
                title = "3 岁以下婴幼儿照护",
                annualCents = infantAnnual,
                detail = if (infantAnnual == 0L) "未填报" else "$infantCount 个婴幼儿 × 2,000 元/月 × 12 个月 × $infantShare%",
                policy = "每个 3 岁以下婴幼儿 2,000 元/月，父母可选择各扣 50% 或由一方 100% 扣除。",
            ),
            DeductionItem(
                key = KEY_CONTINUING,
                title = "继续教育",
                annualCents = continuingAnnual,
                detail = continuingDetail,
                policy = "学历（学位）继续教育 400 元/月，最长累计 48 个月；职业资格继续教育在取得证书当年一次性扣除 3,600 元。",
            ),
            DeductionItem(
                key = KEY_HOUSING_LOAN,
                title = "住房贷款利息",
                annualCents = loanAnnual,
                detail = loanDetail,
                policy = "首套住房贷款利息在实际发生贷款利息的年度按 1,000 元/月扣除，最长 240 个月；夫妻双方可选择由一方扣除。",
            ),
            DeductionItem(
                key = KEY_HOUSING_RENT,
                title = "住房租金",
                annualCents = rentAnnual,
                detail = rentDetail,
                policy = "按城市档 1,500 / 1,100 / 800 元/月扣除；住房贷款利息与住房租金不得同时享受。",
            ),
            DeductionItem(
                key = KEY_ELDERLY,
                title = "赡养老人",
                annualCents = elderlyAnnual,
                detail = elderlyDetail,
                policy = "独生子女 3,000 元/月；非独生子女与兄弟姐妹分摊 3,000 元/月总额，每人不超过 1,500 元/月。",
            ),
            DeductionItem(
                key = KEY_SERIOUS_ILLNESS,
                title = "大病医疗",
                annualCents = seriousAnnual,
                detail = seriousDetail,
                policy = "医保报销后个人自付累计超过 15,000 元的部分，在 80,000 元/年范围内据实扣除；只能在年度汇算时办理，预扣预缴阶段不扣。",
            ),
        )
    }

    /** 计算 3 项其他扣除（含限额封顶），未填报为 0 */
    fun otherDeductions(input: OtherDeductionInput): List<DeductionItem> {
        val pension = TaxRules.normalize(input.personalPensionCents)
            .coerceAtMost(TaxRules.PERSONAL_PENSION_CAP_CENTS)
        val health = TaxRules.normalize(input.commercialHealthCents)
            .coerceAtMost(TaxRules.COMMERCIAL_HEALTH_CAP_CENTS)
        val charity = TaxRules.normalize(input.charityDonationCents)
        return listOf(
            DeductionItem(
                key = KEY_PENSION,
                title = "个人养老金",
                annualCents = pension,
                detail = if (pension == 0L) "未填报" else "年度缴费 ${TaxRules.yuanText(pension)} 元（限额 12,000 元/年）",
                policy = "个人养老金每年缴费不超过 12,000 元的部分，可在综合所得或经营所得中据实扣除。",
            ),
            DeductionItem(
                key = KEY_HEALTH_INSURANCE,
                title = "税优健康险",
                annualCents = health,
                detail = if (health == 0L) "未填报" else "年度保费 ${TaxRules.yuanText(health)} 元（限额 2,400 元/年）",
                policy = "购买符合规定的商业健康保险产品，凭税优识别码按 2,400 元/年（200 元/月）限额扣除。",
            ),
            DeductionItem(
                key = KEY_CHARITY,
                title = "公益慈善捐赠",
                annualCents = charity,
                detail = if (charity == 0L) "未填报" else "按填报金额扣除（需符合 30% 限额等条件）",
                policy = "个人通过境内公益性社会组织、国家机关进行的公益慈善捐赠，不超过申报应纳税所得额 30% 的部分可扣除；超限部分需按规定处理，以税务机关认定为准。",
            ),
        )
    }

    // ────────────────────────────────────────────────────────────
    // 主流程
    // ────────────────────────────────────────────────────────────

    fun compute(input: TaxInput): TaxResult {
        val income = sanitizeIncome(input.income)
        val social = sanitizeSocial(input.socialInsurance)
        val additionalItems = additionalDeductions(input.additional)
        val otherItems = otherDeductions(input.other)

        val basicAnnual = TaxRules.BASIC_DEDUCTION_ANNUAL_CENTS
        val specialAnnual = social.annualTotalCents
        val additionalAnnual = additionalItems.sumOf { it.annualCents }
        val otherAnnual = otherItems.sumOf { it.annualCents }
        val seriousAnnual = additionalItems.first { it.key == KEY_SERIOUS_ILLNESS }.annualCents

        // 1. 收入额折算
        val wageAmount = income.salaryCents
        val laborAmount = TaxRules.percentOf(income.laborCents, 80)
        val authorAmount = TaxRules.percentOf(TaxRules.percentOf(income.authorCents, 80), 70)
        val royaltyAmount = TaxRules.percentOf(income.royaltyCents, 80)
        val comprehensiveAmount = wageAmount + laborAmount + authorAmount + royaltyAmount

        // 2. 综合所得应纳税所得额与税额（不含年终奖）
        val taxable = (comprehensiveAmount - basicAnnual - specialAnnual - additionalAnnual - otherAnnual)
            .coerceAtLeast(0L)
        val bracket = TaxRules.bracketOf(taxable)
        val comprehensiveTax = TaxRules.taxOf(taxable)

        // 3. 预扣预缴：工资薪金走累计预扣法；大病医疗只在汇算扣除
        val withholdingAdditional = (additionalAnnual - seriousAnnual).coerceAtLeast(0L)
        val withholdingRows = wageWithholdingRows(
            salaryCents = wageAmount,
            specialAnnualCents = specialAnnual,
            additionalAnnualCents = withholdingAdditional,
            otherAnnualCents = otherAnnual,
        )
        val wageWithheld = withholdingRows.last().cumulativeTaxCents
        val otherWithheld = otherIncomeWithholding(income)
        val bonusWithheld = TaxRules.bonusTaxOf(income.bonusCents)
        val totalWithheld = wageWithheld + otherWithheld + bonusWithheld

        // 4. 年终奖两方案对比
        val bonusComparison = if (income.bonusCents > 0L) {
            buildBonusComparison(
                bonusCents = income.bonusCents,
                comprehensiveAmountCents = comprehensiveAmount,
                basicAnnualCents = basicAnnual,
                specialAnnualCents = specialAnnual,
                additionalAnnualCents = additionalAnnual,
                otherAnnualCents = otherAnnual,
                comprehensiveTaxCents = comprehensiveTax,
                totalWithheldCents = totalWithheld,
            )
        } else {
            null
        }

        val recommendedScheme = bonusComparison?.recommended
        val totalTax = when (recommendedScheme) {
            BonusScheme.SEPARATE -> bonusComparison?.separateTotalTaxCents ?: comprehensiveTax
            BonusScheme.COMBINED -> bonusComparison?.combinedTotalTaxCents ?: comprehensiveTax
            null -> comprehensiveTax
        }
        val balance = totalTax - totalWithheld
        val payable = balance.coerceAtLeast(0L)
        val refund = (-balance).coerceAtLeast(0L)

        val totalIncomeAmount = comprehensiveAmount + income.bonusCents
        val burdenBasisPoints = if (totalIncomeAmount <= 0L) {
            0
        } else {
            (totalTax * 10_000L / totalIncomeAmount).toInt()
        }

        val notices = buildNotices(
            input = input,
            income = income,
            comprehensiveAmountCents = comprehensiveAmount,
            basicAnnualCents = basicAnnual,
            specialAnnualCents = specialAnnual,
            additionalAnnualCents = additionalAnnual,
            otherAnnualCents = otherAnnual,
            charityCents = otherItems.first { it.key == KEY_CHARITY }.annualCents,
            seriousAnnualCents = seriousAnnual,
        )

        return TaxResult(
            input = input,
            wageIncomeAmountCents = wageAmount,
            laborIncomeAmountCents = laborAmount,
            authorIncomeAmountCents = authorAmount,
            royaltyIncomeAmountCents = royaltyAmount,
            comprehensiveIncomeAmountCents = comprehensiveAmount,
            basicDeductionCents = basicAnnual,
            specialDeductionCents = specialAnnual,
            additionalDeductionCents = additionalAnnual,
            additionalItems = additionalItems,
            otherDeductionCents = otherAnnual,
            otherItems = otherItems,
            taxableIncomeCents = taxable,
            bracket = bracket,
            comprehensiveTaxCents = comprehensiveTax,
            bonusComparison = bonusComparison,
            recommendedScheme = recommendedScheme,
            totalTaxCents = totalTax,
            wageWithheldCents = wageWithheld,
            otherWithheldCents = otherWithheld,
            bonusWithheldCents = bonusWithheld,
            totalWithheldCents = totalWithheld,
            payableCents = payable,
            refundCents = refund,
            taxBurdenBasisPoints = burdenBasisPoints,
            withholdingRows = withholdingRows,
            notices = notices,
        )
    }

    // ────────────────────────────────────────────────────────────
    // 预扣预缴
    // ────────────────────────────────────────────────────────────

    /**
     * 工资薪金累计预扣法：逐月累计收入、累计扣除，按年度税率表命中档位后减去已预扣。
     * 工资按月均摊（年收入 × 月序 / 12），最后一个月累计值正好等于全年数；
     * 实际月度发放不均时，年末累计预扣结果与这里一致，差异只体现在月度分布上。
     */
    private fun wageWithholdingRows(
        salaryCents: Long,
        specialAnnualCents: Long,
        additionalAnnualCents: Long,
        otherAnnualCents: Long,
    ): List<WithholdingRow> {
        val rows = ArrayList<WithholdingRow>(12)
        var previousCumulativeTax = 0L
        for (month in 1..12) {
            val cumulativeIncome = salaryCents * month / 12L
            val cumulativeBasic = TaxRules.BASIC_DEDUCTION_MONTHLY_CENTS * month
            val cumulativeSpecial = specialAnnualCents * month / 12L
            val cumulativeAdditional = additionalAnnualCents * month / 12L
            val cumulativeOther = otherAnnualCents * month / 12L
            val cumulativeTaxable = (
                cumulativeIncome - cumulativeBasic - cumulativeSpecial - cumulativeAdditional - cumulativeOther
                ).coerceAtLeast(0L)
            val cumulativeTax = TaxRules.taxOf(cumulativeTaxable)
            val monthlyWithheld = (cumulativeTax - previousCumulativeTax).coerceAtLeast(0L)
            rows += WithholdingRow(
                month = month,
                cumulativeIncomeCents = cumulativeIncome,
                cumulativeBasicDeductionCents = cumulativeBasic,
                cumulativeSpecialDeductionCents = cumulativeSpecial,
                cumulativeAdditionalDeductionCents = cumulativeAdditional,
                cumulativeOtherDeductionCents = cumulativeOther,
                cumulativeTaxableCents = cumulativeTaxable,
                cumulativeTaxCents = cumulativeTax,
                monthlyWithheldCents = monthlyWithheld,
            )
            previousCumulativeTax = cumulativeTax
        }
        return rows
    }

    /**
     * 劳务报酬 / 稿酬 / 特许权使用费预扣估算。
     * 现实中按「每次」支付预扣，本测算把全年收入视作一次支付，属于偏保守的估算；
     * 汇算时这些所得并入综合所得，已预扣部分用于抵减——多数情况下会产生退税。
     */
    fun otherIncomeWithholding(income: IncomeInput): Long {
        val value = sanitizeIncome(income)
        var total = 0L

        // 劳务报酬：≤4,000 元减 800，>4,000 元减 20%；预扣率 20/30/40%
        if (value.laborCents > 0L) {
            val laborTax = if (value.laborCents <= 400_000L) {
                TaxRules.percentOf((value.laborCents - 80_000L).coerceAtLeast(0L), 20)
            } else {
                val amount = TaxRules.percentOf(value.laborCents, 80)
                val raw = when {
                    amount <= 2_000_000L -> TaxRules.percentOf(amount, 20)
                    amount <= 5_000_000L -> TaxRules.percentOf(amount, 30) - 200_000L
                    else -> TaxRules.percentOf(amount, 40) - 700_000L
                }
                raw.coerceAtLeast(0L)
            }
            total += laborTax
        }

        // 稿酬：收入额 = 收入 × 80% × 70%（预扣率 20%）
        if (value.authorCents > 0L) {
            val amount = if (value.authorCents <= 400_000L) {
                TaxRules.percentOf((value.authorCents - 80_000L).coerceAtLeast(0L), 70)
            } else {
                TaxRules.percentOf(TaxRules.percentOf(value.authorCents, 80), 70)
            }
            total += TaxRules.percentOf(amount, 20)
        }

        // 特许权使用费：收入 × 80%（预扣率 20%）
        if (value.royaltyCents > 0L) {
            val amount = if (value.royaltyCents <= 400_000L) {
                (value.royaltyCents - 80_000L).coerceAtLeast(0L)
            } else {
                TaxRules.percentOf(value.royaltyCents, 80)
            }
            total += TaxRules.percentOf(amount, 20)
        }
        return total
    }

    // ────────────────────────────────────────────────────────────
    // 年终奖两方案
    // ────────────────────────────────────────────────────────────

    private fun buildBonusComparison(
        bonusCents: Long,
        comprehensiveAmountCents: Long,
        basicAnnualCents: Long,
        specialAnnualCents: Long,
        additionalAnnualCents: Long,
        otherAnnualCents: Long,
        comprehensiveTaxCents: Long,
        totalWithheldCents: Long,
    ): BonusComparison {
        val separateBonusTax = TaxRules.bonusTaxOf(bonusCents)
        val separateTotal = comprehensiveTaxCents + separateBonusTax

        val combinedTaxable = (
            comprehensiveAmountCents + bonusCents -
                basicAnnualCents - specialAnnualCents - additionalAnnualCents - otherAnnualCents
            ).coerceAtLeast(0L)
        val combinedBracket = TaxRules.bracketOf(combinedTaxable)
        val combinedTotal = TaxRules.taxOf(combinedTaxable)

        // 两方案税额相同（常见于低奖金）时默认「单独计税」：无需在汇算中额外选择，操作更简单
        val recommended = if (combinedTotal < separateTotal) BonusScheme.COMBINED else BonusScheme.SEPARATE
        return BonusComparison(
            separateBonusTaxCents = separateBonusTax,
            separateTotalTaxCents = separateTotal,
            combinedTaxableIncomeCents = combinedTaxable,
            combinedBracket = combinedBracket,
            combinedTotalTaxCents = combinedTotal,
            recommended = recommended,
            savingCents = (separateTotal - combinedTotal).let { if (it < 0L) -it else it },
            separateBalanceCents = separateTotal - totalWithheldCents,
            combinedBalanceCents = combinedTotal - totalWithheldCents,
        )
    }

    // ────────────────────────────────────────────────────────────
    // 政策提示（不影响税额，只解释计算结果为何与直觉不同）
    // ────────────────────────────────────────────────────────────

    private fun buildNotices(
        input: TaxInput,
        income: IncomeInput,
        comprehensiveAmountCents: Long,
        basicAnnualCents: Long,
        specialAnnualCents: Long,
        additionalAnnualCents: Long,
        otherAnnualCents: Long,
        charityCents: Long,
        seriousAnnualCents: Long,
    ): List<TaxNotice> = buildList {
        val additional = input.additional

        if (additional.housingRentLevel != null && additional.housingLoan) {
            add(
                TaxNotice(
                    TaxNoticeLevel.WARNING,
                    "住房贷款利息与住房租金互斥：同时填报时以住房租金为准，房贷利息未重复扣除。",
                ),
            )
        }

        val selfPaid = TaxRules.normalize(additional.seriousIllnessSelfPaidCents)
        if (selfPaid in 1..TaxRules.SERIOUS_ILLNESS_THRESHOLD_CENTS) {
            add(
                TaxNotice(
                    TaxNoticeLevel.INFO,
                    "大病医疗个人自付未超过 15,000 元起付线，本项扣除为 0。",
                ),
            )
        } else if (selfPaid > TaxRules.SERIOUS_ILLNESS_THRESHOLD_CENTS &&
            seriousAnnualCents >= TaxRules.SERIOUS_ILLNESS_CAP_CENTS
        ) {
            add(
                TaxNotice(
                    TaxNoticeLevel.INFO,
                    "大病医疗扣除已按 80,000 元/年限额封顶，且只能在年度汇算时扣除。",
                ),
            )
        }

        if (TaxRules.normalize(input.other.personalPensionCents) > TaxRules.PERSONAL_PENSION_CAP_CENTS) {
            add(
                TaxNotice(
                    TaxNoticeLevel.INFO,
                    "个人养老金缴费超过 12,000 元/年限额，已按限额计算扣除。",
                ),
            )
        }
        if (TaxRules.normalize(input.other.commercialHealthCents) > TaxRules.COMMERCIAL_HEALTH_CAP_CENTS) {
            add(
                TaxNotice(
                    TaxNoticeLevel.INFO,
                    "税优健康险超过 2,400 元/年限额，已按限额计算扣除。",
                ),
            )
        }

        if (charityCents > 0L) {
            val otherWithoutCharity = (otherAnnualCents - charityCents).coerceAtLeast(0L)
            val donationBase = (
                comprehensiveAmountCents - basicAnnualCents - specialAnnualCents -
                    additionalAnnualCents - otherWithoutCharity
                ).coerceAtLeast(0L)
            val donationLimit = TaxRules.percentOf(donationBase, TaxRules.CHARITY_LIMIT_PERCENT)
            if (charityCents > donationLimit) {
                add(
                    TaxNotice(
                        TaxNoticeLevel.WARNING,
                        "公益捐赠 ${TaxRules.yuanText(charityCents)} 元超过应纳税所得额 30% 的扣除限额 " +
                            "${TaxRules.yuanText(donationLimit)} 元（超限部分需按规定结转或不得扣除）；" +
                            "本测算暂按填报额全额扣除，请以税务机关认定为准。",
                    ),
                )
            }
        }

        if (income.bonusCents > 0L) {
            TaxRules.BONUS_THRESHOLD_CENTS.firstOrNull { threshold ->
                income.bonusCents > threshold && income.bonusCents <= threshold + 100_000L
            }?.let { threshold ->
                val extra = TaxRules.bonusTaxOf(income.bonusCents) - TaxRules.bonusTaxOf(threshold)
                add(
                    TaxNotice(
                        TaxNoticeLevel.WARNING,
                        "年终奖 ${TaxRules.yuanText(income.bonusCents)} 元刚越过 ${TaxRules.yuanText(threshold)} 元临界点：" +
                            "单独计税税额比临界点金额多 ${TaxRules.yuanText(extra)} 元，可考虑与单位协商调整发放金额，或选择并入综合所得。",
                    ),
                )
            }
        }

        if (income.totalCents <= 0L) {
            add(
                TaxNotice(
                    TaxNoticeLevel.INFO,
                    "尚未填写收入：当前结果只演示扣除规则，填写收入后税额会自动更新。",
                ),
            )
        }
    }
}
