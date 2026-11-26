package com.suiyu.ledger.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 岁余自绘线性图标：统一 1.8 笔宽、圆头端点，与衬线数字一起构成「账簿」气质。
 * 只画 Material Icons 里没有的几枚，其余用系统 material-icons-core。
 */
object SyIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 1.8f, block: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = PathFillType.NonZero,
        ) { block(this) }
    }

    private fun ImageVector.Builder.fill(block: PathBuilder.() -> Unit) {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) { block(this) }
    }

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float) {
        val r = radius.coerceAtMost(minOf(right - left, bottom - top) / 2f)
        moveTo(left + r, top)
        lineTo(right - r, top)
        quadTo(right, top, right, top + r)
        lineTo(right, bottom - r)
        quadTo(right, bottom, right - r, bottom)
        lineTo(left + r, bottom)
        quadTo(left, bottom, left, bottom - r)
        lineTo(left, top + r)
        quadTo(left, top, left + r, top)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
        val k = 0.5523f * radius
        moveTo(cx, cy - radius)
        curveTo(cx + k, cy - radius, cx + radius, cy - k, cx + radius, cy)
        curveTo(cx + radius, cy + k, cx + k, cy + radius, cx, cy + radius)
        curveTo(cx - k, cy + radius, cx - radius, cy + k, cx - radius, cy)
        curveTo(cx - radius, cy - k, cx - k, cy - radius, cx, cy - radius)
        close()
    }

    /** 统计：三根高低柱 */
    val Chart: ImageVector = icon("Chart") {
        stroke(1.6f) {
            moveTo(4.5f, 19.5f)
            lineTo(19.5f, 19.5f)
        }
        fill { roundRect(5.6f, 10f, 8.4f, 17.6f, 1.2f) }
        fill { roundRect(10.6f, 5.5f, 13.4f, 17.6f, 1.2f) }
        fill { roundRect(15.6f, 12.8f, 18.4f, 17.6f, 1.2f) }
    }

    /** 报表：带横线的纸张 */
    val Report: ImageVector = icon("Report") {
        stroke {
            roundRect(5f, 3.5f, 19f, 20.5f, 2.4f)
            moveTo(8.4f, 8.2f)
            lineTo(15.6f, 8.2f)
            moveTo(8.4f, 12f)
            lineTo(15.6f, 12f)
            moveTo(8.4f, 15.8f)
            lineTo(12.6f, 15.8f)
        }
    }

    /** 税务：百分号 */
    val Tax: ImageVector = icon("Tax") {
        stroke {
            moveTo(8.2f, 15.8f)
            lineTo(15.8f, 8.2f)
        }
        stroke { circle(7.2f, 7.2f, 2.0f) }
        stroke { circle(16.8f, 16.8f, 2.0f) }
    }

    /** 导入：箭头落入托盘 */
    val Import: ImageVector = icon("Import") {
        stroke {
            moveTo(12f, 3.8f)
            lineTo(12f, 14.2f)
            moveTo(8.2f, 10.6f)
            lineTo(12f, 14.4f)
            lineTo(15.8f, 10.6f)
        }
        stroke {
            moveTo(4.6f, 15.4f)
            lineTo(4.6f, 19.4f)
            lineTo(19.4f, 19.4f)
            lineTo(19.4f, 15.4f)
        }
    }

    /** 相机：导入账单截图 */
    val Camera: ImageVector = icon("Camera") {
        stroke {
            roundRect(3.4f, 6.8f, 20.6f, 20.2f, 3.4f)
            moveTo(8.6f, 6.8f)
            lineTo(10.2f, 4.2f)
            lineTo(13.8f, 4.2f)
            lineTo(15.4f, 6.8f)
        }
        stroke { circle(12f, 13.5f, 2.6f) }
    }

    /** 钱包 */
    val Wallet: ImageVector = icon("Wallet") {
        stroke {
            roundRect(3.4f, 6f, 20.6f, 19f, 3f)
            moveTo(20.4f, 10.4f)
            lineTo(15.4f, 10.4f)
        }
        fill { circle(16.2f, 12.4f, 1.0f) }
    }

    /** 趋势上扬 */
    val Trend: ImageVector = icon("Trend") {
        stroke {
            moveTo(4.2f, 16.6f)
            lineTo(9.4f, 11.4f)
            lineTo(13.2f, 15.2f)
            lineTo(19.8f, 7.4f)
            moveTo(15.4f, 7.4f)
            lineTo(19.8f, 7.4f)
            lineTo(19.8f, 11.8f)
        }
    }

    /** 四角星：智能分析 */
    val Sparkle: ImageVector = icon("Sparkle") {
        fill {
            moveTo(12f, 3.6f)
            lineTo(13.9f, 9.2f)
            lineTo(19.6f, 11.1f)
            lineTo(13.9f, 13f)
            lineTo(12f, 18.6f)
            lineTo(10.1f, 13f)
            lineTo(4.4f, 11.1f)
            lineTo(10.1f, 9.2f)
            close()
        }
    }

    /** 日历 */
    val Calendar: ImageVector = icon("Calendar") {
        stroke {
            roundRect(4f, 5.6f, 20f, 20f, 2.6f)
            moveTo(4f, 10.2f)
            lineTo(20f, 10.2f)
            moveTo(8.6f, 3.4f)
            lineTo(8.6f, 7.4f)
            moveTo(15.4f, 3.4f)
            lineTo(15.4f, 7.4f)
        }
    }

    /** 设置：三条滑杆 */
    val Settings: ImageVector = icon("Settings") {
        stroke(1.6f) {
            moveTo(4.4f, 7.4f)
            lineTo(19.6f, 7.4f)
            moveTo(4.4f, 12f)
            lineTo(19.6f, 12f)
            moveTo(4.4f, 16.6f)
            lineTo(19.6f, 16.6f)
        }
        fill {
            circle(10.4f, 7.4f, 2.1f)
            circle(15.6f, 12f, 2.1f)
            circle(8.2f, 16.6f, 2.1f)
        }
    }

    /** 盾牌：财务体检 / 风险防护 */
    val Shield: ImageVector = icon("Shield") {
        stroke {
            moveTo(12f, 3.2f)
            lineTo(19.6f, 6.2f)
            lineTo(19.6f, 11.6f)
            curveTo(19.6f, 16.4f, 16.4f, 19.6f, 12f, 20.8f)
            curveTo(7.6f, 19.6f, 4.4f, 16.4f, 4.4f, 11.6f)
            lineTo(4.4f, 6.2f)
            close()
            moveTo(9.2f, 11.8f)
            lineTo(11.2f, 13.8f)
            lineTo(14.9f, 9.9f)
        }
    }

    /** 清单 */
    val List: ImageVector = icon("List") {
        stroke(1.6f) {
            moveTo(9f, 6.5f)
            lineTo(19.6f, 6.5f)
            moveTo(9f, 12f)
            lineTo(19.6f, 12f)
            moveTo(9f, 17.5f)
            lineTo(19.6f, 17.5f)
        }
        fill {
            circle(5.5f, 6.5f, 1.1f)
            circle(5.5f, 12f, 1.1f)
            circle(5.5f, 17.5f, 1.1f)
        }
    }
}
