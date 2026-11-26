package com.suiyu.ledger.ocr

import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.newId

/**
 * 一条从截图 / 短信文本中解析出来的账单草稿。
 *
 * 设计约定：
 * - 本类不依赖 Android，`BillParser` 可在 JVM 单元测试里直接跑；
 * - 金额始终为正的「分」，方向交给 [type]，与 [TxRecord] 的口径保持一致；
 * - 解析结果只用于「草稿」，真正入账前用户还可以逐字段修改，
 *   因此置信度 [confidence] 是给用户看的信息，而不是自动入账的依据；
 * - [rawText] 保存该笔账单附近的 OCR 原文片段，便于在导入页展开核对。
 */
data class ParsedBill(
    val source: BillSource,
    val type: TxType,
    /** 金额（分），始终为正数 */
    val amountCents: Long,
    /** 交易对方 / 商户；识别不到时为空串 */
    val merchant: String,
    /** 交易发生时间；只有日期没有年份时按当前年补全，缺失时为 null */
    val occurredAt: Long?,
    /** 支付账户，例如「微信零钱」「余额宝」「招商银行 尾号1234」 */
    val account: String,
    /** 分类 id，优先来自分类关键词，其次来源兜底，保证一定是有效分类 */
    val categoryId: String,
    /** 置信度 0..1：日期 + 金额 + 方向齐全 ≥ 0.9；缺日期 0.6；只有金额 0.4 */
    val confidence: Float,
    /** 生成该草稿的 OCR 原文片段（已归一化） */
    val rawText: String,
) {

    /**
     * 转成可持久化的流水记录。
     * 分类与金额由用户在草稿卡里确认后可能被改写，因此 [ParsedBill] 只提供默认转换；
     * 界面层会在此基础上 `copy` 用户编辑过的字段。
     *
     * id 统一走 `util/newId("t")`，与手动记账保持同一规则。
     */
    fun toRecord(memberId: String): TxRecord = TxRecord(
        id = newId("t"),
        type = type,
        amountCents = amountCents,
        categoryId = categoryId,
        memberId = memberId,
        occurredAt = occurredAt ?: System.currentTimeMillis(),
        merchant = merchant,
        account = account,
        source = source,
        sourceRef = null,
        includeInStats = true,
    )
}
