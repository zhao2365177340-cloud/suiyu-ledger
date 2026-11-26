package com.suiyu.ledger.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyTheme
import kotlinx.coroutines.delay

/**
 * 品牌过渡页：系统启动页只显示图标，这里承担「报名字」的 1.4 秒。
 * 极简排版：将满之月 → 岁余 → 引文 → 落款；点击可跳过。
 */
@Composable
fun BrandSplash(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val xm = SyTheme.colors
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        Haptics.play(HapticEffect.Soft)
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        delay(950)
        onFinished()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(xm.heroSurface)
            .syClickable(effect = HapticEffect.Soft, onClick = onFinished),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(3.dp)
                    .background(xm.gold)
                    .graphicsLayer { alpha = reveal.value },
            )
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = "岁余",
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                letterSpacing = 10.sp,
                color = xm.heroContent,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .alpha(reveal.value)
                    .graphicsLayer {
                        translationY = (1f - reveal.value) * 18f
                    },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "S U Ì  Y Ú",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 4.sp,
                color = xm.heroContent.copy(alpha = 0.55f),
                modifier = Modifier.alpha(reveal.value * 0.9f),
            )
            Spacer(modifier = Modifier.height(46.dp))
            Text(
                text = "今吾日计之而不足，\n岁计之而有余。",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                color = xm.heroContent.copy(alpha = 0.9f),
                modifier = Modifier.alpha(reveal.value),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "——《庄子 · 庚桑楚》",
                style = MaterialTheme.typography.bodySmall,
                color = xm.heroContent.copy(alpha = 0.5f),
                modifier = Modifier.alpha(reveal.value * 0.9f),
            )
            Spacer(modifier = Modifier.height(56.dp))
            Text(
                text = "家庭账本 · 本地优先",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = xm.heroContent.copy(alpha = 0.38f),
                modifier = Modifier.alpha(reveal.value * 0.8f),
            )
        }
    }
}
