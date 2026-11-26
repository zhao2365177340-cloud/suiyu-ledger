# 架构说明 / Architecture

> 面向想阅读或二次开发「岁余」的开发者。产品视角的设计见
> [`产品设计说明.md`](产品设计说明.md)；统计与评分口径见
> [`报表分析规则.md`](报表分析规则.md) 与
> [`财务体检与风险评估说明.md`](财务体检与风险评估说明.md)。

## 1. 目标与约束

「岁余」是一个**单 Activity、单模块**的 Compose 应用，优先保证：

1. **离线与隐私**：没有网络权限、没有账号、没有云同步；
2. **可验证的计算**：个税、统计与财务评分都有纯 Kotlin 实现和单元测试；
3. **可维护的小依赖集**：没有 Room / Hilt / Navigation-Compose / 图表库；
4. **可离线构建**：Gradle Wrapper + 常规 Android SDK 即可构建。

包名 `com.suiyu.ledger`，minSdk 26 / targetSdk 34 / JDK 17 / Kotlin 2.0.21。

## 2. 分层与数据流

```text
UI (Compose)                 AppNav / screens / components / theme
      │  collectAsState
      ▼
State (StateFlow)            AppGraph.ledger: LedgerRepository
      │  mutate (Mutex + 80ms debounce)
      ▼
Persistence                  LedgerCodec (org.json) → filesDir/ledger.json（原子写）
      │
      ├── analytics/          Analytics、Narrative、FinancialHealth（纯 Kotlin）
      ├── tax/                TaxEngine、TaxRules、TaxModels、TaxSamples（纯 Kotlin）
      └── ocr/                BillOcr（ML Kit）+ BillParser（纯 Kotlin）
```

- `AppGraph` 是手写依赖容器，启动时创建唯一 `LedgerRepository`。
- UI 只读取 `StateFlow`，修改一律通过 Repository 的具名方法；不直接改数据。
- 所有派生数据（统计、评分、文案）在 UI 层用 `remember(records, ...)` 计算，
  不写入持久化数据，保证「账本」只有一份事实来源。

## 3. 持久化

- 单文件 `filesDir/ledger.json`，由 `LedgerCodec` 手写编码；
- 写入：序列化 → 写临时文件 → `rename` 原子替换，失败不破坏旧账本；
- 合并写：80ms 防抖 + `Mutex`，避免连续编辑产生大量 IO；
- `version` 字段用于升级兼容；v1.1 → v1.2 会在示例账本上自动补演示资产/负债/画像；
- 备份：JSON 导出完整数据（成员、分类、流水、设置、资产、负债、画像、商户规则）；
  恢复时先校验再整体替换。

金额全部为 `Long` 分；日期为 epoch millis；`MonthKey` 是 `yyyy-MM` 的轻量值对象。

## 4. UI 与导航

- `MainActivity` → `SyTheme` → `AppNav`；
- 底部 5 个 Tab（概览 / 流水 / 统计 / 报表 / 家庭）+ 中央「记一笔」FAB；
- 二级页（导入、个税、财务体检、编辑）用状态路由叠在 Tab 之上，保留 Tab 状态；
- 统一组件：`SyScaffold`、`SyTopBar`、`SyCard`、`SyButton`、`SyChip`、`SySegmented`、
  `SyRecordRow`、`SySectionHeader` 等；
- 所有图表为自绘 Canvas（`DonutChart`、`GroupedBarChart`、`TrendChart`、`Sparkline`、
  `MonthHeatmap`、`RadarChart`），不依赖第三方图表库；
- 数字使用衬线字体与表格对齐；宽屏最大内容宽度 560dp，窄屏 360dp 已适配。

## 5. 计算内核

### 5.1 统计（`analytics/Analytics.kt`）

- `ofMonth` / `comparison` / `categorySlices` / `memberSlices` / `categoryChanges` /
  `healthMetrics` / 趋势与日历；
- 口径：只统计 `includeInStats` 的记录；收入与支出分列；结余率 = 结余 / 收入；
- 同比 / 环比在 `comparison` 中一次算好，供 UI 与分享文本共用。

### 5.2 财务体检（`analytics/FinancialHealth.kt`）

输入：`records + assets + liabilities + profile`。输出：

