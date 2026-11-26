package com.suiyu.ledger.ui.screens.importer

import com.suiyu.ledger.data.DefaultCategories
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.ocr.ParsedBill
import com.suiyu.ledger.util.Dates
import java.time.Instant
import java.time.ZoneId

/**
 * 导入草稿：解析结果的可编辑副本。
 *
 * 为什么不在 [ParsedBill] 上直接改：解析结果代表「OCR 说了什么」，
 * 草稿代表「用户确认后要入账什么」。两者分开后，记忆纠正规则时可以准确判断
 * 哪些分类是用户改过的（[categoryChanged]），也便于卡片回退。
 */
data class ImportDraft(
    val key: Long,
    val bill: ParsedBill,
    val type: TxType,
    val amountCents: Long,
    val merchant: String,
    val occurredAt: Long,
    /** 原始账单是否带日期；没有日期的草稿会按「今天」预填，用户可改 */
    val hasDate: Boolean,
    val categoryId: String,
    val memberId: String,
    val account: String,
    val note: String = "",
    val selected: Boolean = true,
    val expanded: Boolean = false,
) {

    val confidence: Float get() = bill.confidence

    val categoryChanged: Boolean get() = categoryId != bill.categoryId

    val dateChanged: Boolean
        get() = bill.occurredAt == null || Dates.isoDate(bill.occurredAt) != Dates.isoDate(occurredAt)

    /** 用户改过分类且商户非空 → 值得写入「商户 → 分类」规则 */
    val ruleCandidate: Boolean get() = categoryChanged && merchant.isNotBlank()

    /** 转成可入账的流水；id 由 [ParsedBill.toRecord] 用 `newId("t")` 生成 */
    fun toRecord(): TxRecord = bill.toRecord(memberId).copy(
        type = type,
        amountCents = amountCents,
        categoryId = categoryId,
        occurredAt = occurredAt,
        note = note,
        merchant = merchant,
        account = account,
    )
}

object ImportDrafts {

    /**
     * 把解析结果转成草稿。
     * - 分类 id 会校验是否仍在当前分类表里（自定义分类被删 / 换设备时兜底）；
     * - 没有日期的账单先按 [fallbackTime]（通常是现在）预填，方便用户直接改日期。
     */
    fun fromBills(
        bills: List<ParsedBill>,
        categories: List<Category>,
        defaultMemberId: String,
        keySeed: Long,
        fallbackTime: Long = System.currentTimeMillis(),
    ): List<ImportDraft> = bills.mapIndexed { index, bill ->
        val categoryId = bill.categoryId
            .takeIf { id -> categories.any { it.id == id && it.type == bill.type } }
            ?: defaultCategoryId(bill.type, categories)
        ImportDraft(
            key = keySeed + index,
            bill = bill,
            type = bill.type,
            amountCents = bill.amountCents,
            merchant = bill.merchant,
            occurredAt = bill.occurredAt ?: fallbackTime,
            hasDate = bill.occurredAt != null,
            categoryId = categoryId,
            memberId = defaultMemberId,
            account = bill.account,
            note = "",
            selected = true,
            expanded = false,
        )
    }

    /**
     * 同一批次（多张截图 + 粘贴文本）内去重：金额、方向相同，日期同一天，商户近似。
     * 只处理「明显是同一笔」的情况（例如同一张账单截了两次）；同日同商户同金额的
     * 两笔真实消费有概率被误合并，属于已知取舍，可在草稿列表里手动补回。
     */
    fun dedupeAcrossBatch(bills: List<ParsedBill>): List<ParsedBill> {
        val result = ArrayList<ParsedBill>(bills.size)
        for (bill in bills) {
            if (result.none { existing -> looksLikeSameBill(existing, bill) }) result += bill
        }
        return result
    }

    private fun looksLikeSameBill(first: ParsedBill, second: ParsedBill): Boolean {
        if (first.type != second.type || first.amountCents != second.amountCents) return false
        val firstDate = first.occurredAt
        val secondDate = second.occurredAt
        val sameDay = firstDate != null && secondDate != null && Dates.isoDate(firstDate) == Dates.isoDate(secondDate)
        if (!sameDay) return false
        val firstMerchant = normalizeMerchant(first.merchant)
        val secondMerchant = normalizeMerchant(second.merchant)
        if (firstMerchant.isEmpty() && secondMerchant.isEmpty()) return true
        if (firstMerchant.isEmpty() || secondMerchant.isEmpty()) return false
        return firstMerchant.contains(secondMerchant) || secondMerchant.contains(firstMerchant)
    }

    private fun normalizeMerchant(raw: String): String =
        raw.replace(" ", "").replace("·", "").replace("-", "").trim()

    /** 按方向找兜底分类：内置「其他」优先，其次同方向第一个分类 */
    fun defaultCategoryId(type: TxType, categories: List<Category>): String {
        val builtin = if (type == TxType.EXPENSE) {
            DefaultCategories.OTHER_EXPENSE
        } else {
            DefaultCategories.OTHER_INCOME
        }
        categories.firstOrNull { it.id == builtin }?.let { return it.id }
        categories.firstOrNull { it.type == type }?.let { return it.id }
        return categories.firstOrNull()?.id ?: builtin
    }

    /** 切换方向时保证分类与方向一致 */
    fun categoryForType(type: TxType, currentCategoryId: String, categories: List<Category>): String {
        val current = categories.firstOrNull { it.id == currentCategoryId }
        if (current != null && current.type == type) return currentCategoryId
        return defaultCategoryId(type, categories)
    }
}

/** 日期选择器回调里可能用到：本地日期 ↔ UTC 毫秒换算 */
internal fun localDateOfUtcMillis(utcMillis: Long): java.time.LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()

/** 只替换日期、保留原来的时分 */
internal fun mergePickedDate(utcMillis: Long, existingMillis: Long): Long {
    val date = localDateOfUtcMillis(utcMillis)
    val time = Instant.ofEpochMilli(existingMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    return date.atTime(time.hour, time.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
