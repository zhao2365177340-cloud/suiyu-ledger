package com.suiyu.ledger.ocr

import com.suiyu.ledger.data.DefaultCategories
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.TxType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 一条样例账单的期望值。日期只断言「月 / 日」，因为解析器在缺少年份时用当年补全，
 * 而 App 内自测可能在任何一年跑；年份规则单独由单元测试覆盖。
 */
data class SampleExpectation(
    val type: TxType,
    val amountCents: Long,
    val source: BillSource,
    val merchantContains: String = "",
    val categoryId: String? = null,
    val month: Int? = null,
    val day: Int? = null,
)

/** 一条真实格式的账单样例：既做单元测试素材，也在 App 内「样例自测」里跑通/失败 */
data class BillSample(
    val id: String,
    val title: String,
    val desc: String,
    val text: String,
    val expectations: List<SampleExpectation>,
)

data class SampleCheckResult(
    val sampleId: String,
    val title: String,
    val passed: Boolean,
    /** 失败时的中文原因；通过时为空串 */
    val detail: String,
)

data class SampleReport(val results: List<SampleCheckResult>) {
    val passedCount: Int get() = results.count { it.passed }
    val total: Int get() = results.size
    val allPassed: Boolean get() = results.isNotEmpty() && results.all { it.passed }
}

/**
 * 账单解析样例库。
 *
 * 样例全部按主流 App 的截图 / 短信真实文案整理（微信、支付宝、招商银行 App 与短信、
 * 银行工资入账等），覆盖 12 条格式。用途有两个：
 *  1. JVM 单测直接喂给 [BillParser]，保证正则与规则不回归；
 *  2. App 导入页的「样例自测」折叠区，用户能就地看到解析引擎通过 / 失败情况。
 */
object BillSamples {

