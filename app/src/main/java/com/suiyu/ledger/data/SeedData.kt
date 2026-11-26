package com.suiyu.ledger.data

import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.AssetCategory
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.EmploymentType
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.model.LiabilityCategory
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.MonthKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 示例数据：首次启动自动写入，让「统计 / 报表 / 同比环比 / 税务测算」开箱即可体验。
 * 数据是虚构的，覆盖最近 16 个月（含去年同月，保证同比有数），并含部分「截图识别」来源，
 * 用于演示来源角标与去重逻辑。设置页可一键清空。
 */
object SeedData {

    val members: List<Member> = listOf(
        Member(id = "mem_self", name = "我", emoji = "🙂", colorHex = "#24483E", role = "本人", isSelf = true, order = 0),
        Member(id = "mem_spouse", name = "爱人", emoji = "🌸", colorHex = "#B4432E", role = "配偶", order = 1),
        Member(id = "mem_child", name = "小余", emoji = "🧒", colorHex = "#B08A4A", role = "子女", order = 2),
    )

    private const val SELF = "mem_self"
    private const val SPOUSE = "mem_spouse"
    private const val CHILD = "mem_child"

    private val accounts = listOf("微信零钱", "支付宝", "招商银行储蓄卡", "建设银行信用卡", "现金")
    private val sources = listOf(BillSource.WECHAT, BillSource.ALIPAY, BillSource.BANK, BillSource.SMS)

