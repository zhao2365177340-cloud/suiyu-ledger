package com.suiyu.ledger.model

/**
 * 岁余 · 领域模型
 *
 * 设计约定：
 * - 金额一律使用「分」为单位的 Long，避免浮点误差（税务模块尤其不能出现 0.01 的漂移）；
 * - 成员 / 分类使用字符串 id，便于 JSON 本地持久化与后续同步扩展；
 * - 所有 UI 需要的展示信息都从模型派生，不在模型里保存格式化字符串。
 */

enum class TxType(val label: String, val sign: Int) {
    EXPENSE("支出", -1),
    INCOME("收入", 1);

    fun opposite(): TxType = if (this == EXPENSE) INCOME else EXPENSE
}

/** 账单来源：手动、截图识别或粘贴文本识别 */
enum class BillSource(val label: String) {
    MANUAL("手动记账"),
    WECHAT("微信支付"),
    ALIPAY("支付宝"),
    BANK("银行账单"),
    SMS("短信账单"),
    OTHER("其他来源");

    /** 短标签，用于列表角标 */
    val short: String
        get() = when (this) {
            MANUAL -> "手动"
            WECHAT -> "微信"
            ALIPAY -> "支付宝"
            BANK -> "银行"
            SMS -> "短信"
            OTHER -> "导入"
        }

    val isOcr: Boolean
        get() = this != MANUAL

    companion object {
        fun of(key: String?): BillSource =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: MANUAL
    }
}

/** 家庭成员（带自添加名称标签） */
data class Member(
    val id: String,
    val name: String,
    /** 头像 emoji，例如 🙂 👩 👦 */
    val emoji: String = "🙂",
    /** 主题色，形如 "#24483E" */
    val colorHex: String = "#24483E",
    /** 身份标签：本人 / 配偶 / 子女 / 父母 / 其他 */
    val role: String = "其他",
    /** 是否「我」——默认记账归属 */
    val isSelf: Boolean = false,
    val order: Int = 0,
) {
    val initial: String get() = name.take(1)
}

/** 收支分类 */
data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val type: TxType,
    val colorHex: String,
    val builtin: Boolean = true,
    /** OCR 归类关键词，命中则归入该分类 */
    val keywords: List<String> = emptyList(),
    val order: Int = 0,
)

/** 一笔收支记录 */
data class TxRecord(
    val id: String,
    val type: TxType,
    /** 金额（分），始终为正；方向由 type 决定 */
    val amountCents: Long,
    val categoryId: String,
    val memberId: String,
    val occurredAt: Long,
    val note: String = "",
    /** 交易对方 / 商户 */
    val merchant: String = "",
    /** 支付账户：微信零钱 / 支付宝 / 招商银行储蓄卡 / 现金 ... */
    val account: String = "",
    val source: BillSource = BillSource.MANUAL,
    /** 来源截图或文本在本机的引用（不参与统计） */
    val sourceRef: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 是否纳入统计（如内部转账可置为 false） */
    val includeInStats: Boolean = true,
) {
    /** 带符号的分值，便于直接求和 */
    val signedCents: Long get() = if (type == TxType.EXPENSE) -amountCents else amountCents
}

/** 资产分类：liquidity 3=随时可用 2=较快变现 1=较难变现 0=自用资产 */
enum class AssetCategory(
    val label: String,
    val group: String,
    val liquidity: Int,
    val emoji: String,
) {
    CASH("现金与活期", "现金类", 3, "💵"),
    DEPOSIT("定期存款", "现金类", 2, "🏦"),
    WEALTH("理财 / 货币基金", "金融资产", 2, "📈"),
    INVEST("股票 / 基金 / 债券", "金融资产", 1, "📊"),
    INSURANCE("保单现金价值", "金融资产", 1, "🛡️"),
    PROPERTY_HOME("自住房产", "房产", 0, "🏠"),
    PROPERTY_INVEST("投资房产", "房产", 0, "🏢"),
    VEHICLE("车辆", "其他资产", 0, "🚗"),
    RECEIVABLE("借出 / 应收", "其他资产", 1, "🤝"),
    OTHER("其他资产", "其他资产", 1, "📦");

    val isCashLike: Boolean get() = liquidity >= 2
    val isFinancial: Boolean get() = this == CASH || this == DEPOSIT || this == WEALTH || this == INVEST || this == INSURANCE
}

