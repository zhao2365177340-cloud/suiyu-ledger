package com.suiyu.ledger.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.analytics.MonthSummary
import com.suiyu.ledger.analytics.Narrative
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyBottomSpacer
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyDivider
import com.suiyu.ledger.ui.components.SyEmpty
import com.suiyu.ledger.ui.components.SyMonthSwitcher
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.theme.SyTheme
import com.suiyu.ledger.util.MonthKey
import com.suiyu.ledger.util.Share

/**
 * 报表页：把本月流水整理成一张可读的家庭财务报表。
 *
 * 阅读顺序刻意模仿纸质月报：
 * 抬头与总述 → 总览 → 结构 → 环比 → 同比 → 成员分账 → 财务健康 → 自动洞察 → 分享 / 个税测算。
 * 所有数据来自 [Analytics] 与 [Narrative]，本页只负责排版、差值徽标与展开交互。
 */
@Composable
fun ReportScreen(
    onOpenTax: () -> Unit,
    onOpenHealth: () -> Unit,
) {
    val ledger = AppGraph.ledger
    val records by ledger.records.collectAsState()
    val members by ledger.members.collectAsState()
    val categories by ledger.categories.collectAsState()
    val settings by ledger.settings.collectAsState()
    val assets by ledger.assets.collectAsState()
    val liabilities by ledger.liabilities.collectAsState()
    val profile by ledger.profile.collectAsState()
    val context = LocalContext.current

    var month by remember { mutableStateOf(MonthKey.current()) }
    var expandedMemberId by remember { mutableStateOf<String?>(null) }

    val masked = settings.privacyMask
    val familyName = settings.familyName
    val comparison = remember(records, month) { Analytics.comparison(records, month) }
    val summary = comparison.current
    val categorySlices = remember(records, categories, month) {
        Analytics.categorySlices(records, categories, month, TxType.EXPENSE)
    }
    val memberSlices = remember(records, members, month) {
        Analytics.memberSlices(records, members, month)
    }
    val changes = remember(records, categories, month) {
        Analytics.categoryChanges(records, categories, month)
    }
    val health = remember(records, categories, month) {
        Analytics.healthMetrics(records, categories, month)
    }
    val overview = remember(comparison, familyName) {
        Narrative.overview(comparison, familyName)
    }
    val insights = remember(categorySlices, changes, memberSlices, health, summary) {
        Narrative.categoryInsights(categorySlices, changes) +
            Narrative.memberInsights(memberSlices, summary) +
            Narrative.healthInsights(health, summary)
    }
    // 分享文本与屏幕上的数字共用同一份 Analytics 结果，避免「看到的」和「分享出去的」不一致
    val reportText = remember(comparison, categorySlices, memberSlices, health, familyName) {
        Narrative.reportText(comparison, categorySlices, memberSlices, health, familyName)
    }
    val shareReport: () -> Unit = { Share.shareText(context, reportText) }
    val healthReport = remember(records, assets, liabilities, profile) {
        com.suiyu.ledger.analytics.FinancialHealth.evaluate(records, assets, liabilities, profile)
    }

    SyScaffold(
        scroll = true,
        title = "报表",
        actions = {
            SyTextButton(text = "分享", onClick = shareReport)
        },
    ) {
        // ── 账期 ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyMonthSwitcher(
                month = month,
                onMonthChange = {
                    month = it
                    expandedMemberId = null
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (month.isCurrent()) "本月" else month.short(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ── 抬头 + 总述（像纸质月报的页首）──────────────
        ReportHeader(
            familyName = familyName,
            month = month,
            summary = summary,
            overview = overview,
        )

        if (summary.isEmpty) {
            SyEmpty(
                emoji = "🧾",
                title = "${month.label()}还没有账目",
                description = "报表由本机流水自动生成。到首页点「导入账单」，导入账单一键开始：识别一张微信 / 支付宝 / 银行账单截图，或手动记一笔，约 30 秒就能得到第一份月报。",
            )
            ReportActions(onShare = shareReport, onOpenTax = onOpenTax)
        } else {
            // ── 总览四宫格 ─────────────────────────────
            SySectionHeader(
                title = "总览",
                subtitle = "收入、支出、结余与结余率；环比、同比见徽标",
            )
            OverviewGrid(comparison = comparison, masked = masked)

            // ── 结构 ──────────────────────────────────
            StructureBlock(slices = categorySlices, masked = masked)

            // ── 环比 ──────────────────────────────────
            MomBlock(changes = changes, masked = masked)

            // ── 同比 ──────────────────────────────────
            YoyBlock(comparison = comparison, masked = masked)

            // ── 成员分账 ───────────────────────────────
            MembersBlock(
                memberSlices = memberSlices,
                members = members,
                records = records,
                categories = categories,
                month = month,
                expandedMemberId = expandedMemberId,
                onToggle = { id ->
                    expandedMemberId = if (expandedMemberId == id) null else id
                },
                masked = masked,
            )

            // ── 财务健康 ───────────────────────────────
            HealthBlock(
                health = health,
                summary = summary,
                categories = categories,
                masked = masked,
            )

            // ── 家庭财务体检（v1.2）───────────────────
            SySectionHeader(
                title = "家庭财务体检",
                subtitle = "资产、负债与固定收支一起看，才是完整的家庭财务",
            )
            WealthSummaryBlock(
                report = healthReport,
                masked = masked,
                onOpenHealth = onOpenHealth,
            )

            // ── 自动洞察 ───────────────────────────────
            InsightsBlock(insights = insights)

            // ── 分享 / 个税测算 ────────────────────────
            ReportActions(onShare = shareReport, onOpenTax = onOpenTax)
        }

        SyBottomSpacer()
    }
}

/** 报表抬头：家庭名、账期、双细线、总述段落 —— 用留白与细线建立「一张纸」的阅读节奏 */
@Composable
private fun ReportHeader(
    familyName: String,
    month: MonthKey,
    summary: MonthSummary,
    overview: String,
) {
    val xm = SyTheme.colors
    SyCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
        Text(
            text = "岁余 · 家庭财务报表",
            style = MaterialTheme.typography.labelMedium,
            color = xm.gold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${familyName} · 月度财务报告",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${month.label()} · 共 ${summary.recordCount} 笔收支",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 金色细线 + 常规细线：月报抬头常见的双线分隔
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.2.dp)
                .background(xm.gold.copy(alpha = 0.55f)),
        )
        Spacer(modifier = Modifier.height(3.dp))
        SyDivider()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = overview,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自动生成 · 仅供参考",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "数据不出设备",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 报表页的财务体检摘要：不重复整页体检，只回答「家里现在安不安全」。
 * 点按进入完整体检，录入资产 / 负债 / 工作信息。
 */
@Composable
private fun WealthSummaryBlock(
    report: com.suiyu.ledger.analytics.FinancialHealthReport,
    masked: Boolean,
    onOpenHealth: () -> Unit,
) {
    val xm = SyTheme.colors
    val gradeColor = when (report.grade) {
        com.suiyu.ledger.analytics.RiskGrade.EXCELLENT,
        com.suiyu.ledger.analytics.RiskGrade.HEALTHY -> xm.income
        com.suiyu.ledger.analytics.RiskGrade.WATCH -> xm.gold
        com.suiyu.ledger.analytics.RiskGrade.RISK -> xm.expense
    }
    SyCard(onClick = onOpenHealth, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "家庭净资产",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (masked) "¥ ••••" else com.suiyu.ledger.util.Money.format(report.netWorthCents),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(gradeColor.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "风险 ${report.grade.label} · ${report.score} 分",
                    style = MaterialTheme.typography.labelMedium,
                    color = gradeColor,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            ReportHealthStat(
                label = "应急储备金",
                value = when {
                    report.emergencyMonths >= 1.0 -> String.format(java.util.Locale.CHINA, "%.1f 个月", report.emergencyMonths)
                    else -> "待补充"
                },
                modifier = Modifier.weight(1f),
            )
            ReportHealthStat(
                label = "资产负债率",
                value = if (report.totalAssetsCents > 0L || report.totalLiabilitiesCents > 0L) {
                    com.suiyu.ledger.util.Money.percent(report.debtRatio, false)
                } else {
                    "—"
                },
                modifier = Modifier.weight(1f),
            )
            ReportHealthStat(
                label = "月供收入比",
                value = report.debtServiceRatio?.let { com.suiyu.ledger.util.Money.percent(it, false) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "查看完整财务体检 →（录入存款、金融资产、房产、负债与工作情况）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReportHealthStat(label: String, value: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
