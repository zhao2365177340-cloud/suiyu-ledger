package com.suiyu.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.suiyu.ledger.nav.SuiyuRoot
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.screens.splash.BrandSplash
import com.suiyu.ledger.ui.theme.SuiyuTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val firstLaunch = savedInstanceState == null
        setContent {
            val settings by AppGraph.ledger.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            // 触感开关与强度跟随设置实时生效
            LaunchedEffect(settings.hapticsEnabled, settings.hapticStrength) {
                Haptics.setEnabled(settings.hapticsEnabled)
                Haptics.setStrength(settings.hapticStrength)
            }
            // 深色 / 浅色模式下系统栏图标颜色
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            SuiyuTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SuiyuRoot()
                        var showSplash by rememberSaveable { mutableStateOf(firstLaunch) }
                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(animationSpec = tween(320)),
                        ) {
                            BrandSplash(onFinished = { showSplash = false })
                        }
                    }
                }
            }
        }
    }
}
