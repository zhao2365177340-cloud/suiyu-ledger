# 安全策略 / Security Policy

## 支持范围

只维护最新发布版本（当前为 **v1.2.1**）。

## 报告安全问题

请**不要**在公开 Issue 中披露安全问题。请通过以下方式私下报告：

- GitHub 仓库 **Security → Advisories → Report a vulnerability**
  （<https://github.com/zhao2365177340-cloud/suiyu-ledger/security/advisories/new>）

请尽量说明：

- 受影响的版本、Android 版本与设备；
- 复现步骤或最小验证方式；
- 影响范围（数据泄露、越权、拒绝服务等）；
- 若涉及数据文件，请使用脱敏后的示例。

## 设计上的隐私承诺

- App 不申请 `INTERNET` 权限（Manifest 中显式移除），也没有账号体系；
- 账本保存在应用私有目录，仅在用户主动导出/分享时才离开设备；
- 备份文件为明文 JSON，请自行妥善保管；
- 分享月报为纯文本，不包含完整账号信息。

## 不在范围内

- 使用第三方修改版、调试签名重打包版本导致的问题；
- 税务/财务计算结果的准确性争议（见 README 免责声明）；
- 用户自行导出后造成的文件泄露。
