package com.suiyu.ledger.ui.screens.family

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.analytics.Analytics
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ui.components.SyBottomSpacer
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.util.MonthKey
import com.suiyu.ledger.util.Share
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 家庭与设置 Tab。
 *
 * 该页面是「账本的家」：家庭成员、分类字典、偏好、备份与关于信息都在这里维护。
 * 数据全部来自 [AppGraph.ledger] 的 StateFlow，写入后由仓库负责防抖落盘，
 * 页面上不持有任何需要手动同步的副本，避免设置项与仓库状态漂移。
 *
 * 交互约定：
 *  - 危险操作（删除成员/分类、清空、重置、恢复）一律先二次确认；
 *  - 只要不是纯展示的元素，都通过 syClickable / SyButton / SyToggleRow 带上触感；
 *  - 备份恢复读文件与导出写文件都在 IO 协程里做，主线程只负责 Toast 与分享。
 */
@Composable
fun FamilyScreen() {
    val ledger = AppGraph.ledger
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val members by ledger.members.collectAsState()
    val categories by ledger.categories.collectAsState()
    val records by ledger.records.collectAsState()
    val settings by ledger.settings.collectAsState()

    // 家庭页固定看「本月」，与首页/报表的默认账期一致；月份切换留给首页与统计页
    val month = remember { MonthKey.current() }
    val summary = remember(records, month) { Analytics.summary(records, month) }
    val slices = remember(records, members, month) {
        Analytics.memberSlices(records, members, month).associateBy { it.memberId }
    }

    // ── 弹窗状态机：同一时刻最多只有一个 AlertDialog ──
    var showFamilyNameDialog by remember { mutableStateOf(false) }
    var addingMember by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<Member?>(null) }
    var deletingMember by remember { mutableStateOf<Member?>(null) }
    var addingCategoryType by remember { mutableStateOf<TxType?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var confirmingRestore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** 导出统一走这里：IO 生成文件 → 主线程拉起系统分享，失败时给出可理解的提示 */
    fun exportFile(
        label: String,
        mimeType: String,
        chooserTitle: String,
        builder: () -> File,
    ) {
        if (busy) return
        busy = true
        scope.launch {
            val file = runCatching { withContext(Dispatchers.IO) { builder() } }.getOrNull()
            busy = false
            if (file == null) {
                Haptics.play(HapticEffect.Error)
                toast("${label}生成失败，请稍后重试")
            } else {
                Haptics.play(HapticEffect.Success)
                runCatching { Share.shareFile(context, file, mimeType, chooserTitle) }
                    .onFailure { toast("没有可用的分享应用") }
                toast("已生成$label：${file.name}")
            }
        }
    }

    // 备份恢复：先选文件，再在 IO 协程里读取并交给仓库解析，绝不在主线程读大文件
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            toast("未选择备份文件")
        } else {
            busy = true
            scope.launch {
                val json = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                }.getOrNull()
                val payload = json?.takeIf { it.isNotBlank() }
                if (payload == null) {
                    busy = false
                    Haptics.play(HapticEffect.Error)
                    toast("恢复失败：无法读取备份文件")
                } else {
                    val restored = runCatching {
                        withContext(Dispatchers.IO) { ledger.restoreBackup(payload) }
                    }.getOrDefault(false)
                    busy = false
                    if (restored) {
                        Haptics.play(HapticEffect.Success)
                        toast("备份已恢复，数据已更新")
                    } else {
                        Haptics.play(HapticEffect.Error)
                        toast("恢复失败：文件不是有效的岁余备份")
                    }
                }
            }
        }
    }

    SyScaffold(title = "家庭", scroll = true) {
        Spacer(modifier = Modifier.height(SyDim.gapS))
        FamilyOverviewCard(
            familyName = settings.familyName,
            memberCount = members.size,
            expenseCents = summary.expenseCents,
            incomeCents = summary.incomeCents,
            masked = settings.privacyMask,
            onEditName = { showFamilyNameDialog = true },
        )
        MembersSection(
            members = members,
            slices = slices,
            totalExpenseCents = summary.expenseCents,
            masked = settings.privacyMask,
            onMemberClick = { editingMember = it },
            onAddMember = { addingMember = true },
        )
        CategoriesSection(
            categories = categories,
            onAddCategory = { addingCategoryType = it },
            onDeleteCategory = { deletingCategory = it },
        )
        PreferenceSection(
            settings = settings,
            onHapticsEnabled = { enabled ->
                ledger.setHapticsEnabled(enabled)
                Haptics.setEnabled(enabled)
                if (enabled) Haptics.play(HapticEffect.Toggle)
            },
            onHapticStrength = { index ->
                // 先落设置与全局强度，再播放一次「确认」触感，让用户直接感受档位差异
                ledger.setHapticStrength(index + 1)
                Haptics.setStrength(index + 1)
                Haptics.play(HapticEffect.Confirm)
            },
            onPrivacyMask = { masked -> ledger.setPrivacyMask(masked) },
            onThemeMode = { mode -> ledger.setThemeMode(mode) },
        )
        DataSection(
            recordCount = records.size,
            seededDemo = settings.seededDemo,
            busy = busy,
            onExportCsv = {
                exportFile(
                    label = "CSV",
                    mimeType = "text/csv",
                    chooserTitle = "导出明细",
                    builder = { ledger.exportCsv() },
                )
            },
            onExportBackup = {
                exportFile(
                    label = "备份",
                    mimeType = "application/json",
                    chooserTitle = "导出备份",
                    builder = { ledger.exportBackup() },
                )
            },
            onRestore = { confirmingRestore = true },
            onClearRecords = { confirmingClear = true },
            onResetDemo = { confirmingReset = true },
        )
        AboutSection()
        SyBottomSpacer()
    }

    if (showFamilyNameDialog) {
        FamilyNameDialog(
            initial = settings.familyName,
            onDismiss = { showFamilyNameDialog = false },
            onConfirm = { name ->
                ledger.setFamilyName(name)
                showFamilyNameDialog = false
                toast("家庭名称已更新")
            },
        )
    }

    if (addingMember) {
        MemberEditorDialog(
            initial = null,
            onDismiss = { addingMember = false },
            onSave = { name, emoji, colorHex, role ->
                ledger.addMember(name = name, emoji = emoji, colorHex = colorHex, role = role)
                addingMember = false
                toast("已添加成员「$name」")
            },
        )
    }

    editingMember?.let { target ->
        // 删除入口需要先关掉编辑弹窗，再打开确认弹窗，避免弹窗叠弹窗
        val deleteAction: (() -> Unit)? = if (target.isSelf) {
            null
        } else {
            {
                editingMember = null
                deletingMember = target
            }
        }
        MemberEditorDialog(
            initial = target,
            onDismiss = { editingMember = null },
            onSave = { name, emoji, colorHex, role ->
                ledger.updateMember(
                    target.copy(name = name, emoji = emoji, colorHex = colorHex, role = role),
                )
                editingMember = null
                toast("已保存成员「$name」")
            },
            onDelete = deleteAction,
        )
    }

    deletingMember?.let { target ->
        FamilyConfirmDialog(
            title = "删除成员「${target.name}」？",
            message = "该成员名下的流水会自动归到「我」，此操作无法撤销。",
            confirmText = "删除",
            onDismiss = { deletingMember = null },
            onConfirm = {
                ledger.deleteMember(target.id)
                deletingMember = null
                toast("已删除成员「${target.name}」")
            },
        )
    }

    addingCategoryType?.let { type ->
        CategoryEditorDialog(
            initialType = type,
            onDismiss = { addingCategoryType = null },
            onSave = { name, emoji, chosenType, colorHex ->
                ledger.addCategory(name = name, emoji = emoji, type = chosenType, colorHex = colorHex)
                addingCategoryType = null
                toast("已添加分类「$name」")
            },
        )
    }

    deletingCategory?.let { target ->
        val fallbackName = if (target.type == TxType.EXPENSE) "其他" else "其他收入"
        FamilyConfirmDialog(
            title = "删除分类「${target.name}」？",
            message = "该分类下的流水将自动归入「$fallbackName」，此操作无法撤销。",
            confirmText = "删除",
            onDismiss = { deletingCategory = null },
            onConfirm = {
                ledger.deleteCategory(target.id)
                deletingCategory = null
                toast("已删除分类「${target.name}」")
            },
        )
    }

    if (confirmingClear) {
        FamilyConfirmDialog(
            title = "清空全部流水？",
            message = "将删除当前的 ${records.size} 笔流水，成员与自定义分类会保留。此操作无法撤销，建议先导出备份。",
            confirmText = "清空",
            onDismiss = { confirmingClear = false },
            onConfirm = {
                ledger.clearRecords()
                confirmingClear = false
                toast("流水已清空")
            },
        )
    }

    if (confirmingReset) {
        FamilyConfirmDialog(
            title = "重置为示例数据？",
            message = "将清空当前全部数据（流水、成员、分类与设置），并写入一份示例账本。此操作无法撤销，建议先导出备份。",
            confirmText = "重置",
            onDismiss = { confirmingReset = false },
            onConfirm = {
                ledger.resetAllWithDemo()
                confirmingReset = false
                toast("已重置为示例数据")
            },
        )
    }

    if (confirmingRestore) {
        FamilyConfirmDialog(
            title = "从备份恢复？",
            message = "恢复会覆盖当前全部数据（流水、成员、分类与设置），且无法撤销。建议先导出当前备份。",
            confirmText = "选择备份文件",
            onDismiss = { confirmingRestore = false },
            onConfirm = {
                confirmingRestore = false
                runCatching {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }.onFailure { toast("无法打开文件选择器") }
            },
        )
    }
}
