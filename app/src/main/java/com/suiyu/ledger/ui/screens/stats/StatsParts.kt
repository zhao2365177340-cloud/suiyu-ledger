package com.suiyu.ledger.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.analytics.CategorySlice
import com.suiyu.ledger.analytics.Change
import com.suiyu.ledger.analytics.MonthPoint
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyRecordRow
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey
import java.time.LocalDate

/**
 * 统计页的明细面板与展示小结构：
 * 分类下钻 / 单日流水，以及页面里复用的指标块与环比徽标。
 * 与 [StatsScreen] 同包，数据仍全部来自 Analytics，不在本文件内另算口径。
 */
// ────────────────────────────────────────────────────────────────
// 视图内小结构
// ────────────────────────────────────────────────────────────────

/** 对比柱的一根：保留分值，避免 Float 反算回金额时出现 1 分钱误差 */
internal data class CompareBar(
    val label: String,
    val emoji: String,
    val currentCents: Long,
    val baseCents: Long,
)

/** 与 Analytics.summary.avgDailyExpenseCents 相同的天数口径：本月按已过天数，历史月按整月 */
internal fun daysOfPeriod(month: MonthKey): Int {
    val days = if (month == MonthKey.current()) {
        Dates.toLocalDate(System.currentTimeMillis()).dayOfMonth
    } else {
        Dates.lengthOfMonth(month)
    }
    return days.coerceAtLeast(1)
}

internal fun amountText(cents: Long, masked: Boolean): String =
    if (masked) "¥ ••••" else Money.format(cents)

@Composable
internal fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MetricCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    SyCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 环比差值：箭头 + 百分比，涨跌颜色按「对家庭是否有益」决定 */
@Composable
internal fun ChangeTag(change: Change, masked: Boolean, goodWhenUp: Boolean) {
    if (masked) return
    val xm = SyTheme.colors
    val text = when {
        !change.hasBase -> "上月无数据"
        change.direction == 0 -> "→ 持平"
        else -> "${if (change.direction > 0) "↑" else "↓"} ${Money.percent(change.pct, signed = true)}"
    }
    val good = when {
        !change.hasBase || change.direction == 0 -> true
        goodWhenUp -> change.direction > 0
        else -> change.direction < 0
    }
    val color = when {
        !change.hasBase -> MaterialTheme.colorScheme.onSurfaceVariant
        good -> xm.positive
        else -> xm.negative
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
internal fun CompareDetail(bar: CompareBar, masked: Boolean, goodWhenUp: Boolean) {
    val xm = SyTheme.colors
    val change = Change(bar.currentCents, bar.baseCents)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = bar.emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = bar.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ChangeTag(change = change, masked = masked, goodWhenUp = goodWhenUp)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (masked) {
            "本月 ¥ •••• · 上月 ¥ ••••"
        } else {
            "本月 ${Money.format(bar.currentCents)} · 上月 ${Money.format(bar.baseCents)} · 差额 ${Money.format(change.diff, sign = true)}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (bar.baseCents == 0L && bar.currentCents > 0L) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "上月没有同类记录，属于新增项。",
            style = MaterialTheme.typography.labelSmall,
            color = xm.gold,
        )
    }
}

@Composable
internal fun TrendDetail(point: MonthPoint, masked: Boolean) {
    Column {
        Text(
            text = point.month.label(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = if (masked) {
                "支出 ¥ •••• · 收入 ¥ •••• · 结余 ¥ ••••"
            } else {
                "支出 ${Money.format(point.expenseCents)} · 收入 ${Money.format(point.incomeCents)} · 结余 ${Money.format(point.balanceCents)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 底部面板
// ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@Composable
internal fun CategoryDrillContent(
    slice: CategorySlice,
    records: List<TxRecord>,
    categories: List<Category>,
    members: List<Member>,
    month: MonthKey,
    masked: Boolean,
) {
    val total = records.sumOf { it.amountCents }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SyDim.gutter),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(parseHexColor(slice.colorHex).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = slice.emoji, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${slice.name} · ${month.short()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${slice.count} 笔 · 合计 ${amountText(total, masked)} · 占比 ${Money.percent(slice.ratio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        SyDivider()
        if (records.isEmpty()) {
            Text(
                text = "这个分类当月没有明细。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            records.forEach { record ->
                SyRecordRow(
                    record = record,
                    category = categories.firstOrNull { it.id == record.categoryId },
                    member = members.firstOrNull { it.id == record.memberId },
                    showTime = true,
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
internal fun DayRecordsContent(
    month: MonthKey,
    day: Int,
    records: List<TxRecord>,
    categories: List<Category>,
    members: List<Member>,
    masked: Boolean,
) {
    val date = LocalDate.of(month.year, month.month, day)
    val expense = records.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val income = records.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SyDim.gutter),
    ) {
        Text(
            text = "${date.monthValue}月${date.dayOfMonth}日 ${Dates.weekday(Dates.fromLocalDate(date))}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (masked) {
                "支出 ¥ •••• · 收入 ¥ •••• · ${records.size} 笔"
            } else {
                "支出 ${Money.format(expense)} · 收入 ${Money.format(income)} · ${records.size} 笔"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SyDivider()
        if (records.isEmpty()) {
            Text(
                text = "当天没有流水记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            records.forEach { record ->
                SyRecordRow(
                    record = record,
                    category = categories.firstOrNull { it.id == record.categoryId },
                    member = members.firstOrNull { it.id == record.memberId },
                    showTime = true,
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}