    fun demoRecords(now: Long = System.currentTimeMillis()): List<TxRecord> {
        val random = Random(20261121)
        val zone = ZoneId.systemDefault()
        val endMonth = MonthKey.of(now)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val records = ArrayList<TxRecord>(900)
        var seq = 0

        fun epoch(month: MonthKey, day: Int, hour: Int, minute: Int): Long {
            val safeDay = day.coerceIn(1, LocalDate.of(month.year, month.month, 1).lengthOfMonth())
            return LocalDate.of(month.year, month.month, safeDay)
                .atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                .atZone(zone).toInstant().toEpochMilli()
        }

        fun add(
            type: TxType,
            cents: Long,
            categoryId: String,
            memberId: String,
            occurredAt: Long,
            merchant: String = "",
            account: String = "",
            source: BillSource = BillSource.MANUAL,
            note: String = "",
        ) {
            seq += 1
            records += TxRecord(
                id = "t_seed_%04d".format(seq),
                type = type,
                amountCents = cents,
                categoryId = categoryId,
                memberId = memberId,
                occurredAt = occurredAt,
                note = note,
                merchant = merchant,
                account = account.ifEmpty { randomAccount(random) },
                source = source,
                createdAt = occurredAt,
            )
        }

        fun pick(vararg ids: String): String = ids[random.nextInt(ids.size)]

        fun maybeOcr(): BillSource =
            if (random.nextFloat() < 0.34f) sources[random.nextInt(sources.size)] else BillSource.MANUAL

        fun jitter(baseCents: Long, spread: Double = 0.35): Long {
            val factor = 1.0 - spread / 2 + random.nextDouble() * spread
            return (baseCents * factor).roundToLong().coerceAtLeast(1)
        }

        for (offset in -15..0) {
            val month = endMonth.plusMonths(offset)
            val lastDay = if (offset == 0) today.dayOfMonth else LocalDate.of(month.year, month.month, 1).lengthOfMonth()
            fun day(max: Int = lastDay): Int = random.nextInt(1, max.coerceAtLeast(1) + 1)
            val season = month.month

            // ── 固定收入：工资 ─────────────────────────────
            add(TxType.INCOME, 3_200_000L, "cat_salary", SELF, epoch(month, 10, 9, 30), "某某科技有限公司", "招商银行储蓄卡", BillSource.BANK, "月度工资")
            add(TxType.INCOME, 2_400_000L, "cat_salary", SPOUSE, epoch(month, 15, 10, 5), "某某设计事务所", "建设银行储蓄卡", BillSource.BANK, "月度工资")

            // ── 年终奖（1 月）────────────────────────────
            if (season == 1) {
                add(TxType.INCOME, 5_800_000L, "cat_bonus", SELF, epoch(month, 20, 11, 0), "某某科技有限公司", "招商银行储蓄卡", BillSource.BANK, "年终奖")
                add(TxType.INCOME, 3_200_000L, "cat_bonus", SPOUSE, epoch(month, 22, 11, 0), "某某设计事务所", "建设银行储蓄卡", BillSource.BANK, "年终奖")
            }

            // ── 固定支出 ────────────────────────────────
            add(TxType.EXPENSE, 680_000L, "cat_housing", SELF, epoch(month, 5, 8, 0), "招商银行·住房贷款", "招商银行储蓄卡", BillSource.BANK, "房贷月供")
            add(TxType.EXPENSE, jitter(46_000L, 0.2), "cat_housing", SPOUSE, epoch(month, 8, 9, 0), "小区物业", "招商银行储蓄卡", BillSource.MANUAL, "物业费")
            add(TxType.EXPENSE, jitter(28_000L, 0.5), "cat_housing", SPOUSE, epoch(month, 12, 20, 0), "国家电网", "支付宝", maybeOcr(), "电费")
            add(TxType.EXPENSE, jitter(12_000L, 0.4), "cat_housing", SELF, epoch(month, 13, 20, 0), "自来水公司", "支付宝", maybeOcr(), "水费")
            add(TxType.EXPENSE, 12_900L, "cat_telecom", SELF, epoch(month, 20, 10, 0), "中国移动", "微信零钱", BillSource.SMS, "话费")
            add(TxType.EXPENSE, 4_700L, "cat_telecom", SPOUSE, epoch(month, 21, 10, 0), "中国联通", "微信零钱", BillSource.SMS, "话费")

            // ── 孩子教育 ────────────────────────────────
            add(TxType.EXPENSE, 220_000L, "cat_education", CHILD, epoch(month, 3, 9, 0), "阳光幼儿园", "招商银行储蓄卡", BillSource.MANUAL, "保教费")
            if (random.nextFloat() < 0.55f) {
                add(TxType.EXPENSE, jitter(68_000L, 0.6), "cat_education", CHILD, epoch(month, day(), 18, 0), pick("少儿编程", "美术课", "游泳课"), "微信零钱", maybeOcr(), "课外班")
            }

            // ── 餐饮 ────────────────────────────────────
            val diningCount = if (season in listOf(2, 5, 10)) 15 else 12
            repeat(diningCount) {
                val merchant = pick(
                    "星巴克", "瑞幸咖啡", "喜茶", "霸王茶姬", "麦当劳", "肯德基", "老乡鸡", "和府捞面",
                    "西贝莜面村", "海底捞", "美团外卖", "饿了么", "巷口小馆", "沙县小吃", "南城香",
                )
                val member = if (random.nextFloat() < 0.45f) SPOUSE else SELF
                add(
                    TxType.EXPENSE, jitter(if (merchant.contains("外卖")) 4_600L else 8_800L, 1.2),
                    "cat_food", member, epoch(month, day(), random.nextInt(7, 21), random.nextInt(0, 60)),
                    merchant, randomAccount(random), maybeOcr(),
                )
            }

            // ── 买菜 / 日用 ─────────────────────────────
            repeat(9) {
                add(
                    TxType.EXPENSE, jitter(13_500L, 0.9), "cat_grocery",
                    if (random.nextFloat() < 0.4f) SPOUSE else SELF,
                    epoch(month, day(), random.nextInt(8, 21), random.nextInt(0, 60)),
                    pick("盒马鲜生", "永辉超市", "钱大妈", "山姆会员店", "菜市场", "全家便利店", "叮咚买菜"),
                    randomAccount(random), maybeOcr(),
                )
            }

            // ── 交通 ────────────────────────────────────
            repeat(10) {
                add(
                    TxType.EXPENSE, jitter(1_200L, 1.5), "cat_transport", SELF,
                    epoch(month, day(), random.nextInt(7, 22), random.nextInt(0, 60)),
                    pick("地铁出行", "滴滴出行", "公交", "高铁票", "停车场", "中石化加油", "哈啰单车"),
                    pick("微信零钱", "支付宝", "现金"), maybeOcr(),
                )
            }

            // ── 购物 ────────────────────────────────────
            repeat(4) {
                add(
                    TxType.EXPENSE, jitter(46_000L, 2.2), "cat_shopping",
                    if (random.nextFloat() < 0.5f) SPOUSE else SELF,
                    epoch(month, day(), random.nextInt(9, 23), random.nextInt(0, 60)),
                    pick("淘宝", "京东", "优衣库", "名创优品", "小米之家", "Apple Store", "抖音电商"),
                    randomAccount(random), maybeOcr(),
                )
            }

            // ── 娱乐 / 健身 ─────────────────────────────
            repeat(2) {
                add(
                    TxType.EXPENSE, jitter(12_000L, 1.2), "cat_entertainment",
                    if (random.nextFloat() < 0.3f) CHILD else SELF,
                    epoch(month, day(), random.nextInt(10, 22), random.nextInt(0, 60)),
                    pick("万达影城", "腾讯视频会员", "乐刻健身", "西西弗书店", "大麦网", "剧本杀"),
                    randomAccount(random), maybeOcr(),
                )
            }

            // ── 医疗 ────────────────────────────────────
            if (random.nextFloat() < 0.75f) {
                add(
                    TxType.EXPENSE, jitter(22_000L, 2.0), "cat_medical",
                    if (random.nextFloat() < 0.6f) CHILD else SPOUSE,
                    epoch(month, day(), random.nextInt(9, 20), random.nextInt(0, 60)),
                    pick("市第一医院", "叮当快药", "口腔诊所", "儿童医院", "社区卫生服务中心"),
                    randomAccount(random), maybeOcr(),
                )
            }

            // ── 旅行（寒暑假 / 国庆）──────────────────────
            if (season in listOf(1, 2, 7, 8, 10)) {
                add(
                    TxType.EXPENSE, jitter(268_000L, 1.8), "cat_travel", SELF,
                    epoch(month, day(), 12, 0),
                    pick("携程旅行", "飞猪", "亚朵酒店", "景区门票"),
                    "建设银行信用卡", maybeOcr(),
                )
            }

            // ── 人情 ────────────────────────────────────
            if (season == 2) {
                repeat(2) {
                    add(
                        TxType.EXPENSE, jitter(120_000L, 1.2), "cat_social",
                        if (random.nextFloat() < 0.5f) SELF else SPOUSE,
                        epoch(month, day(), random.nextInt(9, 22), 0),
                        pick("春节红包", "长辈礼金", "家庭聚餐"), "微信零钱", BillSource.WECHAT,
                    )
                }
                add(TxType.INCOME, 520_000L, "cat_redpacket", CHILD, epoch(month, 1, 12, 0), "压岁钱", "微信零钱", BillSource.WECHAT)
            }
            if (random.nextFloat() < 0.4f) {
                add(
                    TxType.EXPENSE, jitter(88_000L, 1.0), "cat_social",
                    if (random.nextFloat() < 0.5f) SELF else SPOUSE,
                    epoch(month, day(), 18, 0), pick("婚礼份子钱", "生日礼物", "朋友聚餐"), "微信零钱", BillSource.WECHAT,
                )
            }

            // ── 其他支出 ────────────────────────────────
            if (random.nextFloat() < 0.5f) {
                add(TxType.EXPENSE, jitter(6_000L, 2.0), "cat_other", SELF, epoch(month, day(), 12, 0), "其他支出", "微信零钱", maybeOcr())
            }

            // ── 非工资收入 ──────────────────────────────
            add(TxType.INCOME, jitter(18_000L, 1.6), "cat_invest", SELF, epoch(month, 21, 15, 0), pick("余额宝收益", "基金分红", "银行理财收益"), "支付宝", BillSource.MANUAL)
            if (offset % 4 == 0) {
                add(TxType.INCOME, jitter(320_000L, 1.4), "cat_side", SPOUSE, epoch(month, day(), 20, 0), pick("设计稿费", "课程课时费", "咨询费"), "支付宝", BillSource.MANUAL)
            }
            if (random.nextFloat() < 0.5f) {
                add(TxType.INCOME, jitter(58_000L, 1.4), "cat_reimburse", SELF, epoch(month, day(), 11, 0), pick("差旅报销", "退货退款", "医保报销"), "招商银行储蓄卡", BillSource.BANK)
            }
            if (random.nextFloat() < 0.35f) {
                add(TxType.INCOME, jitter(30_000L, 2.0), "cat_redpacket", SPOUSE, epoch(month, day(), 21, 0), "亲友转账", "微信零钱", BillSource.WECHAT)
            }
        }

        return records.sortedByDescending { it.occurredAt }
    }

