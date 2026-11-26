# 贡献指南 / Contributing

感谢你愿意改进「岁余」。这是一个单人维护的个人项目，提交前请先阅读以下约定。

## 可以贡献什么

- Bug 修复（尤其是 OCR 解析、个税计算、统计口径问题）
- 新的账单版式模板与解析测试样例
- 文档修订、错别字、截图更新
- 无障碍、性能、内存占用改进
- 小型功能（请先开 Issue 讨论，避免大改后无法合并）

## 提交 Issue

- 使用模板：Bug 报告 / 功能建议
- Bug 请附：Android 版本、设备型号、岁余版本、复现步骤、脱敏截图或日志
- **不要上传真实账本、手机号、身份证、保单号、地址等隐私数据**

## 本地开发

```bash
git clone https://github.com/zhao2365177340-cloud/suiyu-ledger.git
cd suiyu-ledger
# 用 Android Studio 打开会自动生成 local.properties；命令行可手动创建：
# echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

环境：JDK 17、Android SDK Platform 34 / Build-Tools 34.0.0，Gradle 使用仓库自带 Wrapper（8.9）。

## 代码约定

- Kotlin 官方风格（4 空格缩进），文件编码 UTF-8，LF 换行（见 `.editorconfig`）。
- **金额一律用 `Long` 分**，禁止用 `Double` 表示金额。
- 计算逻辑（`analytics/`、`tax/`、`ocr/` 的解析部分）保持为纯 Kotlin，不依赖 Android，便于单测。
- 不新增第三方依赖（包括 Room、Hilt、图表库）；确有必要请先开 Issue 说明理由与包体影响。
- 不提交 `local.properties`、`*.apk`、`*.jks`、`build/`、`.gradle/` 等生成物。
- 遵循现有 UI 组件与主题（`ui/components`、`ui/theme`），不另起一套样式。

## 测试

- 修改 `tax/`、`analytics/FinancialHealth.kt`、账单解析器时，**必须补充或更新对应单元测试**。
- 提交前请确认全部通过：

```bash
./gradlew :app:testDebugUnitTest
```

当前基线：76 个测试全部通过。

## 提交信息

推荐使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```text
feat: 新增 xxx
fix: 修复 xxx
docs: 更新 xxx
test: 补充 xxx 测试
refactor: 重构 xxx
chore: 构建 / 依赖调整
```

## Pull Request 清单

- [ ] 关联了 Issue（或说明背景）
- [ ] `./gradlew :app:testDebugUnitTest` 通过
- [ ] 新增/修改的计算逻辑有测试覆盖
- [ ] 未提交隐私数据、密钥、APK 或构建产物
- [ ] 涉及界面改动时附上截图（360dp 与标准宽度各一张更佳）
- [ ] 已阅读并同意下方的贡献授权说明

## 贡献授权

由于本项目采用「禁止商用 + 可另行商业授权」的双轨模式，提交 PR 即表示你同意：

1. 你的贡献以 **PolyForm Noncommercial License 1.0.0** 授权给本项目及所有使用者；
2. 你授予项目维护者一项永久的、全球范围的、免费的、可再许可的权利，
   允许维护者将你的贡献用于商业授权；
3. 你保证提交的内容为原创，或已获得必要授权，且不侵犯第三方权利。

如果你不同意以上条款，请不要提交 PR；可以先开 Issue 讨论其他合作方式。

## 安全问题

请勿公开披露安全漏洞。报告方式见 [SECURITY.md](SECURITY.md)。
