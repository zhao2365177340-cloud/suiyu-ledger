package com.suiyu.ledger.ocr

import com.suiyu.ledger.data.DefaultCategories
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * 账单文本解析器（纯 Kotlin，无 Android 依赖，可在 JVM 单测里直接跑）。
 *
 * 输入是 ML Kit 中文 OCR 出来的文本（或用户粘贴的银行短信），输出若干 [ParsedBill] 草稿。
 *
 * 解析思路（为什么这样做）：
 *  1. 先「归一化」：全角转半角、合并多余空格、逐行裁剪，让后续正则只处理一种形态；
 *  2. 用来源关键词判断账单来自哪里（微信 / 支付宝 / 银行 App / 银行短信）；
 *  3. 用「金额行」作为锚点拆分：一张列表截图可能有多笔，每行金额对应一笔；
 *     详情页会出现「¥25.00 / -25.00」这类重复金额，用详情页特征词 + 金额相同来合并；
 *  4. 方向（收 / 支）优先看金额前缀的正负号，其次看「消费 / 退款 / 入账 / 已存入」等词；
 *  5. 日期、商户、账户、分类都在锚点附近（或标签行）找。日期 / 商户优先取
 *     「两条金额行之间」的同一笔区间，避免把下一笔的日期或上一笔的商户张冠李戴；
 *  6. 分类只使用传入的分类表（含自定义分类）的关键词，按关键词长度降序匹配，
 *     避免「餐」先于「餐厅」造成误判，再按来源/方向兜底到「其他」。
 *
 * 稳定输出约定：金额为正的「分」；时间用 epoch 毫秒；结果按时间倒序，时间缺失的排最后。
 */
object BillParser {

    /** 兼容无年份日期时「未来日期回退一年」的判定容差 */
    private const val FUTURE_TOLERANCE_MS = 2L * 24 * 60 * 60 * 1000

    /** 只有日期没有时间时，默认记到当天中午，避免时区边界把日期算到前一天 */
    private const val DEFAULT_HOUR = 12

    // ────────────────────────── 对外入口 ──────────────────────────

    /**
     * @param text OCR 文本或粘贴的短信原文
     * @param categories 分类表（[DefaultCategories.all] 或仓库里的全量分类，含自定义分类）
     * @param nowMillis 当前时间；单元测试可传固定值，保证「缺年份日期」结果稳定
     */
    fun parse(
        text: String,
        categories: List<Category>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ParsedBill> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()
        val lines = normalized.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val source = detectSource(normalized)
        val bills = if (source == BillSource.SMS) {
            val sms = parseSms(normalized, categories, nowMillis)
            if (sms.isNotEmpty()) sms else parseGeneric(lines, normalized, source, categories, nowMillis)
        } else {
            parseGeneric(lines, normalized, source, categories, nowMillis)
        }
        return bills
            .filter { it.amountCents > 0 }
            .sortedWith(compareByDescending { it.occurredAt ?: Long.MIN_VALUE })
    }

    // ────────────────────────── 归一化 ──────────────────────────

    private val WHITESPACE = Regex("[ \\u00A0\\u3000]+")

