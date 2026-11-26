package com.suiyu.ledger.ui.screens.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suiyu.ledger.AppGraph
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ocr.BillOcr
import com.suiyu.ledger.ocr.BillParser
import com.suiyu.ledger.ocr.ParsedBill
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SyProgressBar
import com.suiyu.ledger.ui.components.SyScaffold
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.components.SyTextButton
import com.suiyu.ledger.ui.components.SyIcons
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyDim
import com.suiyu.ledger.ui.theme.SyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 账单导入工作台：
 *  ① 说明「截图只在本机识别」+ 选择截图 / 粘贴文本；
 *  ② 本机 OCR（ML Kit 中文）→ BillParser 解析，显示进度与触感；
 *  ③ 草稿复核：每张可折叠、字段可改、来源与置信度可见、疑似重复高亮；
 *  ④ 全选批量入账 + 记住用户纠正的分类；
 *  ⑤ 失败 / 空态提供「手动补录」出口。
 */
@Composable
fun ImportScreen(onBack: () -> Unit, onImported: (Int) -> Unit) {
    val context = LocalContext.current
    val ledger = AppGraph.ledger
    val categories by ledger.categories.collectAsState()
    val members by ledger.members.collectAsState()

    var stage by remember { mutableStateOf(ImportStage.INTRO) }
    var drafts by remember { mutableStateOf<List<ImportDraft>>(emptyList()) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var recognitionNotice by remember { mutableStateOf<String?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResultSummary?>(null) }
    var nextDraftKey by remember { mutableLongStateOf(0L) }
    var recognizingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // ML Kit 识别器常驻当前页面，离开时释放；重复进入导入页不会泄漏
    val ocr = remember { BillOcr(context) }
    DisposableEffect(Unit) {
        onDispose {
            recognizingJob?.cancel()
            ocr.close()
        }
    }

    fun updateDraft(next: ImportDraft) {
        drafts = drafts.map { if (it.key == next.key) next else it }
    }

    fun removeDraft(key: Long) {
        drafts = drafts.filterNot { it.key == key }
        if (drafts.isEmpty()) {
            stage = ImportStage.INTRO
            failureMessage = "草稿已全部移除，可以重新选择截图或手动补录"
        }
    }

    fun addBills(bills: List<ParsedBill>, notice: String? = null) {
        val unique = ImportDrafts.dedupeAcrossBatch(bills)
        if (unique.isEmpty()) {
            failureMessage = "没有找到可入账的账单：这批内容可能是重复账单或缺少金额与日期"
            recognitionNotice = null
            stage = ImportStage.INTRO
            Haptics.play(HapticEffect.Warning)
            return
        }
        val base = nextDraftKey
        val appended = ImportDrafts.fromBills(
            bills = unique,
            categories = ledger.categories.value,
            defaultMemberId = ledger.selfMember.id,
            keySeed = base,
        )
        nextDraftKey = base + appended.size
        drafts = drafts + appended
        failureMessage = null
        recognitionNotice = notice
        stage = ImportStage.REVIEW
        Haptics.play(HapticEffect.Success)
    }

    fun confirmImport() {
        val selected = drafts.filter { it.selected }
        if (selected.isEmpty()) return
        val records = selected.map { it.toRecord() }
        // 只记住「用户改过分类」的商户，避免把 OCR 猜测也写进规则
        val rules = selected.filter { it.ruleCandidate }.associate { it.merchant to it.categoryId }
        ledger.commitImport(records, rules)
        Haptics.play(HapticEffect.Wave)
        importResult = ImportResultSummary(
            count = records.size,
            expenseCount = selected.count { it.type == TxType.EXPENSE },
            incomeCount = selected.count { it.type == TxType.INCOME },
        )
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        recognizingJob?.cancel()
        recognizingJob = scope.launch {
            stage = ImportStage.RECOGNIZING
            progressDone = 0
            progressTotal = uris.size
            failureMessage = null
            recognitionNotice = null
            Haptics.play(HapticEffect.Select)
            val results = try {
                ocr.recognize(uris) { done, total ->
                    progressDone = done
                    progressTotal = total
                    // 每完成一张给一次轻触感，用户能感知「还在动」
                    Haptics.play(HapticEffect.Tick)
                }
            } catch (cancellation: CancellationException) {
                return@launch
            }
            val failed = results.count { it.failed }
            val readable = results.filter { !it.failed && it.text.isNotBlank() }
            if (readable.isEmpty()) {
                failureMessage = if (failed > 0) {
                    "${uris.size} 张截图里有 $failed 张识别失败，可换更清晰的截图重试"
                } else {
                    "截图里没有识别到文字，可换一张包含完整金额与日期的截图"
                }
                stage = ImportStage.INTRO
                Haptics.play(HapticEffect.Warning)
                return@launch
            }
            val catalog = ledger.categories.value
            val bills = withContext(Dispatchers.Default) {
                readable.flatMap { page -> BillParser.parse(page.text, catalog) }
            }
            val notice = when {
                failed > 0 -> "本次 ${uris.size} 张截图，其中 $failed 张未识别出文字"
                results.size > 1 -> "本次识别 ${uris.size} 张截图，已自动去掉重复账单"
                else -> null
            }
            if (bills.isEmpty()) {
                failureMessage = "识别到文字但没找到账单金额，可回首页手动补录，或换一张账单详情截图"
                stage = ImportStage.INTRO
                Haptics.play(HapticEffect.Warning)
            } else {
                addBills(bills, notice)
            }
        }
    }

    if (showPasteDialog) {
        PasteTextDialog(
            onDismiss = { showPasteDialog = false },
            onConfirm = { text ->
                showPasteDialog = false
                Haptics.play(HapticEffect.Confirm)
                scope.launch {
                    val catalog = ledger.categories.value
                    val bills = withContext(Dispatchers.Default) { BillParser.parse(text, catalog) }
                    if (bills.isEmpty()) {
                        failureMessage = "这段文字里没识别到账单，可能是暂不支持的格式，可回首页手动补录"
                        stage = ImportStage.INTRO
                        Haptics.play(HapticEffect.Warning)
                    } else {
                        addBills(bills)
                    }
                }
            },
        )
    }

    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = {
                importResult = null
                onImported(result.count)
            },
            title = {
                Text(
                    text = "已入账 ${result.count} 笔",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    Text(
                        text = "支出 ${result.expenseCount} 笔 · 收入 ${result.incomeCount} 笔",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "流水已写入本机账本，可在首页查看；若某些分类归错，可在记录详情里修改。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importResult = null
                        onImported(result.count)
                    },
                ) { Text("完成") }
            },
        )
    }

    when (stage) {
        ImportStage.INTRO -> IntroContent(
            failureMessage = failureMessage,
            categories = categories,
            onPickImages = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onPasteText = { showPasteDialog = true },
            onManualEntry = onBack,
            onBack = onBack,
        )

        ImportStage.RECOGNIZING -> RecognizingContent(
            done = progressDone,
            total = progressTotal,
            onCancel = {
                recognizingJob?.cancel()
                recognizingJob = null
                stage = ImportStage.INTRO
            },
        )

        ImportStage.REVIEW -> {
            val duplicates = remember(drafts) {
                drafts.associate { draft -> draft.key to ledger.findDuplicates(draft.toRecord()).size }
            }
            ReviewContent(
                drafts = drafts,
                categories = categories,
                members = members,
                duplicates = duplicates,
                notice = recognitionNotice,
                onDraftChange = ::updateDraft,
                onDraftRemove = ::removeDraft,
                onSelectAll = { drafts = drafts.map { it.copy(selected = true) } },
                onClearSelection = { drafts = drafts.map { it.copy(selected = false) } },
                onConfirm = ::confirmImport,
                onBack = onBack,
            )
        }
    }
}

