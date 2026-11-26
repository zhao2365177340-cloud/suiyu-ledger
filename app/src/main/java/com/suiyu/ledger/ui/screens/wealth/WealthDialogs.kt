package com.suiyu.ledger.ui.screens.wealth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.AssetCategory
import com.suiyu.ledger.model.EmploymentType
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.model.LiabilityCategory
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.SyToggleRow
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Money

/** 金额输入辅助：分 → 输入框文本（0 视为空） */
private fun centsToInput(cents: Long): String = if (cents <= 0L) "" else Money.plain(cents)

/** 输入框文本 → 分（非法/空 → 0） */
private fun inputToCents(text: String): Long = Money.parseCents(text)?.coerceAtLeast(0L) ?: 0L

@Composable
internal fun AssetEditorDialog(
    initial: Asset?,
    onDismiss: () -> Unit,
    onSave: (name: String, category: AssetCategory, valueCents: Long, note: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: AssetCategory.CASH) }
    var valueText by remember(initial) { mutableStateOf(centsToInput(initial?.valueCents ?: 0L)) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var showError by remember(initial) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加资产" else "编辑资产", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    singleLine = true,
                    isError = showError,
                    label = { Text("名称") },
                    placeholder = { Text("如：招行活期 / 自住房") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogLabel("资产类别")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                AssetCategory.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { item ->
                            SyChip(
                                text = "${item.emoji} ${item.label}",
                                selected = category == item,
                                onClick = { category = item; Haptics.play(HapticEffect.Select) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it; showError = false },
                    singleLine = true,
                    isError = showError,
                    label = { Text("当前估值（元）") },
                    placeholder = { Text("如：68000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(SyDim.gapXs))
                    Text(
                        text = "请填写名称，且估值需大于 0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(SyDim.gapL))
                    SyTextButton(
                        text = "删除这项资产",
                        onClick = onDelete,
                        color = SyTheme.colors.expense,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = inputToCents(valueText)
                if (name.isBlank() || cents <= 0L) {
                    showError = true
                    Haptics.play(HapticEffect.Warning)
                } else {
                    Haptics.play(HapticEffect.Confirm)
                    onSave(name.trim(), category, cents, note.trim())
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun LiabilityEditorDialog(
    initial: Liability?,
    onDismiss: () -> Unit,
    onSave: (name: String, category: LiabilityCategory, balanceCents: Long, monthlyCents: Long, note: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: LiabilityCategory.MORTGAGE) }
    var balanceText by remember(initial) { mutableStateOf(centsToInput(initial?.balanceCents ?: 0L)) }
    var monthlyText by remember(initial) { mutableStateOf(centsToInput(initial?.monthlyPaymentCents ?: 0L)) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var showError by remember(initial) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加负债" else "编辑负债", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    singleLine = true,
                    isError = showError,
                    label = { Text("名称") },
                    placeholder = { Text("如：招行住房贷款") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogLabel("负债类别")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                LiabilityCategory.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { item ->
                            SyChip(
                                text = "${item.emoji} ${item.label}",
                                selected = category == item,
                                onClick = { category = item; Haptics.play(HapticEffect.Select) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it; showError = false },
                    singleLine = true,
                    isError = showError,
                    label = { Text("剩余欠款（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                OutlinedTextField(
                    value = monthlyText,
                    onValueChange = { monthlyText = it },
                    singleLine = true,
                    label = { Text("每月还款（元，可选）") },
                    placeholder = { Text("用于计算月供收入比") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    singleLine = true,
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Spacer(modifier = Modifier.height(SyDim.gapXs))
                    Text(
                        text = "请填写名称，且剩余欠款需大于 0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(SyDim.gapL))
                    SyTextButton(
                        text = "删除这项负债",
                        onClick = onDelete,
                        color = SyTheme.colors.expense,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val balance = inputToCents(balanceText)
                if (name.isBlank() || balance <= 0L) {
                    showError = true
                    Haptics.play(HapticEffect.Warning)
                } else {
                    Haptics.play(HapticEffect.Confirm)
                    onSave(name.trim(), category, balance, inputToCents(monthlyText), note.trim())
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ProfileEditorDialog(
    profile: FinancialProfile,
    onDismiss: () -> Unit,
    onSave: (FinancialProfile) -> Unit,
) {
    var occupation by remember(profile) { mutableStateOf(profile.occupation) }
    var employmentType by remember(profile) { mutableStateOf(profile.employmentType) }
    var jobYearsText by remember(profile) { mutableStateOf(if (profile.jobYears > 0) profile.jobYears.toString() else "") }
    var stability by remember(profile) { mutableStateOf(profile.incomeStability) }
    var spouseWorks by remember(profile) { mutableStateOf(profile.spouseWorks) }
    var elderlyText by remember(profile) { mutableStateOf(if (profile.elderlyCount > 0) profile.elderlyCount.toString() else "") }
    var childrenText by remember(profile) { mutableStateOf(if (profile.childrenCount > 0) profile.childrenCount.toString() else "") }
    var medical by remember(profile) { mutableStateOf(profile.hasMedicalInsurance) }
    var critical by remember(profile) { mutableStateOf(profile.hasCriticalIllnessInsurance) }
    var life by remember(profile) { mutableStateOf(profile.hasLifeInsurance) }
    var coverageText by remember(profile) { mutableStateOf(centsToInput(profile.insuranceCoverageCents)) }
    var incomeText by remember(profile) { mutableStateOf(centsToInput(profile.manualMonthlyIncomeCents)) }
    var expenseText by remember(profile) { mutableStateOf(centsToInput(profile.manualMonthlyFixedExpenseCents)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("工作与家庭情况", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = occupation,
                    onValueChange = { occupation = it },
                    singleLine = true,
                    label = { Text("职业 / 行业") },
                    placeholder = { Text("如：产品经理 · 互联网") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogLabel("单位性质")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                EmploymentType.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { item ->
                            SyChip(
                                text = item.label,
                                selected = employmentType == item,
                                onClick = { employmentType = item; Haptics.play(HapticEffect.Select) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapS)) {
                    OutlinedTextField(
                        value = jobYearsText,
                        onValueChange = { jobYearsText = it.filter { ch -> ch.isDigit() }.take(2) },
                        singleLine = true,
                        label = { Text("工作年限") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        DialogLabel("收入稳定性")
                        Spacer(modifier = Modifier.height(SyDim.gapXs))
                        SySegmented(
                            items = listOf("波动", "一般", "稳定"),
                            selectedIndex = (stability - 1).coerceIn(0, 2),
                            onSelect = { index -> stability = index + 1 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                SyToggleRow(
                    title = "配偶也在工作",
                    description = "双收入结构能显著提高家庭抗风险能力",
                    checked = spouseWorks,
                    onCheckedChange = { spouseWorks = it },
                )
                Spacer(modifier = Modifier.height(SyDim.gapXs))
                Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapS)) {
                    OutlinedTextField(
                        value = elderlyText,
                        onValueChange = { elderlyText = it.filter { ch -> ch.isDigit() }.take(2) },
                        singleLine = true,
                        label = { Text("赡养老人数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = childrenText,
                        onValueChange = { childrenText = it.filter { ch -> ch.isDigit() }.take(2) },
                        singleLine = true,
                        label = { Text("子女数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogLabel("保障情况")
                Spacer(modifier = Modifier.height(SyDim.gapXs))
                SyToggleRow(title = "有医保", checked = medical, onCheckedChange = { medical = it })
                SyToggleRow(title = "有重疾险", checked = critical, onCheckedChange = { critical = it })
                SyToggleRow(title = "有寿险", checked = life, onCheckedChange = { life = it })
                Spacer(modifier = Modifier.height(SyDim.gapXs))
                OutlinedTextField(
                    value = coverageText,
                    onValueChange = { coverageText = it },
                    singleLine = true,
                    label = { Text("重疾 / 寿险保额（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogLabel("固定收支（可选，留空 = 按流水自动估算）")
                Spacer(modifier = Modifier.height(SyDim.gapXs))
                OutlinedTextField(
                    value = incomeText,
                    onValueChange = { incomeText = it },
                    singleLine = true,
                    label = { Text("月固定收入（元）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapS))
                OutlinedTextField(
                    value = expenseText,
                    onValueChange = { expenseText = it },
                    singleLine = true,
                    label = { Text("月固定支出（元，不含月供）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                Text(
                    text = "这些信息只保存在本机，用于评估收入稳定性与家庭负担。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Haptics.play(HapticEffect.Confirm)
                onSave(
                    profile.copy(
                        occupation = occupation.trim(),
                        employmentType = employmentType,
                        jobYears = jobYearsText.toIntOrNull()?.coerceIn(0, 60) ?: 0,
                        incomeStability = stability.coerceIn(1, 3),
                        spouseWorks = spouseWorks,
                        elderlyCount = elderlyText.toIntOrNull()?.coerceIn(0, 10) ?: 0,
                        childrenCount = childrenText.toIntOrNull()?.coerceIn(0, 10) ?: 0,
                        hasMedicalInsurance = medical,
                        hasCriticalIllnessInsurance = critical,
                        hasLifeInsurance = life,
                        insuranceCoverageCents = inputToCents(coverageText),
                        manualMonthlyIncomeCents = inputToCents(incomeText),
                        manualMonthlyFixedExpenseCents = inputToCents(expenseText),
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DialogLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun WealthConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                Haptics.play(HapticEffect.Warning)
                onConfirm()
            }) { Text(confirmText, color = SyTheme.colors.expense) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
