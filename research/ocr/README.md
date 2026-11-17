# 手机本机中文 OCR 探针

独立研究包 `org.autoding.ocrprobe`，最低 Android API 31，目标 API 33。与现有 AutoDing 包分离，不修改其排程或凭据，也不点击钉钉。用户手动选择本机图片，或运行内置合成考勤图；结果只显示日期、实际签到/签退时间与稳定错误码。

采用 `com.google.mlkit:text-recognition-chinese:16.0.1` bundled 模型，模型随 APK 安装；这里没有使用需要后续下载模型的 `play-services-mlkit-*`。合并 Manifest 显式移除 `INTERNET` 和 `ACCESS_NETWORK_STATE`，构建脚本会复核产物。依赖下载发生在电脑构建阶段，手机识别无需网络权限。

## 构建

需要 Windows、JDK 17、Gradle 8.14.3、Android SDK Platform 35 / Build Tools 35.0.0。项目使用 AGP 8.13.2。首次构建需要 Google Maven 与 Maven Central 访问；后续依赖已缓存时可追加 `-Offline`。

```powershell
# 在仓库根目录执行；自动使用 Gradle 命令、对应 wrapper 缓存或项目 wrapper。
./research/ocr/test/Test-ReceiptParser.ps1
./research/tools/Build-OcrProbe.ps1
# 也可通过 -GradlePath 'C:\Tools\gradle-8.14.3\bin\gradle.bat' 指定版本。
```

脚本将电脑的 SDK 路径写到已忽略的 `local.properties`，Gradle 使用研究项目内部的已忽略缓存，避免修改共享缓存。构建后复核无网络权限、中文模型及 ARM 识别库，输出 `research/ocr/build/distribution/AutoDing-OcrProbe-debug.apk` 与 SHA256 文件。APK 使用 Android 默认 debug 签名，只供研究验证。

## 本机实验

安装后打开“AutoDing OCR Research”，先选择“运行内置合成样例”。预期得到固定测试日期 2026-11-05、实际上班 09:18、实际下班 18:22。为自动化本实验，可显式启动合成样例：

```powershell
adb -s SERIAL install -r ./research/ocr/build/distribution/AutoDing-OcrProbe-debug.apk
adb -s SERIAL shell am start -n org.autoding.ocrprobe/.MainActivity --ez run_fixture true
```

此入口只生成本地测试图，与真实考勤记录无关。应用未申请网络权限，因此该样例测试也可验证手机脱离电脑后依赖已随 APK 安装。人工选图分支仅取得该张图片的短期读取权限，不申请媒体全库权限；应用代码不保存图片或原始 OCR 文字。

## 验证范围

- 识别区域目前只沿用华为 1080×2400 竖屏三块参考裁剪；同宽高比按尺寸缩放，其他布局保持待核实。
- 纯 Java 解析测试覆盖时间/日期/星期、未来时间、缺少标题、多个实际记录、未打卡、更新打卡文字混入和原始个人信息不输出。
- 当前探针不做锁屏解锁、无障碍截图、自动选图、后台启动、考勤回传或动态签退。它验证“中文模型能否随 APK 安装并在手机端独立识别”这一子问题。
- 真机首次安装、无网络条件下运行内置样例和实际布局识别需分别记录；电脑构建成功不代表这些设备项目已通过。
- ML Kit Android 文档：<https://developers.google.com/ml-kit/vision/text-recognition/v2/android>。该 SDK 条款由其依赖方提供，研究项目源码继承仓库许可，第三方模型/二进制不因此改许可。
