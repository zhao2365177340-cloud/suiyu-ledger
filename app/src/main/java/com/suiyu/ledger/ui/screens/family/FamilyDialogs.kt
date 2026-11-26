package com.suiyu.ledger.ui.screens.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyChip
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyMemberAvatar
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.ChartPaletteLight
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import java.util.Locale

/**
 * 家庭页的弹窗集合：家庭名称、成员增改、分类新增、危险操作二次确认。
 *
 * 设计取舍：
 *  - 成员 / 分类编辑器都做成「表单 + 实时预览」，避免保存后才发现头像配色难看；
 *  - 删除类操作不塞进编辑器内二次弹窗，而是由页面状态机接管（先关编辑再开确认），
 *    这样任何时候屏幕上只有一个 AlertDialog，不会出现弹窗叠弹窗。
 */

/** 精选手绘感 emoji，覆盖常见家庭角色，避免全量 emoji 键盘带来的长列表 */
private val MemberEmojiChoices = listOf(
    "🙂", "😊", "😄", "🥰", "😎", "🤗",
    "🧑", "👩", "👨", "🧒", "👧", "👶",
    "👵", "👴", "🐱", "🐶", "🌸", "🍀",
)

/** 分类 emoji：优先覆盖内置分类已有的意象，新增分类时不至于找不到合适的图标 */
private val CategoryEmojiChoices = listOf(
    "🍜", "🥬", "🚇", "🛍️", "🏠", "📱",
    "💊", "📚", "🎬", "🧧", "✈️", "🐾",
    "💼", "🎉", "📈", "🧾", "💰", "📎",
    "🎁", "☕", "🎮", "🚗", "💡", "🧹",
)

private val RoleChoices = listOf("本人", "配偶", "子女", "父母", "其他")

/** 分类主题色固定取设计系统图表色板的前 8 色，保证与统计页色彩语义一致 */
private val ColorChoices: List<Pair<String, Color>> = ChartPaletteLight.take(8).map { colorHex(it) to it }

private fun colorHex(color: Color): String =
    String.format(Locale.US, "#%06X", color.toArgb() and 0xFFFFFF)

// ────────────────────────────────────────────────────────────────
// 通用小件
// ────────────────────────────────────────────────────────────────

@Composable
private fun DialogFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 单选 emoji 网格：6 列，选中项用稻金描边提示 */
@Composable
private fun EmojiChoicesGrid(
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        choices.chunked(6).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SyDim.gapS),
            ) {
                row.forEach { emoji ->
                    val isSelected = emoji == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> SyTheme.colors.goldSoft
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                },
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, SyTheme.colors.gold, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .syClickable(effect = HapticEffect.Select) { onSelect(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 19.sp)
                    }
                }
                repeat(6 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(SyDim.gapS))
        }
    }
}

/** 分类主题色选择：4 列色点，选中项加墨色描边 */
@Composable
private fun ColorChoicesGrid(
    selectedHex: String,
    onSelect: (String) -> Unit,
) {
    Column {
        ColorChoices.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SyDim.gapM),
            ) {
                row.forEach { (hex, color) ->
                    val isSelected = hex.equals(selectedHex, ignoreCase = true)
                    // 点击热区做到整格大小（38dp 高），色点只是视觉主体，避免只能点中小圆点
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .syClickable(effect = HapticEffect.Select) { onSelect(hex) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
            Spacer(modifier = Modifier.height(SyDim.gapXs))
        }
    }
}

@Composable
private fun RoleChoicesChips(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        RoleChoices.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SyDim.gapS),
            ) {
                row.forEach { role ->
                    SyChip(text = role, selected = role == selected, onClick = { onSelect(role) })
                }
            }
            Spacer(modifier = Modifier.height(SyDim.gapS))
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 家庭名称
// ────────────────────────────────────────────────────────────────

@Composable
internal fun FamilyNameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "家庭名称", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(text = "名称") },
                    placeholder = { Text(text = "如：我们家") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapS))
                Text(
                    text = "名称只用于本机展示，留空将使用「我家」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Haptics.play(HapticEffect.Confirm)
                    onConfirm(name)
                },
            ) { Text(text = "保存") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Haptics.play(HapticEffect.Soft)
                    onDismiss()
                },
            ) { Text(text = "取消") }
        },
    )
}

// ────────────────────────────────────────────────────────────────
// 成员新增 / 编辑
// ────────────────────────────────────────────────────────────────

/**
 * 成员编辑器。[initial] 为 null 表示新增。
 * 删除入口只在编辑非「我」的成员时出现，真正的二次确认由页面负责。
 */
