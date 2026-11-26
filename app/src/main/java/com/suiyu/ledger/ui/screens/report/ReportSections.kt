package com.suiyu.ledger.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.analytics.CategoryChange
import com.suiyu.ledger.analytics.CategorySlice
import com.suiyu.ledger.analytics.Change
import com.suiyu.ledger.analytics.MonthComparison
import com.suiyu.ledger.ui.components.BarGroup
import com.suiyu.ledger.ui.components.ChartRankItem
import com.suiyu.ledger.ui.components.ChartRankList
import com.suiyu.ledger.ui.components.ChartSlice
import com.suiyu.ledger.ui.components.DonutChart
import com.suiyu.ledger.ui.components.GroupedBarChart
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey
import java.util.Locale
import kotlin.math.abs

/**
 * 报表页上半部分：总览四宫格 / 收支结构 / 环比分析 / 同比分析。
 * 下半部分（成员分账、财务健康、自动洞察、分享与个税行动区）见 ReportMembers.kt。
 * 版式保持「一张纸」的阅读节奏：先给结论，再给结构，再给对比。
 * 所有数值都取自 [Analytics] / [Narrative] 的既有结构，这里只做排版与差值展示。
 */

/** 报表内统一金额文本（隐私模式下遮罩） */
internal fun reportAmount(cents: Long, masked: Boolean): String =
    if (masked) "¥ ••••" else Money.format(cents)

private fun ppText(delta: Double): String =
    String.format(Locale.CHINA, "%+.1f", delta * 100.0) + "pp"

// ────────────────────────────────────────────────────────────────
// 四宫格总览
// ────────────────────────────────────────────────────────────────

@Composable
internal fun OverviewGrid(comparison: MonthComparison, masked: Boolean) {
    val current = comparison.current
    val last = comparison.lastYear
    val xm = SyTheme.colors
    val balanceYoY = Change(current.balanceCents, last.balanceCents)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AmountTile(
            label = "收入",
            cents = current.incomeCents,
            valueColor = xm.income,
            moM = comparison.incomeMoM,
            yoY = comparison.incomeYoY,
            goodWhenUp = true,
            masked = masked,
            modifier = Modifier.weight(1f),
        )
        AmountTile(
            label = "支出",
            cents = current.expenseCents,
            valueColor = xm.expense,
            moM = comparison.expenseMoM,
            yoY = comparison.expenseYoY,
            goodWhenUp = false,
            masked = masked,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BalanceTile(
            cents = current.balanceCents,
            moM = comparison.balanceMoM,
            yoY = balanceYoY,
            masked = masked,
            modifier = Modifier.weight(1f),
        )
        RateTile(
            rate = current.savingsRate,
            hasIncome = current.incomeCents > 0L,
            moMDelta = if (comparison.previous.incomeCents > 0L) comparison.savingsRateDelta else null,
            yoYDelta = if (last.incomeCents > 0L) current.savingsRate - last.savingsRate else null,
            masked = masked,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AmountTile(
    label: String,
    cents: Long,
    valueColor: Color,
    moM: Change,
    yoY: Change,
    goodWhenUp: Boolean,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    SyCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = reportAmount(cents, masked),
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(7.dp))
        BadgeLine(label = "环比", change = moM, masked = masked, goodWhenUp = goodWhenUp)
        Spacer(modifier = Modifier.height(3.dp))
        BadgeLine(label = "同比", change = yoY, masked = masked, goodWhenUp = goodWhenUp)
    }
}

@Composable
private fun BalanceTile(
    cents: Long,
    moM: Change,
    yoY: Change,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    val xm = SyTheme.colors
    val color = when {
        cents > 0L -> xm.income
        cents < 0L -> xm.expense
        else -> MaterialTheme.colorScheme.onSurface
    }
    SyCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)) {
        Text(text = "结余", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = reportAmount(cents, masked),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(7.dp))
        BadgeLine(label = "环比", change = moM, masked = masked, goodWhenUp = true)
        Spacer(modifier = Modifier.height(3.dp))
        BadgeLine(label = "同比", change = yoY, masked = masked, goodWhenUp = true)
    }
}

