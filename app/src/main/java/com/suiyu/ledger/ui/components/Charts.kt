package com.suiyu.ledger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.MonthKey
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 岁余自绘图表：不依赖第三方图表库，保证体积小、风格统一、动画可控。
 * 每个图表只回答一个财务问题：占比环、对比柱、趋势线、日历热力。
 */

data class ChartSlice(
    val key: String,
    val label: String,
    val value: Float,
    val color: Color,
)

data class ChartRankItem(
    val key: String,
    val label: String,
    val emoji: String,
    val valueLabel: String,
    val ratio: Float,
    val color: Color,
)

data class BarGroup(
    val label: String,
    val primary: Float,
    val secondary: Float = 0f,
)

data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
)

// ────────────────────────────────────────────────────────────────
// 环形占比图
// ────────────────────────────────────────────────────────────────

@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    centerValue: String? = null,
    strokeWidth: Dp = 24.dp,
    onSliceClick: ((Int) -> Unit)? = null,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.001f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis = 720, easing = FastOutSlowInEasing))
    }
    var selected by remember { mutableIntStateOf(-1) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onSliceClick != null) {
                        Modifier.pointerInput(slices) {
                            detectTapGestures { offset ->
                                val stroke = strokeWidth.toPx()
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                val distance = sqrt(dx * dx + dy * dy)
                                val outer = min(size.width, size.height) / 2f + 10f
                                val inner = min(size.width, size.height) / 2f - stroke - 14f
                                if (distance in inner..outer) {
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                    if (angle < 0f) angle += 360f
                                    var cursor = 0f
                                    var hit = -1
                                    for (index in slices.indices) {
                                        val sweep = (slices[index].value / total) * 360f
                                        if (angle >= cursor && angle < cursor + sweep) {
                                            hit = index
                                            break
                                        }
                                        cursor += sweep
                                    }
                                    if (hit >= 0) {
                                        selected = if (selected == hit) -1 else hit
                                        Haptics.play(HapticEffect.Tick)
                                        onSliceClick(hit)
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val stroke = strokeWidth.toPx()
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val gap = if (slices.size > 1) 1.8f else 0f
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = (slice.value / total) * 360f * anim.value
                val color = if (selected == -1 || selected == index) {
                    slice.color
                } else {
                    slice.color.copy(alpha = 0.28f)
                }
                drawArc(
                    color = color,
                    startAngle = start + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        if (centerLabel != null || centerValue != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (centerLabel != null) {
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (centerValue != null) {
                    Text(
                        text = centerValue,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 排行榜式分类占比（比饼图更易读，适合手机窄屏） */
@Composable
fun ChartRankList(
    items: List<ChartRankItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((Int) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val animatedRatio by animateFloatAsState(
                targetValue = item.ratio.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 640, easing = FastOutSlowInEasing),
                label = "rankRatio",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onItemClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .androidxClickable { onItemClick(index) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 8.dp, horizontal = if (onItemClick != null) 4.dp else 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.emoji, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = item.valueLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(item.ratio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp),
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatio)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(item.color),
                    )
                }
            }
        }
    }
}

/** 让 ChartRankList 的可点击不依赖 Components 内部导入顺序 */
private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.syClickable(effect = HapticEffect.Soft, onClick = onClick),
)

// ────────────────────────────────────────────────────────────────
// 分组柱状图（环比 / 同比 / 成员对比）
// ────────────────────────────────────────────────────────────────

@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
    primaryLabel: String = "本期",
    secondaryLabel: String? = null,
    onGroupClick: ((Int) -> Unit)? = null,
) {
    val xm = SyTheme.colors
    val primaryColor = xm.expense
    val secondaryColor = xm.gold
    val outlineColor = MaterialTheme.colorScheme.outline
    val maxValue = groups.maxOfOrNull { maxOf(it.primary, it.secondary) }?.coerceAtLeast(1f) ?: 1f
    val anim = remember { Animatable(0f) }
    LaunchedEffect(groups) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
    }
    var selected by remember { mutableIntStateOf(-1) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(color = primaryColor, label = primaryLabel)
            if (secondaryLabel != null) {
                Spacer(modifier = Modifier.width(14.dp))
                LegendDot(color = secondaryColor, label = secondaryLabel)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (selected in groups.indices) {
                val group = groups[selected]
                Text(
                    text = "${group.label} · " + formatChartValue(group.primary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(groups) {
                    detectTapGestures { offset ->
                        if (groups.isEmpty()) return@detectTapGestures
                        val slot = size.width.toFloat() / groups.size
                        val index = (offset.x / slot).toInt().coerceIn(0, groups.lastIndex)
                        selected = if (selected == index) -1 else index
                        Haptics.play(HapticEffect.Tick)
                        onGroupClick?.invoke(index)
                    }
                },
        ) {
            val width = size.width
            val height = size.height
            val baseline = height - 2f
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            // 三条参考线
            for (line in 1..3) {
                val y = baseline - height * 0.92f * (line / 3f)
                drawLine(
                    color = outlineColor.copy(alpha = 0.28f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }
            drawLine(
                color = outlineColor.copy(alpha = 0.6f),
                start = Offset(0f, baseline),
                end = Offset(width, baseline),
                strokeWidth = 1.2f,
            )
            val slot = width / groups.size
            val barWidth = (slot * 0.24f).coerceAtLeast(4f)
            val twoBars = secondaryLabel != null
            groups.forEachIndexed { index, group ->
                val center = slot * index + slot / 2f
                val alpha = if (selected == -1 || selected == index) 1f else 0.35f
                val primaryHeight = (group.primary / maxValue) * height * 0.88f * anim.value
                val primaryLeft = if (twoBars) center - barWidth - 2f else center - barWidth / 2f
                drawRoundRect(
                    color = primaryColor.copy(alpha = alpha),
                    topLeft = Offset(primaryLeft, baseline - primaryHeight),
                    size = Size(barWidth, primaryHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                )
                if (twoBars) {
                    val secondaryHeight = (group.secondary / maxValue) * height * 0.88f * anim.value
                    drawRoundRect(
                        color = secondaryColor.copy(alpha = alpha * 0.9f),
                        topLeft = Offset(center + 2f, baseline - secondaryHeight),
                        size = Size(barWidth, secondaryHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            groups.forEach { group ->
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatChartValue(value: Float): String {
    val yuan = value / 100.0
    return if (abs(yuan) >= 10_000) {
        String.format(java.util.Locale.CHINA, "¥%.1f万", yuan / 10_000)
    } else {
        String.format(java.util.Locale.CHINA, "¥%,.0f", yuan)
    }
}

// ────────────────────────────────────────────────────────────────
// 趋势折线 / 迷你走势
// ────────────────────────────────────────────────────────────────

@Composable
fun TrendChart(
    series: List<LineSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    onPointClick: ((Int) -> Unit)? = null,
) {
    val pointCount = series.maxOfOrNull { it.values.size } ?: 0
    if (pointCount == 0) return
    val maxValue = series.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val anim = remember { Animatable(0f) }
    LaunchedEffect(series) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    var selected by remember { mutableIntStateOf(-1) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            series.forEachIndexed { index, line ->
                if (index > 0) Spacer(modifier = Modifier.width(14.dp))
                LegendDot(color = line.color, label = line.label)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (selected in 0 until pointCount) {
                val lines = series.map { line ->
                    val raw = line.values.getOrNull(selected) ?: 0f
                    "${line.label} ${formatChartValue(raw)}"
                }
                Text(
                    text = lines.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(series, labels) {
                    detectTapGestures { offset ->
                        if (pointCount <= 1) return@detectTapGestures
                        val step = size.width.toFloat() / (pointCount - 1)
                        val index = ((offset.x / step).roundToInt()).coerceIn(0, pointCount - 1)
                        selected = if (selected == index) -1 else index
                        Haptics.play(HapticEffect.Tick)
                        onPointClick?.invoke(index)
                    }
                },
        ) {
            val width = size.width
            val height = size.height
            val topPad = 6f
            val bottomPad = 8f
            val usable = height - topPad - bottomPad
            val stepX = if (pointCount > 1) width / (pointCount - 1) else width
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            for (line in 1..3) {
                val y = topPad + usable * (line / 3f)
                drawLine(
                    color = outlineColor.copy(alpha = 0.26f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                    pathEffect = dash,
                )
            }
            series.forEachIndexed { seriesIndex, line ->
                val points = line.values.take(pointCount)
                if (points.isEmpty()) return@forEachIndexed
                val path = Path()
                val fillPath = Path()
                points.forEachIndexed { index, value ->
                    val x = stepX * index
                    val y = topPad + usable * (1f - (value / maxValue) * anim.value)
                    if (index == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, topPad + usable)
                        fillPath.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                if (points.size > 1) {
                    fillPath.lineTo(stepX * (points.size - 1), topPad + usable)
                    fillPath.close()
                    if (seriesIndex == 0) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(line.color.copy(alpha = 0.22f), line.color.copy(alpha = 0.02f)),
                                startY = topPad,
                                endY = topPad + usable,
                            ),
                        )
                    }
                    drawPath(
                        path = path,
                        color = line.color,
                        style = Stroke(width = 2.4f, cap = StrokeCap.Round),
                    )
                }
                if (selected in points.indices) {
                    val index = selected
                    val x = stepX * index
                    val y = topPad + usable * (1f - (points[index] / maxValue) * anim.value)
                    drawLine(
                        color = line.color.copy(alpha = 0.4f),
                        start = Offset(x, topPad),
                        end = Offset(x, topPad + usable),
                        strokeWidth = 1f,
                        pathEffect = dash,
                    )
                    drawCircle(color = surfaceColor, radius = 5.4f, center = Offset(x, y))
                    drawCircle(color = line.color, radius = 3.6f, center = Offset(x, y))
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = SyTheme.colors.income,
    filled: Boolean = true,
) {
    if (values.size < 2) {
        Box(modifier = modifier)
        return
    }
    val maxValue = values.max().coerceAtLeast(1f)
    val minValue = min(0f, values.min())
    val range = (maxValue - minValue).coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stepX = width / (values.size - 1)
        val path = Path()
        val fillPath = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = height - ((value - minValue) / range) * (height - 4f) - 2f
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        if (filled) {
            fillPath.lineTo(width, height)
            fillPath.close()
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.0f)),
                ),
            )
        }
        drawPath(path = path, color = color, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

/** 线性进度条 */
@Composable
fun SyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 6.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(color),
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 月度日历热力（看哪天花得多）
// ────────────────────────────────────────────────────────────────

@Composable
fun MonthHeatmap(
    month: MonthKey,
    values: List<Long>,
    modifier: Modifier = Modifier,
    onDayClick: ((Int) -> Unit)? = null,
) {
    if (values.isEmpty()) return
    val maxValue = values.max().coerceAtLeast(1L)
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val firstWeekday = Dates.firstWeekdayOfMonth(month) // 1 = Monday
    val dayCount = values.size
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val cells = ArrayList<Int?>(firstWeekday - 1 + dayCount)
        repeat(firstWeekday - 1) { cells += null }
        for (day in 1..dayCount) cells += day
        while (cells.size % 7 != 0) cells += null
        val weeks = cells.chunked(7)
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
                        if (day != null) {
                            val intensity = (values[day - 1].toDouble() / maxValue.toDouble()).toFloat()
                            val base = SyTheme.colors.expense
                            val background = if (values[day - 1] == 0L) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            } else {
                                base.copy(alpha = 0.12f + 0.72f * intensity)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(background)
                                    .then(
                                        if (onDayClick != null) {
                                            Modifier.syClickable(effect = HapticEffect.Tick) { onDayClick(day) }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.55f) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(30.dp))
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────
// 雷达图（财务体检六维评分）
// ────────────────────────────────────────────────────────────────

data class RadarAxis(val label: String, val value: Float)

/**
 * 六维雷达图：value 取 0..1。
 * 标签用 nativeCanvas 绘制，避免引入额外文本测量依赖。
 */
@Composable
fun RadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outline,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (axes.size < 3) return
    val labelColorArgb = labelColor.toArgb()
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(230.dp)) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val labelGap = 26.dp.toPx()
            val radius = (min(size.width, size.height) / 2f - labelGap).coerceAtLeast(10f)
            val count = axes.size
            val angleStep = (Math.PI * 2 / count).toFloat()
            fun pointAt(index: Int, ratio: Float): androidx.compose.ui.geometry.Offset {
                val angle = (-Math.PI / 2).toFloat() + angleStep * index
                val r = radius * ratio
                return androidx.compose.ui.geometry.Offset(
                    center.x + cos(angle) * r,
                    center.y + sin(angle) * r,
                )
            }
            // 网格环
            for (ring in 1..4) {
                val ratio = ring / 4f
                val path = Path()
                for (index in 0 until count) {
                    val point = pointAt(index, ratio)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
                drawPath(
                    path = path,
                    color = gridColor.copy(alpha = if (ring == 4) 0.55f else 0.25f),
                    style = Stroke(width = if (ring == 4) 1.4f else 1f),
                )
            }
            // 轴线
            for (index in 0 until count) {
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = center,
                    end = pointAt(index, 1f),
                    strokeWidth = 1f,
                )
            }
            // 数据多边形
            val dataPath = Path()
            axes.forEachIndexed { index, axis ->
                val point = pointAt(index, axis.value.coerceIn(0.05f, 1f))
                if (index == 0) dataPath.moveTo(point.x, point.y) else dataPath.lineTo(point.x, point.y)
            }
            dataPath.close()
            drawPath(path = dataPath, color = color.copy(alpha = 0.22f))
            drawPath(path = dataPath, color = color, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            axes.forEachIndexed { index, axis ->
                drawCircle(color = color, radius = 3.4f, center = pointAt(index, axis.value.coerceIn(0.05f, 1f)))
            }
            // 轴标签
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 11.sp.toPx()
                this.color = labelColorArgb
                textAlign = android.graphics.Paint.Align.CENTER
            }
            axes.forEachIndexed { index, axis ->
                val angle = (-Math.PI / 2).toFloat() + angleStep * index
                val labelRadius = radius + 18.dp.toPx()
                val x = center.x + cos(angle) * labelRadius
                val y = center.y + sin(angle) * labelRadius + 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(axis.label, x, y, paint)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        axes.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { axis ->
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = axis.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