@Composable
internal fun MemberEditorDialog(
    initial: Member?,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, colorHex: String, role: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var emoji by remember(initial) { mutableStateOf(initial?.emoji ?: MemberEmojiChoices.first()) }
    var colorHex by remember(initial) {
        mutableStateOf(initial?.colorHex ?: ColorChoices.first().first)
    }
    var role by remember(initial) {
        mutableStateOf(
            // 旧版本备份里的标签可能不在预设里，兜底到「其他」以免出现空选中
            initial?.role?.takeIf { it in RoleChoices } ?: RoleChoices.last(),
        )
    }
    var showNameError by remember(initial) { mutableStateOf(false) }

    val preview = Member(
        id = "preview",
        name = name.ifBlank { "成员" },
        emoji = emoji,
        colorHex = colorHex,
        role = role,
        isSelf = initial?.isSelf == true,
        order = initial?.order ?: 0,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "添加成员" else "编辑成员",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SyMemberAvatar(member = preview, size = 42.dp)
                    Spacer(modifier = Modifier.width(SyDim.gapM))
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showNameError = false
                        },
                        singleLine = true,
                        isError = showNameError,
                        label = { Text(text = "名称") },
                        placeholder = { Text(text = "如：爸爸") },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (showNameError) {
                    Spacer(modifier = Modifier.height(SyDim.gapXs))
                    Text(
                        text = "请填写成员名称",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogFieldLabel(text = "头像")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                EmojiChoicesGrid(
                    choices = MemberEmojiChoices,
                    selected = emoji,
                    onSelect = { emoji = it },
                )
                DialogFieldLabel(text = "主题色")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                ColorChoicesGrid(selectedHex = colorHex, onSelect = { colorHex = it })
                DialogFieldLabel(text = "身份标签")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                RoleChoicesChips(selected = role, onSelect = { role = it })
                Spacer(modifier = Modifier.height(SyDim.gapS))
                SyDivider()
                Spacer(modifier = Modifier.height(SyDim.gapM))
                if (initial?.isSelf == true) {
                    Text(
                        text = "「我」是默认记账人，不可删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (onDelete != null) {
                    Text(
                        text = "删除成员后，其名下流水会自动归到「我」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(SyDim.gapS))
                    SyButton(
                        text = "删除成员",
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        style = SyButtonStyle.Danger,
                        haptic = HapticEffect.Warning,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        showNameError = true
                        Haptics.play(HapticEffect.Warning)
                    } else {
                        Haptics.play(HapticEffect.Confirm)
                        onSave(trimmed, emoji, colorHex, role)
                    }
                },
            ) { Text(text = if (initial == null) "添加" else "保存") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Haptics.play(HapticEffect.Soft)
                    onDismiss()
                },
            ) { Text(text = "取消") }
        },
    )
}

// ────────────────────────────────────────────────────────────────
// 自定义分类新增
// ────────────────────────────────────────────────────────────────

@Composable
internal fun CategoryEditorDialog(
    initialType: TxType,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, type: TxType, colorHex: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(CategoryEmojiChoices.first()) }
    var colorHex by remember { mutableStateOf(ColorChoices.first().first) }
    var type by remember(initialType) { mutableStateOf(initialType) }
    var showNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加自定义分类", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DialogFieldLabel(text = "类型")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                SySegmented(
                    items = listOf("支出", "收入"),
                    selectedIndex = if (type == TxType.EXPENSE) 0 else 1,
                    onSelect = { index -> type = if (index == 0) TxType.EXPENSE else TxType.INCOME },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(SyDim.gapM))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showNameError = false
                    },
                    singleLine = true,
                    isError = showNameError,
                    label = { Text(text = "名称") },
                    placeholder = { Text(text = "如：宠物医疗") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showNameError) {
                    Spacer(modifier = Modifier.height(SyDim.gapXs))
                    Text(
                        text = "请填写分类名称",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(SyDim.gapM))
                DialogFieldLabel(text = "图标")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                EmojiChoicesGrid(
                    choices = CategoryEmojiChoices,
                    selected = emoji,
                    onSelect = { emoji = it },
                )
                DialogFieldLabel(text = "颜色")
                Spacer(modifier = Modifier.height(SyDim.gapS))
                ColorChoicesGrid(selectedHex = colorHex, onSelect = { colorHex = it })
                Spacer(modifier = Modifier.height(SyDim.gapS))
                Text(
                    text = "自定义分类可随时删除；删除后其下流水会归入对应的「其他」分类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        showNameError = true
                        Haptics.play(HapticEffect.Warning)
                    } else {
                        Haptics.play(HapticEffect.Confirm)
                        onSave(trimmed, emoji, type, colorHex)
                    }
                },
            ) { Text(text = "添加") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Haptics.play(HapticEffect.Soft)
                    onDismiss()
                },
            ) { Text(text = "取消") }
        },
    )
}

// ────────────────────────────────────────────────────────────────
// 危险操作二次确认
// ────────────────────────────────────────────────────────────────

@Composable
internal fun FamilyConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Haptics.play(if (danger) HapticEffect.Warning else HapticEffect.Confirm)
                    onConfirm()
                },
            ) {
                Text(
                    text = confirmText,
                    color = if (danger) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    Haptics.play(HapticEffect.Soft)
                    onDismiss()
                },
            ) { Text(text = "取消") }
        },
    )
}