@Composable
private fun RateTile(
    rate: Double,
    hasIncome: Boolean,
    moMDelta: Double?,
    yoYDelta: Double?,
    masked: Boolean,
    modifier: Modifier = Modifier,
) {
    val xm = SyTheme.colors
    // 收入为 0 时结余率没有分母，按契约显示「—」而不是 0.0%
    val valueText = if (hasIncome) Money.percent(rate) else "—"
    val color = when {
        !hasIncome -> MaterialTheme.colorScheme.onSurfaceVariant
        rate >= 0.10 -> xm.income
        rate >= 0.0 -> xm.gold
        else -> xm.expense
    }
    SyCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)) {
        Text(text = "结余率", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "环比",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            DeltaBadge(delta = moMDelta, masked = masked, goodWhenUp = true)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "同比",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
            DeltaBadge(delta = yoYDelta, masked = masked, goodWhenUp = true)
        }
    }
}

@Composable
private fun BadgeLine(label: String, change: Change, masked: Boolean, goodWhenUp: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        ChangeBadge(change = change, masked = masked, goodWhenUp = goodWhenUp)
    }
}

/** 环比 / 同比徽标：箭头 + 百分比；上涨是否算好事由 [goodWhenUp] 决定 */
@Composable
internal fun ChangeBadge(change: Change, masked: Boolean, goodWhenUp: Boolean) {
    val xm = SyTheme.colors
    if (masked) {
        Badge(text = "••", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    // 基准为 0 或为负（如结余/结余率为负）时，相对百分比会失真，统一显示「无基准」
    val usableBase = change.hasBase && change.base > 0L
    val text = when {
        !usableBase -> "— 无基准"
        change.direction == 0 -> "→ 持平"
        else -> "${if (change.direction > 0) "↑" else "↓"} ${Money.percent(change.pct, signed = true)}"
    }
    val good = when {
        !usableBase || change.direction == 0 -> true
        goodWhenUp -> change.direction > 0
        else -> change.direction < 0
    }
    val color = when {
        !usableBase -> MaterialTheme.colorScheme.onSurfaceVariant
        good -> xm.positive
        else -> xm.negative
    }
    Badge(text = text, color = color)
}

/** 结余率变化用百分点（pp），避免与「相对变化」混淆 */
@Composable
private fun DeltaBadge(delta: Double?, masked: Boolean, goodWhenUp: Boolean) {
    val xm = SyTheme.colors
    if (masked) {
        Badge(text = "••", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (delta == null || delta.isNaN() || delta.isInfinite()) {
        Badge(text = "— 无基准", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val text = when {
        abs(delta) < 0.0005 -> "→ 持平"
        delta > 0 -> "↑ ${ppText(delta)}"
        else -> "↓ ${ppText(delta)}"
    }
    val good = if (goodWhenUp) delta >= 0 else delta <= 0
    Badge(text = text, color = if (good) xm.positive else xm.negative)
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

// ────────────────────────────────────────────────────────────────
// 收支结构
// ────────────────────────────────────────────────────────────────

@Composable
internal fun StructureBlock(slices: List<CategorySlice>, masked: Boolean) {
    SySectionHeader(
        title = "收支结构",
        subtitle = "本月支出的分类占比，排行取前 5 类",
    )
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        if (slices.isEmpty()) {
            Text(
                text = "本月没有支出记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SyCard
        }
        val total = slices.sumOf { it.totalCents }
        val topThreeRatio = slices.take(3).sumOf { it.ratio }
        val chartSlices = slices.map { slice ->
            ChartSlice(
                key = slice.categoryId,
                label = slice.name,
                value = slice.totalCents.toFloat(),
                color = parseHexColor(slice.colorHex),
            )
        }
        DonutChart(
            slices = chartSlices,
            modifier = Modifier.fillMaxWidth().height(190.dp),
            centerLabel = "本月支出",
            centerValue = reportAmount(total, masked),
            strokeWidth = 22.dp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "共 ${slices.size} 个分类，前三类合计占 ${Money.percent(topThreeRatio)}。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SyDivider()
        Spacer(modifier = Modifier.height(6.dp))
        ChartRankList(
            items = slices.take(5).map { slice ->
                ChartRankItem(
                    key = slice.categoryId,
                    label = slice.name,
                    emoji = slice.emoji,
                    valueLabel = reportAmount(slice.totalCents, masked),
                    ratio = slice.ratio.toFloat(),
                    color = parseHexColor(slice.colorHex),
                )
            },
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 环比分析（分类变化 Top 5）
// ────────────────────────────────────────────────────────────────

@Composable
internal fun MomBlock(changes: List<CategoryChange>, masked: Boolean) {
    SySectionHeader(
        title = "环比分析",
        subtitle = "与上月相比，变化最大的 5 个分类",
    )
    val active = changes.filter { it.currentCents > 0L || it.baseCents > 0L }.take(5)
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        if (active.isEmpty()) {
            Text(
                text = "本月与上月没有可比的支出记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@SyCard
        }
        active.forEachIndexed { index, change ->
            CategoryChangeRow(change = change, masked = masked)
            if (index != active.lastIndex) SyDivider()
        }
    }
}

@Composable
private fun CategoryChangeRow(change: CategoryChange, masked: Boolean) {
    val xm = SyTheme.colors
    val delta = change.change.diff
    val color = when {
        delta > 0L -> xm.negative
        delta < 0L -> xm.positive
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = change.emoji, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = change.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "本月 ${reportAmount(change.currentCents, masked)} · 上月 ${reportAmount(change.baseCents, masked)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (masked) "••" else Money.format(delta, sign = true),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
            )
            Text(
                text = changeRatioText(change),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private fun changeRatioText(change: CategoryChange): String {
    val delta = change.change
    return when {
        change.baseCents == 0L -> "上月无此类"
        change.currentCents == 0L -> "本月为 0"
        else -> Money.percent(delta.pct, signed = true)
    }
}

// ────────────────────────────────────────────────────────────────
// 同比分析（本月 vs 去年同月）
// ────────────────────────────────────────────────────────────────

@Composable
internal fun YoyBlock(comparison: MonthComparison, masked: Boolean) {
    val current = comparison.current
    val last = comparison.lastYear
    val lastYearMonth = MonthKey(comparison.month.year - 1, comparison.month.month)
    SySectionHeader(
        title = "同比分析",
        subtitle = "本月 vs ${lastYearMonth.label()}",
    )
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        val groups = listOf(
            BarGroup(label = "支出", primary = current.expenseCents.toFloat(), secondary = last.expenseCents.toFloat()),
            BarGroup(label = "收入", primary = current.incomeCents.toFloat(), secondary = last.incomeCents.toFloat()),
        )
        if (groups.all { it.primary <= 0f && it.secondary <= 0f }) {
            Text(
                text = "本月与去年同月都没有记录，暂无法同比。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SyCard
        }
        GroupedBarChart(
            groups = groups,
            primaryLabel = "本月",
            secondaryLabel = "去年同月",
        )
        Spacer(modifier = Modifier.height(10.dp))
        SyDivider()
        Spacer(modifier = Modifier.height(4.dp))
        YoyRow(
            label = "支出",
            currentCents = current.expenseCents,
            lastCents = last.expenseCents,
            change = comparison.expenseYoY,
            goodWhenUp = false,
            masked = masked,
        )
        SyDivider()
        YoyRow(
            label = "收入",
            currentCents = current.incomeCents,
            lastCents = last.incomeCents,
            change = comparison.incomeYoY,
            goodWhenUp = true,
            masked = masked,
        )
        SyDivider()
        YoyRow(
            label = "结余",
            currentCents = current.balanceCents,
            lastCents = last.balanceCents,
            change = Change(current.balanceCents, last.balanceCents),
            goodWhenUp = true,
            masked = masked,
        )
    }
}

@Composable
private fun YoyRow(
    label: String,
    currentCents: Long,
    lastCents: Long,
    change: Change,
    goodWhenUp: Boolean,
    masked: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(44.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "去年同月 ${reportAmount(lastCents, masked)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "本月 ${reportAmount(currentCents, masked)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ChangeBadge(change = change, masked = masked, goodWhenUp = goodWhenUp)
    }
}
