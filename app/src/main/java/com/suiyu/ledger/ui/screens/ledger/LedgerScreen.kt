package com.suiyu.ledger.ui.screens.ledger

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyEmpty
import com.suiyu.ledger.ui.components.SyMonthSwitcher
import com.suiyu.ledger.ui.components.SyRecordRow
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey

/**
 * 流水明细页：按日滚动的完整账本。
 *
 * 交互约定：
 *  - 单击一笔 → 进入编辑（改金额 / 分类 / 成员 / 删除单笔）；
 *  - 长按任意一笔 → 进入多选，追加选择更多笔；
 *  - 多选工具条：全选 / 删除选中；顶栏「清空」= 一键删除本月/全部流水（仅保留成员与分类）。
 */
private data class DayGroup(val dayStart: Long, val records: List<TxRecord>)

@Composable
fun LedgerScreen(onOpenEntry: (String) -> Unit) {
    val ledger = AppGraph.ledger
    val context = LocalContext.current
    val records by ledger.records.collectAsState()
    val categories by ledger.categories.collectAsState()
    val members by ledger.members.collectAsState()
    val settings by ledger.settings.collectAsState()
    val masked = settings.privacyMask

    var month by remember { mutableStateOf(MonthKey.current()) }
    var typeFilter by remember { mutableStateOf(0) }
    var memberFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var showClearAll by remember { mutableStateOf(false) }

    val selectionMode = selected.isNotEmpty()

    // 多选状态下，系统返回键应该退出多选，而不是直接退出应用
    BackHandler(enabled = selectionMode) {
        selected = emptySet()
        Haptics.play(HapticEffect.Soft)
    }

    // 切换账期 / 筛选时退出多选，避免「选中了但列表里看不到」
    LaunchedEffect(month, typeFilter, memberFilter) {
        selected = emptySet()
    }
    // 记录被删除后清理失效选中
    LaunchedEffect(records.size) {
        if (selected.isNotEmpty()) {
            val alive = records.map { it.id }.toSet()
            if (!alive.containsAll(selected)) {
                selected = selected.intersect(alive)
            }
        }
    }

    val monthRecords = remember(records, month) { Analytics.ofMonth(records, month) }
    val filtered = remember(monthRecords, typeFilter, memberFilter) {
        monthRecords.filter { record ->
            val typeOk = when (typeFilter) {
                1 -> record.type == TxType.EXPENSE
                2 -> record.type == TxType.INCOME
                else -> true
            }
            val memberOk = memberFilter == null || record.memberId == memberFilter
            typeOk && memberOk
        }.sortedByDescending { it.occurredAt }
    }
    val filteredIds = remember(filtered) { filtered.map { it.id }.toSet() }
    val allSelected = selectionMode && filteredIds.isNotEmpty() && selected.containsAll(filteredIds)
    val selectedRecords = filtered.filter { it.id in selected }
    val selectedExpense = selectedRecords.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val selectedIncome = selectedRecords.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    val expenseCents = filtered.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val incomeCents = filtered.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    val groups = remember(filtered) {
        filtered.groupBy { Dates.startOfDay(it.occurredAt) }
            .toList()
            .sortedByDescending { it.first }
            .map { (dayStart, dayRecords) -> DayGroup(dayStart, dayRecords) }
    }

    fun deleteSelected() {
        val count = selected.size
        ledger.deleteRecords(selected)
        selected = emptySet()
        Haptics.play(HapticEffect.Success)
        Toast.makeText(context, "已删除 $count 笔流水", Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SyScaffold(
            title = if (selectionMode) "已选 ${selected.size} 笔" else "流水",
            subtitle = if (selectionMode) {
                "再点按可追加选择，删除后不可恢复"
            } else {
                "按日明细 · 点击编辑 · 长按多选删除"
            },
            onBack = if (selectionMode) {
                { selected = emptySet() }
            } else {
                null
            },
            actions = {
                if (!selectionMode) {
                    SyTextButton(
                        text = "清空",
                        onClick = { showClearAll = true },
                        color = SyTheme.colors.expense,
                    )
                }
            },
            scroll = false,
            bottomPadding = 4.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyMonthSwitcher(month = month, onMonthChange = { month = it })
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${filtered.size} 笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SySegmented(
                items = listOf("全部", "支出", "收入"),
                selectedIndex = typeFilter,
                onSelect = { typeFilter = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyChip(
                    text = "家庭",
                    selected = memberFilter == null,
                    onClick = { memberFilter = null },
                    leadingEmoji = "👨‍👩‍👧",
                )
                members.forEach { member ->
                    SyChip(
                        text = member.name,
                        selected = memberFilter == member.id,
                        onClick = { memberFilter = member.id },
                        leadingEmoji = member.emoji,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (masked) "支出 ¥ ••••" else "支出 ${Money.format(expenseCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SyTheme.colors.expense,
                )
                Text(
                    text = "   ·   ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (masked) "收入 ¥ ••••" else "收入 ${Money.format(incomeCents)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SyTheme.colors.income,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SyEmpty(
                        emoji = "📒",
                        title = "本月没有符合条件的流水",
                        description = "换一个账期或筛选条件，或者去「记一笔」记录一条新的收支。",
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "day_${group.dayStart}") {
                            DayHeader(group = group, masked = masked)
                        }
                        items(items = group.records, key = { record -> record.id }) { record ->
                            SyRecordRow(
                                record = record,
                                category = categories.firstOrNull { it.id == record.categoryId },
                                member = members.firstOrNull { it.id == record.memberId },
                                showTime = true,
                                selectionMode = selectionMode,
                                selected = record.id in selected,
                                onClick = {
                                    if (selectionMode) {
                                        selected = selected.toggle(record.id)
                                    } else {
                                        onOpenEntry(record.id)
                                    }
                                },
                                onLongClick = {
                                    selected = selected + record.id
                                },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.6.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectionMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp)
                .padding(bottom = 84.dp),
            enter = slideInVertically(animationSpec = androidx.compose.animation.core.tween(200)) { it } + fadeIn(),
            exit = slideOutVertically(animationSpec = androidx.compose.animation.core.tween(160)) { it } + fadeOut(),
        ) {
            SelectionActionsBar(
                count = selected.size,
                expenseCents = selectedExpense,
                incomeCents = selectedIncome,
                masked = masked,
                allSelected = allSelected,
                onToggleAll = {
                    selected = if (allSelected) emptySet() else filteredIds
                    Haptics.play(HapticEffect.Select)
                },
                onDelete = { showDeleteSelected = true },
            )
        }
    }

    if (showDeleteSelected) {
        AlertDialog(
            onDismissRequest = { showDeleteSelected = false },
            title = { Text("删除选中的 ${selected.size} 笔流水？") },
            text = { Text("删除后不可恢复，统计与报表会同步更新。建议先在「家庭」页导出备份。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteSelected = false
                        deleteSelected()
                    },
                ) {
                    Text("删除", color = SyTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelected = false }) { Text("取消") }
            },
        )
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("清空全部流水？") },
            text = {
                Text(
                    "将删除账本内全部 ${records.size} 笔收支记录（含截图导入与手动记账），此操作无法撤销。" +
                        "成员、分类与设置会保留；建议先在「家庭」页导出备份。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAll = false
                        val count = records.size
                        ledger.clearRecords()
                        selected = emptySet()
                        Haptics.play(HapticEffect.Success)
                        Toast.makeText(context, "已清空 $count 笔流水", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("全部删除", color = SyTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DayHeader(group: DayGroup, masked: Boolean) {
    val expense = group.records.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val income = group.records.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Dates.dayLabel(group.dayStart),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = Dates.weekday(group.dayStart),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (masked) {
                "支 ¥ ••••"
            } else {
                "支 ${Money.format(expense)}" + if (income > 0L) " · 收 ${Money.format(income)}" else ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionActionsBar(
    count: Int,
    expenseCents: Long,
    incomeCents: Long,
    masked: Boolean,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已选 $count 笔",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (masked) {
                    "支出 ¥ •••• · 收入 ¥ ••••"
                } else {
                    "支出 ${Money.format(expenseCents)} · 收入 ${Money.format(incomeCents)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SyTextButton(text = if (allSelected) "取消全选" else "全选", onClick = onToggleAll)
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.width(92.dp)) {
            SyButton(
                text = "删除",
                onClick = onDelete,
                style = SyButtonStyle.Danger,
                haptic = HapticEffect.Warning,
            )
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (contains(id)) this - id else this + id
