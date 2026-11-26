package com.suiyu.ledger.data

import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.TxType

/**
 * 内置分类：先粗后细，默认 13 个支出分类 + 7 个收入分类。
 * keywords 同时服务两个场景：
 *  1. 截图 / 短信账单识别时把商户名映射到分类；
 *  2. 「规则学习」没有命中时，作为兜底猜测。
 */
object DefaultCategories {

    val expense: List<Category> = listOf(
        Category(
            id = "cat_food", name = "餐饮", emoji = "🍜", type = TxType.EXPENSE, colorHex = "#B4432E", order = 0,
            keywords = listOf(
                "餐", "饭", "食", "面", "粉", "饺", "火锅", "烧烤", "麻辣烫", "小吃", "早点", "早餐", "午餐", "晚餐", "夜宵",
                "咖啡", "奶茶", "茶饮", "星巴克", "瑞幸", "麦当劳", "肯德基", "必胜客", "汉堡", "披萨", "美团", "饿了么",
                "外卖", "食堂", "酒楼", "餐厅", "料理", "寿司", "烘焙", "面包", "蛋糕", "甜品", "酒吧", "聚餐", "买单"
            )
        ),
        Category(
            id = "cat_grocery", name = "买菜", emoji = "🥬", type = TxType.EXPENSE, colorHex = "#7E8C6B", order = 1,
            keywords = listOf(
                "超市", "盒马", "永辉", "沃尔玛", "家乐福", "华润", "物美", "菜市场", "菜场", "生鲜", "水果", "蔬菜",
                "便利", "全家", "罗森", "便利蜂", "7-11", "711", "钱大妈", "叮咚", "朴朴", "山姆", "Costco", "粮油", "海鲜"
            )
        ),
        Category(
            id = "cat_transport", name = "交通", emoji = "🚇", type = TxType.EXPENSE, colorHex = "#3E6B8A", order = 2,
            keywords = listOf(
                "地铁", "公交", "巴士", "打车", "滴滴", "出租", "网约车", "曹操", "T3", "高铁", "动车", "火车", "12306",
                "机票", "航空", "机场", "加油", "中石化", "中石油", "停车", "ETC", "高速", "共享单车", "哈啰", "青桔",
                "美团单车", "充电", "车费", "路费", "过路"
            )
        ),
        Category(
            id = "cat_shopping", name = "购物", emoji = "🛍️", type = TxType.EXPENSE, colorHex = "#6E5F8A", order = 3,
            keywords = listOf(
                "淘宝", "天猫", "京东", "拼多多", "唯品会", "抖音商城", "商城", "商场", "百货", "优衣库", "MUJI", "无印良品",
                "服饰", "衣服", "鞋", "帽", "箱包", "化妆品", "护肤", "数码", "手机", "电脑", "耳机", "苹果", "Apple",
                "华为", "小米", "戴森", "网购", "快递", "日用", "家居", "宜家", "名创优品", "饰品"
            )
        ),
        Category(
            id = "cat_housing", name = "居住", emoji = "🏠", type = TxType.EXPENSE, colorHex = "#8A7B6B", order = 4,
            keywords = listOf(
                "房租", "租金", "房贷", "月供", "物业", "水费", "电费", "燃气", "天然气", "取暖", "宽带", "中介",
                "公寓", "家政", "保洁", "维修", "装修", "家具", "家电"
            )
        ),
        Category(
            id = "cat_telecom", name = "通讯", emoji = "📱", type = TxType.EXPENSE, colorHex = "#4F8A86", order = 5,
            keywords = listOf("话费", "流量", "移动", "联通", "电信", "充话费", "套餐", "手机费", "宽带费")
        ),
        Category(
            id = "cat_medical", name = "医疗", emoji = "💊", type = TxType.EXPENSE, colorHex = "#A8746A", order = 6,
            keywords = listOf(
                "医院", "药", "药店", "诊所", "门诊", "急诊", "挂号", "体检", "牙科", "口腔", "眼科", "疫苗",
                "医保", "健康", "理疗", "康复", "卫生"
            )
        ),
        Category(
            id = "cat_education", name = "教育", emoji = "📚", type = TxType.EXPENSE, colorHex = "#B99A5C", order = 7,
            keywords = listOf(
                "学费", "培训", "课程", "书店", "图书", "教材", "网课", "幼儿园", "学校", "考试", "报名", "家教",
                "辅导", "知识付费", "得到", "樊登", "文具", "学习"
            )
        ),
        Category(
            id = "cat_entertainment", name = "娱乐", emoji = "🎬", type = TxType.EXPENSE, colorHex = "#B4432E", order = 8,
            keywords = listOf(
                "电影", "影院", "演出", "话剧", "演唱会", "KTV", "音乐", "游戏", "Steam", "视频会员", "腾讯视频",
                "爱奇艺", "优酷", "芒果", "Netflix", "剧本杀", "桌游", "健身", "游泳", "瑜伽", "球", "露营", "门票"
            )
        ),
        Category(
            id = "cat_social", name = "人情", emoji = "🧧", type = TxType.EXPENSE, colorHex = "#C05A4A", order = 9,
            keywords = listOf("红包", "礼金", "份子", "婚礼", "生日", "礼物", "请客", "捐赠", "慈善", "慰问", "过节")
        ),
        Category(
            id = "cat_travel", name = "旅行", emoji = "✈️", type = TxType.EXPENSE, colorHex = "#5B8FA8", order = 10,
            keywords = listOf(
                "酒店", "民宿", "携程", "飞猪", "去哪儿", "同程", "景区", "度假", "旅行", "旅游", "导游", "签证",
                "门票", "客栈", "青旅", "邮轮"
            )
        ),
        Category(
            id = "cat_pet", name = "宠物", emoji = "🐾", type = TxType.EXPENSE, colorHex = "#8C7B5A", order = 11,
            keywords = listOf("宠物", "猫粮", "狗粮", "兽医", "宠物医院", "寄养", "猫砂", "洗澡", "驱虫", "宠物店")
        ),
        Category(
            id = "cat_other", name = "其他", emoji = "📎", type = TxType.EXPENSE, colorHex = "#9A9487", order = 12,
            keywords = listOf("其他", "手续费", "罚款", "赔偿", "杂费", "服务费")
        ),
    )