    /** 全角转半角 + 合并空白 + 去空行；同时把 OCR 常见变体符号统一 */
    internal fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch == '\r' -> builder.append('\n')
                ch == '\n' -> builder.append('\n')
                ch == '\t' || ch == '\u00A0' || ch == '\u3000' -> builder.append(' ')
                ch == '\uFEFF' || ch == '\u200B' || ch == '\u200C' -> Unit
                ch == '￥' -> builder.append('¥')
                ch == '−' || ch == '–' || ch == '—' -> builder.append('-')
                ch in '\uFF01'..'\uFF5E' -> builder.append((ch.code - 0xFEE0).toChar())
                else -> builder.append(ch)
            }
        }
        return builder.toString()
            .replace(WHITESPACE, " ")
            .lines()
            // 表格截图常被 OCR 出竖线 / 圆点等装饰符（如「|收款方」），先剥掉再解析
            .map { line ->
                line.trim()
                    .trimStart('|', '丨', '¦', '│', '┃', '·', '•')
                    .trim()
                    .trimEnd('|', '丨', '¦', '│', '┃')
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    // ────────────────────────── 来源判定 ──────────────────────────

    private val SMS_BANK_HEADER = Regex("""【[^】]{0,20}银行[^】]{0,12}】""")

    private fun detectSource(text: String): BillSource {
        // 短信特征最明显（【xx银行】、退订、致电），优先判定，
        // 因为银行短信里也会出现「微信支付」这类字样，不能先判成微信。
        if (SMS_BANK_HEADER.containsMatchIn(text) || looksLikeSms(text)) return BillSource.SMS
        if (text.contains("微信") || text.contains("零钱") || text.contains("财付通")) return BillSource.WECHAT
        if (text.contains("支付宝") || text.contains("花呗") || text.contains("余额宝") ||
            text.contains("借呗") || text.contains("余利宝")
        ) {
            return BillSource.ALIPAY
        }
        if (text.contains("银行") || text.contains("信用卡") || text.contains("储蓄卡") || text.contains("尾号")) {
            return BillSource.BANK
        }
        return BillSource.OTHER
    }

    private fun looksLikeSms(text: String): Boolean {
        val looksLikeCardMessage = text.contains("尾号") || text.contains("银行") ||
            text.contains("信用卡") || text.contains("储蓄卡")
        if (!looksLikeCardMessage) return false
        return text.contains("退订") || text.contains("如有疑问") || text.contains("致电") ||
            text.contains("短信") || text.contains("【")
    }

    // ────────────────────────── 金额 token ──────────────────────────

    /**
     * 金额出现的形态：`-25.00` / `+25.00` / `¥25.00` / `¥1,234.56` / `25.00元` / `25元`。
     * 注意排除时间（12:31）与日期（2026-11-20 / 2026.11.20）里的数字段。
     */
    private val AMOUNT_TOKEN = Regex(
        """(?<![\d.,:：])([-+]?)\s*([¥￥])?\s*(\d[\d,]*(?:\.\d{1,2})?)(?:\s*(元))?(?![\d])(?!\.\d)""",
    )

    private data class AmountToken(
        val start: Int,
        val end: Int,
        val cents: Long,
        /** -1 支出 / 0 无符号 / 1 收入 */
        val explicitSign: Int,
        val hasSymbol: Boolean,
        val hasYuan: Boolean,
        val hasDecimal: Boolean,
    ) {
        /** 是否足够像一笔金额，可作为拆笔锚点；没有符号/元/小数点的整数（日期、序号）不算 */
        val isAnchor: Boolean get() = explicitSign != 0 || hasSymbol || hasYuan || hasDecimal

        val strength: Int get() = when {
            explicitSign != 0 -> 4
            hasSymbol || hasYuan -> 3
            hasDecimal -> 2
            else -> 1
        }
    }

    private fun scanTokens(line: String): List<AmountToken> = AMOUNT_TOKEN.findAll(line).mapNotNull { match ->
        val number = match.groupValues[3]
        val cents = parseTokenCents(number) ?: return@mapNotNull null
        val sign = when (match.groupValues[1]) {
            "-" -> -1
            "+" -> 1
            else -> 0
        }
        AmountToken(
            start = match.range.first,
            end = match.range.last + 1,
            cents = cents,
            explicitSign = sign,
            hasSymbol = match.groupValues[2].isNotEmpty(),
            hasYuan = match.groupValues[4].isNotEmpty(),
            hasDecimal = number.contains('.'),
        )
    }.toList()

    /** 把 "1,234.56" / "25.5" / "25" 解析成「分」 */
    internal fun parseTokenCents(number: String): Long? {
        val cleaned = number.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size > 2) return null
        val yuan = parts[0].toLongOrNull() ?: return null
        var fen = 0L
        if (parts.size == 2) {
            val raw = parts[1]
            if (raw.isEmpty() || raw.length > 2) return null
            if (raw.any { !it.isDigit() }) return null
            fen = raw.padEnd(2, '0').toLong()
        }
        return yuan * 100 + fen
    }

    // ────────────────────────── 日期 ──────────────────────────

    private data class DateParts(
        val year: Int?,
        val month: Int,
        val day: Int,
        val hour: Int?,
        val minute: Int?,
        /** 今天 / 昨天 / 前天 */
        val daysBack: Int? = null,
    )

    private data class DateHit(val lineIndex: Int, val millis: Long)

    private val DATE_FULL = Regex("""(\d{4})\s*[-/年.]\s*(\d{1,2})\s*[-/月.]\s*(\d{1,2})\s*[日号]?""")
    private val DATE_CN = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*[日号]""")
    private val DATE_DASH = Regex("""(?<![\d年])(\d{1,2})[-/](\d{1,2})(?![\d/])""")
    private val DATE_RELATIVE = Regex("""今天|昨天|前天|今日|昨日""")
    private val TIME_COLON = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val TIME_CN = Regex("""(\d{1,2})\s*[时点]\s*(?:(\d{1,2})\s*分?)?""")

    private fun extractDateParts(line: String): DateParts? {
        DATE_FULL.find(line)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            if (!isValidMonthDay(month, day)) return@let
            val (hour, minute) = extractTime(line)
            return DateParts(year, month, day, hour, minute)
        }
        DATE_CN.find(line)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            if (!isValidMonthDay(month, day)) return@let
            val (hour, minute) = extractTime(line)
            return DateParts(null, month, day, hour, minute)
        }
        DATE_DASH.find(line)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            if (!isValidMonthDay(month, day)) return@let
            val (hour, minute) = extractTime(line)
            return DateParts(null, month, day, hour, minute)
        }
        DATE_RELATIVE.find(line)?.let { match ->
            val daysBack = when (match.value) {
                "今天", "今日" -> 0
                "昨天", "昨日" -> 1
                else -> 2
            }
            val (hour, minute) = extractTime(line)
            return DateParts(null, 1, 1, hour, minute, daysBack)
        }
        return null
    }

    private fun isValidMonthDay(month: Int, day: Int): Boolean = month in 1..12 && day in 1..31

    private fun extractTime(line: String): Pair<Int?, Int?> {
        TIME_COLON.find(line)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull()
            if (hour != null && minute != null && hour in 0..23 && minute in 0..59) return hour to minute
        }
        TIME_CN.find(line)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour != null && hour in 0..23 && minute in 0..59) return hour to minute
        }
        return null to null
    }

    private fun resolveDateMillis(parts: DateParts, nowMillis: Long): Long? {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val hour = parts.hour ?: DEFAULT_HOUR
        val minute = parts.minute ?: 0
        val date: LocalDate = when {
            parts.daysBack != null -> today.minusDays(parts.daysBack.toLong())
            parts.year != null -> runCatching { LocalDate.of(parts.year, parts.month, parts.day) }
                .getOrNull() ?: return null
            else -> {
                val base = runCatching { LocalDate.of(today.year, parts.month, parts.day) }
                    .getOrNull() ?: return null
                val candidate = base.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                // 截图上没有年份时默认当年；如果算出来明显在未来，说明截图是去年的
                if (candidate > nowMillis + FUTURE_TOLERANCE_MS) base.minusYears(1) else base
            }
        }
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    private fun dateHitOf(line: String, lineIndex: Int, nowMillis: Long): DateHit? {
        val parts = extractDateParts(line) ?: return null
        val millis = resolveDateMillis(parts, nowMillis) ?: return null
        return DateHit(lineIndex, millis)
    }

    /**
     * 给列表里的每一笔分配日期。
     *
     * 列表截图的 OCR 行序不稳定：可能是「日期 → 商户 → 金额」，也可能是
     * 「商户 + 金额同一行 → 日期」。这里按行号顺序逐笔处理，先在「上一笔金额行」
     * 到「本笔金额行」之间的区间里找（大多数 App 的日期在金额上方），找不到再在
     * 「本笔金额行」到「下一笔金额行」之间找（金额在上、日期在下的布局），
     * 并且一行日期只分配给一笔，避免相邻行张冠李戴。
     */
    private fun assignDates(
        lines: List<String>,
        anchors: List<Anchor>,
        nowMillis: Long,
    ): Map<Int, Long?> {
        val sorted = anchors.sortedBy { it.lineIndex }
        val usedLines = HashSet<Int>()
        val result = HashMap<Int, Long?>(sorted.size)
        sorted.forEachIndexed { index, anchor ->
            val anchorLine = anchor.lineIndex
            val self = dateHitOf(lines[anchorLine], anchorLine, nowMillis)?.takeIf { it.lineIndex !in usedLines }
            if (self != null) {
                usedLines += self.lineIndex
                result[anchorLine] = self.millis
                return@forEachIndexed
            }
            val previousLine = sorted.getOrNull(index - 1)?.lineIndex ?: -1
            val nextLine = sorted.getOrNull(index + 1)?.lineIndex ?: lines.size
            val up = (anchorLine - 1 downTo previousLine + 1)
                .mapNotNull { dateHitOf(lines[it], it, nowMillis) }
                .firstOrNull { it.lineIndex !in usedLines }
            val down = if (up == null) {
                (anchorLine + 1 until nextLine)
                    .mapNotNull { dateHitOf(lines[it], it, nowMillis) }
                    .firstOrNull { it.lineIndex !in usedLines }
            } else {
                null
            }
            val hit = up ?: down
            if (hit != null) usedLines += hit.lineIndex
            result[anchorLine] = hit?.millis
        }
        return result
    }

    // ────────────────────────── 标签行 ──────────────────────────

    private val MERCHANT_LABELS = listOf(
        "转账-来自", "转账-转给", "商户名称", "商户全称", "交易对方", "对方账户", "商品说明",
        "商品名称", "商家名称", "收款方", "付款方", "收款人", "商户", "对方", "商家",
    )
    /** 「商户身份」标签：可信度高于转账关系标签 */
    private val MERCHANT_IDENTITY_LABELS = setOf(
        "商户名称", "商户全称", "交易对方", "对方账户", "商家名称", "收款方", "付款方", "收款人", "商户", "商家", "对方",
    )
    private val ACCOUNT_LABELS = listOf(
        "支付方式", "付款方式", "交易方式", "付款账户", "支付账户", "交易账户", "收款账户", "银行卡", "账户",
    )
    private val DATE_LABELS = listOf(
        "转账时间", "创建时间", "交易时间", "支付时间", "付款时间", "入账时间", "消费时间",
        "记账时间", "交易日期", "时间",
    )
    private val CATEGORY_LABELS = listOf("账单分类", "交易分类", "分类")
    private val STATUS_LABELS = listOf("当前状态", "状态")
    private val NOTE_LABELS = listOf("转账说明", "商品说明", "备注")
    private val ALL_LABELS: List<String> =
        (MERCHANT_LABELS + ACCOUNT_LABELS + DATE_LABELS + CATEGORY_LABELS + STATUS_LABELS + NOTE_LABELS)
            .sortedByDescending { it.length }

    /** 命中「标签：值」行；值为空时表示值在下一行 */
    private fun matchLabel(line: String, labels: List<String>): Pair<String, String>? {
        for (label in labels) {
            if (!line.startsWith(label)) continue
            val rest = line.substring(label.length).trimStart(' ', ':', '：', '-', '·', '|')
            return label to rest.trim()
        }
        return null
    }

    private fun findLabel(
        lines: List<String>,
        labels: List<String>,
        anchorIndex: Int? = null,
        maxDistance: Int = Int.MAX_VALUE,
    ): Pair<Int, Pair<String, String>>? {
        val sortedLabels = labels.sortedByDescending { it.length }
        val hits = ArrayList<Pair<Int, Pair<String, String>>>()
        lines.forEachIndexed { index, line ->
            matchLabel(line, sortedLabels)?.let { hits += index to it }
        }
        if (hits.isEmpty()) return null
        if (anchorIndex == null) return hits.first()
        return hits.filter { abs(it.first - anchorIndex) <= maxDistance }
            .minByOrNull { abs(it.first - anchorIndex) }
    }

    /** 取标签的值：同一行为空时取下一行 */
    private fun labelValue(lines: List<String>, hit: Pair<Int, Pair<String, String>>): String {
        val (index, pair) = hit
        val inline = pair.second
        if (inline.isNotBlank()) return inline
        val next = lines.getOrNull(index + 1)?.trim().orEmpty()
        if (next.isEmpty()) return ""
        if (extractDateParts(next) != null) return ""
        if (scanTokens(next).any { it.isAnchor }) return ""
        return if (isNoiseLine(next)) "" else next
    }

    // ────────────────────────── 噪声 / 语义判断 ──────────────────────────

    private val HARD_NOISE_WORDS = listOf(
        "单号", "流水号", "凭证号", "序列号", "编号", "验证码", "优惠", "立减", "折扣", "抵扣",
        "积分", "汇率", "原价", "参考价", "手续费", "尾号", "卡号", "账号", "手机号",
        "预留手机", "交易号", "账单详", "收支明", "账单列",
    )

    private val BALANCE_NOISE = Regex("""(?:^|[，,。;；、\s])(?:余额|账户余额|可用余额|当前余额|总额|合计)\s*[¥￥]?\s*\d""")

    private val HEADER_LINES = setOf(
        "微信", "微信支付", "支付宝", "账单", "账单详情", "交易详情", "收支明细", "全部账单", "全部",
        "支出", "收入", "支付成功", "交易成功", "当前状态", "状态", "支付方式", "付款方式",
        "账单分类", "分类", "人民币", "零钱", "余额", "已存入零钱", "查看更多", "更多", "详情",
        "返回", "筛选", "搜索", "我的", "首页", "消息", "银行卡", "信用卡", "储蓄卡",
        "交易金额", "支付金额", "消费金额", "金额", "转账说明", "商品说明", "备注", "今天", "昨天",
    )

    private val DIRECTION_PHRASES = setOf(
        "消费", "支出", "收入", "入账", "工资入账", "转账", "付款", "收款", "退款",
        "转出", "转入", "交易", "工资", "还款", "取现", "缴费", "扣款",
    )

    private fun isNoiseLine(line: String): Boolean {
        if (HARD_NOISE_WORDS.any { line.contains(it) }) return true
        if (BALANCE_NOISE.containsMatchIn(line)) return true
        return false
    }

    /** 判断「余额 1,234.56 元」这类行里的数字不是交易金额 */
    private fun isBalanceOnlyLine(line: String): Boolean {
        if (BALANCE_NOISE.containsMatchIn(line)) return true
        if (!line.contains("余额") && !line.contains("额度")) return false
        return !line.contains("余额宝") && !line.contains("收益")
    }

    private fun isHeaderLine(line: String): Boolean {
        if (line in HEADER_LINES) return true
        if (line in DIRECTION_PHRASES) return true
        // OCR 会把「账单详情」读成「账单详惰」、「交易成功」读成「交易成咸功」，
        // 短行按前缀 + 状态词兜底，避免页头文字被当成商户。
        if (line.length <= 10 && (
                line.startsWith("账单") || line.startsWith("交易") || line.startsWith("支付") ||
                    line.contains("成功") || line.contains("完成") || line.contains("已支付") ||
                    line.contains("已付款") || line.contains("已收款") || line.contains("到账")
                )
        ) {
            return true
        }
        // 「招商银行」「招商银行信用卡」这类首行是账户抬头，不是商户
        if (line.endsWith("银行") || line.contains("信用卡") || line.contains("储蓄卡")) return true
        return false
    }

    /** 是否是合格的「商户候选行」 */
    private fun isMerchantCandidate(line: String): Boolean {
        val text = line.trim()
        if (text.length !in 2..24) return false
        if (isNoiseLine(text) || isBalanceOnlyLine(text)) return false
        if (isHeaderLine(text)) return false
        if (extractDateParts(text) != null) return false
        if (scanTokens(text).any { it.isAnchor }) return false
        if (matchLabel(text, ALL_LABELS) != null) return false
        // 至少要有一个中文或字母，纯数字 / 符号行直接跳过
        return text.any { it.isLetter() }
    }

    // ────────────────────────── 方向 ──────────────────────────

    private val INCOME_WORDS = listOf(
        "退款成功" to 3, "退款到账" to 3, "已退款" to 3, "退款金额" to 2, "退款" to 2,
        "已存入" to 3, "入账金额" to 2, "入账" to 2, "到账" to 2, "转入" to 2, "存入" to 1,
        "工资" to 2, "代发" to 2, "奖金" to 2, "报销" to 2, "返还" to 2, "退回" to 2,
        "收款" to 2, "收入" to 2, "收到" to 1, "来自" to 1, "红包" to 1,
    )

    private val EXPENSE_WORDS = listOf(
        "支付成功" to 3, "付款成功" to 3, "转账给" to 3, "转账-转给" to 3,
        "支出" to 2, "消费" to 2, "已支付" to 2, "已付款" to 2, "扣款" to 2, "已扣" to 2,
        "转出" to 2, "取现" to 2, "缴费" to 2, "转给" to 2, "还款" to 1, "购买" to 1, "买单" to 1,
    )

    /** 返回正数偏收入、负数偏支出；0 表示判断不出 */
    private fun scoreDirection(text: String): Int {
        if (text.isBlank()) return 0
        var score = 0
        // 「申请退款」是电商页面的按钮，不代表收款，需要排除
        val refundOnlyAction = text.contains("申请退款") &&
            !text.contains("退款成功") && !text.contains("已退款") && !text.contains("退款到账")
        INCOME_WORDS.forEach { (word, weight) ->
            if (refundOnlyAction && word.startsWith("退款")) return@forEach
            if (text.contains(word)) score += weight
        }
        EXPENSE_WORDS.forEach { (word, weight) ->
            if (text.contains(word)) score -= weight
        }
        return score
    }

    private fun directionOf(text: String): TxType? {
        val score = scoreDirection(text)
        return when {
            score > 0 -> TxType.INCOME
            score < 0 -> TxType.EXPENSE
            else -> null
        }
    }

    /**
     * 强收入信号：这些词出现时，即使金额 OCR 把「+」识别成「-」也应判为收入。
     * 真实截图里符号误识别很常见（尤其是转账收入的 + 号），而「转账-来自 / 已存入零钱 /
     * 退款成功 / 入账」这类语义比一个符号更可靠。
     * 注意排除「申请退款」这种电商按钮文案。
     */
    private val INCOME_OVERRIDE_WORDS = listOf(
        "转账-来自", "退款成功", "退款到账", "已退款", "退款金额", "退款",
        "已存入零钱", "已存入", "入账", "到账", "代发", "工资入账", "代发工资",
    )

    private fun hasIncomeOverride(text: String): Boolean {
        if (text.isBlank()) return false
        val refundOnlyAction = text.contains("申请退款") &&
            !text.contains("退款成功") && !text.contains("已退款") && !text.contains("退款到账")
        return INCOME_OVERRIDE_WORDS.any { word ->
            if (refundOnlyAction && word.startsWith("退款")) return@any false
            text.contains(word)
        }
    }

    // ────────────────────────── 商户 / 账户 ──────────────────────────

    private fun cleanMerchant(raw: String): String {
        var text = raw.trim()
        for (prefix in listOf("来自", "转给", "向", "在", "给", "付给")) {
            if (text.startsWith(prefix) && text.length > prefix.length) {
                text = text.substring(prefix.length)
                break
            }
        }
        text = text.trim(' ', '-', ':', '：', ',', '，', '、', '。', '.', '·', '|', '(', ')', '（', '）', '【', '】')
        text = text.replace(Regex("\\s+"), " ")
        if (text.length > 24) text = text.take(24)
        return balanceBrackets(text)
    }

    /** 补齐被 trim 掉的右括号，避免展示成「星巴克(国贸店」 */
    private fun balanceBrackets(raw: String): String {
        var text = raw
        val pairs = listOf('(' to ')', '（' to '）', '【' to '】')
        pairs.forEach { (open, close) ->
            val openCount = text.count { it == open }
            val closeCount = text.count { it == close }
            if (openCount > closeCount) {
                text += close.toString().repeat(openCount - closeCount)
            }
        }
        return text
    }

    /** 从文本里剥掉日期 / 时间片段，用于「商户与日期同行」的 OCR 行 */
    private fun stripDateText(raw: String): String {
        var text = raw
        for (regex in listOf(DATE_FULL, DATE_CN, DATE_DASH, TIME_COLON, TIME_CN, DATE_RELATIVE)) {
            text = regex.replace(text, " ")
        }
        return text.replace(WHITESPACE, " ").trim()
    }

    private fun merchantFromRemainder(line: String, token: AmountToken): String {
        val fragments = listOf(line.substring(0, token.start), line.substring(token.end))
        for (fragment in fragments) {
            val text = fragment.trim().trim('-', ':', '：', ',', '，', '·', '.', '|', ' ')
            if (text.length < 2) continue
            val label = matchLabel(text, MERCHANT_LABELS)
            if (label != null) {
                if (label.second.isNotBlank()) return cleanMerchant(label.second)
                continue
            }
            if (isMerchantCandidate(text)) return cleanMerchant(text)
            // 同一行里可能夹着日期（"11月20日 老王面馆 25.00元"），去掉日期后再判断
            val stripped = stripDateText(text)
            if (stripped.length >= 2 && isMerchantCandidate(stripped)) return cleanMerchant(stripped)
        }
        return ""
    }

    /**
     * 只在「本笔区间」里找商户：上一条金额行（不含）到这一条金额行之间优先，
     * 找不到再看本笔金额行到下一条金额行之间，避免拿上一笔或下一笔的商户。
     */
    private fun merchantFromOwnBlock(
        lines: List<String>,
        anchors: List<Anchor>,
        anchor: Anchor,
        order: Int,
        categories: List<Category>,
        type: TxType,
    ): String {
        val previousLine = anchors.getOrNull(order - 1)?.lineIndex ?: -1
        val nextLine = anchors.getOrNull(order + 1)?.lineIndex ?: lines.size
        // 商户身份标签优先级高于「转账-来自」这类关系标签：
        // 列表截图里上一笔的「转账-来自 张伟」不应盖过本笔的「滴滴出行」。
        fun inspect(index: Int): Pair<String, Int>? {
            if (index !in lines.indices) return null
            val line = lines[index]
            matchLabel(line, MERCHANT_LABELS)?.let { label ->
                val value = if (label.second.isNotBlank()) {
                    label.second
                } else {
                    lines.getOrNull(index + 1)?.takeIf { isMerchantCandidate(it) }.orEmpty()
                }
                if (value.isNotBlank()) {
                    return cleanMerchant(value) to if (label.first in MERCHANT_IDENTITY_LABELS) 2 else 1
                }
            }
            if (isMerchantCandidate(line)) return cleanMerchant(line) to 0
            return null
        }
        val candidates = ArrayList<Pair<String, Int>>()
        for (index in anchor.lineIndex - 1 downTo previousLine + 1) {
            inspect(index)?.let { candidates += it }
        }
        for (index in anchor.lineIndex + 1 until nextLine) {
            inspect(index)?.let { candidates += it }
        }
        if (candidates.isEmpty()) return ""
        // 支付宝列表会在商户与金额之间夹一行「账单分类」文案（餐饮美食 / 交通出行），
        // 这类行含有分类名，优先跳过它选真正的商户行。
        // 同时优先「收款方 / 商户全称」这类标签给出的值，避免把页面上方的「账单详情」当商户。
        val usable = candidates.filter { (candidate, _) ->
            !looksLikeCategoryLine(candidate, categories, type)
        }
        // 只有「商户全称 / 收款方」这类强标签才提权；其余情况保持原始行序优先级，
        // 以免列表截图里相邻的「转账-来自 张伟」抢走本笔的真实商户。
        return usable.firstOrNull { it.second == 2 }?.first
            ?: usable.firstOrNull()?.first
            ?: candidates.first().first
    }

    private fun looksLikeCategoryLine(text: String, categories: List<Category>, type: TxType): Boolean =
        categories.any { category -> category.type == type && category.name.isNotBlank() && text.contains(category.name) }

    private fun headerAccount(lines: List<String>): String {
        for (line in lines) {
            val text = line.trim()
            if (text.length !in 2..24) continue
            if (text.contains("信用卡") || text.contains("储蓄卡") || text.endsWith("银行")) {
                return text.take(24)
            }
        }
        return ""
    }

    private fun cleanAccount(raw: String): String {
        var text = raw.trim()
        text = text.trim(' ', ':', '：', '·', '-', '(', ')', '（', '）')
        if (text.length > 24) text = text.take(24)
        return balanceBrackets(text)
    }

    private fun defaultAccount(source: BillSource): String = when (source) {
        BillSource.WECHAT -> "微信"
        BillSource.ALIPAY -> "支付宝"
        BillSource.BANK -> "银行卡"
        BillSource.SMS -> "银行卡"
        else -> ""
    }

    // ────────────────────────── 分类 ──────────────────────────

    internal fun resolveCategory(
        type: TxType,
        merchant: String,
        categoryHint: String,
        context: String,
        categories: List<Category>,
    ): String {
        val fallback = defaultCategoryId(type, categories)
        val typed = categories.filter { it.type == type }
        if (typed.isEmpty()) return fallback
        // 关键词按长度降序：先命中「美团单车」再命中「美团」，避免短词误判
        val index = typed.flatMap { category -> category.keywords.map { Triple(it, category.id, category.order) } }
            .sortedWith(compareByDescending<Triple<String, String, Int>> { it.first.length }.thenBy { it.third })
        fun matchText(text: String): String? {
            if (text.isBlank()) return null
            for ((keyword, categoryId) in index) {
                if (text.contains(keyword)) return categoryId
            }
            return null
        }
        return matchText(merchant)
            ?: matchText(categoryHint)
            ?: matchText(context)
            ?: fallback
    }

    private fun defaultCategoryId(type: TxType, categories: List<Category>): String {
        val builtin = if (type == TxType.EXPENSE) {
            DefaultCategories.OTHER_EXPENSE
        } else {
            DefaultCategories.OTHER_INCOME
        }
        categories.firstOrNull { it.id == builtin }?.let { return it.id }
        categories.firstOrNull { it.type == type }?.let { return it.id }
        return categories.firstOrNull()?.id ?: builtin
    }

    // ────────────────────────── 通用解析（微信 / 支付宝 / 银行 App） ──────────────────────────

    private data class Anchor(val lineIndex: Int, val token: AmountToken)

    private val DETAIL_MARKERS = listOf(
        "账单详情", "交易详情", "当前状态", "交易单号", "订单号", "转账说明", "转账时间",
        "创建时间", "支付时间", "付款时间", "交易时间", "商品说明", "账单分类", "商户名称",
        "收款方", "支付成功", "交易成功", "退款成功", "已存入", "入账金额", "交易金额",
    )

    private fun isDetailPage(text: String): Boolean = DETAIL_MARKERS.any { text.contains(it) }

    private fun parseGeneric(
        lines: List<String>,
        normalized: String,
        source: BillSource,
        categories: List<Category>,
        nowMillis: Long,
    ): List<ParsedBill> {
        val anchors = ArrayList<Anchor>()
        lines.forEachIndexed { index, line ->
            if (isNoiseLine(line)) return@forEachIndexed
            val tokens = scanTokens(line)
                .filter { it.isAnchor && it.cents > 0 && !isBalanceToken(line, it) }
            if (tokens.isEmpty()) return@forEachIndexed
            val best = tokens.maxWithOrNull(compareBy<AmountToken> { it.strength }.thenBy { it.cents })
                ?: return@forEachIndexed
            anchors += Anchor(index, best)
        }
        if (anchors.isEmpty()) return emptyList()

        val detail = isDetailPage(normalized)
        if (detail) {
            val anchor = chooseDetailAnchor(anchors)
            val occurredAt = labeledDate(lines, nowMillis)
                ?: nearestDate(lines, anchor.lineIndex, 8, nowMillis)
                ?: uniqueDate(lines, nowMillis)
            return listOfNotNull(
                buildBill(anchor, anchors, lines, normalized, source, categories, nowMillis, occurredAt, detail = true),
            )
        }
        val dates = assignDates(lines, anchors, nowMillis)
        return anchors.mapIndexedNotNull { order, anchor ->
            buildBill(
                anchor = anchor,
                anchors = anchors,
                lines = lines,
                normalized = normalized,
                source = source,
                categories = categories,
                nowMillis = nowMillis,
                occurredAt = dates[anchor.lineIndex] ?: if (anchors.size == 1) uniqueDate(lines, nowMillis) else null,
                detail = false,
                order = order,
            )
        }
    }

    private fun isBalanceToken(line: String, token: AmountToken): Boolean {
        if (token.explicitSign != 0 || token.hasSymbol) return false
        return isBalanceOnlyLine(line)
    }

    /** 详情页里「¥25.00」与「-25.00」是同一笔，优先选择带正负号的金额 */
    private fun chooseDetailAnchor(anchors: List<Anchor>): Anchor {
        anchors.firstOrNull { it.token.explicitSign != 0 }?.let { return it }
        return anchors.maxByOrNull { it.token.cents } ?: anchors.first()
    }

    private fun buildBill(
        anchor: Anchor,
        anchors: List<Anchor>,
        lines: List<String>,
        normalized: String,
        source: BillSource,
        categories: List<Category>,
        nowMillis: Long,
        occurredAt: Long?,
        detail: Boolean,
        order: Int = 0,
    ): ParsedBill? {
        val token = anchor.token
        if (token.cents <= 0) return null
        val anchorLine = lines[anchor.lineIndex]

        val statusValue = findLabel(lines, STATUS_LABELS, anchor.lineIndex, maxDistance = 6)
            ?.let { labelValue(lines, it) }.orEmpty()
        val ownBlock = if (detail) "" else ownBlockText(lines, anchors, order)
        val semanticContext = if (detail) filteredContext(lines, anchor.lineIndex, 6) else ownBlock
        val localContext = semanticContext + " " + statusValue
        val signType = when {
            token.explicitSign > 0 -> TxType.INCOME
            token.explicitSign < 0 -> TxType.EXPENSE
            else -> null
        }
        val keywordType = directionOf(localContext)
            ?: if (detail || anchors.size == 1) directionOf(normalized) else null
        val overrideText = if (detail) normalized else ownBlock
        val incomeOverride = hasIncomeOverride(overrideText)
        val type = when {
            incomeOverride -> TxType.INCOME
            signType != null -> signType
            keywordType != null -> keywordType
            else -> TxType.EXPENSE
        }
        val directionEvidence = incomeOverride || signType != null || keywordType != null

        // 商户：详情页优先标签；列表页优先行内剩余文本，其次本笔区间
        var merchant = ""
        if (detail) {
            findLabel(lines, MERCHANT_LABELS, anchor.lineIndex, maxDistance = 12)?.let { hit ->
                val value = labelValue(lines, hit)
                if (value.isNotBlank()) merchant = cleanMerchant(value)
            }
        }
        if (merchant.isBlank()) merchant = merchantFromRemainder(anchorLine, token)
        if (merchant.isBlank() && detail) {
            merchant = merchantFromNeighbor(lines, anchor.lineIndex)
        }
        if (merchant.isBlank() && !detail) {
            merchant = merchantFromOwnBlock(lines, anchors, anchor, order, categories, type)
        }
        if (merchant.isBlank() && detail) {
            findLabel(lines, MERCHANT_LABELS)?.let { hit ->
                val value = labelValue(lines, hit)
                if (value.isNotBlank()) merchant = cleanMerchant(value)
            }
        }
        if (merchant == "人民币" || merchant == "零钱") merchant = ""

        // 账户
        var account = findLabel(lines, ACCOUNT_LABELS)
            ?.let { labelValue(lines, it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { cleanAccount(it) }
            .orEmpty()
        if (account.isBlank() && source == BillSource.BANK) account = headerAccount(lines)
        if (account.isBlank()) account = defaultAccount(source)

        // 分类：商户关键词 > 账单分类标签 > 上下文关键词 > 兜底
        val categoryHint = findLabel(lines, CATEGORY_LABELS)
            ?.let { labelValue(lines, it) }
            .orEmpty()
        val categoryContext = if (detail) filteredContext(lines, anchor.lineIndex, 7) else ownBlock
        val categoryId = resolveCategory(type, merchant, categoryHint, categoryContext, categories)

        val confidence = confidenceOf(occurredAt != null, directionEvidence, merchant.isNotBlank(), token.strength)
        val rawText = if (detail) normalized else windowText(lines, anchor.lineIndex, 4)
        return ParsedBill(
            source = source,
            type = type,
            amountCents = token.cents,
            merchant = merchant,
            occurredAt = occurredAt,
            account = account,
            categoryId = categoryId,
            confidence = confidence,
            rawText = rawText,
        )
    }

    private fun merchantFromNeighbor(lines: List<String>, anchorIndex: Int): String {
        for (distance in 1..3) {
            val indexes = listOf(anchorIndex - distance, anchorIndex + distance)
            for (index in indexes) {
                if (index !in lines.indices) continue
                val line = lines[index]
                matchLabel(line, MERCHANT_LABELS)?.let { label ->
                    val value = if (label.second.isNotBlank()) {
                        label.second
                    } else {
                        lines.getOrNull(index + 1)?.takeIf { isMerchantCandidate(it) }.orEmpty()
                    }
                    if (value.isNotBlank()) return cleanMerchant(value)
                }
                if (isMerchantCandidate(line)) return cleanMerchant(line)
            }
        }
        return ""
    }

    private fun windowText(lines: List<String>, index: Int, radius: Int): String {
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + radius + 1).coerceAtMost(lines.size)
        return lines.subList(start, end).joinToString(" ")
    }

    /**
     * 语义上下文：窗口内去掉「标签：值」行，避免账户名 / 时间 / 分类标签污染方向与分类判断
     * （例如支付宝详情里的「支付方式 余额宝」曾把退款误判成理财收益）。
     */
    private fun filteredContext(lines: List<String>, index: Int, radius: Int): String {
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + radius + 1).coerceAtMost(lines.size)
        return lines.subList(start, end)
            .filter { matchLabel(it, ALL_LABELS) == null }
            .joinToString(" ")
    }

    /**
     * 本笔账单的「判定区间」：上一条金额行之后、最多向上 3 行，到本行之后 1 行、下一条金额行之前。
     * 限制向下 1 行是为了不把下一笔的「转账-来自 / 退款」标签算到这一笔头上
     * （列表里下一笔的商户标签常常紧跟在本笔金额行之后）。
     */
    private fun ownBlockText(lines: List<String>, anchors: List<Anchor>, order: Int): String {
        val anchorLine = anchors[order].lineIndex
        val start = maxOf((anchors.getOrNull(order - 1)?.lineIndex ?: -1) + 1, anchorLine - 3)
        val end = minOf((anchors.getOrNull(order + 1)?.lineIndex ?: lines.size), anchorLine + 2, lines.size)
        if (start >= end) return lines[anchorLine]
        return lines.subList(start, end).joinToString(" ")
    }

    private fun nearestDate(lines: List<String>, anchorIndex: Int, radius: Int, nowMillis: Long): Long? {
        for (distance in 0..radius) {
            val indexes = if (distance == 0) listOf(anchorIndex) else listOf(anchorIndex - distance, anchorIndex + distance)
            for (index in indexes) {
                if (index !in lines.indices) continue
                dateHitOf(lines[index], index, nowMillis)?.let { return it.millis }
            }
        }
        return null
    }

    private fun labeledDate(lines: List<String>, nowMillis: Long): Long? {
        for (label in DATE_LABELS) {
            val hit = findLabel(lines, listOf(label)) ?: continue
            val value = labelValue(lines, hit)
            if (value.isNotBlank()) dateHitOf(value, hit.first, nowMillis)?.let { return it.millis }
        }
        return null
    }

    private fun uniqueDate(lines: List<String>, nowMillis: Long): Long? {
        val dates = lines.mapNotNull { line -> dateHitOf(line, 0, nowMillis)?.millis }.distinct()
        return dates.singleOrNull()
    }

    private fun confidenceOf(
        hasDate: Boolean,
        hasDirection: Boolean,
        hasMerchant: Boolean,
        amountStrength: Int,
    ): Float = when {
        hasDate && hasDirection -> (
            0.9f +
                (if (hasMerchant) 0.04f else 0f) +
                (if (amountStrength >= 3) 0.02f else 0f)
            ).coerceAtMost(0.99f)
        hasDate -> 0.78f
        hasDirection || hasMerchant -> 0.6f
        else -> 0.4f
    }

    // ────────────────────────── 银行短信 ──────────────────────────

    private val SMS_EXPENSE = Regex(
        """(?:消费|支出|支付|付款|取现|转出|扣款|缴费|交易)\s*(?:人民币)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*元""",
    )
    private val SMS_INCOME = Regex(
        """(?:入账|收入|转入|汇入|到账|代发|发放|存入|收款)\s*(?:人民币)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*元""",
    )
    private val SMS_YUAN_AMOUNT = Regex("""([0-9][0-9,]*(?:\.\d{1,2})?)\s*元""")
    private val SMS_BANK_NAME = Regex("""([\u4e00-\u9fa5]{2,6}银行)""")
    private val SMS_TAIL = Regex("""(?:尾号|末四位|卡号|账号)[^0-9]{0,4}?(\d{4})""")
    private val SMS_EXPENSE_MERCHANT =
        Regex("""(?:向|在)([^，,。;；]{1,16}?)(?:消费|支付|付款|转账|扣款|使用|还款)""")
    private val SMS_INCOME_MERCHANT = Regex("""[（(]([^）)]{1,16})[）)]""")
    private val SMS_INCOME_SOURCE =
        Regex("""(?:来自|由)([^，,。;；]{1,16}?)(?:转入|汇入|入账|代发|发放)""")

    private fun parseSms(
        normalized: String,
        categories: List<Category>,
        nowMillis: Long,
    ): List<ParsedBill> {
        val expenseMatch = SMS_EXPENSE.find(normalized)
        val incomeMatch = SMS_INCOME.find(normalized)
        var amountText: String
        val type: TxType
        when {
            expenseMatch != null && incomeMatch != null -> {
                val expenseFirst = expenseMatch.range.first <= incomeMatch.range.first
                amountText = (if (expenseFirst) expenseMatch else incomeMatch).groupValues[1]
                type = if (expenseFirst) TxType.EXPENSE else TxType.INCOME
            }
            expenseMatch != null -> {
                amountText = expenseMatch.groupValues[1]
                type = TxType.EXPENSE
            }
            incomeMatch != null -> {
                amountText = incomeMatch.groupValues[1]
                type = TxType.INCOME
            }
            else -> {
                val generic = SMS_YUAN_AMOUNT.findAll(normalized).firstOrNull { match ->
                    val tail = normalized.substring(0, match.range.first).takeLast(6)
                    !tail.contains("余额") && !tail.contains("额度") && !tail.contains("可用")
                } ?: return emptyList()
                amountText = generic.groupValues[1]
                type = directionOf(normalized) ?: TxType.EXPENSE
            }
        }
        val cents = parseTokenCents(amountText) ?: return emptyList()
        if (cents <= 0) return emptyList()

        val occurredAt = uniqueDate(normalized.split('\n'), nowMillis)
        val merchant = smsMerchant(normalized, type)
        val bankName = SMS_BANK_NAME.find(normalized)?.value ?: "银行卡"
        val tail = SMS_TAIL.find(normalized)?.groupValues?.get(1)
        val account = if (tail != null) "$bankName 尾号$tail" else bankName
        val categoryId = resolveCategory(type, merchant, "", normalized, categories)
        val confidence = confidenceOf(occurredAt != null, hasDirection = true, merchant.isNotBlank(), amountStrength = 3)
        return listOf(
            ParsedBill(
                source = BillSource.SMS,
                type = type,
                amountCents = cents,
                merchant = merchant,
                occurredAt = occurredAt,
                account = account,
                categoryId = categoryId,
                confidence = confidence,
                rawText = normalized,
            ),
        )
    }

    private fun smsMerchant(normalized: String, type: TxType): String {
        val expenseMerchant = SMS_EXPENSE_MERCHANT.find(normalized)?.groupValues?.get(1)
        val incomeMerchant = SMS_INCOME_MERCHANT.find(normalized)?.groupValues?.get(1)
            ?: SMS_INCOME_SOURCE.find(normalized)?.groupValues?.get(1)
        val raw = if (type == TxType.INCOME) incomeMerchant ?: expenseMerchant else expenseMerchant ?: incomeMerchant
        return cleanMerchant(raw.orEmpty())
    }
}