- 净资产、总资产 / 负债、流动类资产、资产配置；
- 现金流基线：近 6 个月工资类收入（或手动固定收入）、居住+通讯+教育支出（或手动值）、
  负债月供、结余与储蓄率；
- 六维评分：流动性 25% / 负债健康 25% / 收入稳定 20% / 储蓄 15% / 保障 10% / 配置 5%；
- 综合分与评级（≥85 稳健 / 70–84 健康 / 55–69 需关注 / <55 高风险）；
- 按薄弱维度生成 3–5 条带具体金额的建议。

### 5.3 个税（`tax/TaxEngine.kt`）

- 综合所得（工资、劳务、稿酬、特许权使用费）与全年一次性奖金；
- 累计预扣法与年度汇算两种视角；七项专项附加扣除与其他扣除；
- 年终奖「单独计税 / 并入综合所得」两种方案对比；
- 所有税率表、速算扣除数集中在 `TaxRules.kt`，`TaxSamples.kt` 提供可回归的样例。

## 6. OCR 导入链路

```text
截图 URI
  └─ BillOcr.recognize()        ML Kit 中文识别（端侧，模型内置）
       └─ reconstructRows()     按文字 y 坐标重建行，左右列拼接
            └─ BillParser      识别表头、金额、时间、收付款方向、商户
                 └─ 草稿草稿状态（ImportScreen）
                      └─ commitImport() → Repository 批量写入 + 商户规则学习
```

- 双列账单（微信 / 支付宝）先按行重建再解析；
- 噪声行（汇总、积分、汇率、卡号等）由 `BillParser` 过滤；
- 解析器为纯 Kotlin，`ocr/BillSamples.kt` 提供 12 组样例自检；
- 识别失败可退回手动补录，不阻塞用户。

## 7. 测试策略

| 测试 | 位置 | 说明 |
|---|---|---|
| 个税 | `app/src/test/.../tax/` | 31 个：税率边界、年终奖两方案、扣除项 |
| 账单解析 | `.../ocr/` | 21 个：版式、金额、方向、噪声 |
| 统计 + 体检 | `.../analytics/` | 19 个：口径、同比环比、六维评分与建议 |
| 金额/日期 | `.../util/` | 5 个：分单位、账期 |

计算内核不依赖 Android，可在 JVM 上直接跑；UI 行为靠模拟器手工回归（见 README 截图）。

## 8. 构建与发布

- 构建：`./gradlew :app:assembleDebug`，CI 见 `.github/workflows/build.yml`；
- 版本号在 `app/build.gradle.kts`（当前 1.2.1 / versionCode 4）；
- 发布流程：更新 CHANGELOG → 构建 debug APK → 在 GitHub Releases 上传
  `suiyu-vX.Y.Z.apk` → 更新 README 的版本信息；
- APK 为 debug 签名（个人项目未上架商店），升级需保持签名一致。

## 9. 设计取舍

| 决策 | 原因 | 代价 |
|---|---|---|
| 不用 Room | 数据模型简单、单文件即可；避免 KSP/注解处理器与包体 | 需手写编解码与升级兼容 |
| 不用 Navigation-Compose | 页面少，状态路由更直接，Tab 状态保留容易 | 路由逻辑集中在 `AppNav.kt` |
| 自绘图表 | 视觉与交互完全可控；避免图表库体积与主题不一致 | 图表代码需自己维护 |
| 纯 Kotlin 计算内核 | 可测试、可解释、无 Android 依赖 | 需要保持接口稳定 |
| JSON 单文件 | 备份/导出简单，用户可读 | 数据量大时整体序列化有成本（当前规模足够） |
| 无网络权限 | 隐私承诺可被系统检查 | 无法做云同步/多端 |

## 10. 二次开发指引

- 加新页面：在 `AppNav.kt` 增加 route → 新建 `ui/screens/xxx` → 复用 `SyScaffold`；
- 加新统计：在 `analytics/Analytics.kt` 增加纯函数 → 补测试 → 在页面消费；
- 改个税规则：只改 `tax/TaxRules.kt`，补 `TaxSamples` 与测试；
- 适配新账单版式：在 `ocr/BillSamples.kt` 增加样例 → 调整 `BillParser` → 跑 OCR 测试；
- 新增资产类别：`model/AssetCategory` 加枚举 → `FinancialHealth` 的配置/流动性分组按需调整。
