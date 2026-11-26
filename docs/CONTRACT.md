# 岁余 · 工程协作契约（CONTRACT）

> 本文件是所有并行开发者（含 AI 子代理）的**唯一接口约定**。动手前先读完全文；只写「文件归属」中划给你的文件。

## 0. 项目一句话

「岁余」是一款**本地优先、无广告**的安卓家庭记账 App：截图 OCR 自动记账 → 图表统计 → 家庭成员分账 → 自动生成含同比/环比的财务报表 → 中国个人所得税测算与模拟申报。名字取自《庄子·庚桑楚》「今吾日计之而不足，岁计之而有余」，Slogan：「日计不足，岁计有余」。

技术：Kotlin 2.0.21 + Jetpack Compose（Material 3）+ 单模块，minSdk 26 / compileSdk 34 / targetSdk 34。
无 Room、无 Hilt、无 Navigation-Compose、无第三方图表库。持久化是 `filesDir/ledger.json`。端侧 OCR 用 ML Kit `text-recognition-chinese`（内置模型、离线）。

## 1. 构建与验证（重要）

沙箱内**只能写工作区目录**，所以：
- **必须**设置 `GRADLE_USER_HOME` 到工作区，否则 Gradle 报 `Could not initialize native services`；
- 不要尝试 `assembleDebug`（签名 keystore 被安全策略禁用），验证用 `:app:compileDebugKotlin` 与 `:app:testDebugUnitTest`；
- 不要在 `~` 下创建/修改任何文件。

```bash
cd suiyu-ledger
# 设置 JDK 17（macOS 示例）
# export JAVA_HOME="$HOME/.toolchain/jdk17/Contents/Home"
export ANDROID_HOME="$HOME/Android/Sdk"  # 按本机 SDK 路径调整
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --console=plain :app:compileDebugKotlin
# 单元测试：
./gradlew --console=plain :app:testDebugUnitTest
```

**并行编译冲突**：别的代理可能正在写自己的文件，编译错误若出现在 `你负责之外` 的文件，请忽略（除非该文件是你必须先用的公共 API）。只修复 `你的文件` 的报错。

## 2. 文件归属（严格）

| 归属 | 文件 |
|---|---|
| 公共基础（**任何人不得修改**，有需要请报告 Lead） | `model/Models.kt`、`data/*`、`util/*`、`analytics/Analytics.kt`、`analytics/Narrative.kt`、`ui/theme/*`、`ui/components/*`、`ui/haptics/*`、`AppGraph.kt`、`MainActivity.kt`、`nav/*`、`ui/screens/home/*`、`ui/screens/entry/*`、`build.gradle.kts`、`AndroidManifest.xml`、`res/*` |
| 税务 | `tax/**`、`ui/screens/tax/**`、`app/src/test/java/com/suiyu/ledger/tax/**`、`docs/个人所得税测算说明.md` |
| 账单识别 | `ocr/**`、`ui/screens/importer/**`、`app/src/test/java/com/suiyu/ledger/ocr/**`、`docs/账单识别说明.md` |
| 统计报表 | `ui/screens/stats/**`、`ui/screens/report/**`、`docs/报表分析规则.md` |
| 家庭设置 | `ui/screens/family/**` |
| 文档 | `docs/产品设计说明.md`、`docs/使用说明.md`、`docs/命名与品牌.md`、`README.md` |

规则：
1. 只新建/修改「归属」中属于你的文件；**严禁**改公共基础（否则会与其他代理冲突）。
2. 不得新增第三方依赖；只用现有依赖（Compose、ML Kit、core-ktx、activity-compose）。不要引入 `androidx.navigation`、Room、Hilt、coil、任何图表库。
3. 不写 TODO / 占位实现 / 假数据；每个按钮都要有真实行为（可降级为提示）。
4. 所有界面文案用**中文**，语气克制、专业、不卖萌；数字用衬线体。
5. 只用 Compose + Material3 既有 API；不要使用实验性 API 除非加 `@OptIn(ExperimentalMaterial3Api::class)`。

## 3. 设计系统（必须遵守）

