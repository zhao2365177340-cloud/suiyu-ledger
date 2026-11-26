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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.analytics.HealthMetrics
import com.suiyu.ledger.analytics.Insight
import com.suiyu.ledger.analytics.InsightTone
import com.suiyu.ledger.analytics.MemberSlice
import com.suiyu.ledger.analytics.MonthSummary
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.ui.components.ChartRankItem
import com.suiyu.ledger.ui.components.ChartRankList
import com.suiyu.ledger.ui.components.LineSeries
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyMemberAvatar
import com.suiyu.ledger.ui.components.SyProgressBar
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SyTag
import com.suiyu.ledger.ui.components.TrendChart
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey

/**
 * 报表页下半部分：成员分账（含成员单独分析）、财务健康、自动洞察、分享 / 个税行动区。
 * 与 [ReportScreen] 同包，数值全部来自 Analytics / Narrative。
 */
// ────────────────────────────────────────────────────────────────
// 成员分账 + 成员单独分析
// ────────────────────────────────────────────────────────────────

@Composable
internal fun MembersBlock(
    memberSlices: List<MemberSlice>,
    members: List<Member>,
    records: List<TxRecord>,
    categories: List<Category>,
    month: MonthKey,
    expandedMemberId: String?,
    onToggle: (String) -> Unit,
    masked: Boolean,
) {
    SySectionHeader(
        title = "成员分账",
        subtitle = "按成员看支出、收入、结余与家庭占比；点按一行展开个人分析",
    )
    val familyExpenseCents = memberSlices.sumOf { it.expenseCents }
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        if (memberSlices.isEmpty()) {
            Text(
                text = "还没有家庭成员。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@SyCard
        }
        memberSlices.forEachIndexed { index, slice ->
            val member = members.firstOrNull { it.id == slice.memberId }
            MemberRow(
                slice = slice,
                member = member,
                familyExpenseCents = familyExpenseCents,
                expanded = expandedMemberId == slice.memberId,
                masked = masked,
                onToggle = { onToggle(slice.memberId) },
            )
            if (expandedMemberId == slice.memberId) {
                MemberAnalysis(
                    slice = slice,
                    records = records,
                    categories = categories,
                    month = month,
                    masked = masked,
                )
            }
            if (index != memberSlices.lastIndex) SyDivider()
        }
    }
}

