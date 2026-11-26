package com.suiyu.ledger.data

import com.suiyu.ledger.model.AppSettings
import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.AssetCategory
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.EmploymentType
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.LedgerData
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.model.LiabilityCategory
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地 JSON 编解码。选择手写 org.json 而不引入 Room/序列化插件：
 *  - 零注解处理器，构建更快、更稳；
 *  - 数据量级（数千条流水）下读写耗时可以忽略；
 *  - 备份文件本身就是可读 JSON，符合「数据主权」原则。
 */
object LedgerCodec {

    fun encode(data: LedgerData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("members", JSONArray().apply { data.members.forEach { put(memberToJson(it)) } })
        root.put("categories", JSONArray().apply { data.categories.forEach { put(categoryToJson(it)) } })
        root.put("records", JSONArray().apply { data.records.forEach { put(recordToJson(it)) } })
        root.put("settings", settingsToJson(data.settings))
        root.put("merchantRules", JSONObject().apply {
            data.merchantRules.forEach { (key, value) -> put(key, value) }
        })
        root.put("assets", JSONArray().apply { data.assets.forEach { put(assetToJson(it)) } })
        root.put("liabilities", JSONArray().apply { data.liabilities.forEach { put(liabilityToJson(it)) } })
        root.put("profile", profileToJson(data.profile))
        return root.toString()
    }

    fun decode(raw: String): LedgerData? = runCatching {
        val root = JSONObject(raw)
        LedgerData(
            version = root.optInt("version", 1),
            members = root.optJSONArray("members").toList { memberFromJson(it) },
            categories = root.optJSONArray("categories").toList { categoryFromJson(it) },
            records = root.optJSONArray("records").toList { recordFromJson(it) },
            settings = root.optJSONObject("settings")?.let { settingsFromJson(it) } ?: AppSettings.DEFAULT,
            merchantRules = root.optJSONObject("merchantRules")?.let { obj ->
                buildMap {
                    obj.keys().forEach { key -> put(key, obj.optString(key)) }
                }
            } ?: emptyMap(),
            assets = root.optJSONArray("assets").toList { assetFromJson(it) },
            liabilities = root.optJSONArray("liabilities").toList { liabilityFromJson(it) },
            profile = root.optJSONObject("profile")?.let { profileFromJson(it) } ?: FinancialProfile(),
        )
    }.getOrNull()