    val all: List<BillSample> = listOf(
        BillSample(
            id = "wechat_income_detail",
            title = "微信 · 转账收入详情",
            desc = "转账-来自 / 已存入零钱 / 金额符号被 OCR 识别成 -25.00",
            text = """
                微信支付
                账单详情
                转账-来自 张伟
                当前状态 已存入零钱
                ¥25.00
                -25.00
                转账说明 帮忙垫付
                支付方式 零钱
                转账时间 2026年11月20日 12:31
                交易单号 4200001234202611201234567890
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.INCOME,
                    amountCents = 2_500,
                    source = BillSource.WECHAT,
                    merchantContains = "张伟",
                    categoryId = "cat_income_other",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "wechat_expense_detail",
            title = "微信 · 消费详情",
            desc = "账单详情 / 商户名称 / -25.00",
            text = """
                微信支付
                账单详情
                当前状态 支付成功
                -25.00
                商户名称 美团外卖
                支付方式 零钱
                创建时间 2026年11月20日 12:31
                交易单号 4200001234202611209876543210
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.EXPENSE,
                    amountCents = 2_500,
                    source = BillSource.WECHAT,
                    merchantContains = "美团",
                    categoryId = "cat_food",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "wechat_list",
            title = "微信 · 收支明细列表",
            desc = "一张截图三笔：两笔支出 + 一笔转账收入",
            text = """
                微信支付
                收支明细
                全部账单
                11月20日 12:31
                美团外卖
                -25.00
                11月19日 20:10
                滴滴出行
                -18.50
                11月18日 09:30
                转账-来自 张伟
                +200.00
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.EXPENSE, amountCents = 2_500, source = BillSource.WECHAT,
                    merchantContains = "美团", categoryId = "cat_food", month = 11, day = 20,
                ),
                SampleExpectation(
                    type = TxType.EXPENSE, amountCents = 1_850, source = BillSource.WECHAT,
                    merchantContains = "滴滴", categoryId = "cat_transport", month = 11, day = 19,
                ),
                SampleExpectation(
                    type = TxType.INCOME, amountCents = 20_000, source = BillSource.WECHAT,
                    merchantContains = "张伟", categoryId = "cat_income_other", month = 11, day = 18,
                ),
            ),
        ),
        BillSample(
            id = "alipay_expense_detail",
            title = "支付宝 · 消费详情",
            desc = "支付方式 / 收款方 / 账单分类 / 创建时间",
            text = """
                支付宝
                账单详情
                支付成功
                ¥25.00
                支付方式 余额宝
                收款方 美团外卖
                账单分类 餐饮美食
                创建时间 2026-11-20 12:31
                订单号 2026112022001234567890
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.EXPENSE,
                    amountCents = 2_500,
                    source = BillSource.ALIPAY,
                    merchantContains = "美团",
                    categoryId = "cat_food",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "alipay_list",
            title = "支付宝 · 账单列表",
            desc = "多行商户 + 金额 + 日期，最后一笔为退款收入",
            text = """
                支付宝
                账单
                全部账单
                11月20日 12:31
                美团外卖
                -25.00
                11月19日 20:10
                滴滴出行
                -18.50
                11月18日 09:00
                退款
                +12.00
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.EXPENSE, amountCents = 2_500, source = BillSource.ALIPAY,
                    merchantContains = "美团", categoryId = "cat_food", month = 11, day = 20,
                ),
                SampleExpectation(
                    type = TxType.EXPENSE, amountCents = 1_850, source = BillSource.ALIPAY,
                    merchantContains = "滴滴", categoryId = "cat_transport", month = 11, day = 19,
                ),
                SampleExpectation(
                    type = TxType.INCOME, amountCents = 1_200, source = BillSource.ALIPAY,
                    categoryId = "cat_reimburse", month = 11, day = 18,
                ),
            ),
        ),
        BillSample(
            id = "bank_sms_expense",
            title = "招商银行 · 消费短信",
            desc = "【银行】账户尾号 + 时间 + 向某商户消费 + 余额",
            text = """
                【招商银行】您账户尾号1234于11月20日12:31向美团外卖消费人民币25.00元，余额1,234.56元。如有疑问请致电95555。
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.EXPENSE,
                    amountCents = 2_500,
                    source = BillSource.SMS,
                    merchantContains = "美团",
                    categoryId = "cat_food",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "bank_sms_salary",
            title = "招商银行 · 工资入账短信",
            desc = "入账人民币 + 代发工资 + 余额",
            text = """
                【招商银行】您账户尾号1234于11月15日10:00入账人民币8,500.00元（代发工资），余额25,600.00元。
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.INCOME,
                    amountCents = 850_000,
                    source = BillSource.SMS,
                    merchantContains = "代发工资",
                    categoryId = "cat_salary",
                    month = 11,
                    day = 15,
                ),
            ),
        ),
        BillSample(
            id = "bank_app_credit",
            title = "银行 App · 信用卡消费",
            desc = "账户抬头 + 日期 + 消费 + 商户 + 人民币金额",
            text = """
                招商银行信用卡
                11月20日 12:31
                消费
                美团外卖
                人民币 -25.00
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.EXPENSE,
                    amountCents = 2_500,
                    source = BillSource.BANK,
                    merchantContains = "美团",
                    categoryId = "cat_food",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "bank_app_salary",
            title = "银行 App · 工资入账",
            desc = "工资入账 / +8,500.00 / 代发工资",
            text = """
                招商银行
                工资入账
                +8,500.00
                11月15日 09:30
                代发工资
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.INCOME,
                    amountCents = 850_000,
                    source = BillSource.BANK,
                    merchantContains = "代发工资",
                    categoryId = "cat_salary",
                    month = 11,
                    day = 15,
                ),
            ),
        ),
        BillSample(
            id = "wechat_red_packet",
            title = "微信 · 红包收入",
            desc = "收到红包 / +8.88 / 已存入零钱",
            text = """
                微信支付
                收到红包
                +8.88
                当前状态 已存入零钱
                支付方式 零钱
                交易时间 2026年11月20日 20:15
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.INCOME,
                    amountCents = 888,
                    source = BillSource.WECHAT,
                    merchantContains = "红包",
                    categoryId = "cat_redpacket",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "alipay_refund",
            title = "支付宝 · 退款收入",
            desc = "退款成功 / 退款金额 +25.00 / 收款方 / 余额宝",
            text = """
                支付宝
                账单详情
                退款成功
                ¥25.00
                退款金额 +25.00
                收款方 美团外卖
                支付方式 余额宝
                创建时间 2026-11-20 14:05
            """.trimIndent(),
            expectations = listOf(
                SampleExpectation(
                    type = TxType.INCOME,
                    amountCents = 2_500,
                    source = BillSource.ALIPAY,
                    merchantContains = "美团",
                    categoryId = "cat_reimburse",
                    month = 11,
                    day = 20,
                ),
            ),
        ),
        BillSample(
            id = "noise_chat",
            title = "噪声文本 · 不应识别",
            desc = "聊天记录、年份、人数等数字不能被当成金额",
            text = """
                聊天记录
                张三：晚上一起吃饭吗？
                李四：好呀，我请客，人均 88 左右
                周末去爬山，2026年11月20日 星期五 第3节课
            """.trimIndent(),
            expectations = emptyList(),
        ),
    )

