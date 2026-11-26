package com.suiyu.ledger.ui.screens.tax

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import com.suiyu.ledger.tax.AdditionalDeductionInput
import com.suiyu.ledger.tax.BonusComparison
import com.suiyu.ledger.tax.BonusScheme
import com.suiyu.ledger.tax.DeductionItem
import com.suiyu.ledger.tax.HousingRentLevel
import com.suiyu.ledger.tax.IncomeInput
import com.suiyu.ledger.tax.OtherDeductionInput
import com.suiyu.ledger.tax.SocialInsuranceInput
import com.suiyu.ledger.tax.TaxEngine
import com.suiyu.ledger.tax.TaxInput
import com.suiyu.ledger.tax.TaxNoticeLevel
import com.suiyu.ledger.tax.TaxResult
import com.suiyu.ledger.tax.TaxRules
import com.suiyu.ledger.tax.TaxSample
import com.suiyu.ledger.tax.TaxSamples
import com.suiyu.ledger.ui.components.SyAmount
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.SyStat
import com.suiyu.ledger.ui.components.SyTag
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.SyToggleRow
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.Share
import java.util.Locale

/**
 * 个人所得税测算 / 模拟申报页。
 *
 * 交互分五步：收入 → 三险一金 → 专项附加扣除 → 其他扣除 → 结果与模拟申报。
 * 所有税额来自 [TaxEngine]（纯 Kotlin + 整数分），页面只负责收集输入与格式化展示；
 * 每一笔金额都用 OutlinedTextField 的数字键盘输入，所有点击都带触感反馈。
 */

private const val POLICY_INCOME = "income"
private const val POLICY_SOCIAL = "social"
private const val POLICY_OTHER = "other"

private const val STEP_COUNT = 5