    private fun randomAccount(random: Random): String = accounts[random.nextInt(accounts.size)]

    // ────────────────────────── 财务体检演示数据（v1.2）──────────────────────────

    /** 演示资产：现金类 + 金融资产 + 自住房 + 车辆，贴近一线城市三口之家 */
    fun demoAssets(): List<Asset> = listOf(
        Asset(id = "asset_demo_cash", name = "招行活期", category = AssetCategory.CASH, valueCents = 68_000_00L, note = "日常开销与应急金"),
        Asset(id = "asset_demo_deposit", name = "三年期定期", category = AssetCategory.DEPOSIT, valueCents = 200_000_00L, note = "家庭备用金"),
        Asset(id = "asset_demo_wealth", name = "银行理财 + 货币基金", category = AssetCategory.WEALTH, valueCents = 150_000_00L),
        Asset(id = "asset_demo_invest", name = "指数基金定投", category = AssetCategory.INVEST, valueCents = 120_000_00L, note = "每月定投 2000"),
        Asset(id = "asset_demo_insurance", name = "重疾险现金价值", category = AssetCategory.INSURANCE, valueCents = 45_000_00L),
        Asset(id = "asset_demo_home", name = "自住房（按当前市值估算）", category = AssetCategory.PROPERTY_HOME, valueCents = 3_200_000_00L),
        Asset(id = "asset_demo_car", name = "家庭用车", category = AssetCategory.VEHICLE, valueCents = 150_000_00L),
        Asset(id = "asset_demo_lend", name = "借给亲友", category = AssetCategory.RECEIVABLE, valueCents = 20_000_00L),
    )