语义色（不要硬编码颜色，除图表分类色）：
```kotlin
val xm = SyTheme.colors   // @Composable 属性
// xm.expense 支出红 / xm.income 收入绿 / xm.gold 稻金 / xm.expenseSoft / xm.incomeSoft
// xm.goldSoft / xm.paper / xm.inkSoft / xm.hairline / xm.chartPalette: List<Color>
```
- 间距节奏：`SyDim.gutter`(20dp)、`SyDim.gapXs/S/M/L/Xl/2Xl`；卡片圆角 `SyDim.cardRadius`；版心 `SyDim.contentMaxWidth`。
- 字体：`MaterialTheme.typography`；大数字/标题用 `headlineMedium` / `titleLarge`（衬线）；正文 `bodyMedium`；标签 `labelMedium`。
- 页面骨架：`SyScaffold(...)`，Tab 页在内容末尾调用 `SyBottomSpacer()`。
- 组件（全部在 `ui/components/`，签名已固定）：
```kotlin
SyScaffold(title, subtitle, onBack, actions, scroll = false, horizontalPadding, bottomPadding, content)
SyTopBar(title, subtitle, onBack, actions)
SyCard(modifier, onClick, color, contentPadding) { ... }
SySectionHeader(title, subtitle, trailing, onTrailingClick)
SyButton(text, onClick, modifier, style = SyButtonStyle.Primary/Secondary/Ghost/Danger, enabled, leading, haptic)
SyTextButton(text, onClick, modifier, color)
SyChip(text, selected, onClick, modifier, leadingEmoji)
SySegmented(items: List<String>, selectedIndex, onSelect, modifier)
SyToggleRow(title, checked, onCheckedChange, modifier, description)
SyMemberAvatar(member, modifier, size, selected)
SyTag(text, modifier, color, background) / SySourceTag(source)
SyAmount(cents, type, modifier, style, showSign, masked, color)
SyStat(label, value, modifier, sub, valueColor)
SyEmpty(emoji, title, description, modifier, actionText, onAction)
SyRecordRow(record, category, member, modifier, showTime, onClick)
SyMonthSwitcher(month, onMonthChange, modifier)   // 内含月份选择弹窗
SyMonthPickerDialog(initial, onDismiss, onConfirm)
SyNumberKeypad(onKey, onBackspace, onDone, modifier, doneLabel, keyHeight)
SyDivider() / SyBottomSpacer()
Modifier.syClickable(effect = HapticEffect.Tick) { ... }   // onClick 必须放最后/尾 lambda
```
图表（`ui/components/Charts.kt`）：
```kotlin
DonutChart(slices: List<ChartSlice>, modifier, centerLabel, centerValue, strokeWidth, onSliceClick)
ChartRankList(items: List<ChartRankItem>, modifier, onItemClick)
GroupedBarChart(groups: List<BarGroup>, modifier, primaryLabel, secondaryLabel, onGroupClick)
TrendChart(series: List<LineSeries>, labels: List<String>, modifier, onPointClick)
Sparkline(values: List<Float>, modifier, color, filled)
SyProgressBar(progress, modifier, color, height)
MonthHeatmap(month: MonthKey, values: List<Long>, modifier, onDayClick)
```
数据类：`ChartSlice(key,label,value:Float,color)`、`ChartRankItem(key,label,emoji,valueLabel,ratio:Float,color)`、`BarGroup(label, primary:Float, secondary:Float=0f)`、`LineSeries(label,color,values:List<Float>)`。注意图表数值单位统一为**分（Float）**。

触感（每个可点元素都要有）：
```kotlin
Haptics.play(HapticEffect.Tick)     // 键盘、列表
Haptics.play(HapticEffect.Select)   // tab / chip / 月份切换
Haptics.play(HapticEffect.Confirm)  // 提交、保存
Haptics.play(HapticEffect.Success)  // 完成（导入、测算完成）
Haptics.play(HapticEffect.Warning / Error / Toggle / Soft / LongPress / Wave)
```
用 `Modifier.syClickable(...)` 时已自动带触感；单独 `clickable` 时要手动 `Haptics.play(...)`。设置页可全局关闭（仓库已处理）。

隐私：金额字段遵循 `settings.privacyMask` 时用 `SyAmount(masked = true)`。

## 4. 数据层 API（`AppGraph.ledger`，单例）

