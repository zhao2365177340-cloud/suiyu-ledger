package com.suiyu.ledger.ui.screens.wealth

import android.widget.Toast
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.analytics.FinancialHealth
import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.util.Money
import com.suiyu.ledger.util.Share

/**
 * 财务体检（v1.2）：资产 / 负债 / 工作与家庭情况 → 六维风险评分 → 可分享体检报告。
 * 入口：首页快捷入口与净资产卡、报表页财务体检摘要、家庭页财务资料。
 */
@Composable
fun WealthScreen(onBack: () -> Unit) {
    val ledger = AppGraph.ledger
    val context = LocalContext.current
    val records by ledger.records.collectAsState()
    val assets by ledger.assets.collectAsState()
    val liabilities by ledger.liabilities.collectAsState()
    val profile by ledger.profile.collectAsState()
    val settings by ledger.settings.collectAsState()
    val masked = settings.privacyMask

    val report = remember(records, assets, liabilities, profile) {
        FinancialHealth.evaluate(
            records = records,
            assets = assets,
            liabilities = liabilities,
            profile = profile,
        )
    }

    var assetEditorOpen by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<Asset?>(null) }
    var deletingAsset by remember { mutableStateOf<Asset?>(null) }
    var liabilityEditorOpen by remember { mutableStateOf(false) }
    var editingLiability by remember { mutableStateOf<Liability?>(null) }
    var deletingLiability by remember { mutableStateOf<Liability?>(null) }
    var profileEditorOpen by remember { mutableStateOf(false) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    SyScaffold(
        title = "财务体检",
        subtitle = "资产 · 现金流 · 风险",
        onBack = onBack,
        actions = {
            SyTextButton(
                text = "分享",
                onClick = {
                    Haptics.play(HapticEffect.Soft)
                    Share.shareText(
                        context,
                        FinancialHealth.toShareText(report, settings.familyName),
                        subject = "岁余 · 家庭财务体检",
                    )
                },
            )
        },
        scroll = true,
        bottomPadding = 32.dp,
    ) {
        HealthHero(report = report, masked = masked)

        if (!report.hasAnyData) {
            Spacer(modifier = Modifier.height(SyDim.gapM))
            EmptyHealthHint(
                onAddAsset = {
                    editingAsset = null
                    assetEditorOpen = true
                },
                onEditProfile = { profileEditorOpen = true },
            )
        }

        SySectionHeader(
            title = "现金流基线",
            subtitle = "把固定收入与固定支出放在同一张表里",
        )
        BaselineCard(
            cashflow = report.cashflow,
            masked = masked,
            onEditProfile = { profileEditorOpen = true },
        )

        SySectionHeader(
            title = "风险评分",
            subtitle = "六维评分 · 综合 ${report.score} 分 · ${report.grade.label}",
        )
        RadarCard(report = report)
        Spacer(modifier = Modifier.height(SyDim.gapM))
        MetricsCard(report = report, masked = masked)

        SySectionHeader(
            title = "资产结构",
            subtitle = if (report.totalAssetsCents > 0L) {
                "总资产 " + (if (masked) "¥ ••••" else Money.format(report.totalAssetsCents))
            } else {
                "录入资产后自动生成配置图"
            },
        )
        AllocationCard(report = report, masked = masked)

        SySectionHeader(
            title = "资产清单",
            subtitle = "存款、金融资产、房产、车辆与其他资产",
            trailing = "＋ 添加",
            onTrailingClick = {
                editingAsset = null
                assetEditorOpen = true
            },
        )
        AssetsSection(
            assets = assets,
            masked = masked,
            onAdd = {
                editingAsset = null
                assetEditorOpen = true
            },
            onEdit = { asset ->
                editingAsset = asset
                assetEditorOpen = true
            },
        )

        SySectionHeader(
            title = "负债清单",
            subtitle = "房贷、车贷、消费贷、信用卡与借款",
            trailing = "＋ 添加",
            onTrailingClick = {
                editingLiability = null
                liabilityEditorOpen = true
            },
        )
        LiabilitiesSection(
            liabilities = liabilities,
            masked = masked,
            onAdd = {
                editingLiability = null
                liabilityEditorOpen = true
            },
            onEdit = { liability ->
                editingLiability = liability
                liabilityEditorOpen = true
            },
        )

        SySectionHeader(
            title = "工作与家庭情况",
            subtitle = "影响收入稳定性、家庭负担与保障评分",
        )
        ProfileCard(profile = profile, onEdit = { profileEditorOpen = true })

        SySectionHeader(
            title = "体检建议",
            subtitle = "按薄弱维度排序，先做最重要的一件事",
        )
        AdviceCard(advice = report.advice)

        Spacer(modifier = Modifier.height(SyDim.gapL))
        Box(modifier = Modifier.fillMaxWidth()) {
            SyButton(
                text = "分享家庭体检报告",
                onClick = {
                    Haptics.play(HapticEffect.Confirm)
                    Share.shareText(
                        context,
                        FinancialHealth.toShareText(report, settings.familyName),
                        subject = "岁余 · 家庭财务体检",
                    )
                },
                style = SyButtonStyle.Secondary,
            )
        }
        Spacer(modifier = Modifier.height(SyDim.gapS))
        Text(
            text = "财务体检基于你自己填写的资产、负债与近 ${FinancialHealth.WINDOW_MONTHS} 个月流水估算，仅供参考，不构成投资建议；" +
                "资产估值请按当前可变现价值填写。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    if (assetEditorOpen) {
        AssetEditorDialog(
            initial = editingAsset,
            onDismiss = {
                assetEditorOpen = false
                editingAsset = null
            },
            onSave = { name, category, valueCents, note ->
                val current = editingAsset
                if (current == null) {
                    ledger.addAsset(name, category, valueCents, note)
                    toast("已添加资产")
                } else {
                    ledger.updateAsset(
                        current.copy(
                            name = name,
                            category = category,
                            valueCents = valueCents,
                            note = note,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    toast("已更新资产")
                }
                Haptics.play(HapticEffect.Success)
                assetEditorOpen = false
                editingAsset = null
            },
            onDelete = editingAsset?.let { asset ->
                {
                    assetEditorOpen = false
                    editingAsset = null
                    deletingAsset = asset
                }
            },
        )
    }

    if (liabilityEditorOpen) {
        LiabilityEditorDialog(
            initial = editingLiability,
            onDismiss = {
                liabilityEditorOpen = false
                editingLiability = null
            },
            onSave = { name, category, balanceCents, monthlyCents, note ->
                val current = editingLiability
                if (current == null) {
                    ledger.addLiability(name, category, balanceCents, monthlyCents, note)
                    toast("已添加负债")
                } else {
                    ledger.updateLiability(
                        current.copy(
                            name = name,
                            category = category,
                            balanceCents = balanceCents,
                            monthlyPaymentCents = monthlyCents,
                            note = note,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    toast("已更新负债")
                }
                Haptics.play(HapticEffect.Success)
                liabilityEditorOpen = false
                editingLiability = null
            },
            onDelete = editingLiability?.let { liability ->
                {
                    liabilityEditorOpen = false
                    editingLiability = null
                    deletingLiability = liability
                }
            },
        )
    }

    if (profileEditorOpen) {
        ProfileEditorDialog(
            profile = profile,
            onDismiss = { profileEditorOpen = false },
            onSave = { updated ->
                ledger.updateProfile(updated)
                profileEditorOpen = false
                Haptics.play(HapticEffect.Success)
                toast("已保存工作与家庭情况")
            },
        )
    }

    deletingAsset?.let { asset ->
        WealthConfirmDialog(
            title = "删除资产「${asset.name}」？",
            message = "删除后资产总额与风险评分会立即更新，此操作无法撤销。",
            onDismiss = { deletingAsset = null },
            onConfirm = {
                ledger.deleteAsset(asset.id)
                deletingAsset = null
                Haptics.play(HapticEffect.Success)
                toast("已删除资产")
            },
        )
    }

    deletingLiability?.let { liability ->
        WealthConfirmDialog(
            title = "删除负债「${liability.name}」？",
            message = "删除后负债与月供收入比会立即更新，此操作无法撤销。",
            onDismiss = { deletingLiability = null },
            onConfirm = {
                ledger.deleteLiability(liability.id)
                deletingLiability = null
                Haptics.play(HapticEffect.Success)
                toast("已删除负债")
            },
        )
    }
}
