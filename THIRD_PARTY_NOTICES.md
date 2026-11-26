# 第三方组件与许可 / Third-Party Notices

「岁余」自身代码按 [PolyForm Noncommercial License 1.0.0](LICENSE) 发布。
下列第三方组件仍适用其各自许可；本项目的许可不改变、也不能替代这些许可。

## 运行依赖

| 组件 | 版本 | 许可 / 说明 |
|---|---|---|
| [AndroidX Core, Activity, Lifecycle](https://developer.android.com/jetpack/androidx) | 见 `app/build.gradle.kts` | Apache License 2.0 |
| [Jetpack Compose（UI / Foundation / Material3 / Tooling）](https://developer.android.com/jetpack/compose) | BOM 2024.09.00 | Apache License 2.0 |
| [Kotlin Standard Library & kotlinx.coroutines](https://kotlinlang.org/) | 由 Compose 传递 | Apache License 2.0 |
| [Google ML Kit Text Recognition v2（中文）](https://developers.google.com/ml-kit/vision/text-recognition/v2) | `com.google.mlkit:text-recognition-chinese:16.0.1` | Google ML Kit 条款与隐私政策；模型随 APK 打包，离线运行。详见 [ML Kit Terms](https://developers.google.com/ml-kit/terms) 与 [Google APIs Terms](https://developers.google.com/terms) |
| [Android Gradle Plugin / Gradle](https://developer.android.com/build) | AGP 8.5.2 / Gradle 8.9 | Apache License 2.0 |

## 测试依赖

| 组件 | 版本 | 许可 |
|---|---|---|
| [JUnit 4](https://junit.org/junit4/) | 4.13.2 | Eclipse Public License 1.0 |

## 字体与截图

- App 使用 Android 系统字体，不随包分发第三方字体。
- `docs/screenshots/` 中的界面截图由本项目作者在自己的测试设备上截取，
  其中出现的 Android 系统界面、图标与演示数据不包含任何真实用户信息。
- `tools/gen_promo.py` 是发布素材生成脚本，使用 macOS 系统字体（宋体、冬青黑体、
  Avenir Next）在本机渲染图片；这些字体文件本身不包含在本仓库中。

## 说明

- 本项目不包含任何 Google Play 服务或广告 SDK，也没有网络权限；
  ML Kit 的端侧模型包含在 APK 内。
- 如果你要再分发本项目的 APK 或修改版，请同时遵守 PolyForm Noncommercial
  许可和上述第三方条款；商业分发需另行取得授权。
