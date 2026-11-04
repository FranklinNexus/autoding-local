# AutoDing Local

一个在安卓手机上定时打开钉钉的本地实验项目。由已安装的钉钉处理极速打卡，实际结果以钉钉考勤记录为准。

当前阶段：**单机试用原型**。已在一台华为 JEF-AN00、HarmonyOS 4.2、Android API 31、钉钉 9.0.5 上验证充电亮屏时从后台定时打开 App。隔夜、连续多日、断网恢复、整机重启以及其他设备版本还需验证。

## 实现范围

- 上下班两个本地时间，可选周一至周五或每天执行。
- 可选时间浮动：上班提前、下班延后；每天生成一次并保存准确触发时间。
- 使用系统闹钟排程，保存未来任务，并在正常重启后解锁、应用更新及时间变更时尝试恢复。
- 前台服务、可见的“等待定时”状态条与启动提醒。
- 应用内最近运行记录，以及电脑端可选日志采集工具。
- App 自身未申请联网权限；无需云端推送、邮箱或无障碍服务。

## 已验证的运行条件

保持手机供电、亮屏、已解锁，并保留“AutoDing · 等待定时”状态条。华为应用启动管理中开启自启动、关联启动和后台活动；允许精确定时、悬浮窗和通知。钉钉保持登录并启用极速打卡，实际位置、时间和身份验证要求仍由钉钉处理。

熄屏且带密码锁定时的无人值守唤起未通过本次验证。周一至周五只表示星期过滤；节假日、调休和当天浮动班次需要手动核对。强行停止应用会影响后续任务；重启后先在手机上解锁并检查首页排程。

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

## 与上游的关系

参考 [littleclaw/AutoDing](https://github.com/littleclaw/AutoDing) 的定时启动思路，审阅的上游提交为 `07f99cc19b748e625e2139e0c9f718a50a3aa7b2`。本仓库是独立的精简实现，沿用上游示例 applicationId `xcom.warof.chosen`，与上游安装包会冲突，并非上游完整功能的镜像或官方版本。

遵循 **GPL-3.0-or-later**；完整文本见 [LICENSE](LICENSE)。