    /**
     * 跑一遍全部样例并返回通过 / 失败明细。
     * 失败信息写成中文，直接展示在导入页的「样例自测」里。
     */
    fun runSelfTest(
        categories: List<Category> = DefaultCategories.all,
        nowMillis: Long = System.currentTimeMillis(),
    ): SampleReport = SampleReport(all.map { check(it, categories, nowMillis) })

    private fun check(
        sample: BillSample,
        categories: List<Category>,
        nowMillis: Long,
    ): SampleCheckResult {
        val parsed = runCatching { BillParser.parse(sample.text, categories, nowMillis) }
            .getOrElse { error ->
                return SampleCheckResult(
                    sample.id,
                    sample.title,
                    false,
                    "解析异常：${error.message ?: error::class.java.simpleName}",
                )
            }
        val issues = ArrayList<String>()
        if (parsed.size != sample.expectations.size) {
            issues += "笔数 ${parsed.size}，期望 ${sample.expectations.size}"
        }
        val remaining = parsed.toMutableList()
        for (expected in sample.expectations) {
            val match = remaining.firstOrNull { it.type == expected.type && it.amountCents == expected.amountCents }
            if (match == null) {
                issues += "缺少「${expected.type.label} ${yuan(expected.amountCents)}」"
                continue
            }
            remaining.remove(match)
            val label = "${expected.type.label} ${yuan(expected.amountCents)}"
            if (match.source != expected.source) {
                issues += "$label：来源 ${match.source.label}，期望 ${expected.source.label}"
            }
            if (expected.merchantContains.isNotBlank() && !match.merchant.contains(expected.merchantContains)) {
                issues += "$label：商户「${match.merchant}」不含「${expected.merchantContains}」"
            }
            if (expected.categoryId != null && match.categoryId != expected.categoryId) {
                issues += "$label：分类 ${categoryName(match.categoryId, categories)}，期望 ${categoryName(expected.categoryId, categories)}"
            }
            if (expected.month != null && expected.day != null) {
                val date = match.occurredAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                if (date == null) {
                    issues += "$label：缺少日期"
                } else if (date.monthValue != expected.month || date.dayOfMonth != expected.day) {
                    issues += "$label：日期 ${date.monthValue}月${date.dayOfMonth}日，期望 ${expected.month}月${expected.day}日"
                }
            }
        }
        return SampleCheckResult(sample.id, sample.title, issues.isEmpty(), issues.joinToString("；"))
    }

    private fun categoryName(id: String?, categories: List<Category>): String =
        categories.firstOrNull { it.id == id }?.name ?: (id ?: "未分类")

    private fun yuan(cents: Long): String = String.format(Locale.CHINA, "¥%.2f", cents / 100.0)
}