@Composable
fun TaxScreen(onBack: () -> Unit) {
    val xm = SyTheme.colors
    val context = LocalContext.current

    // ── 页面状态 ──
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showSelfCheck by rememberSaveable { mutableStateOf(false) }
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var runKey by rememberSaveable { mutableIntStateOf(0) }

    // 收入（元文本）
    var salaryText by rememberSaveable { mutableStateOf("") }
    var bonusText by rememberSaveable { mutableStateOf("") }
    var laborText by rememberSaveable { mutableStateOf("") }
    var authorText by rememberSaveable { mutableStateOf("") }
    var royaltyText by rememberSaveable { mutableStateOf("") }

    // 三险一金（月度，元文本）
    var socialBaseText by rememberSaveable { mutableStateOf("") }
    var fundPercent by rememberSaveable { mutableIntStateOf(12) }
    var estimateMessage by rememberSaveable { mutableStateOf("") }
    var pensionText by rememberSaveable { mutableStateOf("") }
    var medicalText by rememberSaveable { mutableStateOf("") }
    var unemploymentText by rememberSaveable { mutableStateOf("") }
    var fundText by rememberSaveable { mutableStateOf("") }

    // 专项附加扣除
    var childCount by rememberSaveable { mutableIntStateOf(0) }
    var childShare by rememberSaveable { mutableIntStateOf(1) } // 0 = 各 50%，1 = 一方 100%
    var infantCount by rememberSaveable { mutableIntStateOf(0) }
    var infantShare by rememberSaveable { mutableIntStateOf(1) }
    var degreeMonths by rememberSaveable { mutableIntStateOf(0) }
    var certificate by rememberSaveable { mutableStateOf(false) }
    var housingLoan by rememberSaveable { mutableStateOf(false) }
    var rentLevelIndex by rememberSaveable { mutableIntStateOf(0) }
    var elderlyMode by rememberSaveable { mutableIntStateOf(0) } // 0 不适用 / 1 独生 / 2 非独生
    var elderlySharedText by rememberSaveable { mutableStateOf("") }
    var seriousText by rememberSaveable { mutableStateOf("") }

    // 其他扣除
    var pensionText2 by rememberSaveable { mutableStateOf("") }
    var healthText by rememberSaveable { mutableStateOf("") }
    var charityText by rememberSaveable { mutableStateOf("") }

    // ── 派生输入 ──
    val income = IncomeInput(
        salaryCents = centsOf(salaryText),
        bonusCents = centsOf(bonusText),
        laborCents = centsOf(laborText),
        authorCents = centsOf(authorText),
        royaltyCents = centsOf(royaltyText),
    )
    val socialInsurance = SocialInsuranceInput(
        pensionCents = centsOf(pensionText),
        medicalCents = centsOf(medicalText),
        unemploymentCents = centsOf(unemploymentText),
        housingFundCents = centsOf(fundText),
    )
    val additional = AdditionalDeductionInput(
        childCount = childCount,
        childSharePercent = if (childShare == 0) 50 else 100,
        infantCount = infantCount,
        infantSharePercent = if (infantShare == 0) 50 else 100,
        continuingDegreeMonths = degreeMonths,
        continuingCertificate = certificate,
        housingLoan = housingLoan,
        housingRentLevel = rentLevelOf(rentLevelIndex),
        elderlyOnlyChild = elderlyMode == 1,
        elderlySharedMonthlyCents = if (elderlyMode == 2) centsOf(elderlySharedText) else 0L,
        seriousIllnessSelfPaidCents = centsOf(seriousText),
    )
    val other = OtherDeductionInput(
        personalPensionCents = centsOf(pensionText2),
        commercialHealthCents = centsOf(healthText),
        charityDonationCents = centsOf(charityText),
    )
    val input = TaxInput(
        income = income,
        socialInsurance = socialInsurance,
        additional = additional,
        other = other,
    )
    val result = remember(input) { TaxEngine.compute(input) }
    val sampleChecks = remember(runKey) {
        TaxSamples.showcase.map { sample -> TaxSampleCheck(sample, TaxEngine.compute(sample.input)) }
    }
    val passedSamples = sampleChecks.count { it.passed }

    // 非文本类字段的更新入口（文本金额单独维护，避免光标跳动）
    val updateAdditional: (AdditionalDeductionInput) -> Unit = { value ->
        childCount = value.childCount
        childShare = if (value.childSharePercent <= 50) 0 else 1
        infantCount = value.infantCount
        infantShare = if (value.infantSharePercent <= 50) 0 else 1
        degreeMonths = value.continuingDegreeMonths
        certificate = value.continuingCertificate
        housingLoan = value.housingLoan
        rentLevelIndex = when (value.housingRentLevel) {
            null -> 0
            HousingRentLevel.FIRST -> 1
            HousingRentLevel.SECOND -> 2
            HousingRentLevel.THIRD -> 3
        }
        elderlyMode = when {
            value.elderlyOnlyChild -> 1
            value.elderlySharedMonthlyCents > 0L -> 2
            else -> 0
        }
    }
    val estimateSocial: () -> Unit = {
        val base = centsOf(socialBaseText).let { if (it > 0L) it else centsOf(salaryText) / 12L }
        if (base <= 0L) {
            estimateMessage = "请先填写月工资基数或年工资薪金，再自动估算。"
        } else {
            val estimate = TaxRules.estimateSocialInsurance(base, fundPercent)
            pensionText = Money.plain(estimate.pensionCents)
            medicalText = Money.plain(estimate.medicalCents)
            unemploymentText = Money.plain(estimate.unemploymentCents)
            fundText = Money.plain(estimate.housingFundCents)
            estimateMessage = "已按基数 ${Money.plain(base)} 元估算（不含缴费基数上下限），可手动修改。"
        }
    }

    SyScaffold(
        title = if (showSelfCheck) "计算器自检" else "个税测算",
        subtitle = if (showSelfCheck) {
            "样例期望值与引擎实际结果逐条比对"
        } else {
            "2026 年度综合所得汇算 · 结果仅供参考"
        },
        onBack = {
            if (showSelfCheck) showSelfCheck = false else onBack()
        },
        scroll = true,
    ) {
        if (showSelfCheck) {
            SelfCheckPage(
                checks = sampleChecks,
                onRerun = { runKey += 1 },
            )
        } else {
            PolicyBanner()
            Spacer(modifier = Modifier.height(SyDim.gapM))
            TaxStepper(step = step, onSelect = { step = it })
            Spacer(modifier = Modifier.height(SyDim.gapL))

            when (step) {
                0 -> IncomeStep(
                    salaryText = salaryText,
                    onSalary = { salaryText = it },
                    bonusText = bonusText,
                    onBonus = { bonusText = it },
                    laborText = laborText,
                    onLabor = { laborText = it },
                    authorText = authorText,
                    onAuthor = { authorText = it },
                    royaltyText = royaltyText,
                    onRoyalty = { royaltyText = it },
                    comprehensiveIncomeCents = result.comprehensiveIncomeAmountCents,
                    expandedKey = expandedKey,
                    onExpand = { expandedKey = it },
                )

                1 -> SocialInsuranceStep(
                    baseText = socialBaseText,
                    onBase = { socialBaseText = it },
                    fundPercent = fundPercent,
                    onFundPercent = { fundPercent = it },
                    pensionText = pensionText,
                    onPension = { pensionText = it },
                    medicalText = medicalText,
                    onMedical = { medicalText = it },
                    unemploymentText = unemploymentText,
                    onUnemployment = { unemploymentText = it },
                    fundText = fundText,
                    onFund = { fundText = it },
                    monthlyTotalCents = socialInsurance.monthlyTotalCents,
                    annualTotalCents = socialInsurance.annualTotalCents,
                    estimateMessage = estimateMessage,
                    onEstimate = estimateSocial,
                    expandedKey = expandedKey,
                    onExpand = { expandedKey = it },
                )

                2 -> AdditionalDeductionStep(
                    additional = additional,
                    onChange = updateAdditional,
                    items = result.additionalItems,
                    elderlyMode = elderlyMode,
                    elderlySharedText = elderlySharedText,
                    onElderlySharedText = { elderlySharedText = it },
                    seriousText = seriousText,
                    onSeriousText = { seriousText = it },
                    totalCents = result.additionalDeductionCents,
                    expandedKey = expandedKey,
                    onExpand = { expandedKey = it },
                )

                3 -> OtherDeductionStep(
                    personalPensionText = pensionText2,
                    onPersonalPension = { pensionText2 = it },
                    healthText = healthText,
                    onHealth = { healthText = it },
                    charityText = charityText,
                    onCharity = { charityText = it },
                    items = result.otherItems,
                    totalCents = result.otherDeductionCents,
                    expandedKey = expandedKey,
                    onExpand = { expandedKey = it },
                )

                else -> ResultStep(
                    result = result,
                    passedSamples = passedSamples,
                    sampleCount = sampleChecks.size,
                    onSelfCheck = { showSelfCheck = true },
                    onShare = {
                        Share.shareText(
                            context = context,
                            text = buildShareText(result),
                            subject = "岁余 · 个税测算结果",
                        )
                    },
                )
            }

            StepNav(
                step = step,
                onBack = { if (step > 0) step -= 1 },
                onNext = {
                    step = if (step >= STEP_COUNT - 1) 0 else step + 1
                },
            )
            Spacer(modifier = Modifier.height(SyDim.gap2Xl))
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 顶部：政策依据 / 步骤 / 底部导航
// ────────────────────────────────────────────────────────────────

@Composable
private fun PolicyBanner() {
    val xm = SyTheme.colors
    SyCard(color = xm.goldSoft, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "📜", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "2026 年度汇算 · 政策依据与适用年度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = TaxRules.POLICY_BASIS,
            style = MaterialTheme.typography.bodySmall,
            color = xm.inkSoft,
        )
    }
}

@Composable
private fun TaxStepper(step: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("1 收入", "2 三险一金", "3 专项附加", "4 其他扣除", "5 结果")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            SyChip(text = label, selected = step == index, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun StepNav(step: Int, onBack: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SyDim.gapXl),
        horizontalArrangement = Arrangement.spacedBy(SyDim.gapS),
    ) {
        if (step > 0) {
            SyButton(
                text = "上一步",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                style = SyButtonStyle.Secondary,
            )
        }
        SyButton(
            text = when (step) {
                0 -> "下一步：三险一金"
                1 -> "下一步：专项附加扣除"
                2 -> "下一步：其他扣除"
                3 -> "生成测算结果"
                else -> "回到收入修改"
            },
            onClick = onNext,
            modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
            haptic = if (step == 3) HapticEffect.Success else HapticEffect.Confirm,
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 步骤 1：收入
// ────────────────────────────────────────────────────────────────

@Composable
private fun IncomeStep(
    salaryText: String,
    onSalary: (String) -> Unit,
    bonusText: String,
    onBonus: (String) -> Unit,
    laborText: String,
    onLabor: (String) -> Unit,
    authorText: String,
    onAuthor: (String) -> Unit,
    royaltyText: String,
    onRoyalty: (String) -> Unit,
    comprehensiveIncomeCents: Long,
    expandedKey: String?,
    onExpand: (String?) -> Unit,
) {
    val xm = SyTheme.colors
    SySectionHeader(title = "收入", subtitle = "按年度、税前金额填写；单位：元")
    SyCard(contentPadding = PaddingValues(16.dp)) {
        AmountField("工资薪金（年度）", salaryText, onSalary, hint = "全额计入收入额；12 个月预扣表按平均发放估算")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("全年一次性奖金", bonusText, onBonus, hint = "2027 年底前可选择单独计税，也可并入综合所得")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("劳务报酬", laborText, onLabor, hint = "按收入的 80% 计入综合所得收入额")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("稿酬", authorText, onAuthor, hint = "按收入的 80% × 70% = 56% 计入收入额")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("特许权使用费", royaltyText, onRoyalty, hint = "按收入的 80% 计入收入额")
        SyDivider(modifier = Modifier.padding(vertical = SyDim.gapM))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "计入综合所得的收入额",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(comprehensiveIncomeCents),
                style = MaterialTheme.typography.titleMedium,
                color = xm.income,
            )
        }
        Text(
            text = "年终奖不计入此处，将在结果页单独比较两套方案。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PolicyToggle(
            key = POLICY_INCOME,
            expandedKey = expandedKey,
            onExpand = onExpand,
            policy = "收入额折算规则：工资薪金按全额计入；劳务报酬按 80% 计入；稿酬按 80% × 70% 计入；" +
                "特许权使用费按 80% 计入。综合所得年度汇算时，再减除 60,000 元基本费用、三险一金、" +
                "专项附加扣除和依法确定的其他扣除。",
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 步骤 2：三险一金
// ────────────────────────────────────────────────────────────────

@Composable
private fun SocialInsuranceStep(
    baseText: String,
    onBase: (String) -> Unit,
    fundPercent: Int,
    onFundPercent: (Int) -> Unit,
    pensionText: String,
    onPension: (String) -> Unit,
    medicalText: String,
    onMedical: (String) -> Unit,
    unemploymentText: String,
    onUnemployment: (String) -> Unit,
    fundText: String,
    onFund: (String) -> Unit,
    monthlyTotalCents: Long,
    annualTotalCents: Long,
    estimateMessage: String,
    onEstimate: () -> Unit,
    expandedKey: String?,
    onExpand: (String?) -> Unit,
) {
    val xm = SyTheme.colors
    val percents = listOf(5, 7, 8, 10, 12)
    SySectionHeader(title = "三险一金", subtitle = "个人月度缴纳额；年度合计 = 月缴 × 12")
    SyCard(contentPadding = PaddingValues(16.dp)) {
        AmountField(
            label = "月工资基数（用于估算）",
            value = baseText,
            onValueChange = onBase,
            hint = "留空时按月工资薪金 = 年工资 ÷ 12 估算",
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Text(
            text = "住房公积金比例",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            percents.forEach { percent ->
                SyChip(
                    text = "$percent%",
                    selected = fundPercent == percent,
                    onClick = { onFundPercent(percent) },
                )
            }
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        SyButton(
            text = "按工资基数自动估算三险一金",
            onClick = onEstimate,
            modifier = Modifier.fillMaxWidth(),
            style = SyButtonStyle.Secondary,
        )
        if (estimateMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = estimateMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("养老保险（8%）", pensionText, onPension, hint = "个人缴纳部分，月度")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("医疗保险（2%）", medicalText, onMedical, hint = "个人缴纳部分，月度")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("失业保险（0.5%）", unemploymentText, onUnemployment, hint = "个人缴纳部分，月度")
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField("住房公积金（5%-12%）", fundText, onFund, hint = "个人缴纳部分，月度")
        SyDivider(modifier = Modifier.padding(vertical = SyDim.gapM))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "月度合计",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(text = Money.format(monthlyTotalCents), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "年度合计（税前扣除）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(annualTotalCents),
                style = MaterialTheme.typography.titleMedium,
                color = xm.income,
            )
        }
        Text(
            text = "估算按 养老 8%、医疗 2%、失业 0.5%、公积金 5%-12% 计算，不含社保缴费基数上下限；实际以社保 / 公积金机构核定为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        PolicyToggle(
            key = POLICY_SOCIAL,
            expandedKey = expandedKey,
            onExpand = onExpand,
            policy = "专项扣除即「三险一金」中个人缴纳的部分：基本养老保险、基本医疗保险、失业保险和住房公积金。" +
                "年度汇算时按全年实际缴纳额扣除；本页按月缴额 × 12 计算，工作月份不足 12 个月时请按实际缴纳总额调整。",
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 步骤 3：专项附加扣除（7 项）
// ────────────────────────────────────────────────────────────────

@Composable
private fun AdditionalDeductionStep(
    additional: AdditionalDeductionInput,
    onChange: (AdditionalDeductionInput) -> Unit,
    items: List<DeductionItem>,
    elderlyMode: Int,
    elderlySharedText: String,
    onElderlySharedText: (String) -> Unit,
    seriousText: String,
    onSeriousText: (String) -> Unit,
    totalCents: Long,
    expandedKey: String?,
    onExpand: (String?) -> Unit,
) {
    val xm = SyTheme.colors
    SySectionHeader(title = "专项附加扣除", subtitle = "7 项标准；未填写的项目不计入扣除")
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_CHILD),
        expanded = expandedKey == TaxEngine.KEY_CHILD,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_CHILD) null else TaxEngine.KEY_CHILD) },
    ) {
        StepperRow(
            label = "子女数量",
            value = additional.childCount,
            range = 0..10,
            onChange = { onChange(additional.copy(childCount = it)) },
            suffix = " 个",
        )
        Spacer(modifier = Modifier.height(8.dp))
        SySegmented(
            items = listOf("父母各 50%", "一方 100%"),
            selectedIndex = if (additional.childSharePercent <= 50) 0 else 1,
            onSelect = { onChange(additional.copy(childSharePercent = if (it == 0) 50 else 100)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_INFANT),
        expanded = expandedKey == TaxEngine.KEY_INFANT,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_INFANT) null else TaxEngine.KEY_INFANT) },
    ) {
        StepperRow(
            label = "婴幼儿数量",
            value = additional.infantCount,
            range = 0..10,
            onChange = { onChange(additional.copy(infantCount = it)) },
            suffix = " 个",
        )
        Spacer(modifier = Modifier.height(8.dp))
        SySegmented(
            items = listOf("父母各 50%", "一方 100%"),
            selectedIndex = if (additional.infantSharePercent <= 50) 0 else 1,
            onSelect = { onChange(additional.copy(infantSharePercent = if (it == 0) 50 else 100)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_CONTINUING),
        expanded = expandedKey == TaxEngine.KEY_CONTINUING,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_CONTINUING) null else TaxEngine.KEY_CONTINUING) },
    ) {
        StepperRow(
            label = "学历继续教育月数",
            value = additional.continuingDegreeMonths,
            range = 0..12,
            onChange = { onChange(additional.copy(continuingDegreeMonths = it)) },
            suffix = " 个月",
        )
        Spacer(modifier = Modifier.height(4.dp))
        SyToggleRow(
            title = "职业资格继续教育",
            checked = additional.continuingCertificate,
            onCheckedChange = { onChange(additional.copy(continuingCertificate = it)) },
            description = "取证当年一次性扣除 3,600 元",
        )
    }
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_HOUSING_LOAN),
        expanded = expandedKey == TaxEngine.KEY_HOUSING_LOAN,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_HOUSING_LOAN) null else TaxEngine.KEY_HOUSING_LOAN) },
    ) {
        SyToggleRow(
            title = "住房贷款利息",
            checked = additional.housingLoan,
            onCheckedChange = { checked ->
                // 与住房租金互斥：选择房贷时清空租金
                onChange(additional.copy(housingLoan = checked, housingRentLevel = if (checked) null else additional.housingRentLevel))
            },
            description = "首套房贷 1,000 元/月，本年度按 12 个月、最长累计 240 个月",
        )
    }
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_HOUSING_RENT),
        expanded = expandedKey == TaxEngine.KEY_HOUSING_RENT,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_HOUSING_RENT) null else TaxEngine.KEY_HOUSING_RENT) },
    ) {
        Text(
            text = "城市档位（与住房贷款利息互斥）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SyChip(text = "不适用", selected = additional.housingRentLevel == null, onClick = {
                onChange(additional.copy(housingRentLevel = null))
            })
            HousingRentLevel.entries.forEach { level ->
                SyChip(
                    text = "${level.label.replace("类", "")} ${level.monthlyCents / 100}",
                    selected = additional.housingRentLevel == level,
                    onClick = {
                        onChange(additional.copy(housingRentLevel = level, housingLoan = false))
                    },
                )
            }
        }
        if (additional.housingRentLevel != null) {
            Text(
                text = additional.housingRentLevel!!.description + "；本年度按 12 个月计算",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_ELDERLY),
        expanded = expandedKey == TaxEngine.KEY_ELDERLY,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_ELDERLY) null else TaxEngine.KEY_ELDERLY) },
    ) {
        SySegmented(
            items = listOf("不适用", "独生子女", "非独生子女"),
            selectedIndex = when {
                additional.elderlyOnlyChild -> 1
                additional.elderlySharedMonthlyCents > 0L -> 2
                else -> 0
            },
            onSelect = { index ->
                if (index == 2 && elderlySharedText.isBlank()) onElderlySharedText("1500")
                val shared = if (index == 2) {
                    centsOf(if (elderlySharedText.isBlank()) "1500" else elderlySharedText)
                } else {
                    0L
                }
                onChange(
                    additional.copy(
                        elderlyOnlyChild = index == 1,
                        elderlySharedMonthlyCents = shared,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (elderlyMode == 2) {
            Spacer(modifier = Modifier.height(8.dp))
            AmountField(
                label = "本人分摊金额（元/月）",
                value = elderlySharedText,
                onValueChange = onElderlySharedText,
                hint = "非独生子女与兄弟姐妹分摊 3,000 元/月总额，每人不超过 1,500 元/月",
            )
        }
    }
    DeductionEditor(
        item = items.itemOf(TaxEngine.KEY_SERIOUS_ILLNESS),
        expanded = expandedKey == TaxEngine.KEY_SERIOUS_ILLNESS,
        onTogglePolicy = { onExpand(if (expandedKey == TaxEngine.KEY_SERIOUS_ILLNESS) null else TaxEngine.KEY_SERIOUS_ILLNESS) },
    ) {
        AmountField(
            label = "医保报销后个人自付累计（年度）",
            value = seriousText,
            onValueChange = onSeriousText,
            hint = "超过 15,000 元的部分扣除，年限额 80,000 元；仅在年度汇算时扣除",
        )
    }
    SyCard(color = xm.goldSoft, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "专项附加扣除合计（年度）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(totalCents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 步骤 4：其他扣除
// ────────────────────────────────────────────────────────────────

@Composable
private fun OtherDeductionStep(
    personalPensionText: String,
    onPersonalPension: (String) -> Unit,
    healthText: String,
    onHealth: (String) -> Unit,
    charityText: String,
    onCharity: (String) -> Unit,
    items: List<DeductionItem>,
    totalCents: Long,
    expandedKey: String?,
    onExpand: (String?) -> Unit,
) {
    val xm = SyTheme.colors
    SySectionHeader(title = "其他扣除", subtitle = "依法确定的其他扣除，单位：元/年")
    SyCard(contentPadding = PaddingValues(16.dp)) {
        AmountField(
            label = "个人养老金",
            value = personalPensionText,
            onValueChange = onPersonalPension,
            hint = "每年最高扣除 12,000 元；超出部分不计入",
        )
        Spacer(modifier = Modifier.height(6.dp))
        SyTextButton(text = "按上限填入 12,000 元", onClick = { onPersonalPension("12000") })
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField(
            label = "税优健康险",
            value = healthText,
            onValueChange = onHealth,
            hint = "凭税优识别码，每年最高扣除 2,400 元",
        )
        Spacer(modifier = Modifier.height(6.dp))
        SyTextButton(text = "按上限填入 2,400 元", onClick = { onHealth("2400") })
        Spacer(modifier = Modifier.height(SyDim.gapM))
        AmountField(
            label = "公益慈善捐赠",
            value = charityText,
            onValueChange = onCharity,
            hint = "按填报额扣除；需符合不超过应纳税所得额 30% 等条件，超限会提示",
        )
        Text(
            text = items.first { it.key == TaxEngine.KEY_CHARITY }.policy,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        PolicyToggle(
            key = POLICY_OTHER,
            expandedKey = expandedKey,
            onExpand = onExpand,
            policy = "其他扣除包括个人养老金（≤12,000 元/年）、税优健康险（≤2,400 元/年）以及符合条件的公益慈善捐赠。" +
                "个人养老金与税优健康险需有对应凭证或税优识别码；捐赠扣除需符合 30% 限额等条件，最终以税务机关认定为准。",
        )
    }
    SyCard(
        modifier = Modifier.padding(top = SyDim.gapM),
        color = xm.goldSoft,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "其他扣除合计（年度）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.format(totalCents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 步骤 5：结果与模拟申报
// ────────────────────────────────────────────────────────────────

@Composable
private fun ResultStep(
    result: TaxResult,
    passedSamples: Int,
    sampleCount: Int,
    onSelfCheck: () -> Unit,
    onShare: () -> Unit,
) {
    val xm = SyTheme.colors
    val balance = result.payableCents - result.refundCents

    SySectionHeader(title = "测算结果", subtitle = "按推荐方案计算；所有金额为人民币元")
    SyCard(contentPadding = PaddingValues(18.dp)) {
        Text(
            text = when {
                result.refundCents > 0L -> "预计可退税额"
                result.payableCents > 0L -> "预计应补税额"
                else -> "预计不退不补"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        SyAmount(
            cents = if (result.refundCents > 0L) result.refundCents else result.payableCents,
            style = MaterialTheme.typography.displaySmall,
            color = when {
                result.refundCents > 0L -> xm.income
                result.payableCents > 0L -> xm.expense
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (result.hasBonus) {
            result.recommendedScheme?.let { scheme ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "年终奖已按「${scheme.label}」计算" +
                        (result.bonusComparison?.let { "，比另一方案少缴 ${Money.format(it.savingCents)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = xm.gold,
                )
            }
        }
        SyDivider(modifier = Modifier.padding(vertical = SyDim.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapM)) {
            SyStat(
                label = "全年应纳税额",
                value = Money.format(result.totalTaxCents),
                modifier = Modifier.weight(1f),
            )
            SyStat(
                label = "已预缴税额",
                value = Money.format(result.totalWithheldCents),
                modifier = Modifier.weight(1f),
            )
        }
        if (result.hasBonus && result.bonusWithheldCents > 0L) {
            Text(
                text = "已预缴税额含年终奖单独预扣 ${Money.format(result.bonusWithheldCents)}。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapM)) {
            SyStat(
                label = "应纳税所得额",
                value = Money.format(result.filingTaxableIncomeCents),
                modifier = Modifier.weight(1f),
            )
            SyStat(
                label = "综合税负率",
                value = burdenText(result.taxBurdenBasisPoints),
                modifier = Modifier.weight(1f),
                valueColor = xm.inkSoft,
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapM)) {
            SyStat(
                label = "适用税率",
                value = "${result.filingBracket.ratePercent}%",
                modifier = Modifier.weight(1f),
                valueColor = xm.gold,
            )
            SyStat(
                label = "速算扣除数",
                value = Money.format(result.filingBracket.quickDeductionCents),
                modifier = Modifier.weight(1f),
                valueColor = xm.gold,
            )
        }
        SyDivider(modifier = Modifier.padding(vertical = SyDim.gapM))
        SummaryRow("综合所得收入额", result.filingIncomeAmountCents)
        SummaryRow("减除费用", result.basicDeductionCents)
        SummaryRow("专项扣除（三险一金）", result.specialDeductionCents)
        SummaryRow("专项附加扣除", result.additionalDeductionCents)
        SummaryRow("其他扣除", result.otherDeductionCents)
        SummaryRow("应补（退）税额", balance, emphasize = true)
    }

    if (result.notices.isNotEmpty()) {
        Column(modifier = Modifier.padding(top = SyDim.gapM)) {
            result.notices.forEach { notice ->
                val color = if (notice.level == TaxNoticeLevel.WARNING) xm.expense else xm.inkSoft
                Text(
                    text = "· ${notice.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }

    result.bonusComparison?.let { comparison ->
        SySectionHeader(
            title = "年终奖两方案对比",
            subtitle = "单独计税政策延续至 2027 年 12 月 31 日",
        )
        BonusComparisonCard(result = result, comparison = comparison)
    }

    if (result.input.income.salaryCents > 0L) {
        SySectionHeader(title = "12 个月预扣明细", subtitle = "工资薪金累计预扣法 · 按月均摊估算")
        WithholdingTable(result = result)
    }

    SySectionHeader(title = "模拟申报", subtitle = "综合所得年度汇算（简化 11 项）")
    SyCard(contentPadding = PaddingValues(16.dp)) {
        FilingRow("收入额", result.filingIncomeAmountCents)
        FilingRow("减除费用", result.basicDeductionCents)
        FilingRow("专项扣除", result.specialDeductionCents)
        FilingRow("专项附加扣除", result.additionalDeductionCents)
        FilingRow("其他扣除", result.otherDeductionCents)
        FilingRow("应纳税所得额", result.filingTaxableIncomeCents, emphasize = true)
        FilingTextRow("适用税率", "${result.filingBracket.ratePercent}%")
        FilingRow("速算扣除数", result.filingBracket.quickDeductionCents)
        FilingRow("应纳税额", result.filingTaxCents, emphasize = true)
        FilingRow("已缴税额", result.filingWithheldCents)
        val filingBalance = result.filingBalanceCents
        FilingRow(
            label = "应补（退）税额",
            cents = filingBalance,
            emphasize = true,
            color = when {
                filingBalance > 0L -> xm.expense
                filingBalance < 0L -> xm.income
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        if (result.hasBonus && result.recommendedScheme == BonusScheme.SEPARATE) {
            val bonusTax = result.bonusComparison?.separateBonusTaxCents ?: 0L
            Text(
                text = "年终奖 ${Money.format(result.input.income.bonusCents)} 按单独计税（税额 ${Money.format(bonusTax)}），" +
                    "不计入本表综合所得，也不占用本表已缴税额。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        SyButton(
            text = "分享测算结果",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
            style = SyButtonStyle.Secondary,
        )
    }

    SyCard(
        modifier = Modifier.padding(top = SyDim.gapM),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = "免责声明",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本页是测算工具，不是正式纳税申报：未考虑减免税、境外所得抵免、一次性奖金以外的特殊计税、" +
                "补税豁免等情形；年度汇算以个人所得税 App 及税务机关认定为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(SyDim.gapL))
    SyButton(
        text = "计算器自检（$passedSamples / $sampleCount 组样例通过）",
        onClick = onSelfCheck,
        modifier = Modifier.fillMaxWidth(),
        style = SyButtonStyle.Ghost,
    )
}

@Composable
private fun BonusComparisonCard(result: TaxResult, comparison: BonusComparison) {
    val xm = SyTheme.colors
    SyCard(contentPadding = PaddingValues(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TableCell("方案", weight = 1.2f, align = TextAlign.Start)
            TableCell("应纳税额", weight = 1f)
            TableCell("已预缴", weight = 1f)
            TableCell("应补/退", weight = 1f)
        }
        SyDivider(modifier = Modifier.padding(vertical = 6.dp))
        ComparisonRow(
            label = BonusScheme.SEPARATE.label,
            taxCents = comparison.separateTotalTaxCents,
            withheldCents = result.totalWithheldCents,
            balanceCents = comparison.separateBalanceCents,
            recommended = comparison.recommended == BonusScheme.SEPARATE,
        )
        ComparisonRow(
            label = BonusScheme.COMBINED.label,
            taxCents = comparison.combinedTotalTaxCents,
            withheldCents = result.totalWithheldCents,
            balanceCents = comparison.combinedBalanceCents,
            recommended = comparison.recommended == BonusScheme.COMBINED,
        )
        SyDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = "推荐「${comparison.recommended.label}」：与另一方案相比全年少缴 ${Money.format(comparison.savingCents)}。" +
                "若单位已按单独计税预扣，选择并入综合所得会在汇算时多退/少补相应差额。",
            style = MaterialTheme.typography.bodySmall,
            color = xm.gold,
        )
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    taxCents: Long,
    withheldCents: Long,
    balanceCents: Long,
    recommended: Boolean,
) {
    val xm = SyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (recommended) xm.goldSoft else Color.Transparent)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (recommended) "$label ✓" else label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f),
        )
        TableCell(Money.plain(taxCents), weight = 1f, serif = true)
        TableCell(Money.plain(withheldCents), weight = 1f, serif = true)
        Text(
            text = when {
                balanceCents > 0L -> "补 ${Money.plain(balanceCents)}"
                balanceCents < 0L -> "退 ${Money.plain(-balanceCents)}"
                else -> "—"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                balanceCents > 0L -> xm.expense
                balanceCents < 0L -> xm.income
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WithholdingTable(result: TaxResult) {
    SyCard(contentPadding = PaddingValues(12.dp)) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TableCell("月份", width = 44.dp, align = TextAlign.Start)
                TableCell("累计收入", width = 92.dp)
                TableCell("累计应税", width = 92.dp)
                TableCell("本月预扣", width = 82.dp)
                TableCell("累计预扣", width = 86.dp)
            }
            // 横向滚动容器宽度不受限，SyDivider 的 fillMaxWidth 会退化成 0，这里用固定宽度细线
            Box(
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .width(396.dp)
                    .height(0.7.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            )
            result.withholdingRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${row.month} 月",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(44.dp),
                    )
                    TableCell(Money.plain(row.cumulativeIncomeCents), width = 92.dp)
                    TableCell(Money.plain(row.cumulativeTaxableCents), width = 92.dp)
                    TableCell(Money.plain(row.monthlyWithheldCents), width = 82.dp)
                    TableCell(Money.plain(row.cumulativeTaxCents), width = 86.dp)
                }
            }
        }
        Text(
            text = "演示按 12 个月平均发放；实际月度发放波动只改变各月预扣分布，年末累计预扣不变。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun FilingRow(
    label: String,
    cents: Long,
    emphasize: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val valueColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(cents, symbol = true, sign = false),
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun FilingTextRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun SummaryRow(label: String, cents: Long, emphasize: Boolean = false) {
    val color = if (emphasize) {
        when {
            cents > 0L -> SyTheme.colors.expense
            cents < 0L -> SyTheme.colors.income
            else -> MaterialTheme.colorScheme.onSurface
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (emphasize) "$label（正为补税、负为退税）" else label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(cents, symbol = true, sign = emphasize),
            style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 计算器自检
// ────────────────────────────────────────────────────────────────

private data class TaxSampleCheck(val sample: TaxSample, val actual: TaxResult) {
    val mismatches: List<String> get() = sample.mismatches(actual)
    val passed: Boolean get() = mismatches.isEmpty()
}

@Composable
private fun SelfCheckPage(checks: List<TaxSampleCheck>, onRerun: () -> Unit) {
    val xm = SyTheme.colors
    val passed = checks.count { it.passed }
    SyCard(
        color = if (passed == checks.size) xm.incomeSoft else xm.expenseSoft,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = "自检结果：$passed / ${checks.size} 组通过",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "样例期望值由独立的 Python 整数复算脚本生成，覆盖 7 个税率档、年终奖 36,000 / 144,000 临界点、" +
                "专项附加扣除边界以及退税 / 补税场景。点击「重新运行」会重新调用 TaxEngine 逐条比对。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        SyButton(
            text = "重新运行自检",
            onClick = {
                Haptics.play(HapticEffect.Confirm)
                onRerun()
            },
            modifier = Modifier.fillMaxWidth(),
            style = SyButtonStyle.Secondary,
        )
    }
    Spacer(modifier = Modifier.height(SyDim.gapM))
    checks.forEach { check ->
        TaxSampleCheckCard(check)
        Spacer(modifier = Modifier.height(SyDim.gapS))
    }
}

@Composable
private fun TaxSampleCheckCard(check: TaxSampleCheck) {
    val xm = SyTheme.colors
    val sample = check.sample
    val passed = check.passed
    SyCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${sample.id} · ${sample.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SyTag(
                text = if (passed) "通过" else "失败",
                color = if (passed) xm.income else xm.expense,
                background = if (passed) xm.incomeSoft else xm.expenseSoft,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = sample.scenario,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Text(
                text = "期望税额 ${Money.format(sample.expectedTotalTaxCents)}",
                style = MaterialTheme.typography.labelMedium,
                color = xm.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "实际 ${Money.format(check.actual.totalTaxCents)} · ${sample.balanceLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = if (passed) xm.income else xm.expense,
            )
        }
        check.mismatches.forEach { message ->
            Text(
                text = "· $message",
                style = MaterialTheme.typography.bodySmall,
                color = xm.expense,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 通用小组件
// ────────────────────────────────────────────────────────────────

@Composable
private fun DeductionEditor(
    item: DeductionItem,
    expanded: Boolean,
    onTogglePolicy: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val xm = SyTheme.colors
    SyCard(
        modifier = Modifier.padding(bottom = SyDim.gapM),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (item.annualCents > 0L) {
                SyAmount(
                    cents = item.annualCents,
                    style = MaterialTheme.typography.titleSmall,
                    color = xm.income,
                )
            } else {
                SyTag(text = "未填报")
            }
        }
        Text(
            text = item.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        content()
        PoliToggleInline(expanded = expanded, onToggle = onTogglePolicy, policy = item.policy)
    }
}

@Composable
private fun PoliToggleInline(expanded: Boolean, onToggle: () -> Unit, policy: String) {
    Spacer(modifier = Modifier.height(6.dp))
    SyTextButton(
        text = if (expanded) "收起政策说明" else "政策说明",
        onClick = onToggle,
        color = MaterialTheme.colorScheme.primary,
    )
    if (expanded) {
        Text(
            text = policy,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun PolicyToggle(
    key: String,
    expandedKey: String?,
    onExpand: (String?) -> Unit,
    policy: String,
) {
    PoliToggleInline(
        expanded = expandedKey == key,
        onToggle = { onExpand(if (expandedKey == key) null else key) },
        policy = policy,
    )
}

@Composable
private fun AmountField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(sanitizeAmountInput(it)) },
            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            placeholder = {
                Text(text = "0", style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    suffix: String = "",
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        StepperButton(text = "−", onClick = { onChange((value - 1).coerceIn(range)) })
        Text(
            text = "$value$suffix",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        StepperButton(text = "+", onClick = { onChange((value + 1).coerceIn(range)) })
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
            .syClickable(effect = HapticEffect.Tick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float? = null,
    width: androidx.compose.ui.unit.Dp? = null,
    align: TextAlign = TextAlign.End,
    serif: Boolean = false,
) {
    val style = if (serif) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier
            .let { if (weight != null) it.weight(weight) else it.width(width ?: 0.dp) },
    )
}

// ────────────────────────────────────────────────────────────────
// 纯函数辅助
// ────────────────────────────────────────────────────────────────

private fun centsOf(text: String): Long = Money.parseCents(text)?.coerceAtLeast(0L) ?: 0L

private fun rentLevelOf(index: Int): HousingRentLevel? = when (index) {
    1 -> HousingRentLevel.FIRST
    2 -> HousingRentLevel.SECOND
    3 -> HousingRentLevel.THIRD
    else -> null
}

private fun List<DeductionItem>.itemOf(key: String): DeductionItem =
    firstOrNull { it.key == key } ?: DeductionItem(key, key, 0L, "未填报", "")

private fun sanitizeAmountInput(raw: String): String {
    val builder = StringBuilder()
    var hasDot = false
    raw.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !hasDot -> {
                hasDot = true
                builder.append(char)
            }
        }
    }
    return builder.toString().take(14)
}

private fun burdenText(basisPoints: Int): String =
    String.format(Locale.CHINA, "%.1f%%", basisPoints / 100.0)

private fun balanceText(payableCents: Long, refundCents: Long): String = when {
    payableCents > 0L -> "补 ${Money.format(payableCents)}"
    refundCents > 0L -> "退 ${Money.format(refundCents)}"
    else -> "不退不补"
}

private fun buildShareText(result: TaxResult): String {
    val builder = StringBuilder()
    builder.appendLine("岁余 · 个税测算结果（${TaxRules.POLICY_YEAR} 年度综合所得汇算）")
    builder.appendLine("综合所得收入额：${Money.format(result.comprehensiveIncomeAmountCents)}")
    if (result.hasBonus) {
        builder.appendLine("全年一次性奖金：${Money.format(result.input.income.bonusCents)}")
        result.bonusComparison?.let { comparison ->
            builder.appendLine(
                "年终奖方案：${comparison.recommended.label}（较另一方案少缴 ${Money.format(comparison.savingCents)}）",
            )
        }
    }
    builder.appendLine("减除费用：${Money.format(result.basicDeductionCents)}")
    builder.appendLine("专项扣除（三险一金）：${Money.format(result.specialDeductionCents)}")
    builder.appendLine("专项附加扣除：${Money.format(result.additionalDeductionCents)}")
    builder.appendLine("其他扣除：${Money.format(result.otherDeductionCents)}")
    builder.appendLine("应纳税所得额：${Money.format(result.filingTaxableIncomeCents)}")
    builder.appendLine(
        "适用税率：${result.filingBracket.ratePercent}%｜速算扣除数：${Money.format(result.filingBracket.quickDeductionCents)}",
    )
    builder.appendLine("全年应纳税额：${Money.format(result.totalTaxCents)}")
    builder.appendLine("已预缴税额：${Money.format(result.totalWithheldCents)}")
    builder.appendLine("应补（退）税额：${balanceText(result.payableCents, result.refundCents)}")
    builder.appendLine("综合税负率：${burdenText(result.taxBurdenBasisPoints)}")
    builder.appendLine()
    builder.append("说明：本测算仅供参考，不构成正式纳税申报；实际税额以税务机关认定为准。")
    return builder.toString()
}