    val income: List<Category> = listOf(
        Category(
            id = "cat_salary", name = "工资", emoji = "💼", type = TxType.INCOME, colorHex = "#2C6B55", order = 0,
            keywords = listOf("工资", "薪资", "薪酬", "代发工资", "月薪", "劳务费", "薪水")
        ),
        Category(
            id = "cat_bonus", name = "奖金", emoji = "🎉", type = TxType.INCOME, colorHex = "#B08A4A", order = 1,
            keywords = listOf("奖金", "年终奖", "绩效", "提成", "奖励", "激励", "分红奖")
        ),
        Category(
            id = "cat_invest", name = "理财", emoji = "📈", type = TxType.INCOME, colorHex = "#4F8A86", order = 2,
            keywords = listOf("利息", "分红", "基金", "股票", "理财", "债券", "收益", "余额宝", "存款", "国债", "黄金")
        ),
        Category(
            id = "cat_redpacket", name = "红包", emoji = "🧧", type = TxType.INCOME, colorHex = "#B4432E", order = 3,
            keywords = listOf("红包", "礼金", "压岁钱", "转账收入", "收款", "礼钱")
        ),
        Category(
            id = "cat_side", name = "兼职", emoji = "🧑‍💻", type = TxType.INCOME, colorHex = "#3E6B8A", order = 4,
            keywords = listOf("兼职", "稿费", "稿酬", "外包", "副业", "咨询费", "讲课", "课时费", "设计费")
        ),
        Category(
            id = "cat_reimburse", name = "报销", emoji = "🧾", type = TxType.INCOME, colorHex = "#7E8C6B", order = 5,
            keywords = listOf("报销", "退款", "退费", "赔偿", "医保报销", "返现", "返还", "补发")
        ),
        Category(
            id = "cat_income_other", name = "其他收入", emoji = "💰", type = TxType.INCOME, colorHex = "#9A9487", order = 6,
            keywords = listOf("其他", "杂项", "闲置", "二手")
        ),
    )

    val all: List<Category> = expense + income

    fun byId(id: String): Category? = all.firstOrNull { it.id == id }

    /** 提供给截图识别的完整关键词表（含自定义分类由仓库层再补充） */
    fun keywordIndex(categories: List<Category>): List<Pair<String, String>> =
        categories.flatMap { category -> category.keywords.map { it to category.id } }

    /** 支出分类的兜底 id */
    const val OTHER_EXPENSE = "cat_other"
    const val OTHER_INCOME = "cat_income_other"
}