@Composable
private fun MemberRow(
    slice: MemberSlice,
    member: Member?,
    familyExpenseCents: Long,
    expanded: Boolean,
    masked: Boolean,
    onToggle: () -> Unit,
) {
    // 家庭支出为 0 时占比没有分母，显示「—」
    val ratioText = if (familyExpenseCents > 0L) Money.percent(slice.expenseRatio) else "—"
    val xm = SyTheme.colors
    val memberColor = parseHexColor(slice.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .syClickable(effect = HapticEffect.Select, onClick = onToggle)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (member != null) {
            SyMemberAvatar(member = member, size = 36.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(memberColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = slice.emoji, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slice.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (slice.role.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SyTag(text = slice.role)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "支出 ${reportAmount(slice.expenseCents, masked)} · 收入 ${reportAmount(slice.incomeCents, masked)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SyProgressBar(
                    progress = slice.expenseRatio.toFloat(),
                    color = memberColor,
                    height = 4.dp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "占 $ratioText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "结余",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = reportAmount(slice.balanceCents, masked),
                style = MaterialTheme.typography.titleSmall,
                color = if (slice.balanceCents < 0L) xm.expense else xm.income,
                maxLines = 1,
            )
            Text(
                text = if (expanded) "收起 ▴" else "分析 ▾",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MemberAnalysis(
    slice: MemberSlice,
    records: List<TxRecord>,
    categories: List<Category>,
    month: MonthKey,
    masked: Boolean,
) {
    val xm = SyTheme.colors
    val points = remember(records, slice.memberId, month) {
        Analytics.memberMonthPoints(records, slice.memberId, month, 6)
    }
    val categorySlices = remember(records, categories, slice.memberId, month) {
        Analytics.memberCategorySlices(records, categories, slice.memberId, month)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = "${slice.name} · 个人分析",
            style = MaterialTheme.typography.labelMedium,
            color = xm.gold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本月共 ${slice.count} 笔：支出 ${reportAmount(slice.expenseCents, masked)}，收入 ${reportAmount(slice.incomeCents, masked)}，结余 ${reportAmount(slice.balanceCents, masked)}。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "近 6 个月收支走势",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (points.size >= 2 && points.any { it.expenseCents > 0L || it.incomeCents > 0L }) {
            TrendChart(
                series = listOf(
                    LineSeries(label = "支出", color = xm.expense, values = points.map { it.expenseCents.toFloat() }),
                    LineSeries(label = "收入", color = xm.income, values = points.map { it.incomeCents.toFloat() }),
                ),
                labels = points.map { it.month.short() },
            )
        } else {
            Text(
                text = "近 6 个月记录不足，暂无法形成趋势。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "主要支出分类",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (categorySlices.isEmpty()) {
            Text(
                text = "该成员本月没有支出记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        } else {
            ChartRankList(
                items = categorySlices.take(4).map { item ->
                    ChartRankItem(
                        key = item.categoryId,
                        label = item.name,
                        emoji = item.emoji,
                        valueLabel = reportAmount(item.totalCents, masked),
                        ratio = item.ratio.toFloat(),
                        color = parseHexColor(item.colorHex),
                    )
                },
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 财务健康
// ────────────────────────────────────────────────────────────────

@Composable
internal fun HealthBlock(
    health: HealthMetrics,
    summary: MonthSummary,
    categories: List<Category>,
    masked: Boolean,
) {
    val xm = SyTheme.colors
    SySectionHeader(
        title = "财务健康",
        subtitle = "结余率、刚性支出与消费节奏",
    )
    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
        val hasIncome = summary.incomeCents > 0L
        HealthRow(
            label = "结余率",
            value = if (hasIncome) Money.percent(health.savingsRate) else "—",
            sub = if (hasIncome) savingsRatingLabel(health.savingsRating) else "本月没有收入，结余率不适用",
            valueColor = when {
                !hasIncome -> MaterialTheme.colorScheme.onSurfaceVariant
                health.savingsRate >= 0.10 -> xm.income
                health.savingsRate >= 0.0 -> xm.gold
                else -> xm.expense
            },
        )
        SyDivider()
        val hasExpense = summary.expenseCents > 0L
        HealthRow(
            label = "刚性支出占比",
            value = if (hasExpense) Money.percent(health.fixedExpenseRatio) else "—",
            sub = if (hasExpense) {
                "居住、通讯、教育等固定支出约 ${reportAmount((summary.expenseCents * health.fixedExpenseRatio).toLong(), masked)}"
            } else {
                "本月没有支出记录，占比不适用"
            },
        )
        SyDivider()
        HealthRow(
            label = "零支出天数",
            value = "${health.noSpendDays} 天",
            sub = "本月没有支出记录的天数",
        )
        SyDivider()
        val largest = health.largestExpense
        HealthRow(
            label = "最大单笔支出",
            value = if (largest == null) "—" else reportAmount(largest.amountCents, masked),
            sub = if (largest == null) {
                "本月没有支出记录"
            } else {
                buildString {
                    append(Dates.monthDay(largest.occurredAt))
                    append(" · ")
                    append(categories.firstOrNull { it.id == largest.categoryId }?.name ?: "未分类")
                    if (largest.merchant.isNotBlank()) {
                        append(" · ")
                        append(largest.merchant)
                    }
                }
            },
            valueColor = xm.expense,
        )
    }
}

@Composable
private fun HealthRow(
    label: String,
    value: String,
    sub: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun savingsRatingLabel(rating: Int): String = when (rating) {
    3 -> "健康区间（≥ 30%）"
    2 -> "尚可（10% – 30%）"
    1 -> "偏低（0 – 10%）"
    else -> "支出超过收入"
}

// ────────────────────────────────────────────────────────────────
// 自动洞察
// ────────────────────────────────────────────────────────────────

@Composable
internal fun InsightsBlock(insights: List<Insight>) {
    if (insights.isEmpty()) return
    SySectionHeader(
        title = "自动洞察",
        subtitle = "根据本月数据生成，只陈述可核对的事实",
    )
    insights.forEach { insight ->
        InsightCard(insight = insight)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val xm = SyTheme.colors
    val accent = when (insight.tone) {
        InsightTone.POSITIVE -> xm.positive
        InsightTone.NEUTRAL -> xm.gold
        InsightTone.NEGATIVE -> xm.negative
    }
    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = insight.emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 行动区
// ────────────────────────────────────────────────────────────────

@Composable
internal fun ReportActions(onShare: () -> Unit, onOpenTax: () -> Unit) {
    Spacer(modifier = Modifier.height(22.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SyButton(
            text = "分享这份报表",
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
        SyButton(
            text = "个税测算",
            onClick = onOpenTax,
            modifier = Modifier.weight(1f),
            style = SyButtonStyle.Secondary,
            haptic = HapticEffect.Select,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "分享内容为本机生成的纯文本，不含截图、账号或位置信息。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
