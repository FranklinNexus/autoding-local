# AutoDing Local

一个在安卓手机上定时打开钉钉的本地实验项目。由已安装的钉钉处理极速打卡，实际结果以钉钉考勤记录为准。

当前阶段：**单机试用原型**。已在一台华为 JEF-AN00、HarmonyOS 4.2、Android API 31、钉钉 9.0.5 上验证充电亮屏且已解锁时从后台定时打开 App。首次隔夜试用中闹钟按时触发，但手机仍处于密码锁定，启动链路未完成；连续多日、断网恢复、整机重启以及其他设备版本还需验证。

## 实现范围

- 上下班两个本地时间，可选周一至周五或每天执行。
- 可选时间浮动：上班提前、下班延后；每天生成一次并保存准确触发时间。
- 使用系统闹钟排程，保存未来任务，并在正常重启后解锁、应用更新及时间变更时尝试恢复。
- 前台服务、可见的“等待定时”状态条与启动提醒。
- 应用内最近运行记录，以及电脑端可选日志采集工具。
- App 自身未申请联网权限；无需云端推送、邮箱或无障碍服务。

## 已验证的运行条件

保持手机供电、亮屏、已解锁，并保留“AutoDing · 等待定时”状态条。华为应用启动管理中开启自启动、关联启动和后台活动；允许精确定时、悬浮窗和通知。钉钉保持登录并启用极速打卡，实际位置、时间和身份验证要求仍由钉钉处理。

手机 App 自身在熄屏且密码锁定时的无人值守启动未通过验证。可选 USB PIN 组件随后通过两次从锁屏、电脑解锁到定时打开钉钉的实机验证，其中一次由 Windows 后台任务接管，仍待连续多日检验。周一至周五只表示星期过滤；节假日、调休和当天浮动班次需要手动核对。强行停止应用会影响后续任务；重启后先在手机上解锁并检查首页排程。

0.8 版在状态条显示锁定、熄屏和供电异常，记录触发时设备状态，并在触发后 10 秒检查页面启动回执。两分钟后清理未消费的请求，避免稍后打开首页时执行陈旧任务。这些诊断并不代表实际考勤结果。

## 构建与测试

需要 Windows、PowerShell、JDK 17，以及 Android SDK 的 `platform-tools`、`platforms;android-35` 和 `build-tools;35.0.0`。通过 Android 官方 SDK Manager 安装这些组件；SDK 路径支持 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、默认 `%LOCALAPPDATA%\Android\Sdk` 或显式参数。

```powershell
./test.ps1
./build.ps1
# 或：
./build.ps1 -SdkPath 'D:\Android\Sdk'
```

安装包生成在 `build/AutoDing-local-test.apk`。首次构建会生成本地测试签名密钥 `build/local-test.jks`；同一手机后续更新需沿用该密钥。默认密码仅用于本地测试。构建目录、密钥和设备证据已列入忽略规则。

CI 运行 6003 项排程检查，覆盖浮动窗口、星期过滤、固定时间和触发后的次日排程。CI 结果只验证排程计算；设备兼容性依赖实机测试。当前在同一浮动窗口内再次点击“保存并启用”会重新排程，尚未增加当天执行去重，因此试运行期间请避免反复保存。

## 日志与现场试用

连接已开启 USB 调试并允许电脑连接的手机。先运行一次状态采集：

```powershell
./tools/Observe-Phone.ps1 -Once -OutputDirectory 'D:\AutoDing-trial'
# 持续采集，Ctrl+C 结束：
./tools/Observe-Phone.ps1 -OutputDirectory 'D:\AutoDing-trial'
```

工具仅采集本 App 的日志标签、系统闹钟、本 App 服务、供电与屏幕状态，并每分钟更新 `current.json` 和 `health.jsonl`。连续模式在连接正常时采集 `events-*.txt`，断线后尝试重连。工具不采集钉钉聊天、通知内容、截图或位置。实际目录应放在仓库外；停止方式见 `docs/TRIAL.md`。

验证时分别核对三件事：系统任务是否到点、钉钉是否进入前台、考勤是否产生有效记录。一次打开 App 的日志不代表一次有效打卡。

## 可选 USB 解锁组件

保留数字 PIN 锁时，可在同一台 Windows 电脑运行实验性解锁组件。电脑须保持运行、手机须保持 USB 调试连接，首次需要在手机上允许这台电脑。先构建界面读取工具，再在本地掩码窗口保存 PIN：

```powershell
./tools/Build-UiDump.ps1
pwsh -STA -File ./tools/Set-PhoneUnlock.ps1 -Serial SERIAL
./tools/Watch-PhoneUnlock.ps1 -Serial SERIAL -OutputDirectory 'D:\AutoDing-unlock'
```

PIN 默认使用当前 Windows 账户的 DPAPI 加密保存在 `%LOCALAPPDATA%\AutoDingLocal\phone-unlock.clixml`，凭据文件应留在仓库外。解锁组件在系统中本 App 的闹钟到点前 30 秒唤醒设备，确认系统 PIN 界面、空输入框和数字键盘后输入一次，并核对锁屏解除。它只处理解锁，启动钉钉仍由手机 App 的闹钟链路处理。

PIN 输入通过 ADB 的标准输入发送，不写入 Windows 命令参数或组件日志；Android 设备端 `input` 进程的参数中会短暂出现输入内容。已获准调试这台手机的电脑及当前 Windows 账户应由本人控制。界面读取辅助工具临时通过 Android Shell 的 UiAutomation 获取控件，密码内容不返回，读取结果仅在进程内使用。

每个闹钟仅处理一次。输入失败或界面未确认时会保存阻断标志并停止后续尝试；先核对现场和凭据，再显式清除阻断。测试方式、状态目录和停止方法见 [docs/TRIAL.md](docs/TRIAL.md)。手机重启、USB 断连、Windows 注销或电脑关机后请重新检查运行状态。

## 与上游的关系

参考 [littleclaw/AutoDing](https://github.com/littleclaw/AutoDing) 的定时启动思路，审阅的上游提交为 `07f99cc19b748e625e2139e0c9f718a50a3aa7b2`。本仓库是独立的精简实现，沿用上游示例 applicationId `xcom.warof.chosen`，与上游安装包会冲突，并非上游完整功能的镜像或官方版本。

遵循 **GPL-3.0-or-later**；完整文本见 [LICENSE](LICENSE)。
