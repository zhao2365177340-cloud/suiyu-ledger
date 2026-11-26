package com.suiyu.ledger.ui.screens.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyAmount
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.SySourceTag
import com.suiyu.ledger.ui.components.SyTag
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import kotlin.math.roundToInt

// ────────────────────────────────────────────────────────────────
// 草稿卡
// ────────────────────────────────────────────────────────────────

/**
 * 一笔待确认的导入草稿。折叠时只展示「哪一笔、多少钱、什么来源、可信度」，
 * 展开后可以逐项修改方向 / 金额 / 分类 / 成员 / 日期 / 备注。
 */
@Composable
fun DraftCard(
    draft: ImportDraft,
    categories: List<Category>,
    members: List<Member>,
    duplicateCount: Int,
    onChange: (ImportDraft) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val xm = SyTheme.colors
    val category = categories.firstOrNull { it.id == draft.categoryId }
    var showCategoryPicker by remember(draft.key) { mutableStateOf(false) }
    var showAmountEditor by remember(draft.key) { mutableStateOf(false) }
    var showDatePicker by remember(draft.key) { mutableStateOf(false) }
    var showNoteEditor by remember(draft.key) { mutableStateOf(false) }
    var showRawText by remember(draft.key) { mutableStateOf(false) }
    val duplicated = duplicateCount > 0

    SyCard(
        modifier = modifier.padding(bottom = SyDim.gapM),
        color = if (duplicated) xm.goldSoft.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionDot(selected = draft.selected) { onChange(draft.copy(selected = !draft.selected)) }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .syClickable(effect = HapticEffect.Soft) { onChange(draft.copy(expanded = !draft.expanded)) },
            ) {
                Text(
                    text = draft.merchant.ifBlank { categoryName(category, draft.type) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateSummary(draft),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draft.hasDate) MaterialTheme.colorScheme.onSurfaceVariant else xm.gold,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.syClickable(effect = HapticEffect.Soft) { showAmountEditor = true },
            ) {
                SyAmount(
                    cents = draft.amountCents,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (draft.type == TxType.EXPENSE) {
                        SyTheme.colors.expense
                    } else {
                        SyTheme.colors.income
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (draft.expanded) "收起" else "展开修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (draft.expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (draft.expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SySourceTag(draft.bill.source)
            ConfidenceTag(draft.confidence)
            if (duplicated) {
                SyTag(
                    text = "疑似重复 ×$duplicateCount",
                    color = xm.expense,
                    background = xm.expenseSoft,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${category?.emoji ?: "📎"} ${categoryName(category, draft.type)} · ${draft.type.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (draft.account.isNotBlank()) {
                Text(
                    text = " · ${draft.account}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (draft.expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            SyDivider()
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel("方向")
                SySegmented(
                    items = listOf("支出", "收入"),
                    selectedIndex = if (draft.type == TxType.EXPENSE) 0 else 1,
                    onSelect = { index ->
                        val nextType = if (index == 0) TxType.EXPENSE else TxType.INCOME
                        onChange(
                            draft.copy(
                                type = nextType,
                                categoryId = ImportDrafts.categoryForType(nextType, draft.categoryId, categories),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            EditableField(
                label = "分类",
                value = "${category?.emoji ?: "📎"} ${categoryName(category, draft.type)}",
                onClick = { showCategoryPicker = true },
            )
            EditableField(
                label = "日期",
                value = "${Dates.fullDate(draft.occurredAt)} ${Dates.time(draft.occurredAt)}",
                onClick = { showDatePicker = true },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FieldLabel("成员")
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    members.forEach { member ->
                        SyChip(
                            text = member.name,
                            selected = member.id == draft.memberId,
                            onClick = { onChange(draft.copy(memberId = member.id)) },
                            leadingEmoji = member.emoji,
                        )
                    }
                }
            }

            EditableField(
                label = "备注",
                value = draft.note.ifBlank { "点击填写（可选）" },
                onClick = { showNoteEditor = true },
            )
            if (draft.account.isNotBlank()) {
                EditableField(label = "账户", value = draft.account)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyTextButton(
                    text = if (showRawText) "收起识别原文" else "查看识别原文",
                    onClick = { showRawText = !showRawText },
                )
                Spacer(modifier = Modifier.weight(1f))
                SyTextButton(
                    text = "移除此笔",
                    onClick = {
                        Haptics.play(HapticEffect.Warning)
                        onRemove()
                    },
                    color = xm.expense,
                )
            }
            if (showRawText) {
                Text(
                    text = draft.bill.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            type = draft.type,
            selectedId = draft.categoryId,
            onDismiss = { showCategoryPicker = false },
            onSelect = { id ->
                Haptics.play(HapticEffect.Select)
                onChange(draft.copy(categoryId = id))
            },
        )
    }
    if (showAmountEditor) {
        AmountEditDialog(
            initialCents = draft.amountCents,
            type = draft.type,
            onDismiss = { showAmountEditor = false },
            onConfirm = { cents ->
                onChange(draft.copy(amountCents = cents))
                showAmountEditor = false
            },
        )
    }
    if (showDatePicker) {
        ImportDatePickerDialog(
            initialMillis = draft.occurredAt,
            onDismiss = { showDatePicker = false },
            onConfirm = { millis ->
                onChange(draft.copy(occurredAt = millis, hasDate = true))
                showDatePicker = false
            },
        )
    }
    if (showNoteEditor) {
        NoteEditDialog(
            initial = draft.note,
            onDismiss = { showNoteEditor = false },
            onConfirm = { note ->
                onChange(draft.copy(note = note))
                showNoteEditor = false
            },
        )
    }
}

@Composable
private fun dateSummary(draft: ImportDraft): String {
    val day = "${Dates.monthDay(draft.occurredAt)} ${Dates.time(draft.occurredAt)}"
    return if (draft.hasDate) day else "$day · 未识别到日期"
}

private fun categoryName(category: Category?, type: TxType): String =
    category?.name ?: if (type == TxType.EXPENSE) "未分类" else "未分类"

// ────────────────────────────────────────────────────────────────
// 顶部 / 底部操作区
// ────────────────────────────────────────────────────────────────

/** 复核页顶部：笔数、合计、全选 / 取消 */
@Composable
fun ReviewHeader(
    drafts: List<ImportDraft>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = drafts.filter { it.selected }
    val expenseCents = selected.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents }
    val incomeCents = selected.filter { it.type == TxType.INCOME }.sumOf { it.amountCents }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "共 ${drafts.size} 笔草稿",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.weight(1f))
            SyTextButton(text = "全选", onClick = onSelectAll)
            SyTextButton(text = "取消", onClick = onClearSelection)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已选 ${selected.size} 笔 · 支出 ${Money.format(expenseCents)} · 收入 ${Money.format(incomeCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 复核页底部：确认入账 */
@Composable
fun ImportBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        val text = when {
            totalCount == 0 -> "没有可入账的草稿"
            selectedCount == 0 -> "请先勾选要入账的账单"
            else -> "确认入账 $selectedCount 笔"
        }
        SyButton(
            text = text,
            onClick = onConfirm,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
            haptic = HapticEffect.Confirm,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}


// ────────────────────────────────────────────────────────────────
// 小部件
// ────────────────────────────────────────────────────────────────

@Composable
private fun SelectionDot(selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(1.dp, borderColor, CircleShape)
            .syClickable(effect = HapticEffect.Select, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun ConfidenceTag(confidence: Float) {
    val xm = SyTheme.colors
    val percent = (confidence.coerceIn(0f, 1f) * 100).roundToInt()
    val color = when {
        confidence >= 0.9f -> xm.income
        confidence >= 0.6f -> xm.gold
        else -> xm.expense
    }
    SyTag(
        text = "置信 $percent%",
        color = color,
        background = color.copy(alpha = 0.12f),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(48.dp),
    )
}

@Composable
private fun EditableField(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        modifier = (if (onClick != null) base.syClickable(effect = HapticEffect.Soft, onClick = onClick) else base)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Text(
                text = "修改",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
