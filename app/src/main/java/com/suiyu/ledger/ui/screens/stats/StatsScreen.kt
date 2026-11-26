package com.suiyu.ledger.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.BarGroup
import com.suiyu.ledger.ui.components.ChartRankItem
import com.suiyu.ledger.ui.components.ChartRankList
import com.suiyu.ledger.ui.components.ChartSlice
import com.suiyu.ledger.ui.components.DonutChart
import com.suiyu.ledger.ui.components.GroupedBarChart
import com.suiyu.ledger.ui.components.LineSeries
import com.suiyu.ledger.ui.components.MonthHeatmap
import com.suiyu.ledger.ui.components.SyBottomSpacer
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyEmpty
import com.suiyu.ledger.ui.components.SyMonthSwitcher
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.TrendChart
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.MonthKey

/**
 * 统计页：把「这个月的钱花在哪」拆成三个问题——
 * 结构（钱落在哪些分类）、对比（比上个月多了还是少了）、日历（哪天花得最多）。
 *
 * 所有数字都从 [Analytics] 取；本页只做两种「视图裁剪」，
 * 且裁剪条件与 Analytics 内部的成员过滤保持一致，不改写任何口径：
 *  1. 成员视角：先按 memberId 过滤记录，再交给 Analytics.summary / healthMetrics；
 *  2. 日均收入：Analytics.summary 只提供 avgDailyExpenseCents，
 *     收入视图按同一公式（收入总额 ÷ 本期已过天数）换算。
 */
