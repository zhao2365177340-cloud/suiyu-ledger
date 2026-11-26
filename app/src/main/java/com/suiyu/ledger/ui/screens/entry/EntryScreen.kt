package com.suiyu.ledger.ui.screens.entry

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyIcons
import com.suiyu.ledger.ui.components.SyNumberKeypad
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.SyTopBar
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.newId

/**
 * 记一笔：金额键盘常驻、分类一屏可选、保存即入账——目标是「打开到记完 ≤ 3 步」。
 * 编辑模式复用同一页面，回填后保存即更新。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    editId: String?,
    onBack: () -> Unit,
    onSaved: (Boolean) -> Unit,
) {
    val ledger = AppGraph.ledger
    val context = LocalContext.current
    val members by ledger.members.collectAsState()
    val categories by ledger.categories.collectAsState()

    val existing = remember(editId) { editId?.let { ledger.recordById(it) } }
    var type by remember { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amountText by remember { mutableStateOf(existing?.let { Money.plain(it.amountCents) } ?: "0") }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: "") }
    var memberId by remember { mutableStateOf(existing?.memberId ?: ledger.selfMember.id) }
    var occurredAt by remember { mutableStateOf(existing?.occurredAt ?: System.currentTimeMillis()) }
    var account by remember { mutableStateOf(existing?.account ?: "微信零钱") }
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(existing != null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val typeCategories = remember(categories, type) { categories.filter { it.type == type } }
    LaunchedEffect(typeCategories) {
        if (categoryId.isEmpty() || typeCategories.none { it.id == categoryId }) {
            categoryId = typeCategories.firstOrNull()?.id ?: ""
        }
    }
    val selectedCategory = typeCategories.firstOrNull { it.id == categoryId }
    val accounts = listOf("微信零钱", "支付宝", "招商银行储蓄卡", "建设银行信用卡", "现金", "其他")

    fun pressKey(key: String) {
        when {
            key == "." -> {
                if (amountText.contains(".")) return
                amountText = if (amountText.isEmpty() || amountText == "0") "0." else amountText + "."
            }
            else -> {
                val parts = amountText.split(".")
                if (parts.size == 2 && parts[1].length >= 2) return
                if (parts[0].length >= 8) return
                amountText = when {
                    amountText == "0" -> key
                    amountText.isEmpty() -> key
                    else -> amountText + key
                }
            }
        }
    }

    fun backspace() {
        amountText = when {
            amountText.length <= 1 -> "0"
            else -> amountText.dropLast(1).ifEmpty { "0" }
        }
    }

    fun save() {
        val cents = Money.parseCents(amountText) ?: 0L
        if (cents <= 0L) {
            Haptics.play(HapticEffect.Warning)
            Toast.makeText(context, "请先输入金额", Toast.LENGTH_SHORT).show()
            return
        }
        val category = selectedCategory ?: typeCategories.firstOrNull()
        if (category == null) {
            Haptics.play(HapticEffect.Warning)
            Toast.makeText(context, "请先选择分类", Toast.LENGTH_SHORT).show()
            return
        }
        val record = TxRecord(
            id = existing?.id ?: newId("t"),
            type = type,
            amountCents = cents,
            categoryId = category.id,
            memberId = memberId,
            occurredAt = occurredAt,
            note = note.trim(),
            merchant = merchant.trim(),
            account = account,
            source = existing?.source ?: BillSource.MANUAL,
            sourceRef = existing?.sourceRef,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        ledger.upsertRecord(record)
        Haptics.play(HapticEffect.Success)
        Toast.makeText(context, if (existing != null) "已更新" else "已记一笔", Toast.LENGTH_SHORT).show()
        onSaved(existing != null)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding(),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier.widthIn(max = SyDim.contentMaxWidth).fillMaxWidth()) {
                    SyTopBar(
                        title = if (existing != null) "编辑一笔" else "记一笔",
                        subtitle = if (existing != null) "修改后保存即生效" else "金额 → 分类 → 保存，三步完成",
                        onBack = onBack,
                        actions = {
                            if (existing != null) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .syClickable(effect = HapticEffect.Soft) { showDeleteConfirm = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "删除",
                                        tint = SyTheme.colors.expense,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = SyDim.contentMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = SyDim.gutter),
                    ) {
                        // 类型 + 日期
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SySegmented(
                                items = listOf("支出", "收入"),
                                selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                                onSelect = { index ->
                                    val next = if (index == 0) TxType.EXPENSE else TxType.INCOME
                                    if (next != type) {
                                        type = next
                                        categoryId = ""
                                        Haptics.play(HapticEffect.Select)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .syClickable(effect = HapticEffect.Soft) { showDatePicker = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = SyIcons.Calendar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = Dates.dayLabel(occurredAt),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // 金额
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = selectedCategory?.let { "${it.emoji} ${it.name}" } ?: "选择分类",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "¥",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (type == TxType.EXPENSE) SyTheme.colors.expense else SyTheme.colors.income,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = amountText,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontFamily = FontFamily.Serif,
                                    color = if (amountText == "0") {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onBackground
                                    },
                                    maxLines = 1,
                                )
                            }
                        }

                        // 分类
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "分类",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${typeCategories.size} 个可选",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxWidth().height(258.dp).padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 4.dp),
                        ) {
                            items(typeCategories, key = { it.id }) { category ->
                                val selected = category.id == categoryId
                                val color = parseHexColor(category.colorHex)
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (selected) color.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        )
                                        .then(
                                            if (selected) {
                                                Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .syClickable(effect = HapticEffect.Select) { categoryId = category.id }
                                        .padding(vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(text = category.emoji, fontSize = 17.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        // 成员
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "成员",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            members.forEach { member ->
                                SyChip(
                                    text = member.name,
                                    selected = member.id == memberId,
                                    onClick = { memberId = member.id },
                                    leadingEmoji = member.emoji,
                                )
                            }
                        }

                        // 更多（账户 / 商户 / 备注）
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SyTextButton(
                                text = if (showMore) "收起账户与备注" else "账户、商户与备注（可选）",
                                onClick = { showMore = !showMore },
                            )
                        }
                        if (showMore) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                accounts.forEach { item ->
                                    SyChip(
                                        text = item,
                                        selected = account == item,
                                        onClick = { account = item },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = merchant,
                                onValueChange = { merchant = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("商户 / 对方（可选）") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("备注（可选）") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = SyDim.gutter, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.widthIn(max = SyDim.contentMaxWidth).fillMaxWidth()) {
                    SyNumberKeypad(
                        onKey = { key -> pressKey(key) },
                        onBackspace = { backspace() },
                        onDone = { save() },
                        doneLabel = if (existing != null) "更新" else "保存",
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        occurredAt = Dates.fromLocalDate(Dates.toLocalDate(millis), 12, 0)
                    }
                    Haptics.play(HapticEffect.Confirm)
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后不可恢复，相关统计与报表会同步更新。") },
            confirmButton = {
                TextButton(onClick = {
                    existing?.let { ledger.deleteRecord(it.id) }
                    Haptics.play(HapticEffect.Error)
                    showDeleteConfirm = false
                    onSaved(true)
                }) {
                    Text("删除", color = SyTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}
