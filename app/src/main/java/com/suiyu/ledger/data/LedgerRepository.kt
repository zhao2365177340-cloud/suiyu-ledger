package com.suiyu.ledger.data

import android.content.Context
import com.suiyu.ledger.model.AppSettings
import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.AssetCategory
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.LedgerData
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.model.LiabilityCategory
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.MonthKey
import com.suiyu.ledger.util.newId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/**
 * 账本仓库：唯一写入口。所有界面通过它读写，写入后由 File 落盘（本地优先，不上传任何服务器）。
 *
 * 并发策略：内存态用 StateFlow 立即更新保证 UI 跟手；落盘做 80ms 防抖 + Mutex 串行，
 * 避免连续记账时频繁写文件。
 */
class LedgerRepository(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataFile = File(appContext.filesDir, "ledger.json")
    private val writeMutex = Mutex()
    private var persistJob: Job? = null

    private val _data = MutableStateFlow(loadWithSeed())

    val data: StateFlow<LedgerData> = _data.asStateFlow()

    val members: StateFlow<List<Member>> =
        _data.map { it.members.sortedBy { member -> member.order } }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.members)

    val categories: StateFlow<List<Category>> =
        _data.map { it.categories }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.categories)

    val records: StateFlow<List<TxRecord>> =
        _data.map { it.records }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.records)

    val assets: StateFlow<List<Asset>> =
        _data.map { it.assets }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.assets)

    val liabilities: StateFlow<List<Liability>> =
        _data.map { it.liabilities }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.liabilities)

    val profile: StateFlow<FinancialProfile> =
        _data.map { it.profile }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.profile)

    val settings: StateFlow<AppSettings> =
        _data.map { it.settings }
            .stateIn(scope, SharingStarted.Eagerly, _data.value.settings)

    val selfMember: Member
        get() = _data.value.members.firstOrNull { it.isSelf }
            ?: _data.value.members.firstOrNull()
            ?: SeedData.members.first()

    fun memberById(id: String?): Member? = _data.value.members.firstOrNull { it.id == id }

    fun categoryById(id: String?): Category? = _data.value.categories.firstOrNull { it.id == id }

    fun categoryName(id: String?): String = categoryById(id)?.name ?: "未分类"

    fun memberName(id: String?): String = memberById(id)?.name ?: "成员"

    // ────────────────────────── 流水 ──────────────────────────

    fun addRecords(records: List<TxRecord>) {
        if (records.isEmpty()) return
        mutate { current ->
            current.copy(records = (current.records + records).sortedByDescending { it.occurredAt })
        }
    }

    fun upsertRecord(record: TxRecord) {
        mutate { current ->
            val exists = current.records.any { it.id == record.id }
            val next = if (exists) {
                current.records.map { if (it.id == record.id) record else it }
            } else {
                current.records + record
            }
            current.copy(records = next.sortedByDescending { it.occurredAt })
        }
    }

    fun deleteRecord(id: String) {
        mutate { current -> current.copy(records = current.records.filterNot { it.id == id }) }
    }

    /** 批量删除（明细页多选 / 全选删除） */
    fun deleteRecords(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val target = ids.toSet()
        mutate { current -> current.copy(records = current.records.filterNot { it.id in target }) }
    }

    fun recordById(id: String): TxRecord? = _data.value.records.firstOrNull { it.id == id }

    /**
     * 疑似重复检测：同类型、同金额、3 天内，且商户/备注相近。
     * 截图导入时用于避免同一笔账单被重复导入。
     */
    fun findDuplicates(candidate: TxRecord): List<TxRecord> {
        val candidateKey = normalizeText(candidate.merchant + candidate.note)
        return _data.value.records.filter { existing ->
            existing.type == candidate.type &&
                existing.amountCents == candidate.amountCents &&
                abs(existing.occurredAt - candidate.occurredAt) <= 3 * 86_400_000L &&
                (
                    candidateKey.isEmpty() ||
                        normalizeText(existing.merchant + existing.note).let { key ->
                            key.isEmpty() || key.contains(candidateKey) || candidateKey.contains(key)
                        }
                    )
        }
    }

    private fun normalizeText(raw: String): String =
        raw.replace(" ", "").replace("·", "").replace("-", "").replace("（", "").replace("）", "").trim()

    // ────────────────────────── 成员 ──────────────────────────

    fun addMember(name: String, emoji: String, colorHex: String, role: String): Member {
        val member = Member(
            id = newId("mem"),
            name = name.ifBlank { "成员" },
            emoji = emoji,
            colorHex = colorHex,
            role = role.ifBlank { "其他" },
            isSelf = false,
            order = _data.value.members.size,
        )
        mutate { current -> current.copy(members = current.members + member) }
        return member
    }

    fun updateMember(member: Member) {
        mutate { current ->
            current.copy(members = current.members.map { if (it.id == member.id) member else it })
        }
    }

    fun deleteMember(id: String) {
        val fallback = selfMember.id.takeIf { it != id }
            ?: _data.value.members.firstOrNull { it.id != id }?.id
            ?: return
        mutate { current ->
            current.copy(
                members = current.members.filterNot { it.id == id },
                records = current.records.map {
                    if (it.memberId == id) it.copy(memberId = fallback) else it
                },
            )
        }
    }

    // ────────────────────────── 分类 ──────────────────────────

    fun addCategory(name: String, emoji: String, type: TxType, colorHex: String): Category {
        val category = Category(
            id = newId("cat"),
            name = name.ifBlank { "新分类" },
            emoji = emoji,
            type = type,
            colorHex = colorHex,
            builtin = false,
            order = _data.value.categories.count { it.type == type },
        )
        mutate { current -> current.copy(categories = current.categories + category) }
        return category
    }

    fun deleteCategory(id: String) {
        val target = categoryById(id) ?: return
        if (target.builtin) return
        val fallback = if (target.type == TxType.EXPENSE) DefaultCategories.OTHER_EXPENSE else DefaultCategories.OTHER_INCOME
        mutate { current ->
            current.copy(
                categories = current.categories.filterNot { it.id == id },
                records = current.records.map {
                    if (it.categoryId == id) it.copy(categoryId = fallback) else it
                },
            )
        }
    }

    // ────────────────────────── 资产 / 负债 / 工作画像（v1.2）──────────────────────────

    fun addAsset(name: String, category: AssetCategory, valueCents: Long, note: String = ""): Asset {
        val asset = Asset(
            id = newId("asset"),
            name = name.ifBlank { category.label },
            category = category,
            valueCents = valueCents.coerceAtLeast(0L),
            note = note,
        )
        mutate { current -> current.copy(assets = current.assets + asset) }
        return asset
    }

    fun updateAsset(asset: Asset) {
        mutate { current ->
            current.copy(assets = current.assets.map { if (it.id == asset.id) asset else it })
        }
    }

    fun deleteAsset(id: String) {
        mutate { current -> current.copy(assets = current.assets.filterNot { it.id == id }) }
    }

    fun addLiability(
        name: String,
        category: LiabilityCategory,
        balanceCents: Long,
        monthlyPaymentCents: Long = 0L,
        note: String = "",
    ): Liability {
        val liability = Liability(
            id = newId("debt"),
            name = name.ifBlank { category.label },
            category = category,
            balanceCents = balanceCents.coerceAtLeast(0L),
            monthlyPaymentCents = monthlyPaymentCents.coerceAtLeast(0L),
            note = note,
        )
        mutate { current -> current.copy(liabilities = current.liabilities + liability) }
        return liability
    }

    fun updateLiability(liability: Liability) {
        mutate { current ->
            current.copy(liabilities = current.liabilities.map { if (it.id == liability.id) liability else it })
        }
    }

    fun deleteLiability(id: String) {
        mutate { current -> current.copy(liabilities = current.liabilities.filterNot { it.id == id }) }
    }

    fun updateProfile(profile: FinancialProfile) {
        mutate { current -> current.copy(profile = profile.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** 使用演示资产 / 负债 / 工作画像（用于首次启动与「重置为示例」） */
    private fun demoFinance(): Triple<List<Asset>, List<Liability>, FinancialProfile> =
        Triple(SeedData.demoAssets(), SeedData.demoLiabilities(), SeedData.demoProfile())

    // ────────────────────────── 设置 / 规则 ──────────────────────────

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        mutate { current -> current.copy(settings = transform(current.settings)) }
    }

    fun setFamilyName(name: String) = updateSettings { it.copy(familyName = name.ifBlank { "我家" }) }

    fun setHapticsEnabled(enabled: Boolean) = updateSettings { it.copy(hapticsEnabled = enabled) }

    fun setHapticStrength(strength: Int) = updateSettings { it.copy(hapticStrength = strength.coerceIn(1, 3)) }

    fun setThemeMode(mode: Int) = updateSettings { it.copy(themeMode = mode.coerceIn(0, 2)) }

    fun setPrivacyMask(mask: Boolean) = updateSettings { it.copy(privacyMask = mask) }

    /** 记住「商户 → 分类」的纠正结果，下次识别同一商户时自动归类 */
    fun rememberMerchantRules(rules: Map<String, String>) {
        if (rules.isEmpty()) return
        mutate { current ->
            current.copy(merchantRules = current.merchantRules + rules.filterValues { it.isNotBlank() })
        }
    }

    fun resolveMerchantRule(merchant: String): String? {
        if (merchant.isBlank()) return null
        val normalized = normalizeText(merchant)
        if (normalized.isEmpty()) return null
        return _data.value.merchantRules.entries.firstOrNull { (key, _) ->
            val k = normalizeText(key)
            k.isNotEmpty() && (normalized.contains(k) || k.contains(normalized))
        }?.value
    }

    // ────────────────────────── 批量导入 / 重置 ──────────────────────────

    /** 导入确认：一次性写入流水与规则，只触发一次落盘 */
    fun commitImport(newRecords: List<TxRecord>, rules: Map<String, String> = emptyMap()) {
        if (newRecords.isEmpty()) return
        mutate { current ->
            current.copy(
                records = (current.records + newRecords).sortedByDescending { it.occurredAt },
                merchantRules = current.merchantRules + rules.filterValues { it.isNotBlank() },
                settings = current.settings.copy(onboarded = true),
            )
        }
    }

    fun flush() {
        val snapshot = _data.value
        scope.launch { writeMutex.withLock { withContext(Dispatchers.IO) { writeToDisk(snapshot) } } }
    }

    /** 清空全部流水，保留成员与自定义分类 */
    fun clearRecords() {
        mutate { current -> current.copy(records = emptyList(), settings = current.settings.copy(seededDemo = false)) }
    }

    /** 恢复出厂：清空全部数据并重新写入示例 */
    fun resetAllWithDemo() {
        val (assets, liabilities, profile) = demoFinance()
        val demo = LedgerData(
            members = SeedData.members,
            categories = DefaultCategories.all,
            records = SeedData.demoRecords(),
            settings = AppSettings(familyName = "我家", hapticsEnabled = true, seededDemo = true, onboarded = true),
            assets = assets,
            liabilities = liabilities,
            profile = profile,
        )
        _data.value = demo
        persistNow()
    }

    /** 恢复出厂：清空全部数据，保留一份空白账本 */
    fun resetAllBlank() {
        val blank = LedgerData(
            members = SeedData.members,
            categories = DefaultCategories.all,
            settings = AppSettings(familyName = "我家", hapticsEnabled = true, seededDemo = false, onboarded = true),
        )
        _data.value = blank
        persistNow()
    }

    // ────────────────────────── 导入导出 ──────────────────────────

    fun exportCsv(): File {
        val dir = File(appContext.filesDir, "export").apply { mkdirs() }
        val file = File(dir, "岁余-收支明细-${Dates.isoDate(System.currentTimeMillis())}.csv")
        val builder = StringBuilder("\uFEFF")
        builder.append("日期,时间,方向,金额(元),分类,成员,商户,账户,来源,备注\n")
        _data.value.records.sortedBy { it.occurredAt }.forEach { record ->
            val category = categoryName(record.categoryId)
            val member = memberName(record.memberId)
            builder.append(Dates.isoDate(record.occurredAt)).append(',')
                .append(Dates.time(record.occurredAt)).append(',')
                .append(record.type.label).append(',')
                .append(String.format(java.util.Locale.CHINA, "%.2f", record.amountCents / 100.0)).append(',')
                .append(csvEscape(category)).append(',')
                .append(csvEscape(member)).append(',')
                .append(csvEscape(record.merchant)).append(',')
                .append(csvEscape(record.account)).append(',')
                .append(csvEscape(record.source.label)).append(',')
                .append(csvEscape(record.note)).append('\n')
        }
        file.writeText(builder.toString())
        return file
    }

    fun exportBackup(): File {
        val dir = File(appContext.filesDir, "backup").apply { mkdirs() }
        val file = File(dir, "岁余-备份-${Dates.isoDate(System.currentTimeMillis())}.json")
        file.writeText(LedgerCodec.encode(_data.value))
        return file
    }

    fun restoreBackup(json: String): Boolean {
        val decoded = LedgerCodec.decode(json) ?: return false
        if (decoded.members.isEmpty() || decoded.categories.isEmpty()) return false
        _data.value = normalize(decoded)
        persistNow()
        return true
    }

    private fun csvEscape(value: String): String {
        if (value.isBlank()) return ""
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' }
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    // ────────────────────────── 内部 ──────────────────────────

    private fun mutate(transform: (LedgerData) -> LedgerData) {
        _data.value = transform(_data.value)
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(80)
            val snapshot = _data.value
            writeMutex.withLock {
                withContext(Dispatchers.IO) { writeToDisk(snapshot) }
            }
        }
    }

    private fun persistNow() {
        val snapshot = _data.value
        scope.launch {
            writeMutex.withLock { withContext(Dispatchers.IO) { writeToDisk(snapshot) } }
        }
    }

    private fun writeToDisk(snapshot: LedgerData) {
        runCatching {
            val tmp = File(appContext.filesDir, "ledger.json.tmp")
            tmp.writeText(LedgerCodec.encode(snapshot))
            if (!tmp.renameTo(dataFile)) {
                dataFile.writeText(LedgerCodec.encode(snapshot))
                tmp.delete()
            }
        }
    }

    private fun loadWithSeed(): LedgerData {
        val existing = if (dataFile.exists()) {
            runCatching { LedgerCodec.decode(dataFile.readText()) }.getOrNull()
        } else null
        if (existing != null) return normalize(existing)

        val (demoAssets, demoLiabilities, demoProfile) = demoFinance()
        val seeded = LedgerData(
            members = SeedData.members,
            categories = DefaultCategories.all,
            records = SeedData.demoRecords(),
            settings = AppSettings(familyName = "我家", seededDemo = true, onboarded = true),
            assets = demoAssets,
            liabilities = demoLiabilities,
            profile = demoProfile,
        )
        scope.launch { writeMutex.withLock { withContext(Dispatchers.IO) { writeToDisk(seeded) } } }
        return seeded
    }

    /** 兼容老数据 / 备份恢复：补齐内置分类与默认成员 */
    private fun normalize(data: LedgerData): LedgerData {
        val categories = data.categories.toMutableList()
        DefaultCategories.all.forEach { builtin ->
            if (categories.none { it.id == builtin.id }) categories += builtin
        }
        val members = if (data.members.isEmpty()) SeedData.members else data.members
        val validMemberIds = members.map { it.id }.toSet()
        val fallbackMember = members.firstOrNull { it.isSelf }?.id ?: members.first().id
        val validCategoryIds = categories.map { it.id }.toSet()
        val records = data.records.map { record ->
            var next = record
            if (next.memberId !in validMemberIds) next = next.copy(memberId = fallbackMember)
            if (next.categoryId !in validCategoryIds) {
                next = next.copy(
                    categoryId = if (next.type == TxType.EXPENSE) DefaultCategories.OTHER_EXPENSE else DefaultCategories.OTHER_INCOME,
                )
            }
            next
        }
        // v1.1 → v1.2 升级：示例账本没有资产数据时补一份演示资产/画像，
        // 让老用户升级后也能立刻看到财务体检，而不会是一张空白表。
        val upgradedAssets = if (
            data.assets.isEmpty() &&
            data.liabilities.isEmpty() &&
            data.settings.seededDemo &&
            data.profile.occupation.isBlank()
        ) {
            val (assets, liabilities, profile) = demoFinance()
            return data.copy(
                members = members,
                categories = categories,
                records = records,
                assets = assets,
                liabilities = liabilities,
                profile = profile,
            )
        } else {
            data.assets
        }
        return data.copy(
            members = members,
            categories = categories,
            records = records,
            assets = upgradedAssets,
        )
    }
}