    private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val result = ArrayList<T>(length())
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            runCatching { mapper(item) }.getOrNull()?.let { result += it }
        }
        return result
    }

    private fun memberToJson(member: Member) = JSONObject().apply {
        put("id", member.id)
        put("name", member.name)
        put("emoji", member.emoji)
        put("colorHex", member.colorHex)
        put("role", member.role)
        put("isSelf", member.isSelf)
        put("order", member.order)
    }

    private fun memberFromJson(json: JSONObject) = Member(
        id = json.optString("id"),
        name = json.optString("name", "成员"),
        emoji = json.optString("emoji", "🙂"),
        colorHex = json.optString("colorHex", "#24483E"),
        role = json.optString("role", "其他"),
        isSelf = json.optBoolean("isSelf", false),
        order = json.optInt("order", 0),
    )

    private fun categoryToJson(category: Category) = JSONObject().apply {
        put("id", category.id)
        put("name", category.name)
        put("emoji", category.emoji)
        put("type", category.type.name)
        put("colorHex", category.colorHex)
        put("builtin", category.builtin)
        put("keywords", JSONArray().apply { category.keywords.forEach { put(it) } })
        put("order", category.order)
    }

    private fun categoryFromJson(json: JSONObject) = Category(
        id = json.optString("id"),
        name = json.optString("name", "分类"),
        emoji = json.optString("emoji", "📎"),
        type = if (json.optString("type") == TxType.INCOME.name) TxType.INCOME else TxType.EXPENSE,
        colorHex = json.optString("colorHex", "#9A9487"),
        builtin = json.optBoolean("builtin", true),
        keywords = json.optJSONArray("keywords")?.let { array ->
            List(array.length()) { index -> array.optString(index) }
        } ?: emptyList(),
        order = json.optInt("order", 0),
    )

    private fun recordToJson(record: TxRecord) = JSONObject().apply {
        put("id", record.id)
        put("type", record.type.name)
        put("amountCents", record.amountCents)
        put("categoryId", record.categoryId)
        put("memberId", record.memberId)
        put("occurredAt", record.occurredAt)
        put("note", record.note)
        put("merchant", record.merchant)
        put("account", record.account)
        put("source", record.source.name)
        put("sourceRef", record.sourceRef ?: JSONObject.NULL)
        put("createdAt", record.createdAt)
        put("includeInStats", record.includeInStats)
    }

    private fun recordFromJson(json: JSONObject) = TxRecord(
        id = json.optString("id"),
        type = if (json.optString("type") == TxType.INCOME.name) TxType.INCOME else TxType.EXPENSE,
        amountCents = json.optLong("amountCents"),
        categoryId = json.optString("categoryId"),
        memberId = json.optString("memberId"),
        occurredAt = json.optLong("occurredAt"),
        note = json.optString("note"),
        merchant = json.optString("merchant"),
        account = json.optString("account"),
        source = BillSource.of(json.optString("source")),
        sourceRef = if (json.isNull("sourceRef")) null else json.optString("sourceRef"),
        createdAt = json.optLong("createdAt"),
        includeInStats = json.optBoolean("includeInStats", true),
    )

    private fun assetToJson(asset: Asset) = JSONObject().apply {
        put("id", asset.id)
        put("name", asset.name)
        put("category", asset.category.name)
        put("valueCents", asset.valueCents)
        put("note", asset.note)
        put("updatedAt", asset.updatedAt)
    }

    private fun assetFromJson(json: JSONObject) = Asset(
        id = json.optString("id"),
        name = json.optString("name", "资产"),
        category = AssetCategory.entries.firstOrNull { it.name == json.optString("category") } ?: AssetCategory.OTHER,
        valueCents = json.optLong("valueCents"),
        note = json.optString("note"),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun liabilityToJson(liability: Liability) = JSONObject().apply {
        put("id", liability.id)
        put("name", liability.name)
        put("category", liability.category.name)
        put("balanceCents", liability.balanceCents)
        put("monthlyPaymentCents", liability.monthlyPaymentCents)
        put("note", liability.note)
        put("updatedAt", liability.updatedAt)
    }

    private fun liabilityFromJson(json: JSONObject) = Liability(
        id = json.optString("id"),
        name = json.optString("name", "负债"),
        category = LiabilityCategory.of(json.optString("category")),
        balanceCents = json.optLong("balanceCents"),
        monthlyPaymentCents = json.optLong("monthlyPaymentCents"),
        note = json.optString("note"),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun profileToJson(profile: FinancialProfile) = JSONObject().apply {
        put("occupation", profile.occupation)
        put("employmentType", profile.employmentType.name)
        put("jobYears", profile.jobYears)
        put("incomeStability", profile.incomeStability)
        put("spouseWorks", profile.spouseWorks)
        put("elderlyCount", profile.elderlyCount)
        put("childrenCount", profile.childrenCount)
        put("hasMedicalInsurance", profile.hasMedicalInsurance)
        put("hasCriticalIllnessInsurance", profile.hasCriticalIllnessInsurance)
        put("hasLifeInsurance", profile.hasLifeInsurance)
        put("insuranceCoverageCents", profile.insuranceCoverageCents)
        put("manualMonthlyIncomeCents", profile.manualMonthlyIncomeCents)
        put("manualMonthlyFixedExpenseCents", profile.manualMonthlyFixedExpenseCents)
        put("updatedAt", profile.updatedAt)
    }

    private fun profileFromJson(json: JSONObject) = FinancialProfile(
        occupation = json.optString("occupation"),
        employmentType = EmploymentType.of(json.optString("employmentType")),
        jobYears = json.optInt("jobYears"),
        incomeStability = json.optInt("incomeStability", 2).coerceIn(1, 3),
        spouseWorks = json.optBoolean("spouseWorks", true),
        elderlyCount = json.optInt("elderlyCount"),
        childrenCount = json.optInt("childrenCount"),
        hasMedicalInsurance = json.optBoolean("hasMedicalInsurance", true),
        hasCriticalIllnessInsurance = json.optBoolean("hasCriticalIllnessInsurance", false),
        hasLifeInsurance = json.optBoolean("hasLifeInsurance", false),
        insuranceCoverageCents = json.optLong("insuranceCoverageCents"),
        manualMonthlyIncomeCents = json.optLong("manualMonthlyIncomeCents"),
        manualMonthlyFixedExpenseCents = json.optLong("manualMonthlyFixedExpenseCents"),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun settingsToJson(settings: AppSettings) = JSONObject().apply {
        put("familyName", settings.familyName)
        put("hapticsEnabled", settings.hapticsEnabled)
        put("hapticStrength", settings.hapticStrength)
        put("themeMode", settings.themeMode)
        put("seededDemo", settings.seededDemo)
        put("onboarded", settings.onboarded)
        put("privacyMask", settings.privacyMask)
    }

    private fun settingsFromJson(json: JSONObject) = AppSettings(
        familyName = json.optString("familyName", "我家"),
        hapticsEnabled = json.optBoolean("hapticsEnabled", true),
        hapticStrength = json.optInt("hapticStrength", 2).coerceIn(1, 3),
        themeMode = json.optInt("themeMode", 0),
        seededDemo = json.optBoolean("seededDemo", false),
        onboarded = json.optBoolean("onboarded", false),
        privacyMask = json.optBoolean("privacyMask", false),
    )
}
