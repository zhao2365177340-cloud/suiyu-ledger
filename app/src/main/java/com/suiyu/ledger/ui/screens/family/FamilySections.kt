package com.suiyu.ledger.ui.screens.family

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.BuildConfig
import com.suiyu.ledger.analytics.MemberSlice
import com.suiyu.ledger.model.AppSettings
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyAmount
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyEmpty
import com.suiyu.ledger.ui.components.SyMemberAvatar
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SySegmented
import com.suiyu.ledger.ui.components.SyTag
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.SyToggleRow
import com.suiyu.ledger.ui.components.parseHexColor
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Money

/**
 * 家庭页的可展示分区。
 *
 * 这里刻意保持「无状态」：所有数据由 FamilyScreen 从仓库订阅后传下来，
 * 交互只通过回调上抛，方便后续复用到平板双栏布局或 Compose Preview。
 */

// ────────────────────────────────────────────────────────────────
// 家庭概览
// ────────────────────────────────────────────────────────────────

@Composable
internal fun FamilyOverviewCard(
    familyName: String,
    memberCount: Int,
    expenseCents: Long,
    incomeCents: Long,
    masked: Boolean,
    onEditName: () -> Unit,
) {
    SyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 家庭名本身也可点：比只给一个「编辑名称」更符合直觉
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .syClickable(effect = HapticEffect.Soft, onClick = onEditName)
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = familyName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$memberCount 位成员 · 本月账目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "编辑名称",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .syClickable(effect = HapticEffect.Soft, onClick = onEditName)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        SyDivider()
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本月总支出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(3.dp))
                SyAmount(
                    cents = expenseCents,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SyTheme.colors.expense,
                    masked = masked,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "本月收入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(3.dp))
                SyAmount(
                    cents = incomeCents,
                    type = TxType.INCOME,
                    style = MaterialTheme.typography.titleLarge,
                    masked = masked,
                )
            }
        }
        Spacer(modifier = Modifier.height(SyDim.gapL))
        Text(
            text = "日计不足，岁计有余",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.sp,
            ),
            color = SyTheme.colors.gold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 成员
// ────────────────────────────────────────────────────────────────