    /** 演示负债：房贷 + 消费贷 + 信用卡 */
    fun demoLiabilities(): List<Liability> = listOf(
        Liability(
            id = "liability_demo_mortgage",
            name = "招行住房贷款",
            category = LiabilityCategory.MORTGAGE,
            balanceCents = 1_800_000_00L,
            monthlyPaymentCents = 6_800_00L,
            note = "剩余 12 年",
        ),
        Liability(
            id = "liability_demo_consumer",
            name = "装修分期",
            category = LiabilityCategory.CONSUMER_LOAN,
            balanceCents = 20_000_00L,
            monthlyPaymentCents = 1_200_00L,
        ),
        Liability(
            id = "liability_demo_credit",
            name = "信用卡账单",
            category = LiabilityCategory.CREDIT_CARD,
            balanceCents = 3_500_00L,
        ),
    )

    /** 演示工作与家庭情况 */
    fun demoProfile(): FinancialProfile = FinancialProfile(
        occupation = "产品经理 · 互联网",
        employmentType = EmploymentType.PRIVATE,
        jobYears = 8,
        incomeStability = 2,
        spouseWorks = true,
        elderlyCount = 2,
        childrenCount = 1,
        hasMedicalInsurance = true,
        hasCriticalIllnessInsurance = true,
        hasLifeInsurance = false,
        insuranceCoverageCents = 500_000_00L,
    )
}