@Composable
fun StatsScreen() {
    val ledger = AppGraph.ledger
    val records by ledger.records.collectAsState()
    val members by ledger.members.collectAsState()
    val categories by ledger.categories.collectAsState()
    val settings by ledger.settings.collectAsState()
    val masked = settings.privacyMask
    val xm = SyTheme.colors

    var month by remember { mutableStateOf(MonthKey.current()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var memberId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableIntStateOf(0) }
    var trendCount by remember { mutableIntStateOf(6) }
    var compareByMember by remember { mutableStateOf(false) }
    var compareSelected by remember { mutableIntStateOf(-1) }
    var trendSelected by remember { mutableIntStateOf(-1) }
    var drillCategoryId by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // ── 数据（全部来自 Analytics，成员视角只做与 memberSlices 相同的预过滤） ──
    val monthRecords = remember(records, month) { Analytics.ofMonth(records, month) }
    val scopedRecords = remember(records, month, memberId) {
        val base = Analytics.ofMonth(records, month)
        memberId?.let { id -> base.filter { it.memberId == id } } ?: base
    }
    val summary = remember(scopedRecords, month) { Analytics.summary(scopedRecords, month) }
    val slices = remember(records, categories, month, type, memberId) {
        Analytics.categorySlices(records, categories, month, type, memberId)
    }
    val previous = month.plusMonths(-1)
    val categorySlicesWithBase = remember(records, categories, month, type, memberId, previous) {
        Analytics.categorySlices(
            records = records,
            categories = categories,
            month = month,
            type = type,
            memberId = memberId,
            baseMonth = previous,
        )
    }
    val currentMemberSlices = remember(records, members, month) {
        Analytics.memberSlices(records, members, month)
    }
    val previousMemberSlices = remember(records, members, previous) {
        Analytics.memberSlices(records, members, previous)
    }
    val compareBars = remember(
        categorySlicesWithBase, currentMemberSlices, previousMemberSlices, members, memberId, compareByMember,
    ) {
        if (memberId == null && compareByMember) {
            members.map { member ->
                CompareBar(
                    label = member.name,
                    emoji = member.emoji,
                    currentCents = currentMemberSlices.firstOrNull { it.memberId == member.id }
                        ?.let { slice -> if (type == TxType.EXPENSE) slice.expenseCents else slice.incomeCents } ?: 0L,
                    baseCents = previousMemberSlices.firstOrNull { it.memberId == member.id }
                        ?.let { slice -> if (type == TxType.EXPENSE) slice.expenseCents else slice.incomeCents } ?: 0L,
                )
            }.filter { it.currentCents > 0L || it.baseCents > 0L }
        } else {
            categorySlicesWithBase
                .sortedByDescending { maxOf(it.totalCents, it.baseCents) }
                .take(6)
                .map { CompareBar(it.name, it.emoji, it.totalCents, it.baseCents) }
        }
    }
    val trendPoints = remember(records, month, memberId, trendCount) {
        if (memberId == null) {
            Analytics.monthPoints(records, month, trendCount)
        } else {
            Analytics.memberMonthPoints(records, memberId!!, month, trendCount)
        }
    }
    val dayPoints = remember(scopedRecords, month) { Analytics.dailyPoints(scopedRecords, month) }
    val health = remember(scopedRecords, categories, month) {
        Analytics.healthMetrics(scopedRecords, categories, month)
    }

    val scopeLabel = memberId
        ?.let { id -> members.firstOrNull { it.id == id }?.name }
        ?: "家庭"
    val typeLabel = type.label
    val viewTotal = if (type == TxType.EXPENSE) summary.expenseCents else summary.incomeCents
    val viewCount = if (type == TxType.EXPENSE) summary.expenseCount else summary.incomeCount
    val viewDaily = if (type == TxType.EXPENSE) {
        summary.avgDailyExpenseCents
    } else {
        if (summary.incomeCents <= 0L) 0L else summary.incomeCents / daysOfPeriod(month)
    }
    val typeColor = if (type == TxType.EXPENSE) xm.expense else xm.income

    val drillSlice = slices.firstOrNull { it.categoryId == drillCategoryId }
    val drillRecords = remember(records, month, type, memberId, drillCategoryId) {
        val categoryId = drillCategoryId
        if (categoryId == null) {
            emptyList()
        } else {
            Analytics.ofMonth(records, month)
                .filter { it.type == type && it.categoryId == categoryId && (memberId == null || it.memberId == memberId) }
        }
    }
    val dayRecords = remember(records, month, memberId, selectedDay) {
        val day = selectedDay
        if (day == null) {
            emptyList()
        } else {
            Analytics.ofMonth(records, month)
                .filter { Dates.toLocalDate(it.occurredAt).dayOfMonth == day && (memberId == null || it.memberId == memberId) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SyScaffold(scroll = true, title = "统计") {
            // ── 账期 + 支出/收入 ─────────────────────────
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                SyMonthSwitcher(
                    month = month,
                    onMonthChange = {
                        month = it
                        compareSelected = -1
                        trendSelected = -1
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                SySegmented(
                    items = listOf("支出", "收入"),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelect = { index ->
                        type = if (index == 0) TxType.EXPENSE else TxType.INCOME
                        compareSelected = -1
                        trendSelected = -1
                    },
                    modifier = Modifier.width(136.dp),
                )
            }

            // ── 视角：家庭 / 各成员（横向滚动）──────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyChip(
                    text = "家庭",
                    selected = memberId == null,
                    onClick = { memberId = null },
                    leadingEmoji = "👨‍👩‍👧",
                )
                members.forEach { member ->
                    SyChip(
                        text = member.name,
                        selected = memberId == member.id,
                        onClick = { memberId = member.id },
                        leadingEmoji = member.emoji,
                    )
                }
            }

            // ── 分段：结构 / 对比 / 日历 ───────────────────
            SySegmented(
                items = listOf("结构", "对比", "日历"),
                selectedIndex = section,
                onSelect = { index ->
                    section = index
                    compareSelected = -1
                    trendSelected = -1
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            if (monthRecords.isEmpty()) {
                SyEmpty(
                    emoji = "🧾",
                    title = "${month.label()}还没有记录",
                    description = "在首页「导入账单」识别一张微信 / 支付宝 / 银行截图，或手动记一笔，统计会自动生成。",
                )
                SyBottomSpacer()
                return@SyScaffold
            }
            if (scopedRecords.isEmpty()) {
                SyEmpty(
                    emoji = "🍃",
                    title = "${scopeLabel}在${month.label()}没有记录",
                    description = "切换成员或账期看看，也可以到首页记一笔。",
                )
                SyBottomSpacer()
                return@SyScaffold
            }

            when (section) {
                0 -> {
                    // ── 结构：总额 / 笔数 / 日均 + 环形 + 分类排行 ──
                    Spacer(modifier = Modifier.height(4.dp))
                    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            SummaryStat(
                                label = "本期${typeLabel}",
                                value = amountText(viewTotal, masked),
                                valueColor = typeColor,
                                modifier = Modifier.weight(1.15f),
                            )
                            SummaryStat(
                                label = "笔数",
                                value = "$viewCount 笔",
                                modifier = Modifier.weight(0.85f),
                            )
                            SummaryStat(
                                label = "日均",
                                value = amountText(viewDaily, masked),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        SyDivider()
                        Spacer(modifier = Modifier.height(6.dp))
                        if (slices.isEmpty()) {
                            Text(
                                text = "${scopeLabel}本期没有${typeLabel}记录，切换月份或成员看看。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            )
                        } else {
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
                                modifier = Modifier.fillMaxWidth().height(196.dp),
                                centerLabel = "$scopeLabel$typeLabel",
                                centerValue = amountText(viewTotal, masked),
                                strokeWidth = 22.dp,
                                onSliceClick = { index ->
                                    slices.getOrNull(index)?.let { drillCategoryId = it.categoryId }
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "点按扇区查看该分类当月明细；中心为$scopeLabel${typeLabel}总额。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (slices.isNotEmpty()) {
                        SySectionHeader(
                            title = "分类排行",
                            subtitle = "$scopeLabel${typeLabel}按金额排序，点按查看当月明细",
                        )
                        SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            ChartRankList(
                                items = slices.map { slice ->
                                    ChartRankItem(
                                        key = slice.categoryId,
                                        label = slice.name,
                                        emoji = slice.emoji,
                                        valueLabel = amountText(slice.totalCents, masked),
                                        ratio = slice.ratio.toFloat(),
                                        color = parseHexColor(slice.colorHex),
                                    )
                                },
                                onItemClick = { index ->
                                    slices.getOrNull(index)?.let { drillCategoryId = it.categoryId }
                                },
                            )
                        }
                    }
                }

                1 -> {
                    // ── 对比：本月 vs 上月 + 近 6/12 月趋势 ────
                    SySectionHeader(
                        title = "${month.short()} vs ${previous.short()}",
                        subtitle = if (memberId == null && compareByMember) {
                            "按成员对比$typeLabel"
                        } else {
                            "按分类对比$typeLabel"
                        } + "，点按柱子查看变化",
                    )
                    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        if (memberId == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SyChip(
                                    text = "按分类",
                                    selected = !compareByMember,
                                    onClick = { compareByMember = false; compareSelected = -1 },
                                )
                                SyChip(
                                    text = "按成员",
                                    selected = compareByMember,
                                    onClick = { compareByMember = true; compareSelected = -1 },
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (compareBars.isEmpty()) {
                            Text(
                                text = "这两个月都没有${typeLabel}记录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 18.dp),
                            )
                        } else {
                            GroupedBarChart(
                                groups = compareBars.map { bar ->
                                    BarGroup(label = bar.label, primary = bar.currentCents.toFloat(), secondary = bar.baseCents.toFloat())
                                },
                                primaryLabel = "${month.short()}$typeLabel",
                                secondaryLabel = "${previous.short()}$typeLabel",
                                onGroupClick = { index ->
                                    compareSelected = if (compareSelected == index) -1 else index
                                },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SyDivider()
                            Spacer(modifier = Modifier.height(10.dp))
                            val bar = compareBars.getOrNull(compareSelected)
                            if (bar == null) {
                                Text(
                                    text = "点按一根柱子，查看「${month.short()} vs ${previous.short()}」的具体差值。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                CompareDetail(
                                    bar = bar,
                                    masked = masked,
                                    goodWhenUp = type == TxType.INCOME,
                                )
                            }
                        }
                    }

                    SySectionHeader(
                        title = "近 $trendCount 个月趋势",
                        subtitle = if (memberId == null) "家庭支出与收入的月度走势" else "${scopeLabel}的支出与收入走势",
                    )
                    SyCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SyChip(
                                text = "近 6 个月",
                                selected = trendCount == 6,
                                onClick = { trendCount = 6 },
                            )
                            SyChip(
                                text = "近 12 个月",
                                selected = trendCount == 12,
                                onClick = { trendCount = 12 },
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (trendPoints.size < 2) {
                            Text(
                                text = "趋势需要至少两个月的记录。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 18.dp),
                            )
                        } else {
                            TrendChart(
                                series = listOf(
                                    LineSeries(label = "支出", color = xm.expense, values = trendPoints.map { it.expenseCents.toFloat() }),
                                    LineSeries(label = "收入", color = xm.income, values = trendPoints.map { it.incomeCents.toFloat() }),
                                ),
                                labels = trendPoints.map { it.month.short() },
                                onPointClick = { index ->
                                    trendSelected = if (trendSelected == index) -1 else index
                                },
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SyDivider()
                            Spacer(modifier = Modifier.height(10.dp))
                            val point = trendPoints.getOrNull(trendSelected)
                            if (point == null) {
                                Text(
                                    text = "点按折线上的月份节点，查看该月收入、支出与结余。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                TrendDetail(point = point, masked = masked)
                            }
                        }
                    }
                }

                else -> {
                    // ── 日历：热力 + 指标 ─────────────────────
                    SySectionHeader(
                        title = "支出日历",
                        subtitle = if (type == TxType.INCOME) {
                            "${month.label()}每日支出（热力始终按支出统计，便于对照花费节奏）"
                        } else {
                            "${month.label()}每日支出，颜色越深花得越多"
                        },
                    )
                    SyCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)) {
                        MonthHeatmap(
                            month = month,
                            values = dayPoints.map { it.expenseCents },
                            onDayClick = { day -> selectedDay = day },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SyDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "少",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            repeat(5) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(xm.expense.copy(alpha = 0.14f + index * 0.16f)),
                                )
                                if (index != 4) Spacer(modifier = Modifier.width(4.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "多",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "点按某天看流水",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    SySectionHeader(
                        title = "本月指标",
                        subtitle = if (memberId == null) "家庭视角的节奏与极端值" else "${scopeLabel}视角的节奏与极端值",
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(
                            label = "日均支出",
                            value = amountText(health.avgDailyExpenseCents, masked),
                            sub = "按本期天数平均",
                            modifier = Modifier.weight(1f),
                            valueColor = xm.expense,
                        )
                        MetricCard(
                            label = "零支出天数",
                            value = "${health.noSpendDays} 天",
                            sub = "没有支出记录的日子",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
                        Text(
                            text = "最大单笔支出",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val largest = health.largestExpense
                        if (largest == null) {
                            Text(
                                text = "本月没有支出记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = amountText(largest.amountCents, masked),
                                style = MaterialTheme.typography.titleLarge,
                                color = xm.expense,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val category = categories.firstOrNull { it.id == largest.categoryId }
                            val member = members.firstOrNull { it.id == largest.memberId }
                            Text(
                                text = buildString {
                                    append(Dates.monthDay(largest.occurredAt))
                                    append(" · ")
                                    append(category?.name ?: "未分类")
                                    if (member != null) {
                                        append(" · ")
                                        append(member.name)
                                    }
                                    if (largest.merchant.isNotBlank()) {
                                        append(" · ")
                                        append(largest.merchant)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            SyBottomSpacer()
        }

        // ── 分类下钻底部面板 ─────────────────────────────
        if (drillSlice != null) {
            StatsBottomSheet(onDismiss = { drillCategoryId = null }) {
                CategoryDrillContent(
                    slice = drillSlice,
                    records = drillRecords,
                    categories = categories,
                    members = members,
                    month = month,
                    masked = masked,
                )
            }
        }

        // ── 某日流水底部面板 ─────────────────────────────
        val day = selectedDay
        if (day != null) {
            StatsBottomSheet(onDismiss = { selectedDay = null }) {
                DayRecordsContent(
                    month = month,
                    day = day,
                    records = dayRecords,
                    categories = categories,
                    members = members,
                    masked = masked,
                )
            }
        }
    }
}