private enum class ImportStage { INTRO, RECOGNIZING, REVIEW }

private data class ImportResultSummary(
    val count: Int,
    val expenseCount: Int,
    val incomeCount: Int,
)

// ────────────────────────────────────────────────────────────────
// ① 说明页：隐私 + 选择截图 / 粘贴文本 + 样例自测
// ────────────────────────────────────────────────────────────────

@Composable
private fun IntroContent(
    failureMessage: String?,
    categories: List<Category>,
    onPickImages: () -> Unit,
    onPasteText: () -> Unit,
    onManualEntry: () -> Unit,
    onBack: () -> Unit,
) {
    val xm = SyTheme.colors
    SyScaffold(
        title = "导入账单",
        subtitle = "截图与短信自动记账",
        onBack = onBack,
        scroll = true,
    ) {
        SyCard {
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "🔒", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "截图仅在本机识别，不上传",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "图片与识别文字只在这台设备的内存里处理：不联网、不上传、无广告。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (failureMessage != null) {
            Spacer(modifier = Modifier.height(SyDim.gapM))
            SyCard(color = xm.goldSoft.copy(alpha = 0.7f)) {
                Text(
                    text = "这次没有成功识别",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = failureMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    SyTextButton(text = "重新选截图", onClick = onPickImages)
                    Spacer(modifier = Modifier.width(4.dp))
                    SyTextButton(text = "手动补录", onClick = onManualEntry)
                }
            }
        }

        SySectionHeader(title = "第一步 · 选择来源")
        SyCard {
            SyButton(
                text = "选择账单截图",
                onClick = onPickImages,
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Icon(
                        imageVector = SyIcons.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            SyButton(
                text = "粘贴短信 / 账单文本",
                onClick = onPasteText,
                style = SyButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Icon(
                        imageVector = SyIcons.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "最多一次 9 张截图。识别后进入草稿复核，确认前不会写入账本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SySectionHeader(title = "识别范围")
        SyCard {
            val formats = listOf(
                "微信 · 账单详情 / 收支明细",
                "支付宝 · 账单详情 / 账单列表",
                "银行 App · 信用卡 / 储蓄卡明细",
                "银行短信 · 消费 / 工资入账",
                "退款、红包、转账收入",
            )
            formats.forEach { format ->
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = xm.gold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = format,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        SampleSelfTestSection(categories)

        SySectionHeader(title = "识别不理想？")
        SyCard {
            Text(
                text = "可以回到首页点「记一笔」手动补录；也建议换一张包含完整金额与日期的账单截图再试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SyButton(
                text = "返回首页手动记账",
                onClick = onManualEntry,
                style = SyButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                haptic = HapticEffect.Soft,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────
// ② 识别中：进度 + 可取消
// ────────────────────────────────────────────────────────────────

@Composable
private fun RecognizingContent(done: Int, total: Int, onCancel: () -> Unit) {
    val fraction = if (total > 0) done.toFloat() / total.toFloat() else 0f
    SyScaffold(
        title = "正在识别",
        subtitle = "本机 OCR · 不上传",
        onBack = onCancel,
        scroll = false,
    ) {
        Spacer(modifier = Modifier.height(46.dp))
        SyCard {
            Text(
                text = "正在识别 $done / $total 张截图",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SyProgressBar(progress = fraction)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "中文识别模型已内置，逐张处理中；识别完成后进入草稿复核。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        SyButton(
            text = "取消识别",
            onClick = onCancel,
            style = SyButtonStyle.Ghost,
            modifier = Modifier.fillMaxWidth(),
            haptic = HapticEffect.Soft,
        )
    }
}

// ────────────────────────────────────────────────────────────────
// ③④ 草稿复核
// ────────────────────────────────────────────────────────────────

@Composable
private fun ReviewContent(
    drafts: List<ImportDraft>,
    categories: List<Category>,
    members: List<Member>,
    duplicates: Map<Long, Int>,
    notice: String?,
    onDraftChange: (ImportDraft) -> Unit,
    onDraftRemove: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val selectedCount = drafts.count { it.selected }
    SyScaffold(
        title = "导入账单",
        subtitle = "第 2 步 · 核对草稿",
        onBack = onBack,
        scroll = false,
        bottomPadding = 0.dp,
    ) {
        ReviewHeader(
            drafts = drafts,
            onSelectAll = onSelectAll,
            onClearSelection = onClearSelection,
        )
        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(drafts, key = { it.key }) { draft ->
                DraftCard(
                    draft = draft,
                    categories = categories,
                    members = members,
                    duplicateCount = duplicates[draft.key] ?: 0,
                    onChange = onDraftChange,
                    onRemove = { onDraftRemove(draft.key) },
                )
            }
        }
        ImportBottomBar(
            selectedCount = selectedCount,
            totalCount = drafts.size,
            onConfirm = onConfirm,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

// ────────────────────────────────────────────────────────────────
// 粘贴文本弹窗
// ────────────────────────────────────────────────────────────────

@Composable
private fun PasteTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "粘贴账单文本", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    text = "把银行短信或账单页面的文字粘贴进来，同样只在本机解析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    placeholder = { Text("例如：【招商银行】您账户尾号1234于11月20日12:31向…消费25.00元") },
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) onConfirm(text)
                },
                enabled = text.isNotBlank(),
            ) { Text("开始识别") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