```kotlin
object AppGraph { lateinit var ledger: LedgerRepository; fun init(context) }
// 在 Compose 中： val records by AppGraph.ledger.records.collectAsState()
val members: StateFlow<List<Member>>
val categories: StateFlow<List<Category>>
val records: StateFlow<List<TxRecord>>
val settings: StateFlow<AppSettings>
val selfMember: Member
fun memberById(id): Member?   // fun categoryById(id): Category?
fun addRecords(list: List<TxRecord>)   // fun upsertRecord(record: TxRecord)
fun deleteRecord(id: String)   // fun recordById(id): TxRecord?
fun addMember(name, emoji, colorHex, role): Member
fun updateMember(member: Member)   // fun deleteMember(id: String)
fun addCategory(name, emoji, type: TxType, colorHex): Category   // fun deleteCategory(id)
fun updateSettings(transform: (AppSettings) -> AppSettings)   // setFamilyName / setHapticsEnabled / setHapticStrength / setThemeMode / setPrivacyMask
fun findDuplicates(candidate: TxRecord): List<TxRecord>
fun rememberMerchantRules(rules: Map<String, String>)   // fun resolveMerchantRule(merchant): String?
fun commitImport(newRecords: List<TxRecord>, rules: Map<String, String> = emptyMap())
fun exportCsv(): File   // fun exportBackup(): File   // fun restoreBackup(json: String): Boolean
fun clearRecords()   // fun resetAllWithDemo()   // fun resetAllBlank()
```

模型（`model/Models.kt`）：
```kotlin
enum class TxType { EXPENSE, INCOME }        // 有 label / sign
enum class BillSource { MANUAL, WECHAT, ALIPAY, BANK, SMS, OTHER }  // 有 label / short / isOcr
data class Member(id, name, emoji, colorHex, role, isSelf, order)
data class Category(id, name, emoji, type: TxType, colorHex, builtin, keywords, order)
data class TxRecord(id, type, amountCents: Long /*正数*/, categoryId, memberId, occurredAt: Long,
                    note = "", merchant = "", account = "", source = MANUAL, sourceRef = null,
                    createdAt = now, includeInStats = true)  // 有 signedCents
data class AppSettings(familyName, hapticsEnabled, hapticStrength, themeMode, seededDemo, onboarded, privacyMask)
```
金额一律 `Long` 分；展示用 `Money.format(cents)` / `Money.plain(cents)` / `Money.percent(ratio, signed)` / `Money.compact(cents)`（`util/Money.kt`）。
时间用 `Dates.*`（`dayLabel`/`time`/`fullDate`/`monthDay`/`isIsoDate`…）；账期用 `MonthKey(year, month)`（`plusMonths`、`label()`、`startMillis()`、`MonthKey.current()`）。

## 5. 分析层 API（`analytics/Analytics.kt`）

```kotlin
fun ofMonth(records, month): List<TxRecord>
fun summary(records, month): MonthSummary                 // incomeCents/expenseCents/balanceCents/savingsRate/expenseToIncome/avgDailyExpenseCents/recordCount
fun comparison(records, month): MonthComparison           // current/previous/lastYear + expenseMoM/expenseYoY/incomeMoM/incomeYoY/balanceMoM (Change: current/base/diff/pct/direction/hasBase)
fun categorySlices(records, categories, month, type = EXPENSE, memberId = null, baseMonth = null): List<CategorySlice>  // totalCents/ratio/baseCents/change
fun memberSlices(records, members, month): List<MemberSlice>  // expenseCents/incomeCents/balanceCents/expenseRatio/count
fun monthPoints(records, endMonth, count): List<MonthPoint>   // month/incomeCents/expenseCents
fun yearPoints(records, year): List<MonthPoint>
fun dailyPoints(records, month): List<DayPoint>               // day/weekday/expenseCents/incomeCents
fun categoryChanges(records, categories, month, type = EXPENSE): List<CategoryChange>  // 按变化绝对值降序
fun memberCategorySlices(records, categories, memberId, month, type = EXPENSE): List<CategorySlice>
fun topMerchants(records, month, type = EXPENSE, limit = 5): List<MerchantSlice>
fun sourceBreakdown(records, month): Map<BillSource, Int>
fun healthMetrics(records, categories, month): HealthMetrics  // savingsRate/expenseToIncome/avgDailyExpenseCents/noSpendDays/largestExpense/topCategoryRatio/fixedExpenseRatio/passiveIncomeRatio/savingsRating
fun cumulativeExpense(records, month): List<Long>
fun memberMonthPoints(records, memberId, endMonth, count): List<MonthPoint>
fun yearSummary(records, year): MonthSummary
```
自动文案（`analytics/Narrative.kt`）：`Narrative.overview(comparison, familyName)`、`Narrative.categoryInsights(...)`、`memberInsights(...)`、`healthInsights(...)`、`homeHeadline(...)`、`reportText(...)`。返回 `Insight(title, body, tone: InsightTone, emoji)`。可直接用，不要另写一套口径。

