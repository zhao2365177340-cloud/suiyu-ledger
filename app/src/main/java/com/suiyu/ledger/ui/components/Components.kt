package com.suiyu.ledger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.MonthKey
import com.suiyu.ledger.util.TxTypeSign

/** 解析 "#RRGGBB" / "#AARRGGBB" 颜色；解析失败回退 */
fun parseHexColor(hex: String, fallback: Color = Color(0xFF9A9487)): Color = runCatching {
    val raw = hex.trim().removePrefix("#")
    val value = when (raw.length) {
        6 -> 0xFF000000L or raw.toLong(16)
        8 -> raw.toLong(16)
        else -> return fallback
    }
    Color(value.toInt())
}.getOrDefault(fallback)

/** 点击即触发触感，统一全站交互反馈 */
fun Modifier.syClickable(
    effect: HapticEffect = HapticEffect.Tick,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(enabled = enabled) {
    if (enabled) {
        Haptics.play(effect)
        onClick()
    }
}

// ────────────────────────────────────────────────────────────────
// 页面骨架
// ────────────────────────────────────────────────────────────────

/**
 * 页面骨架：安全区 + 版心（最大 560dp，兼容平板/折叠屏）+ 可选滚动。
 * 底部 Tab 页请在内容末尾调用 [SyBottomSpacer]，给悬浮底栏让位。
 */
@Composable
fun SyScaffold(
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = false,
    horizontalPadding: Dp = SyDim.gutter,
    bottomPadding: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                if (title != null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(modifier = Modifier.widthIn(max = SyDim.contentMaxWidth).fillMaxWidth()) {
                            SyTopBar(title = title, subtitle = subtitle, onBack = onBack, actions = actions)
                        }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val base = Modifier
                    .widthIn(max = SyDim.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(start = horizontalPadding, end = horizontalPadding, bottom = bottomPadding)
                if (scroll) {
                    Column(modifier = base.verticalScroll(rememberScrollState()), content = content)
                } else {
                    Column(modifier = base, content = content)
                }
            }
        }
    }
}

@Composable
fun SyTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SyDim.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .syClickable(effect = HapticEffect.Select, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** 给底部 Tab 悬浮栏留出空间 */
@Composable
fun SyBottomSpacer(extra: Dp = 84.dp) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Spacer(modifier = Modifier.height(extra + navBar))
}

@Composable
fun SyDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    )
}

// ────────────────────────────────────────────────────────────────
// 卡片 / 标题 / 按钮
// ────────────────────────────────────────────────────────────────

@Composable
fun SyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SyDim.cardRadius)
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(color)
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
    val interactive = if (onClick != null) {
        base.syClickable(effect = HapticEffect.Soft, onClick = onClick)
    } else {
        base
    }
    Column(modifier = interactive.padding(contentPadding), content = content)
}

@Composable
fun SySectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 15.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SyTheme.colors.gold),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (onTrailingClick != null) {
                            Modifier.syClickable(effect = HapticEffect.Soft, onClick = onTrailingClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

enum class SyButtonStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun SyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SyButtonStyle = SyButtonStyle.Primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    haptic: HapticEffect = HapticEffect.Confirm,
) {
    val xm = SyTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val bg = when (style) {
        SyButtonStyle.Primary -> MaterialTheme.colorScheme.primary
        SyButtonStyle.Secondary, SyButtonStyle.Ghost -> Color.Transparent
        SyButtonStyle.Danger -> xm.expenseSoft
    }
    val fg = when (style) {
        SyButtonStyle.Primary -> MaterialTheme.colorScheme.onPrimary
        SyButtonStyle.Secondary -> MaterialTheme.colorScheme.onSurface
        SyButtonStyle.Ghost -> MaterialTheme.colorScheme.primary
        SyButtonStyle.Danger -> xm.expense
    }
    val border = if (style == SyButtonStyle.Secondary) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f))
    } else {
        null
    }
    var base = modifier
        .fillMaxWidth()
        .height(50.dp)
        .clip(shape)
        .background(bg)
    if (border != null) base = base.border(border, shape)
    base = base.syClickable(enabled = enabled, effect = haptic, onClick = onClick)
    if (!enabled) base = base.alpha(0.4f)
    Box(modifier = base, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.titleMedium, color = fg)
        }
    }
}

