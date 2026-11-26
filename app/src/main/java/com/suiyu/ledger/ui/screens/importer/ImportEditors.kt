package com.suiyu.ledger.ui.screens.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyNumberKeypad
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Money
import java.time.Instant
import java.time.ZoneId

// ────────────────────────────────────────────────────────────────
// 弹窗：分类 / 金额 / 日期 / 备注
// ────────────────────────────────────────────────────────────────

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    type: TxType,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val typed = categories.filter { it.type == type }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择${type.label}分类", style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                items(typed, key = { it.id }) { category ->
                    val selected = category.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )
                            .syClickable(effect = HapticEffect.Select) {
                                onSelect(category.id)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = category.emoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
fun AmountEditDialog(
    initialCents: Long,
    type: TxType,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var input by remember {
        mutableStateOf(if (initialCents > 0) Money.plain(initialCents).replace(",", "") else "")
    }
    val cents = Money.parseCents(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "修改${type.label}金额", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "¥ " + input.ifBlank { "0" },
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (cents != null && cents > 0) {
                        if (type == TxType.EXPENSE) SyTheme.colors.expense else SyTheme.colors.income
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                )
                SyNumberKeypadCompat(
                    input = input,
                    onInputChange = { input = it },
                    onDone = {
                        if (cents != null && cents > 0) {
                            Haptics.play(HapticEffect.Confirm)
                            onConfirm(cents)
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 金额键盘：复用公共 [com.suiyu.ledger.ui.components.SyNumberKeypad]，这里只负责输入串维护 */
@Composable
private fun SyNumberKeypadCompat(
    input: String,
    onInputChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    com.suiyu.ledger.ui.components.SyNumberKeypad(
        onKey = { key -> onInputChange(appendAmountKey(input, key)) },
        onBackspace = { onInputChange(input.dropLast(1)) },
        onDone = onDone,
        doneLabel = "确定",
        keyHeight = 42.dp,
    )
}

private fun appendAmountKey(current: String, key: String): String {
    if (key == ".") {
        if (current.contains('.')) return current
        return if (current.isEmpty()) "0." else "$current."
    }
    if (current.contains('.')) {
        val decimals = current.substringAfter('.')
        if (decimals.length >= 2) return current
    }
    if (current == "0") return key
    if (current.length >= 9) return current
    return current + key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initialUtcMidnight = remember(initialMillis) {
        val date = Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMidnight)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis
                    if (picked == null) {
                        onDismiss()
                    } else {
                        Haptics.play(HapticEffect.Confirm)
                        onConfirm(mergePickedDate(picked, initialMillis))
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@Composable
fun NoteEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "备注", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：家庭聚餐、公司报销…") },
                singleLine = false,
                maxLines = 3,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Haptics.play(HapticEffect.Confirm)
                    onConfirm(text.trim())
                },
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
