package com.suiyu.ledger.ocr

import com.suiyu.ledger.data.DefaultCategories
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 账单解析器单测。
 *
 * 时间相关断言一律使用固定的 [NOW]（2026-11-21 22:00），
 * 因为「缺年份日期」按当前年补全、未来日期回退一年都依赖 now。
 */
class BillParserTest {

    private val categories = DefaultCategories.all

    private val now: Long = LocalDateTime.of(2026, 11, 21, 22, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun parse(text: String): List<ParsedBill> = BillParser.parse(text, categories, now)

    private fun sample(id: String): BillSample = BillSamples.all.first { it.id == id }

    // ────────────────────────── 微信 ──────────────────────────

    @Test
    fun `wechat transfer income detail is parsed as income`() {
        val bills = parse(sample("wechat_income_detail").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.WECHAT, bill.source)
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(2_500L, bill.amountCents)
        assertTrue("商户应包含张伟，实际：${bill.merchant}", bill.merchant.contains("张伟"))
        assertEquals("cat_income_other", bill.categoryId)
        assertEquals("零钱", bill.account)
        assertEquals("2026-11-20", Dates.isoDate(bill.occurredAt!!))
        assertEquals("12:31", Dates.time(bill.occurredAt))
        assertTrue("置信度应 ≥0.9，实际 ${bill.confidence}", bill.confidence >= 0.9f)
    }

    @Test
    fun `wechat expense detail keeps merchant category and time`() {
        val bills = parse(sample("wechat_expense_detail").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.WECHAT, bill.source)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals(2_500L, bill.amountCents)
        assertTrue(bill.merchant.contains("美团"))
        assertEquals("cat_food", bill.categoryId)
        assertEquals("2026-11-20", Dates.isoDate(bill.occurredAt!!))
        assertEquals("12:31", Dates.time(bill.occurredAt))
        assertTrue(bill.confidence >= 0.9f)
    }

    @Test
    fun `wechat list screenshot splits into three bills`() {
        val bills = parse(sample("wechat_list").text)
        assertEquals(3, bills.size)
        assertTrue("结果应按时间倒序", bills.first().occurredAt!! > bills.last().occurredAt!!)
        assertTrue(bills.all { it.source == BillSource.WECHAT })

        val food = bills.first { it.amountCents == 2_500L }
        assertEquals(TxType.EXPENSE, food.type)
        assertEquals("cat_food", food.categoryId)
        assertEquals("2026-11-20", Dates.isoDate(food.occurredAt!!))

        val ride = bills.first { it.amountCents == 1_850L }
        assertEquals(TxType.EXPENSE, ride.type)
        assertEquals("cat_transport", ride.categoryId)
        assertEquals("2026-11-19", Dates.isoDate(ride.occurredAt!!))

        val transfer = bills.first { it.amountCents == 20_000L }
        assertEquals("转账-来自 应识别为收入", TxType.INCOME, transfer.type)
        assertTrue(transfer.merchant.contains("张伟"))
        assertEquals("2026-11-18", Dates.isoDate(transfer.occurredAt!!))
    }

    // ────────────────────────── 支付宝 ──────────────────────────

    @Test
    fun `alipay expense detail reads account and category`() {
        val bills = parse(sample("alipay_expense_detail").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.ALIPAY, bill.source)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals(2_500L, bill.amountCents)
        assertEquals("余额宝", bill.account)
        assertTrue(bill.merchant.contains("美团"))
        assertEquals("cat_food", bill.categoryId)
        assertEquals("2026年11月20日 12:31", Dates.dateTime(bill.occurredAt!!))
    }

    @Test
    fun `alipay list parses refund as income`() {
        val bills = parse(sample("alipay_list").text)
        assertEquals(3, bills.size)
        val refund = bills.first { it.amountCents == 1_200L }
        assertEquals(TxType.INCOME, refund.type)
        assertEquals("cat_reimburse", refund.categoryId)
        assertEquals("2026-11-18", Dates.isoDate(refund.occurredAt!!))
        assertEquals(BillSource.ALIPAY, refund.source)
    }

    // ────────────────────────── 银行短信 / 银行 App ──────────────────────────

    @Test
    fun `bank sms expense parses amount merchant card tail and time`() {
        val bills = parse(sample("bank_sms_expense").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.SMS, bill.source)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals(2_500L, bill.amountCents)
        assertTrue(bill.merchant.contains("美团"))
        assertEquals("cat_food", bill.categoryId)
        assertTrue("账户应带尾号，实际：${bill.account}", bill.account.contains("1234"))
        assertTrue(bill.account.contains("招商银行"))
        assertEquals("2026-11-20", Dates.isoDate(bill.occurredAt!!))
        assertEquals("12:31", Dates.time(bill.occurredAt))
    }

    @Test
    fun `bank sms salary is income with salary category`() {
        val bills = parse(sample("bank_sms_salary").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.SMS, bill.source)
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(850_000L, bill.amountCents)
        assertTrue(bill.merchant.contains("代发工资"))
        assertEquals("cat_salary", bill.categoryId)
        assertEquals("2026-11-15", Dates.isoDate(bill.occurredAt!!))
    }

    @Test
    fun `bank app credit card expense parses header account`() {
        val bills = parse(sample("bank_app_credit").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.BANK, bill.source)
        assertEquals(TxType.EXPENSE, bill.type)
        assertEquals(2_500L, bill.amountCents)
        assertTrue(bill.merchant.contains("美团"))
        assertEquals("cat_food", bill.categoryId)
        assertTrue("账户应取首行抬头，实际：${bill.account}", bill.account.contains("招商银行"))
    }

    @Test
    fun `bank app salary is income with salary category`() {
        val bills = parse(sample("bank_app_salary").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.BANK, bill.source)
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(850_000L, bill.amountCents)
        assertEquals("cat_salary", bill.categoryId)
        assertEquals("2026-11-15", Dates.isoDate(bill.occurredAt!!))
    }

    // ────────────────────────── 退款 / 红包 ──────────────────────────

    @Test
    fun `alipay refund detail is income and maps to reimburse`() {
        val bills = parse(sample("alipay_refund").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(2_500L, bill.amountCents)
        assertEquals("cat_reimburse", bill.categoryId)
        assertTrue(bill.merchant.contains("美团"))
    }

    @Test
    fun `wechat red packet income maps to red packet category`() {
        val bills = parse(sample("wechat_red_packet").text)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(TxType.INCOME, bill.type)
        assertEquals(888L, bill.amountCents)
        assertEquals("cat_redpacket", bill.categoryId)
        assertEquals(BillSource.WECHAT, bill.source)
    }

    // ────────────────────────── 金额 / 日期边界 ──────────────────────────

    @Test
    fun `amount formats support thousands separator currency and cents edge`() {
        val thousands = parse("微信支付\n账单详情\n-¥12,345.67\n商户名称 某商场")
        assertEquals(1, thousands.size)
        assertEquals(1_234_567L, thousands.first().amountCents)
        assertEquals(TxType.EXPENSE, thousands.first().type)

        val oneCent = parse("支付宝\n账单详情\n-0.01\n收款方 某便利店")
        assertEquals(1, oneCent.size)
        assertEquals(1L, oneCent.first().amountCents)
    }

    @Test
    fun `missing year date uses current year and future date rolls back one year`() {
        val current = parse("微信支付\n账单详情\n收款方 张三\n-25.00\n交易时间 11月20日 08:00")
        assertEquals("2026-11-20", Dates.isoDate(current.first().occurredAt!!))

        // now 是 2026-11-21，12月31日 明显在未来 → 应回退到上一年
        val future = parse("微信支付\n账单详情\n收款方 张三\n+25.00\n交易时间 12月31日 10:00")
        val date = Dates.toLocalDate(future.first().occurredAt!!)
        assertEquals(2025, date.year)
        assertEquals(12, date.monthValue)
        assertEquals(31, date.dayOfMonth)
    }

    @Test
    fun `date format with slash and chinese year month day`() {
        val slash = parse("支付宝\n账单详情\n收款方 某店\n-18.50\n创建时间 2026/11/19 20:10")
        assertEquals("2026-11-19", Dates.isoDate(slash.first().occurredAt!!))
        assertEquals("20:10", Dates.time(slash.first().occurredAt!!))

        val chinese = parse("微信支付\n账单详情\n-25.00\n商户名称 某店\n转账时间 2026年11月20日 12:31")
        assertEquals("2026-11-20", Dates.isoDate(chinese.first().occurredAt!!))
    }

    @Test
    fun `confidence levels follow date direction merchant availability`() {
        // 日期 + 金额 + 方向齐全 → ≥0.9
        val full = parse("支付宝\n账单详情\n退款成功\n¥25.00\n收款方 美团外卖\n创建时间 2026-11-20 12:31")
        assertTrue(full.first().confidence >= 0.9f)

        // 缺日期 → 0.6
        val noDate = parse("微信支付\n账单详情\n收款方 老李\n-25.00")
        assertNull(noDate.first().occurredAt)
        assertTrue("缺日期置信度应为 0.6，实际 ${noDate.first().confidence}", noDate.first().confidence == 0.6f)

        // 只有金额 → 0.4
        val onlyAmount = parse("25.00")
        assertEquals(1, onlyAmount.size)
        assertTrue(
            "仅金额置信度应为 0.4，实际 ${onlyAmount.first().confidence}",
            onlyAmount.first().confidence == 0.4f,
        )
    }

    // ────────────────────────── 分类 / 归一化 / 噪声 ──────────────────────────

    @Test
    fun `category matching prefers longest keyword`() {
        // 「美团单车」比「美团」长，应归交通而不是餐饮
        val ride = parse("微信支付\n收支明细\n11月20日 12:31\n美团单车\n-1.50")
        assertEquals("cat_transport", ride.first().categoryId)

        val restaurant = parse("微信支付\n收支明细\n11月20日 12:31\n老张餐厅\n-88.00")
        assertEquals("cat_food", restaurant.first().categoryId)
    }

    @Test
    fun `full width text is normalized`() {
        val bills = parse("微信支付\n账单详情\n收款方 老王\n－２５．００")
        assertEquals(1, bills.size)
        assertEquals(2_500L, bills.first().amountCents)
        assertEquals(TxType.EXPENSE, bills.first().type)
        assertTrue(bills.first().merchant.contains("老王"))
    }

    @Test
    fun `noise text should not produce bills`() {
        assertTrue(parse(sample("noise_chat").text).isEmpty())
        assertTrue(parse("聊天记录\n张三：晚上一起吃饭吗？\n会议室302 第三节课").isEmpty())
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   \n  ").isEmpty())
    }

    @Test
    fun `plain sms without bracket header is recognized`() {
        val bills = parse(
            "您账户尾号8888于11月20日18:05在盒马鲜生消费88.00元，余额500.00元，如有疑问请致电95555。",
        )
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals(BillSource.SMS, bill.source)
        assertEquals(8_800L, bill.amountCents)
        assertTrue(bill.merchant.contains("盒马"))
        assertEquals("cat_grocery", bill.categoryId)
    }

    // ────────────────────────── 转 TxRecord / 样例自测 ──────────────────────────

    @Test
    fun `parsed bill converts to record with stable fields`() {
        val bill = parse(sample("wechat_expense_detail").text).first()
        val record = bill.toRecord("mem_self")
        assertTrue("id 应以 t 开头，实际：${record.id}", record.id.startsWith("t_"))
        assertEquals("mem_self", record.memberId)
        assertEquals(TxType.EXPENSE, record.type)
        assertEquals(2_500L, record.amountCents)
        assertEquals(BillSource.WECHAT, record.source)
        assertEquals("cat_food", record.categoryId)
        assertNotNull(record.occurredAt)
        assertEquals(2_500L, record.amountCents)
        assertTrue(record.amountCents > 0)
    }

    @Test
    fun `app self test runs every sample successfully`() {
        val report = BillSamples.runSelfTest(categories, now)
        assertTrue("样例自测应全部通过，实际 ${report.passedCount}/${report.total}", report.allPassed)
        assertTrue("样例数应 ≥ 10，实际 ${report.total}", report.total >= 10)
    }
}