## 6. 各页面规格

### 6.1 首页（Lead 负责，仅供参考）
月份切换、总览卡（支出/收入/结余/储蓄率）、环比徽标、近 6 月迷你趋势、快捷入口（导入账单 / 记一笔 / 报表 / 个税测算）、成员支出分布、最近流水。底部 Tab 与中央「记一笔」FAB 由 Lead 的 nav 统一提供。

### 6.1b 流水明细（Lead，v1.1 新增）
- `LedgerScreen(onOpenEntry: (String) -> Unit)`：按日分组的完整流水，账期/收支/成员筛选，长按多选、全选、批量删除、一键清空；点按进入编辑；系统返回键在多选态先退出多选。

### 6.1c 财务体检（Lead，v1.2 新增）
- `WealthScreen(onBack)`：资产/负债/工作画像录入与增删改，调用 `analytics/FinancialHealth.evaluate` 计算六维评分；路由 `wealth`，入口：首页快捷入口与净资产卡、报表页体检摘要。
- 数据模型：`Asset`、`Liability`、`FinancialProfile` 挂在 `LedgerData`，经 `LedgerCodec` 持久化；示例账本首次进入补一份演示资产/负债/画像。

### 6.2 记一笔（Lead）
金额键盘 → 分类 → 成员 → 保存（≤3 步）；编辑模式回填。

### 6.3 账单导入（OCR 代理）— `fun ImportScreen(onBack: () -> Unit, onImported: (Int) -> Unit)`
- 顶部说明「全部在本机识别，不上传」；步骤 1 选择截图（`ActivityResultContracts.PickMultipleVisualMedia(9)`），支持粘贴文本（短信）；
- 每张图 OCR（ML Kit 中文）→ `BillParser.parse(text)` → 草稿列表；
- 草稿卡可改：类型、金额、分类、成员、日期、备注；显示来源角标与置信度；疑似重复（`ledger.findDuplicates`）高亮；
- 全选/取消、批量入账（`commitImport`）+ 记住纠正规则；成功后 `HapticEffect.Wave` + 结果页/弹窗，回调 `onImported(count)`；
- 空态、失败态（识别不出）要给出「手动补录」出口；长文用可滚动。
- 解析器需覆盖：微信收支明细/账单详情、支付宝账单详情/列表、银行短信、银行 App 明细；解析结果与样例自测（App 内展示，10 条样例，显示通过/失败）。

### 6.4 统计（统计代理）— `fun StatsScreen()`
- 顶部：`SyMonthSwitcher` + `SySegmented`（支出/收入）+ 视角 `SyChip`（家庭/各成员）；
- 三个分段：「结构」（环形 + `ChartRankList` 分类排行 + 点击下钻该分类明细底部弹窗）、「对比」（`GroupedBarChart` 本月 vs 上月，或成员对比；`TrendChart` 近 6 月/12 月）、「日历」（`MonthHeatmap` + 日均/最大单笔/零支出天数）。
- 所有数字口径必须来自 `Analytics`；每个图表只回答一个问题；点击有触感与选中态。

