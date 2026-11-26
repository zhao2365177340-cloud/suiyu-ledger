package com.suiyu.ledger.nav

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.ui.components.SyIcons
import com.suiyu.ledger.ui.components.syClickable
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.screens.entry.EntryScreen
import com.suiyu.ledger.ui.screens.family.FamilyScreen
import com.suiyu.ledger.ui.screens.home.HomeScreen
import com.suiyu.ledger.ui.screens.ledger.LedgerScreen
import com.suiyu.ledger.ui.screens.importer.ImportScreen
import com.suiyu.ledger.ui.screens.report.ReportScreen
import com.suiyu.ledger.ui.screens.stats.StatsScreen
import com.suiyu.ledger.ui.screens.tax.TaxScreen
import com.suiyu.ledger.ui.screens.wealth.WealthScreen
import com.suiyu.ledger.ui.theme.SyTheme

/**
 * 极简导航：4 个 Tab + 3 个全屏二级页（记一笔 / 导入 / 个税测算）。
 * 不引入 navigation-compose，用可保存的路由字符串 + 系统返回键即可覆盖全部场景，
 * 好处是转场由我们自己控制，触感与动画节奏统一。
 */
private enum class Tab(val label: String) {
    HOME("概览"),
    LEDGER("流水"),
    STATS("统计"),
    REPORT("报表"),
    FAMILY("家庭"),
}

private const val ROUTE_TABS = "tabs"
private const val ROUTE_IMPORT = "import"
private const val ROUTE_TAX = "tax"
private const val ROUTE_WEALTH = "wealth"

@Composable
fun SuiyuRoot() {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf(ROUTE_TABS) }
    val currentTab = Tab.entries[tabIndex.coerceIn(0, Tab.entries.lastIndex)]
    val overlay = route != ROUTE_TABS

    BackHandler(enabled = overlay) {
        Haptics.play(HapticEffect.Soft)
        route = ROUTE_TABS
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn(tween(190)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { tab ->
            when (tab) {
                Tab.HOME -> HomeScreen(
                    onOpenEntry = { id -> route = "entry:" + (id ?: "") },
                    onOpenImport = { route = ROUTE_IMPORT },
                    onOpenStats = { tabIndex = Tab.STATS.ordinal },
                    onOpenReport = { tabIndex = Tab.REPORT.ordinal },
                    onOpenTax = { route = ROUTE_TAX },
                    onOpenFamily = { tabIndex = Tab.FAMILY.ordinal },
                    onOpenLedger = { tabIndex = Tab.LEDGER.ordinal },
                    onOpenHealth = { route = ROUTE_WEALTH },
                )
                Tab.LEDGER -> LedgerScreen(
                    onOpenEntry = { id -> route = "entry:$id" },
                )
                Tab.STATS -> StatsScreen()
                Tab.REPORT -> ReportScreen(
                    onOpenTax = { route = ROUTE_TAX },
                    onOpenHealth = { route = ROUTE_WEALTH },
                )
                Tab.FAMILY -> FamilyScreen()
            }
        }

        if (!overlay) {
            BottomBar(
                current = currentTab,
                onSelect = { target ->
                    if (target.ordinal != tabIndex) {
                        tabIndex = target.ordinal
                    }
                },
                onAdd = { route = "entry:" },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        AnimatedVisibility(
            visible = overlay,
            enter = slideInVertically(animationSpec = tween(240)) { it / 6 } + fadeIn(tween(160)),
            exit = slideOutVertically(animationSpec = tween(180)) { it / 8 } + fadeOut(tween(120)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    route == ROUTE_IMPORT -> ImportScreen(
                        onBack = { route = ROUTE_TABS },
                        onImported = { count ->
                            route = ROUTE_TABS
                            if (count > 0) {
                                Toast.makeText(context, "已入账 $count 笔", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    route == ROUTE_TAX -> TaxScreen(onBack = { route = ROUTE_TABS })
                    route == ROUTE_WEALTH -> WealthScreen(onBack = { route = ROUTE_TABS })
                    route.startsWith("entry:") -> EntryScreen(
                        editId = route.removePrefix("entry:").ifBlank { null },
                        onBack = { route = ROUTE_TABS },
                        onSaved = { route = ROUTE_TABS },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    current: Tab,
    onSelect: (Tab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(12.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(Tab.HOME, Icons.Filled.Home, current, onSelect, Modifier.weight(1f))
            BarItem(Tab.LEDGER, SyIcons.List, current, onSelect, Modifier.weight(1f))
            Box(modifier = Modifier.weight(1.02f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .shadow(10.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .syClickable(effect = HapticEffect.Confirm, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "记一笔",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            BarItem(Tab.STATS, SyIcons.Chart, current, onSelect, Modifier.weight(0.96f))
            BarItem(Tab.REPORT, SyIcons.Report, current, onSelect, Modifier.weight(1f))
            BarItem(Tab.FAMILY, Icons.Filled.Person, current, onSelect, Modifier.weight(0.92f))
        }
    }
}

@Composable
private fun BarItem(
    tab: Tab,
    icon: ImageVector,
    current: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = tab == current
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .syClickable(effect = HapticEffect.Select) { onSelect(tab) }
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tab.label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