/** 负债分类 */
enum class LiabilityCategory(val label: String, val emoji: String) {
    MORTGAGE("房贷", "🏠"),
    CAR_LOAN("车贷", "🚗"),
    CONSUMER_LOAN("消费贷", "💳"),
    CREDIT_CARD("信用卡", "🧾"),
    BORROW("亲友借款", "🤝"),
    OTHER("其他负债", "📎");

    companion object {
        fun of(key: String?): LiabilityCategory =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: OTHER
    }
}

/** 单位性质（影响收入稳定性评估） */
enum class EmploymentType(val label: String) {
    GOVERNMENT("体制内 / 国企"),
    PRIVATE("民营企业"),
    FOREIGN("外企"),
    FREELANCE("自由职业 / 个体"),
    RETIRED("退休"),
    OTHER("其他");

    companion object {
        fun of(key: String?): EmploymentType =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: PRIVATE
    }
}

/** 用户自填的资产（存款、金融资产、房产、车辆、其他） */
data class Asset(
    val id: String,
    val name: String,
    val category: AssetCategory,
    /** 当前估值（分） */
    val valueCents: Long,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 用户自填的负债（房贷、车贷、消费贷、信用卡、借款等） */
data class Liability(
    val id: String,
    val name: String,
    val category: LiabilityCategory,
    /** 当前剩余本金 / 欠款（分） */
    val balanceCents: Long,
    /** 每月还款额（分），用于月供收入比 */
    val monthlyPaymentCents: Long = 0L,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 工作与家庭情况：财务风险评估中「收入稳定性 / 负担 / 保障」的输入 */
data class FinancialProfile(
    /** 职业与行业，如「产品经理 · 互联网」 */
    val occupation: String = "",
    val employmentType: EmploymentType = EmploymentType.PRIVATE,
    /** 当前工作年限 */
    val jobYears: Int = 0,
    /** 收入稳定性自评：1 波动 / 2 一般 / 3 稳定 */
    val incomeStability: Int = 2,
    val spouseWorks: Boolean = true,
    /** 赡养老人数 */
    val elderlyCount: Int = 0,
    /** 子女数 */
    val childrenCount: Int = 0,
    val hasMedicalInsurance: Boolean = true,
    val hasCriticalIllnessInsurance: Boolean = false,
    val hasLifeInsurance: Boolean = false,
    /** 重疾 / 寿险保额（分） */
    val insuranceCoverageCents: Long = 0L,
    /** 手动固定月收入（分）：0 = 自动按近 6 个月工资流水估算 */
    val manualMonthlyIncomeCents: Long = 0L,
    /** 手动固定月支出（分）：0 = 自动按近 6 个月刚性支出 + 月供估算 */
    val manualMonthlyFixedExpenseCents: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** 全局设置（随账本一起本地持久化） */
data class AppSettings(
    val familyName: String = "我家",
    /** 触感总开关：设置页可一键关闭 */
    val hapticsEnabled: Boolean = true,
    /** 触感强度 1 轻 / 2 标准 / 3 明显 */
    val hapticStrength: Int = 2,
    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    /** 是否已写入示例数据 */
    val seededDemo: Boolean = false,
    /** 首次使用引导是否完成 */
    val onboarded: Boolean = false,
    /** 是否隐藏首页金额（隐私模式） */
    val privacyMask: Boolean = false,
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}

/** 账本聚合根（持久化单元） */
data class LedgerData(
    val version: Int = 2,
    val members: List<Member> = emptyList(),
    val categories: List<Category> = emptyList(),
    val records: List<TxRecord> = emptyList(),
    val settings: AppSettings = AppSettings.DEFAULT,
    /** 商户 → 分类 的纠正记忆，用于截图导入的「规则学习」 */
    val merchantRules: Map<String, String> = emptyMap(),
    /** 用户自填资产（v1.2 起） */
    val assets: List<Asset> = emptyList(),
    /** 用户自填负债（v1.2 起） */
    val liabilities: List<Liability> = emptyList(),
    /** 工作与家庭情况（v1.2 起） */
    val profile: FinancialProfile = FinancialProfile(),
)
