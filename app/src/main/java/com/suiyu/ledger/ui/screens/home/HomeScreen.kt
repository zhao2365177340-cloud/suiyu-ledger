package com.suiyu.ledger.ui.screens.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.analytics.FinancialHealth
import com.suiyu.ledger.analytics.CategorySlice
import com.suiyu.ledger.analytics.Change
import com.suiyu.ledger.analytics.MonthComparison
import com.suiyu.ledger.analytics.Narrative
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.Sparkline
import com.suiyu.ledger.ui.components.SyAmount
import com.suiyu.ledger.ui.components.SyBottomSpacer
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyEmpty
import com.suiyu.ledger.ui.components.SyMemberAvatar
import com.suiyu.ledger.ui.components.SyMonthSwitcher
import com.suiyu.ledger.ui.components.SyProgressBar
import com.suiyu.ledger.ui.components.SyRecordRow
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.SyIcons
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey
import java.time.LocalTime
import kotlin.math.abs

/**
 * 首页：一眼看到「这个月过得怎么样」。
 * 结构顺序：问候 → 账期 → 总览 → 快捷入口 → 本月速读 → 成员 → 最近流水。
 */
@Composable
fun HomeScreen(
    onOpenEntry: (String?) -> Unit,
    onOpenImport: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenTax: () -> Unit,
    onOpenFamily: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val ledger = AppGraph.ledger
    val records by ledger.records.collectAsState()
    val members by ledger.members.collectAsState()
    val categories by ledger.categories.collectAsState()
    val settings by ledger.settings.collectAsState()
    val assets by ledger.assets.collectAsState()
    val liabilities by ledger.liabilities.collectAsState()
    val profile by ledger.profile.collectAsState()

    var month by remember { mutableStateOf(MonthKey.current()) }
    val comparison = remember(records, month) { Analytics.comparison(records, month) }
    val memberSlices = remember(records, members, month) { Analytics.memberSlices(records, members, month) }
    val categorySlices = remember(records, categories, month) {
        Analytics.categorySlices(records, categories, month, TxType.EXPENSE)
    }
    val points = remember(records, month) { Analytics.monthPoints(records, month, 6) }
    val recent = remember(records) { records.take(9) }
    val healthReport = remember(records, assets, liabilities, profile) {
        FinancialHealth.evaluate(records, assets, liabilities, profile)
    }
    val masked = settings.privacyMask
    val currentMonth = month == MonthKey.current()

    SyScaffold(scroll = true, bottomPadding = 8.dp) {
        // ── 问候 + 隐私开关 ─────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = settings.familyName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "的账本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .syClickable(effect = HapticEffect.Toggle) {
                        ledger.setPrivacyMask(!masked)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (masked) "🙈" else "👁", fontSize = 16.sp)
            }
        }
        Text(
            text = Dates.monthDay(System.currentTimeMillis()) + " · " + Dates.weekday(System.currentTimeMillis()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp),
        )

        // ── 账期 + 导入 ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyMonthSwitcher(month = month, onMonthChange = { month = it })
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .syClickable(effect = HapticEffect.Soft, onClick = onOpenImport)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = SyIcons.Import,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "导入账单",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // ── 总览卡 ────────────────────────────────────
        Spacer(modifier = Modifier.height(14.dp))
        HeroCard(
            comparison = comparison,
            categorySlices = categorySlices,
            points = points.map { it.expenseCents.toFloat() },
            monthLabels = points.map { it.month.short() },
            masked = masked,
            isCurrent = currentMonth,
        )

        // ── 快捷入口：一行「记账动作」，一行「分析工具」─────────
        // 5 个等宽小卡在 360dp~411dp 屏上会挤压文字（「导入账单」被截成「导入账」），
        // 改为 3 + 2 的分组布局：主操作更大，分析入口带副标题，信息层级也更清楚。
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAction(
                icon = SyIcons.Import,
                label = "导入账单",
                modifier = Modifier.weight(1f),
                onClick = onOpenImport,
            )
            QuickAction(
                icon = Icons.Filled.Add,
                label = "记一笔",
                modifier = Modifier.weight(1f),
                onClick = { onOpenEntry(null) },
            )
            QuickAction(
                icon = SyIcons.Report,
                label = "看报表",
                modifier = Modifier.weight(1f),
                onClick = onOpenReport,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WideQuickAction(
                icon = SyIcons.Shield,
                title = "财务体检",
                subtitle = "资产与风险评分",
                modifier = Modifier.weight(1f),
                onClick = onOpenHealth,
            )
            WideQuickAction(
                icon = SyIcons.Tax,
                title = "税负测算",
                subtitle = "个税与年终奖",
                modifier = Modifier.weight(1f),
                onClick = onOpenTax,
            )
        }

        // ── 本月速读 ───────────────────────────────────
        if (comparison.current.recordCount > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            SyCard(onClick = onOpenReport, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(SyTheme.colors.goldSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SyIcons.Sparkle,
                            contentDescription = null,
                            tint = SyTheme.colors.gold,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本月速读",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = Narrative.homeHeadline(comparison, categorySlices.firstOrNull()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "生成报表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── 财务体检摘要 ───────────────────────────────
        Spacer(modifier = Modifier.height(12.dp))
        val healthColor = when (healthReport.grade) {
            com.suiyu.ledger.analytics.RiskGrade.EXCELLENT,
            com.suiyu.ledger.analytics.RiskGrade.HEALTHY -> SyTheme.colors.income
            com.suiyu.ledger.analytics.RiskGrade.WATCH -> SyTheme.colors.gold
            com.suiyu.ledger.analytics.RiskGrade.RISK -> SyTheme.colors.expense
        }
        SyCard(onClick = onOpenHealth, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SyTheme.colors.goldSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SyIcons.Shield,
                        contentDescription = null,
                        tint = SyTheme.colors.gold,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "家庭净资产 · 财务体检",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (masked) "¥ ••••" else Money.format(healthReport.netWorthCents),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "风险 ${healthReport.grade.label} · 应急金 " + when {
                            healthReport.emergencyMonths >= 1.0 ->
                                String.format(java.util.Locale.CHINA, "%.1f 个月", healthReport.emergencyMonths)
                            else -> "待补充"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(healthColor.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "${healthReport.score} 分",
                        style = MaterialTheme.typography.labelMedium,
                        color = healthColor,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "查看财务体检",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── 示例数据提示 ───────────────────────────────
        if (settings.seededDemo) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SyTheme.colors.goldSoft.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "🌱", fontSize = 15.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "当前为示例数据，可在「家庭」页一键清空",
                    style = MaterialTheme.typography.bodySmall,
                    color = SyTheme.colors.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                SyTextButton(text = "去处理", onClick = onOpenFamily)
            }
        }

        // ── 成员支出 ───────────────────────────────────
        SySectionHeader(
            title = "成员支出",
            subtitle = if (currentMonth) "本月按成员分账" else "${month.short()}按成员分账",
            trailing = "查看统计",
            onTrailingClick = onOpenStats,
        )
        SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            val active = memberSlices.filter { it.count > 0 }
            if (active.isEmpty()) {
                Text(
                    text = "还没有成员支出记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            } else {
                active.forEachIndexed { index, slice ->
                    val member = members.firstOrNull { it.id == slice.memberId }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (member != null) {
                            SyMemberAvatar(member = member, size = 32.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = slice.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = slice.role,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            SyProgressBar(
                                progress = slice.expenseRatio.toFloat(),
                                color = parseHexColor(slice.colorHex),
                                height = 5.dp,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            SyAmount(
                                cents = slice.expenseCents,
                                style = MaterialTheme.typography.titleSmall,
                                color = SyTheme.colors.expense,
                                masked = masked,
                            )
                            Text(
                                text = "${(slice.expenseRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (index != active.lastIndex) SyDivider()
                }
            }
        }

        // ── 最近流水 ───────────────────────────────────
        SySectionHeader(
            title = "最近流水",
            subtitle = "点按「全部」查看完整流水账",
            trailing = if (recent.isEmpty()) null else "全部",
            onTrailingClick = onOpenLedger,
        )
        if (recent.isEmpty()) {
            SyEmpty(
                emoji = "🧾",
                title = "这个月还没有流水",
                description = "导入一张微信 / 支付宝 / 银行账单截图，或手动记一笔，岁余会自动归类并生成分析。",
                actionText = "导入账单截图",
                onAction = onOpenImport,
            )
        } else {
            val grouped = remember(recent) {
                recent.groupBy { Dates.dayLabel(it.occurredAt) }
            }
            grouped.forEach { (label, dayRecords) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(0.7.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    )
                }
                dayRecords.forEach { record ->
                    SyRecordRow(
                        record = record,
                        category = categories.firstOrNull { it.id == record.categoryId },
                        member = members.firstOrNull { it.id == record.memberId },
                        onClick = { onOpenEntry(record.id) },
                    )
                }
            }
        }

        SyBottomSpacer()
    }
}

// ────────────────────────────────────────────────────────────────

@Composable
private fun HeroCard(
    comparison: MonthComparison,
    categorySlices: List<CategorySlice>,
    points: List<Float>,
    monthLabels: List<String>,
    masked: Boolean,
    isCurrent: Boolean,
) {
    val xm = SyTheme.colors
    val summary = comparison.current
    val onboarding = summary.recordCount == 0
    SyCard(
        color = xm.heroSurface,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = if (isCurrent) "本月支出" else "${comparison.month.short()}支出",
            style = MaterialTheme.typography.labelMedium,
            color = xm.heroContent.copy(alpha = 0.72f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (masked) "¥ ••••" else Money.format(summary.expenseCents),
                style = MaterialTheme.typography.displaySmall,
                color = xm.heroContent,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.weight(1f))
            HeroDelta(
                change = comparison.expenseMoM,
                masked = masked,
                goodWhenDown = true,
                contentColor = xm.heroContent,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeroStat("收入", summary.incomeCents, masked, xm.heroContent, Modifier.weight(1f))
            HeroStat("结余", summary.balanceCents, masked, xm.heroContent, Modifier.weight(1f))
            HeroStat(
                label = "结余率",
                valueText = Money.percent(summary.savingsRate),
                modifier = Modifier.weight(1f),
                contentColor = xm.heroContent,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (points.size >= 2) {
            Sparkline(
                values = points,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                color = xm.gold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                monthLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = xm.heroContent.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (onboarding) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "还没有数据 · 导入一张账单截图开始",
                style = MaterialTheme.typography.bodySmall,
                color = xm.heroContent.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun HeroStat(
    label: String,
    valueCents: Long,
    masked: Boolean,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (masked) "••••" else Money.format(valueCents),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroStat(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    contentColor: Color,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroDelta(
    change: Change,
    masked: Boolean,
    goodWhenDown: Boolean,
    contentColor: Color,
) {
    if (masked) return
    val hasBase = change.hasBase
    val direction = change.direction
    val arrow = when {
        !hasBase -> "—"
        direction > 0 -> "↑"
        direction < 0 -> "↓"
        else -> "→"
    }
    val text = if (hasBase) {
        "$arrow " + Money.percent(change.pct, signed = true) + " 环比"
    } else {
        "上月无数据"
    }
    val positive = when {
        !hasBase -> false
        direction == 0 -> true
        goodWhenDown -> direction < 0
        else -> direction > 0
    }
    // 总览卡底色在深浅主题下都是深墨绿，所以徽标文字固定用浅色系保证对比度
    val tint = if (positive) Color(0xFFBFE3CE) else Color(0xFFF3CBBF)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SyCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

/** 「分析工具」宽卡：图标 + 标题 + 一句说明，两个并排也不拥挤 */
@Composable
private fun WideQuickAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SyCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