@Composable
internal fun MembersSection(
    members: List<Member>,
    slices: Map<String, MemberSlice>,
    totalExpenseCents: Long,
    masked: Boolean,
    onMemberClick: (Member) -> Unit,
    onAddMember: () -> Unit,
) {
    SySectionHeader(
        title = "家庭成员",
        subtitle = "点击成员可编辑名称、头像、主题色与身份",
    )
    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        if (members.isEmpty()) {
            SyEmpty(
                emoji = "👪",
                title = "还没有成员",
                description = "添加成员后即可在记账时选择归属，并查看各自的支出占比。",
            )
        } else {
            members.forEachIndexed { index, member ->
                if (index > 0) SyDivider()
                MemberRow(
                    member = member,
                    slice = slices[member.id],
                    totalExpenseCents = totalExpenseCents,
                    masked = masked,
                    onClick = { onMemberClick(member) },
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(SyDim.gapS))
    SyButton(
        text = "添加成员",
        onClick = onAddMember,
        modifier = Modifier.fillMaxWidth(),
        style = SyButtonStyle.Secondary,
    )
}

@Composable
private fun MemberRow(
    member: Member,
    slice: MemberSlice?,
    totalExpenseCents: Long,
    masked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .syClickable(effect = HapticEffect.Soft, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyMemberAvatar(member = member)
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            val incomeText = when {
                slice == null || slice.incomeCents <= 0L -> "本月无收入"
                masked -> "收入 ¥ ••••"
                else -> "收入 ${Money.format(slice.incomeCents)}"
            }
            Text(
                text = "${member.role} · $incomeText · ${slice?.count ?: 0} 笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(SyDim.gapS))
        Column(horizontalAlignment = Alignment.End) {
            SyAmount(
                cents = slice?.expenseCents ?: 0L,
                style = MaterialTheme.typography.titleMedium,
                color = SyTheme.colors.expense,
                masked = masked,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (totalExpenseCents <= 0L) {
                    "占比 —"
                } else {
                    "占比 ${Money.percent(slice?.expenseRatio ?: 0.0)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 分类
// ────────────────────────────────────────────────────────────────

@Composable
internal fun CategoriesSection(
    categories: List<Category>,
    onAddCategory: (TxType) -> Unit,
    onDeleteCategory: (Category) -> Unit,
) {
    SySectionHeader(
        title = "分类管理",
        subtitle = "内置分类只读；自定义分类可随时删除",
    )
    TxType.entries.forEach { type ->
        CategoryGroup(
            type = type,
            categories = categories.filter { it.type == type },
            onAddCategory = onAddCategory,
            onDeleteCategory = onDeleteCategory,
        )
        Spacer(modifier = Modifier.height(SyDim.gapS))
    }
}

@Composable
private fun CategoryGroup(
    type: TxType,
    categories: List<Category>,
    onAddCategory: (TxType) -> Unit,
    onDeleteCategory: (Category) -> Unit,
) {
    val builtin = categories.filter { it.builtin }.sortedBy { it.order }
    val custom = categories.filterNot { it.builtin }.sortedBy { it.order }
    SyCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (type == TxType.EXPENSE) "支出分类" else "收入分类",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            SyTextButton(text = "＋ 添加", onClick = { onAddCategory(type) })
        }
        Spacer(modifier = Modifier.height(SyDim.gapXs))
        builtin.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { category -> BuiltinCategoryCell(category = category, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (custom.isNotEmpty()) {
            Spacer(modifier = Modifier.height(SyDim.gapS))
            SyDivider()
            Spacer(modifier = Modifier.height(SyDim.gapS))
            Text(
                text = "自定义分类",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SyDim.gapXs))
            custom.forEach { category ->
                CustomCategoryRow(category = category, onDelete = { onDeleteCategory(category) })
            }
        }
    }
}

@Composable
private fun BuiltinCategoryCell(category: Category, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryEmojiDot(emoji = category.emoji, colorHex = category.colorHex)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(SyDim.gapXs))
        SyTag(text = "内置")
    }
}

@Composable
private fun CustomCategoryRow(category: Category, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryEmojiDot(emoji = category.emoji, colorHex = category.colorHex)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SyTextButton(
            text = "删除",
            onClick = onDelete,
            color = SyTheme.colors.expense,
        )
    }
}

@Composable
private fun CategoryEmojiDot(emoji: String, colorHex: String) {
    val color = parseHexColor(colorHex)
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = 13.sp)
    }
}

// ────────────────────────────────────────────────────────────────
// 偏好设置
// ────────────────────────────────────────────────────────────────

@Composable
internal fun PreferenceSection(
    settings: AppSettings,
    onHapticsEnabled: (Boolean) -> Unit,
    onHapticStrength: (Int) -> Unit,
    onPrivacyMask: (Boolean) -> Unit,
    onThemeMode: (Int) -> Unit,
) {
    SySectionHeader(
        title = "偏好设置",
        subtitle = "触感、隐私与主题",
    )
    SyCard {
        SyToggleRow(
            title = "触感反馈",
            checked = settings.hapticsEnabled,
            onCheckedChange = onHapticsEnabled,
            description = "轻点、切换与保存时的轻微震动",
        )
        SyDivider()
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = "触感强度",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (settings.hapticsEnabled) "轻 / 标准 / 明显，点选即试振" else "触感已关闭，开启后可试振",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SyDim.gapS))
            SySegmented(
                items = listOf("轻", "标准", "明显"),
                selectedIndex = (settings.hapticStrength - 1).coerceIn(0, 2),
                onSelect = onHapticStrength,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SyDivider()
        SyToggleRow(
            title = "金额遮罩",
            checked = settings.privacyMask,
            onCheckedChange = onPrivacyMask,
            description = "将各页金额显示为 ¥ ••••，适合旁人旁观时",
        )
        SyDivider()
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(
                text = "主题模式",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "跟随系统、浅色或深色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(SyDim.gapS))
            SySegmented(
                items = listOf("跟随系统", "浅色", "深色"),
                selectedIndex = settings.themeMode.coerceIn(0, 2),
                onSelect = onThemeMode,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SyDivider()
        Text(
            text = "所有数据仅保存在本机。卸载应用会一并删除，建议定期导出备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 数据
// ────────────────────────────────────────────────────────────────

@Composable
internal fun DataSection(
    recordCount: Int,
    seededDemo: Boolean,
    busy: Boolean,
    onExportCsv: () -> Unit,
    onExportBackup: () -> Unit,
    onRestore: () -> Unit,
    onClearRecords: () -> Unit,
    onResetDemo: () -> Unit,
) {
    SySectionHeader(
        title = "数据",
        subtitle = "导出、备份、恢复与重置",
    )
    SyCard {
        Text(
            text = if (seededDemo) {
                "当前包含示例数据，可随时清空后开始记录自己的账本。"
            } else {
                "全部数据保存在本机文件，不上传任何服务器。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapS)) {
            SyButton(
                text = "导出 CSV",
                onClick = onExportCsv,
                modifier = Modifier.weight(1f),
                style = SyButtonStyle.Secondary,
                enabled = !busy,
                leading = { Text(text = "📄", fontSize = 14.sp) },
            )
            SyButton(
                text = "导出备份",
                onClick = onExportBackup,
                modifier = Modifier.weight(1f),
                style = SyButtonStyle.Secondary,
                enabled = !busy,
                leading = { Text(text = "🗂", fontSize = 14.sp) },
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapS))
        SyButton(
            text = "从备份恢复",
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
            style = SyButtonStyle.Secondary,
            enabled = !busy,
            leading = { Text(text = "↩", fontSize = 14.sp) },
        )
        if (busy) {
            Spacer(modifier = Modifier.height(SyDim.gapM))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(SyDim.gapS))
                Text(
                    text = "正在处理，请稍候…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(SyDim.gapL))
        SyDivider()
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Text(
            text = "危险操作 · 当前共 $recordCount 笔流水",
            style = MaterialTheme.typography.labelMedium,
            color = SyTheme.colors.expense,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "清空流水会保留成员与自定义分类；重置会恢复为示例账本。两者都无法撤销，建议先导出备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapS)) {
            SyButton(
                text = "清空流水",
                onClick = onClearRecords,
                modifier = Modifier.weight(1f),
                style = SyButtonStyle.Danger,
                enabled = !busy && recordCount > 0,
                haptic = HapticEffect.Warning,
            )
            SyButton(
                text = "重置为示例",
                onClick = onResetDemo,
                modifier = Modifier.weight(1f),
                style = SyButtonStyle.Danger,
                enabled = !busy,
                haptic = HapticEffect.Warning,
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 关于
// ────────────────────────────────────────────────────────────────

@Composable
internal fun AboutSection() {
    SySectionHeader(
        title = "关于岁余",
        subtitle = "本地优先的家庭记账",
    )
    SyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SyTheme.colors.goldSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "余",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SyTheme.colors.gold,
                )
            }
            Spacer(modifier = Modifier.width(SyDim.gapM))
            Column {
                Text(
                    text = "岁余",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Text(
            text = "日计不足，岁计有余",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.sp,
            ),
            color = SyTheme.colors.gold,
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        SyDivider()
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapS)) {
            SyTag(text = "本地存储")
            SyTag(text = "无广告")
        }
        Spacer(modifier = Modifier.height(SyDim.gapXs))
        Row(horizontalArrangement = Arrangement.spacedBy(SyDim.gapS)) {
            SyTag(text = "无账号")
            SyTag(text = "无网络上传")
        }
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Text(
            text = "所有流水、成员与设置只写入本机文件；应用未申请网络权限，截图识别也在本机完成，账单内容不会离开设备。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        SyDivider()
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Text(
            text = "个税口径",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "按 2026 年度个人所得税综合所得汇算口径测算；全年一次性奖金单独计税政策延续至 2027 年底。测算结果仅供参考，不构成税务建议。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SyDim.gapM))
        Text(
            text = "组件致谢",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Jetpack Compose · Material 3 · ML Kit（端侧中文文字识别）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
