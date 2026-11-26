package com.suiyu.ledger.ui.screens.wealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.analytics.AssetAllocation
import com.suiyu.ledger.analytics.CashflowBaseline
import com.suiyu.ledger.analytics.FinancialHealthReport
import com.suiyu.ledger.analytics.InsightTone
import com.suiyu.ledger.analytics.RiskAdvice
import com.suiyu.ledger.analytics.RiskGrade
import com.suiyu.ledger.analytics.RiskMetric
import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.ui.components.ChartSlice
import com.suiyu.ledger.ui.components.DonutChart
import com.suiyu.ledger.ui.components.RadarAxis
import com.suiyu.ledger.ui.components.RadarChart
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyProgressBar
import com.suiyu.ledger.ui.components.SyTag
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Money

@Composable
@ReadOnlyComposable
internal fun gradeColor(grade: RiskGrade): Color = when (grade) {
    RiskGrade.EXCELLENT, RiskGrade.HEALTHY -> SyTheme.colors.income
    RiskGrade.WATCH -> SyTheme.colors.gold
    RiskGrade.RISK -> SyTheme.colors.expense
}

@Composable
internal fun HealthHero(report: FinancialHealthReport, masked: Boolean) {
    val xm = SyTheme.colors
    SyCard(
        color = xm.heroSurface,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "家庭净资产",
                    style = MaterialTheme.typography.labelMedium,
                    color = xm.heroContent.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (masked) "¥ ••••" else Money.format(report.netWorthCents),
                    style = MaterialTheme.typography.displaySmall,
                    color = xm.heroContent,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            ScoreRing(score = report.score, grade = report.grade)
        }
        // 三项统计独占一行，360dp 窄屏上也不会被评分环挤到换行截断
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeroStat("总资产", report.totalAssetsCents, masked, xm.heroContent, Modifier.weight(1f))
            HeroStat("总负债", report.totalLiabilitiesCents, masked, xm.heroContent, Modifier.weight(1f))
            HeroStat("流动类", report.liquidAssetsCents, masked, xm.heroContent, Modifier.weight(1f))
        }
        if (report.hasAnyData) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = report.headline,
                style = MaterialTheme.typography.bodySmall,
                color = xm.heroContent.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    cents: Long,
    masked: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.55f),
        )
        Text(
            text = if (masked) "••••" else Money.format(cents),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScoreRing(score: Int, grade: RiskGrade) {
    // 总览卡是深墨绿底，评分环与评级文字用更亮的同色系保证对比度
    val color = when (grade) {
        RiskGrade.EXCELLENT, RiskGrade.HEALTHY -> Color(0xFF9AD6B8)
        RiskGrade.WATCH -> Color(0xFFE8CD92)
        RiskGrade.RISK -> Color(0xFFF0A796)
    }
    val xm = SyTheme.colors
    Box(
        modifier = Modifier.size(92.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(
                color = xm.heroContent.copy(alpha = 0.16f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * score.coerceIn(0, 100) / 100f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = xm.heroContent,
            )
            Text(
                text = grade.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
internal fun BaselineCard(
    cashflow: CashflowBaseline,
    masked: Boolean,
    onEditProfile: () -> Unit,
) {
    val xm = SyTheme.colors
    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "固定收支基线",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SyTag(
                text = when {
                    cashflow.incomeFromManual || cashflow.expenseFromManual -> "含手动填写"
                    cashflow.monthsUsed > 0 -> "按近 ${cashflow.monthsUsed} 个月流水"
                    else -> "待补充"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BaselineStat("月固定收入", cashflow.monthlyFixedIncomeCents, masked, xm.income, Modifier.weight(1f))
            BaselineStat("月固定支出", cashflow.essentialOutflowCents, masked, xm.expense, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            BaselineStat(
                label = "月均结余",
                cents = cashflow.monthlySurplusCents,
                masked = masked,
                color = if (cashflow.monthlySurplusCents >= 0L) xm.income else xm.expense,
                modifier = Modifier.weight(1f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "储蓄率",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (cashflow.monthlyIncomeCents > 0L) Money.percent(cashflow.savingsRate, false) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (cashflow.savingsRate >= 0) xm.income else xm.expense,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "固定支出 = 居住 + 通讯 + 教育；月供另计。可手动覆盖或继续记账让估算更准。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyTextButton(text = "修改工作与收支", onClick = onEditProfile)
        }
    }
}

@Composable
private fun BaselineStat(label: String, cents: Long, masked: Boolean, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (masked) "¥ ••••" else Money.format(cents),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
internal fun RadarCard(report: FinancialHealthReport) {
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "六维风险雷达",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "分数越高越健康；低于 55 分的维度会出现在下方建议里。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        RadarChart(
            axes = report.dimensions.map { RadarAxis(it.label, it.score / 100f) },
            color = gradeColor(report.grade),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        report.dimensions.forEach { dimension ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dimension.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(72.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    SyProgressBar(
                        progress = dimension.score / 100f,
                        color = gradeColor(RiskGrade.ofScore(dimension.score)),
                        height = 5.dp,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${dimension.score}",
                    style = MaterialTheme.typography.labelMedium,
                    color = gradeColor(RiskGrade.ofScore(dimension.score)),
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    text = dimension.valueText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(112.dp),
                )
            }
        }
    }
}

@Composable
internal fun MetricsCard(report: FinancialHealthReport, masked: Boolean) {
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "关键风险指标",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        report.metrics.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { metric ->
                    MetricCell(metric = metric, masked = masked, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricCell(metric: RiskMetric, masked: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(gradeColor(metric.grade)),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (masked && metric.key != "emergency" && metric.key != "dsr") "¥ ••••" else metric.valueText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AllocationCard(report: FinancialHealthReport, masked: Boolean) {
    val palette = SyTheme.colors.chartPalette
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "资产配置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (report.allocations.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "还没有录入资产。添加存款、理财、房产或车辆后，这里会显示资产结构。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(
                    slices = report.allocations.mapIndexed { index, allocation ->
                        ChartSlice(
                            key = allocation.label,
                            label = allocation.label,
                            value = allocation.cents / 100f,
                            color = palette[index % palette.size],
                        )
                    },
                    modifier = Modifier.size(132.dp),
                    centerLabel = "总资产",
                    centerValue = if (masked) "¥ ••••" else Money.compact(report.totalAssetsCents),
                    strokeWidth = 18.dp,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    report.allocations.forEachIndexed { index, allocation ->
                        AllocationRow(allocation, palette[index % palette.size], masked)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllocationRow(allocation: AssetAllocation, color: Color, masked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = allocation.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (masked) "••" else Money.percent(allocation.ratio, false),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AssetsSection(
    assets: List<Asset>,
    masked: Boolean,
    onAdd: () -> Unit,
    onEdit: (Asset) -> Unit,
) {
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        assets.forEachIndexed { index, asset ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .syClickable(effect = HapticEffect.Soft) { onEdit(asset) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SyTheme.colors.goldSoft.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = asset.category.emoji, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = asset.category.label + (if (asset.note.isNotBlank()) " · ${asset.note}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (masked) "¥ ••••" else Money.format(asset.valueCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            if (index != assets.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                )
            }
        }
        if (assets.isEmpty()) {
            Text(
                text = "还没有资产记录。建议先录入活期 / 定期 / 理财，再补充房产与车辆。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            SyButton(text = "＋ 添加资产", onClick = onAdd, style = SyButtonStyle.Secondary)
        }
    }
}

@Composable
internal fun LiabilitiesSection(
    liabilities: List<Liability>,
    masked: Boolean,
    onAdd: () -> Unit,
    onEdit: (Liability) -> Unit,
) {
    val monthlyTotal = liabilities.sumOf { it.monthlyPaymentCents }
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        liabilities.forEachIndexed { index, liability ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .syClickable(effect = HapticEffect.Soft) { onEdit(liability) }
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SyTheme.colors.expenseSoft.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = liability.category.emoji, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = liability.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = liability.category.label +
                            if (liability.monthlyPaymentCents > 0L) {
                                " · 月供 " + if (masked) "¥ ••••" else Money.format(liability.monthlyPaymentCents)
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (masked) "¥ ••••" else Money.format(liability.balanceCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = SyTheme.colors.expense,
                    maxLines = 1,
                )
            }
            if (index != liabilities.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                )
            }
        }
        if (liabilities.isEmpty()) {
            Text(
                text = "没有负债记录。若有房贷、车贷或信用卡欠款，建议补录，月供会进入现金流与月供收入比。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else if (monthlyTotal > 0L) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "每月还款合计：" + if (masked) "¥ ••••" else Money.format(monthlyTotal),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            SyButton(text = "＋ 添加负债", onClick = onAdd, style = SyButtonStyle.Secondary)
        }
    }
}

@Composable
internal fun ProfileCard(profile: FinancialProfile, onEdit: () -> Unit) {
    val xm = SyTheme.colors
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "收入与保障画像",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SyTextButton(text = "修改", onClick = onEdit)
        }
        Spacer(modifier = Modifier.height(4.dp))
        ProfileRow("职业", profile.occupation.ifBlank { "未填写" })
        ProfileRow("单位性质", profile.employmentType.label)
        ProfileRow(
            "工作年限 / 收入稳定性",
            "${profile.jobYears} 年 · " + when (profile.incomeStability) {
                1 -> "波动"
                3 -> "稳定"
                else -> "一般"
            },
        )
        ProfileRow(
            "家庭结构",
            "配偶" + if (profile.spouseWorks) "在职" else "未在职" +
                " · 赡养 ${profile.elderlyCount} 人 · 子女 ${profile.childrenCount} 人",
        )
        ProfileRow(
            "保障",
            buildString {
                append(if (profile.hasMedicalInsurance) "医保" else "无医保")
                append(" · ")
                append(if (profile.hasCriticalIllnessInsurance) "重疾" else "无重疾")
                append(" · ")
                append(if (profile.hasLifeInsurance) "寿险" else "无寿险")
                if (profile.insuranceCoverageCents > 0L) {
                    append(" · 保额 ")
                    append(Money.format(profile.insuranceCoverageCents))
                }
            },
        )
        ProfileRow(
            "手动固定收支",
            if (profile.manualMonthlyIncomeCents > 0L || profile.manualMonthlyFixedExpenseCents > 0L) {
                "收入 " + (if (profile.manualMonthlyIncomeCents > 0L) Money.format(profile.manualMonthlyIncomeCents) else "自动") +
                    " · 支出 " + (if (profile.manualMonthlyFixedExpenseCents > 0L) Money.format(profile.manualMonthlyFixedExpenseCents) else "自动")
            } else {
                "按流水自动估算"
            },
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "工作与家庭信息只保存在本机，用于判断收入稳定性与保障缺口。",
            style = MaterialTheme.typography.labelSmall,
            color = xm.inkSoft.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(132.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun AdviceCard(advice: List<RiskAdvice>) {
    val xm = SyTheme.colors
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        advice.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when (item.tone) {
                                InsightTone.POSITIVE -> xm.income
                                InsightTone.NEGATIVE -> xm.expense
                                InsightTone.NEUTRAL -> xm.gold
                            },
                        ),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (index != advice.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                )
            }
        }
    }
}

@Composable
internal fun EmptyHealthHint(onAddAsset: () -> Unit, onEditProfile: () -> Unit) {
    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
        Text(
            text = "还没有财务体检数据",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "录入存款、理财、房产等资产，再补充工作与家庭情况，岁余会结合你的固定收支给出风险评分与建议。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SyButton(text = "添加资产", onClick = onAddAsset)
            }
            Box(modifier = Modifier.weight(1f)) {
                SyButton(
                    text = "填写工作与收支",
                    onClick = onEditProfile,
                    style = SyButtonStyle.Secondary,
                )
            }
        }
    }
}

/** 资产/负债编辑入口的小提示（供屏幕组装处复用） */
@Composable
internal fun AssetHint() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyChip(text = "点击任一资产/负债可编辑", selected = false, onClick = {})
    }
}