@Composable
fun SyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .syClickable(effect = HapticEffect.Soft, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ────────────────────────────────────────────────────────────────
// 选择类控件
// ────────────────────────────────────────────────────────────────

@Composable
fun SyChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingEmoji: String? = null,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        label = "chipBg",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .syClickable(effect = HapticEffect.Select, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingEmoji != null) {
            Text(text = leadingEmoji, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
fun SySegmented(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                label = "segBg",
            )
            val radius by animateDpAsState(targetValue = if (selected) 11.dp else 11.dp, label = "segRadius")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(radius))
                    .background(bg)
                    .syClickable(effect = HapticEffect.Select) { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SyToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .syClickable(effect = HapticEffect.Toggle) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 展示类控件
// ────────────────────────────────────────────────────────────────

@Composable
fun SyMemberAvatar(
    member: Member,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    selected: Boolean = false,
) {
    val color = parseHexColor(member.colorHex, MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .then(if (selected) Modifier.border(2.dp, color, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = member.emoji, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun SyTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun SySourceTag(source: BillSource, modifier: Modifier = Modifier) {
    if (!source.isOcr) return
    val color = when (source) {
        BillSource.WECHAT -> Color(0xFF2F8F5B)
        BillSource.ALIPAY -> Color(0xFF2B6FB5)
        BillSource.BANK -> Color(0xFF9A7A3C)
        BillSource.SMS -> Color(0xFF7A6C5C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    SyTag(
        text = source.short,
        modifier = modifier,
        color = color,
        background = color.copy(alpha = 0.12f),
    )
}

@Composable
fun SyAmount(
    cents: Long,
    type: TxType? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    showSign: Boolean = false,
    masked: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val xm = SyTheme.colors
    val resolved = when {
        color != Color.Unspecified -> color
        type == TxType.EXPENSE -> xm.expense
        type == TxType.INCOME -> xm.income
        else -> MaterialTheme.colorScheme.onSurface
    }
    val text = if (masked) {
        "¥ ••••"
    } else {
        Money.format(
            cents = cents,
            symbol = true,
            sign = showSign,
            type = when (type) {
                TxType.EXPENSE -> TxTypeSign.EXPENSE
                TxType.INCOME -> TxTypeSign.INCOME
                null -> TxTypeSign.NONE
            },
        )
    }
    Text(text = text, style = style, color = resolved, modifier = modifier, maxLines = 1)
}

@Composable
fun SyStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
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
        if (!sub.isNullOrBlank()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SyEmpty(
    emoji: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 40.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.width(180.dp)) {
                SyButton(text = actionText, onClick = onAction, style = SyButtonStyle.Secondary)
            }
        }
    }
}

/** 一条流水（首页 / 统计 / 报表复用） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyRecordRow(
    record: TxRecord,
    category: Category?,
    member: Member?,
    modifier: Modifier = Modifier,
    showTime: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** 长按进入多选 / 选择模式 */
    onLongClick: (() -> Unit)? = null,
    /** 当前是否被勾选（明细页多选） */
    selected: Boolean = false,
    /** 是否处于多选模式：左侧显示勾选框 */
    selectionMode: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val clickable = when {
        onLongClick != null -> Modifier.combinedClickable(
            onClick = {
                Haptics.play(HapticEffect.Soft)
                onClick?.invoke()
            },
            onLongClick = {
                Haptics.play(HapticEffect.LongPress)
                onLongClick()
            },
        )
        onClick != null -> Modifier.syClickable(effect = HapticEffect.Soft, onClick = onClick)
        else -> Modifier
    }
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                Color.Transparent
            },
        )
        .then(clickable)
        .padding(horizontal = if (selectionMode) 6.dp else 0.dp, vertical = 9.dp)
    Row(modifier = base, verticalAlignment = Alignment.CenterVertically) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        val color = parseHexColor(category?.colorHex ?: "#9A9487")
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = category?.emoji ?: "📎", fontSize = 17.sp)
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category?.name ?: "未分类",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (member != null) {
                    Text(
                        text = " · ${member.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val subtitle = when {
                    record.merchant.isNotBlank() -> record.merchant
                    record.note.isNotBlank() -> record.note
                    record.account.isNotBlank() -> record.account
                    else -> record.type.label
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (record.source.isOcr) {
                    Spacer(modifier = Modifier.width(6.dp))
                    SySourceTag(record.source)
                }
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            SyAmount(
                cents = record.amountCents,
                type = record.type,
                style = MaterialTheme.typography.titleMedium,
            )
            if (showTime) {
                Text(
                    text = Dates.time(record.occurredAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 账期选择
// ────────────────────────────────────────────────────────────────

@Composable
fun SyMonthSwitcher(
    month: MonthKey,
    onMonthChange: (MonthKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableIntStateOf(0) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ChevronButton(left = true) { onMonthChange(month.plusMonths(-1)) }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .syClickable(effect = HapticEffect.Soft) { showPicker = 1 }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = month.label(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "选择月份",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        ChevronButton(left = false) { onMonthChange(month.plusMonths(1)) }
    }
    if (showPicker == 1) {
        SyMonthPickerDialog(
            initial = month,
            onDismiss = { showPicker = 0 },
            onConfirm = {
                onMonthChange(it)
                showPicker = 0
            },
        )
    }
}

@Composable
private fun ChevronButton(left: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .syClickable(effect = HapticEffect.Select, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (left) "上一个月" else "下一个月",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun SyMonthPickerDialog(
    initial: MonthKey,
    onDismiss: () -> Unit,
    onConfirm: (MonthKey) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择账期", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ChevronButton(left = true) { year -= 1 }
                    Text(
                        text = "${year}年",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ChevronButton(left = false) { year += 1 }
                }
                Spacer(modifier = Modifier.height(4.dp))
                (0 until 4).forEach { rowIndex ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (1..3).forEach { colIndex ->
                            val value = rowIndex * 3 + colIndex
                            val selected = value == month
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    )
                                    .syClickable(effect = HapticEffect.Select) { month = value }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${value}月",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Haptics.play(HapticEffect.Confirm)
                onConfirm(MonthKey(year, month))
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ────────────────────────────────────────────────────────────────
// 数字键盘（记一笔）
// ────────────────────────────────────────────────────────────────

@Composable
fun SyNumberKeypad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    doneLabel: String = "保存",
    keyHeight: Dp = 46.dp,
) {
    val gap = 8.dp
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫"))
    Row(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(3f)) {
            rows.forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { colIndex, key ->
                        if (key == "⌫") {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(keyHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .syClickable(effect = HapticEffect.Soft, onClick = onBackspace),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "⌫",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(keyHeight)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .syClickable(effect = HapticEffect.Tick) { onKey(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 21.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (colIndex != 2) Spacer(modifier = Modifier.width(gap))
                    }
                }
                if (rowIndex != rows.lastIndex) Spacer(modifier = Modifier.height(gap))
            }
        }
        Spacer(modifier = Modifier.width(gap))
        val totalHeight = keyHeight * 4 + gap * 3
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(totalHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .syClickable(effect = HapticEffect.Confirm, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = doneLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
