# 快速开始

适用于 Windows 构建与 USB 辅助试用。先查看 [兼容矩阵](COMPATIBILITY.md)：目前只有一台华为设备完成实机验证。Android App 的最低 API 版本是代码基线；其他系统或机型需要逐项验证。使用钉钉当前账号真实可用的考勤规则与极速打卡功能，实际结果以官方记录为准。

## 1. 准备 Windows 工具

使用预编译版时，从 [预发布下载页](https://github.com/FranklinNexus/autoding-local/releases/tag/v0.10.0-preview.1) 下载 APK、AutoDing-windows-tools.zip 和 SHA256SUMS.txt。用 `Get-FileHash -Algorithm SHA256 FILE` 对照校验值，解压工具包，并将 APK 放进其中的 `build/AutoDing-local-test.apk`。安装 PowerShell 7、Android Platform-Tools 和下述中文 OCR 后，从第 3 节继续；工具包已含 UI jar，试用时无需本地编译。第 2 节供自行构建者使用。

安装 [Git for Windows](https://git-scm.com/downloads/win)、[PowerShell 7](https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-windows)、[JDK 17](https://adoptium.net/temurin/releases/?version=17) 和 [Android SDK](https://developer.android.com/studio)。SDK 可通过 Android Studio 的 SDK Manager 安装：

- Android SDK Platform-Tools。
- Android SDK Platform 35。
- Android SDK Build-Tools 35.0.0。
- Android SDK Command-line Tools；仅命令行安装 SDK 时使用。

打开 PowerShell 7，确认 `java -version`、`javac -version` 和 `git --version` 可用。若采用 SDK Manager 图形界面安装，默认 SDK 目录通常是 `%LOCALAPPDATA%\Android\Sdk`；自定义目录需设置 `ANDROID_HOME`。已安装 `sdkmanager` 时也可执行：

```powershell
sdkmanager 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
sdkmanager --licenses
```

中文 OCR 是 Windows 辅助读取的独立依赖，在“设置 → 时间和语言 → 语言和区域 → 中文（简体）→ 语言选项”检查本地语言及 OCR 组件。脚本要求可用的 `zh-Hans` Windows OCR 引擎；仅安装 PowerShell 或 Android SDK 不会自动具备该引擎。

## 2. 获取源码、检查与构建

```powershell
git clone https://github.com/FranklinNexus/autoding-local.git
Set-Location autoding-local
./test.ps1
./test/Test-SchedulerState.ps1
./test/Test-AttendanceScreen.ps1
./test/Test-AttendanceWatcher.ps1
./test/Test-PhoneUnlock.ps1
./test/Test-UnlockRecovery.ps1
./test/Test-AppBridge.ps1
./test/Test-FullDryRun.ps1
./test/Test-AttendanceLayout.ps1
./test/Test-DeviceCompatibility.ps1
./test/Test-AndroidUi.ps1
./build.ps1
./tools/Build-UiDump.ps1
```

SDK 在自定义目录时，为当前窗口设置环境变量后再执行构建：

```powershell
$env:ANDROID_HOME = 'D:\Android\Sdk'
./build.ps1 -SdkPath $env:ANDROID_HOME
./tools/Build-UiDump.ps1 -SdkPath $env:ANDROID_HOME
```

生成的 APK 是 `build/AutoDing-local-test.apk`，辅助工具是 `build/ui-dump/autoding-ui.jar`。首次构建生成本地测试签名密钥 `build/local-test.jks`，后续更新同一安装必须沿用它。请把密钥留在本地备份，避免加入 Git。自行构建的 APK 与他人提供的预发布 APK 可能签名不同；卸载会清除 App 内记录与排程，切换签名前先导出本地诊断并核对当天任务。

`0.10.0-preview.1` 预发布提供 `AutoDing-local-test.apk`（本地测试签名）和 `AutoDing-windows-tools.zip`。工具包包含对应源码和 `build/ui-dump/autoding-ui.jar`，不包含 ADB / SDK；GitHub 另提供对应源码归档。解压工具包后从其根目录执行本指南，UI jar 已附带时可跳过 `Build-UiDump.ps1`，其余环境与依赖仍需安装。以 [Releases](https://github.com/FranklinNexus/autoding-local/releases) 实际附件为准，按该版本说明核对校验值、签名与匹配源码。预发布附件是试用产物，尚未形成跨设备稳定性结论；Releases 中未提供附件时按上述步骤自行构建。

## 3. 连接手机并安装

在手机开启开发者选项和 USB 调试，用 USB 数据线连接电脑，在手机弹窗允许这台电脑。账号登录、密码和系统安装确认均在手机上完成。

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
# 自定义SDK时：$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adb devices -l
$serial = 'SERIAL' # 在本地替换为上一步显示且状态为device的目标手机
& $adb -s $serial install -r ./build/AutoDing-local-test.apk
& $adb -s $serial shell am start -n xcom.warof.chosen/.MainActivity
```

`unauthorized` 表示等待手机确认调试，`offline` 表示连接尚未就绪；先处理连接再继续。若手机要求手动安装确认，请解锁手机确认。包名为 `xcom.warof.chosen`，会与使用同一包名的上游安装冲突。

## 4. 完成手机端设置

1. 在 AutoDing 首页逐项允许精确定时、后台显示/悬浮窗与通知。
2. 在厂商启动或电池管理中允许自启动、关联启动和后台活动；按 [厂商清单](COMPATIBILITY.md#厂商设置检查表) 检查。
3. 在钉钉确认登录有效、考勤位置可用、极速打卡已启用。若规则需要人脸或其他人工核验，按钉钉提示处理。
4. 先保持供电、亮屏、已解锁，点击“60 秒后测试一次”，退到桌面，观察定时是否打开钉钉。
5. 核对上班时间、最早下班、最短间隔与浮动，保存日常定时。节假日或调休用指定日期覆盖。

默认最短间隔为 9 小时。签退依赖已确认签到；先核对钉钉真实记录，再在首页“确认实际打卡记录”输入，或完成下一步考勤读取。缺少签到时不预设固定 18:00 签退。分钟精度签到留 60 秒计划余量，例如 09:18 对应最早 18:19。

## 5. 先单次检查 Windows 辅助组件

运行目录、日志、凭据使用仓库外路径，推荐当前用户的 LOCALAPPDATA：

```powershell
$trialLogs = Join-Path $env:LOCALAPPDATA 'AutoDingLocal\logs'
$credential = Join-Path $env:LOCALAPPDATA 'AutoDingLocal\phone-unlock.clixml'
./tools/Observe-Phone.ps1 -Serial $serial -Once -OutputDirectory (Join-Path $trialLogs 'AutoDing-trial')
```

有两种运行方式：手机持续已解锁时，只需 Observe 和 Attendance，无需保存 PIN，直接继续考勤读取和第 6 步的 `-SkipUnlock` 安装。需要保留数字 PIN 锁并使用电脑辅助时，再设置本地凭据：

```powershell
pwsh -STA -File ./tools/Set-PhoneUnlock.ps1 -Serial $serial -CredentialPath $credential
```

PIN 在弹出的本地掩码窗口输入，DPAPI 加密绑定当前 Windows 账户。先核对手机 PIN，再在现场测试一次：

```powershell
./tools/Watch-PhoneUnlock.ps1 -Serial $serial -CredentialPath $credential -TestOnce -OutputDirectory (Join-Path $trialLogs 'AutoDing-unlock')
```

仅标准系统数字 PIN 界面在当前适配范围，指纹、图案、复杂密码或非标准锁屏不在该输入器的适配范围。错误 PIN、界面不符或解锁未核实会产生阻断标志并停止自动输入。先检查本地结果和手机，不要反复运行以尝试不同密码；阻断处理见 [试用说明](TRIAL.md#pin-与-120-秒恢复边界)。PIN 输入会短暂出现在 Android `input` 进程参数中，因此调试电脑和 Windows 账户应由本人控制。

在手机手动进入钉钉考勤页，再执行一次读取：

```powershell
./tools/Watch-Attendance.ps1 -Serial $serial -Once -OutputDirectory (Join-Path $trialLogs 'AutoDing-attendance')
```

核对输出 `outcome` 和 App 实际记录。仅当日回执适用，跨夜记录先人工核对。布局和分辨率不符时先查看 [OCR 适配说明](COMPATIBILITY.md#ocr-布局适配)，不要将读取失败等同实际打卡失败。

自动任务启用前执行兼容诊断；它读取环境和布局状态，不把未验证项标为已兼容：

```powershell
./tools/Test-DeviceCompatibility.ps1 -Serial $serial > (Join-Path $trialLogs 'compatibility.json')
# 自定义布局：额外传 -LayoutPath 'C:\Local\my-attendance-layout.json'
```

默认布局在 `layouts/huawei-attendance-portrait-v1.json`。其他机型先按当前页面在本地验收，使用人工确认记录也可运行依赖签到的签退排程。非内置 JSON 或缩放尺寸默认阻断 OCR 自动回传；现场确认裁剪、日期和“已打卡”语义正确后，才在 `Watch-Attendance.ps1` 或安装任务时同时传 `-LayoutPath PATH -AcceptUnverifiedLayout`。相同分辨率不表示相同布局。

## 6. 安装并验收后台任务

停止此前手动运行的持续副本。已解锁、无需 PIN 辅助的方式安装两项任务：

```powershell
./tools/Install-TrialTasks.ps1 -Serial $serial -LogDirectory $trialLogs -SkipUnlock
Start-ScheduledTask -TaskName AutoDingLocal-Observe
Start-ScheduledTask -TaskName AutoDingLocal-Attendance
```

`-SkipUnlock` 不会自动停用已经存在的 Unlock 任务。由旧方式切换时，先执行 `Disable-ScheduledTask -TaskName AutoDingLocal-Unlock` 和 `Stop-ScheduledTask -TaskName AutoDingLocal-Unlock`，并核对手机保持解锁。

已完成本地 PIN 设置及单次验收时，可安装完整三项任务：

```powershell
./tools/Install-TrialTasks.ps1 -Serial $serial -LogDirectory $trialLogs -CredentialPath $credential
Start-ScheduledTask -TaskName AutoDingLocal-Observe
Start-ScheduledTask -TaskName AutoDingLocal-Unlock
Start-ScheduledTask -TaskName AutoDingLocal-Attendance
Get-ScheduledTask -TaskName 'AutoDingLocal-*' | Select-Object TaskName, State
```

自定义布局验收后，可给安装命令追加 `-LayoutPath 'C:\Local\my-attendance-layout.json' -AcceptUnverifiedLayout`，配置会交给 Attendance 任务；默认试用从内置布局开始。

任务在当前用户登录后运行，每两分钟重新拉起已退出的任务；运行中保持单实例。电脑应持续开机、登录和 USB 供电，屏幕可关闭、Windows 桌面可锁定。手动休眠、注销、关机或 USB 断开会中断辅助功能。

最后再做一次现场定时测试，分别验收：闹钟触发、解锁、钉钉前台、官方考勤结果、读取回执。一次打开钉钉仅证明启动，成功判定还需要官方通知或考勤记录。持续停留在钉钉可能让官方极速打卡早于 AutoDing 目标时刻，须核对账号规则。

需要有意停止时先停用对应任务，再在其输出目录创建 `stop.request` 让脚本清理退出；下次运行前删除停止标志。手机日常排程需在 App 内另行停用，详细操作见 [TRIAL.md](TRIAL.md)。