### 6.5 报表（统计代理）— `fun ReportScreen(onOpenTax: () -> Unit)`
- 月份切换 + 「月度财务报告」标题（可用衬线 + 细线排版，像一张纸）；
- 总述段落（`Narrative.overview`）；收支总览四宫格（含环比/同比徽标）；
- 收支结构（环形/排行）；环比变化 Top（`categoryChanges` + 增减标记）；同比对比；
- 成员分账：每人一行（支出/收入/结余/占比）+ 成员单独分析（点击展开该成员近 6 月迷你趋势与主要分类）；
- 财务健康（`healthMetrics`：结余率/刚性支出占比/零支出日/最大单笔）；
- 自动洞察列表（`Insight` 卡片，tone 决定颜色）；
- 「分享报表」→ `Share.shareText(Narrative.reportText(...))`；「个税测算」→ `onOpenTax()`；
- 若当月无数据：`SyEmpty` + 引导。

### 6.6 税务（税务代理）— `fun TaxScreen(onBack: () -> Unit)`
- 顶部标注「税制依据：2019 年起施行的《个人所得税法》及 2023-2027 年延续的全年一次性奖金政策；测算结果仅供参考」。
- 分步引导（Stepper）：① 收入（工资/年终奖/劳务/稿酬/特许权）② 三险一金（可自动按工资基数估算）③ 专项附加扣除 7 项（子女教育 2000×比例、3 岁以下婴幼儿照护 2000×比例、继续教育 400/月 或 3600/年、住房贷款利息 1000/月、住房租金按城市 1500/1100/800、赡养老人 3000 独生 / ≤1500 非独生、大病医疗 15000 起付且≤80000）④ 其他扣除（个人养老金≤12000、税优健康险 2400）⑤ 结果与模拟申报。
- 每个扣除项有「政策说明」可展开、金额可自填/自动算；任一步可回看修改，结果实时或点「测算」更新；
- 结果：年度应纳税所得额、适用税率、速算扣除数、应纳税额、已预缴估算、应补/退税额、综合税负率；年终奖「单独计税 vs 并入综合所得」两方案对比与推荐；12 个月预扣明细表；
- 模拟申报：表格式（收入额/减除费用 6 万/专项扣除/专项附加扣除/其他扣除/应纳税所得额/税率/速算扣除数/应纳税额/已缴税额/应补退税额）+「分享测算结果」+ 免责声明；
- 准确性硬要求：`TaxEngine` 为纯 Kotlin（无 Android 依赖）+ JUnit 单测（至少 12 个用例覆盖各级税率、年终奖临界点、专项附加扣除边界、退税/补税场景），并在 `TaxScreen` 内提供「计算器自检」入口展示样例结果。

### 6.7 家庭与设置（家庭代理）— `fun FamilyScreen()`
- 家庭名称可编辑；成员管理（增/改/删：名称、emoji、主题色、身份标签；删除时流水归到「我」）；
- 成员统计小卡（本月支出/收入/占比，来自 `Analytics.memberSlices`）；
- 分类管理（内置分类只读；自定义分类可增删，颜色、emoji、类型）；
- 偏好设置：触感总开关、强度三档（点击试振）、隐私金额遮罩、主题（跟随系统/浅色/深色）、示例数据说明与「清空流水」「重置为示例」；
- 数据：导出 CSV（`Share.openFile`/`shareFile`）、导出 JSON 备份（SAF `ActivityResultContracts.CreateDocument`）、从备份恢复（`OpenDocument` + `restoreBackup`）、隐私说明（本地存储、无网络上传、无广告）；
- 关于：版本、Slogan、「数据不出设备」、开源组件致谢、税率数据年份。

## 7. 交互与质量底线

- 每个可点元素：选中态 + 触感；禁止无响应按钮。
- 空状态必须有引导（`SyEmpty`）；加载态用 `CircularProgressIndicator` 或骨架。
- 键盘弹出不遮挡输入（`imePadding()` 视情况）；列表用 `LazyColumn` 且 key 稳定。
- 适配主流比例：不要写死高度、不要依赖 16:9；横屏/平板内容居中（`SyScaffold` 已限宽 560dp）。
- 数字用衬线、金额千分位、百分比一位小数；比率分母为 0 显示「—」。
- 不要出现竞品名称、不要抄竞品文案或界面；遵循 `docs/市场与产品调研.md` 的「学什么/避开什么」。
- 代码：Kotlin 官方风格、4 空格、中文注释解释「为什么」；单文件建议 ≤ 700 行；函数职责单一。
