# AutoDing Local

一个在安卓手机上定时打开钉钉的本地实验项目。由钉钉处理极速打卡，实际结果以钉钉考勤记录为准。

当前版本：**0.9-local，单机试用原型**。已安装在一台华为 JEF-AN00、HarmonyOS 4.2、Android API 31、钉钉 9.0.5 上。充电亮屏且已解锁时的后台启动，以及 Windows 通过 USB 辅助解锁后的启动已通过实机测试；首次隔夜试用暴露了密码锁定导致启动中断的问题。实际签退、连续多日、整机重启及其他设备仍待验证。

## 实现范围

- 按实际签到安排当班签退，默认最短间隔 540 分钟、最早下班 18:00；缺少已确认签到时保持待确认。
- 可选 0–15 分钟浮动，默认 0–5 分钟：上班提前，下班只在满足工时的最早时刻之后延后。
- 保存每班随机时间，重复保存设置或回执保持有效时间；按日期和任务类型去重，过期任务记录为错过。
- 支持周一至周五或每天执行，指定日期可设为上班、休息或恢复星期规则。已有实际签到的班次保留签退安排。
- 系统闹钟、前台服务、设备就绪状态条与启动提醒；恢复已保存任务，并限制待处理启动请求的有效期。
- 人工确认实际记录，或用 Windows 本地考勤页读取组件回传记录；应用内摘要、手机文件日志及电脑状态采集。
- App 自身未申请联网权限；无需云端推送、邮箱或常驻无障碍服务。

## 签退时间与实际回执

下班触发窗口从 `max(当班最早下班时间, 实际签到时间 + 当班最短间隔 + 精度余量)` 开始。默认规则是最早 18:00、间隔 540 分钟；仅有分钟的签到记录额外加 60 秒余量，准确到秒的记录沿用实际秒数。例如签到页显示 09:18，最早触发是 18:19，默认浮动窗口是 18:19–18:24；准确签到为 09:18:37 时，最早是 18:18:37。实际下班回执按其报告精度核对，计划余量不会额外加到真实回执上。

“实际签到”须来自钉钉已打卡记录，经首页人工确认或电脑考勤页读取组件核实。计划时间、启动日志和“预计签退”均不会当作实际记录。重复回执保持幂等，冲突回执会明确拒绝；当班确认后冻结工时、最早下班、浮动和时区，后续保存设置用于后续班次。已触发或错过的当班任务不会因恢复而反复补发；跨午夜的同一班次可保留签退。

## 运行条件

手机须保持供电、USB 调试已允许电脑连接，钉钉保持登录并启用极速打卡；华为应用启动管理开启自启动、关联启动和后台活动，App 允许精确定时、悬浮窗和通知。实际位置、时间和身份验证要求由钉钉处理。手机单独运行时须亮屏且已解锁；带 PIN 锁的方案依赖下述 Windows 组件。

电脑须保持开机、当前 Windows 用户已登录、USB 连接正常。任务会请求避免空闲自动休眠，电脑显示器可关闭、桌面可锁定；注销、关机、手动休眠或 USB 断连会中断电脑辅助功能。手机 App 自身在熄屏且密码锁定时的无人值守启动尚未通过验证。强行停止应用后、重启后或更改时间/时区后，请核对首页排程；时区变更会提示，已保存任务保留绝对时间。

0.9 的状态条会显示锁定、熄屏及供电异常，触发后 10 秒检查钉钉启动请求。解锁组件可在有效的 120 秒窗口内恢复原启动请求；超过窗口则清理并提示人工核对，避免稍后执行陈旧任务。

## 构建与测试

需要 Windows、PowerShell 7、JDK 17，以及 Android SDK 的 `platform-tools`、`platforms;android-35` 和 `build-tools;35.0.0`。通过 Android 官方 SDK Manager 安装这些组件；SDK 路径支持 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、默认 `%LOCALAPPDATA%\Android\Sdk` 或显式参数。

```powershell
./test.ps1
./test/Test-SchedulerState.ps1
./test/Test-AttendanceScreen.ps1
./test/Test-PhoneUnlock.ps1
./test/Test-UnlockRecovery.ps1
./test/Test-AppBridge.ps1
./build.ps1
# 或：
./build.ps1 -SdkPath 'D:\Android\Sdk'
```

安装包生成在 `build/AutoDing-local-test.apk`。首次构建生成本地测试签名密钥 `build/local-test.jks`，同一手机后续更新需沿用该密钥。默认密码仅用于本地测试。构建目录、密钥和设备证据已列入忽略规则。

当前验证包括 6003 项原排程、2031 项实际考勤排程、49 项持久状态、60 项考勤观察解析、10 项 PIN 界面识别、11 项有限恢复和 16 项广播解析检查；CI 运行这些回归测试。检查次数属于算法和合成界面测试，设备兼容性、实际打卡及长期稳定性依赖实机证据。

## Windows 三项本地任务

先连接已开启 USB 调试并允许电脑连接的手机，构建界面读取工具，在本地掩码窗口保存 PIN：

```powershell
./tools/Build-UiDump.ps1
pwsh -STA -File ./tools/Set-PhoneUnlock.ps1 -Serial SERIAL
./tools/Install-TrialTasks.ps1 -Serial SERIAL -LogDirectory 'D:\AutoDing-logs'
Start-ScheduledTask -TaskName AutoDingLocal-Observe
Start-ScheduledTask -TaskName AutoDingLocal-Unlock
Start-ScheduledTask -TaskName AutoDingLocal-Attendance
```

| 任务 | 作用 |
| --- | --- |
| `AutoDingLocal-Observe` | 每分钟采集供电、屏幕、锁定、服务、未来闹钟及钉钉是否前台，持续保存本 App 事件。 |
| `AutoDingLocal-Unlock` | 闹钟前 30 秒检查系统 PIN 界面并最多输入一次；解锁后可在 120 秒内恢复原启动。 |
| `AutoDingLocal-Attendance` | 在确认的考勤页面本地读取签到/签退记录，回传已核实时间；已触发任务可从钉钉首页进入“打卡”页。 |

安装前应停止同一手机手动启动的副本。任务只在当前用户已登录时运行；详细状态、停用、恢复及手动运行方式见 [docs/TRIAL.md](docs/TRIAL.md)。

## PIN 与考勤读取

PIN 默认由当前 Windows 账户的 DPAPI 加密保存在 `%LOCALAPPDATA%\AutoDingLocal\phone-unlock.clixml`，凭据放在仓库外。PIN 不写入 Windows 命令参数或组件日志；输入通过 ADB 标准输入发送，Android 端 `input` 进程参数会短暂包含输入内容。当前 Windows 账户和获准 USB 调试的电脑应由本人控制。输入失败或界面未确认时，组件保存阻断标志并停止后续尝试；核对现场与凭据后再显式清除阻断。

考勤读取依赖 Windows 本地简体中文 OCR，当前仅适配已验证华为设备的 **1080×2400 竖屏钉钉考勤布局**。它先核对钉钉页面及控件，再只识别签到、签退和日期三个区域。界面不匹配、日期不清、未来时间或“未打卡”等结果不会确认为实际记录。版本或布局变化后需重新适配。自动 OCR 仅核对当天回执；昨日签到、今晨跨夜签退需人工核对确认，Android 排程算法支持跨夜。

考勤截图在设备和电脑临时保存，处理结束后删除；原始 OCR 和完整界面文本不写入持续日志。输出仅保留日期、时间、精度、来源、结果与诊断状态，不保存姓名、地点或 PIN。截图清理失败会停止该读取器并报告。运行组件不向 GitHub 上传凭据或现场数据，源码仓库排除密钥、日志和截图。

## 日志与结果

Observe 任务更新 `current.json`、`health.jsonl` 和 `events-*.txt`。解锁与考勤任务分别保存 `status.json`、`events.jsonl`；任务包装器保存 `task-events.jsonl`。App 自身在外部应用目录的 `files/logs/events.jsonl` 持久记录事件，按日归档，保留 14 天；首页保留最近约 6500 字符摘要。实际日志目录应在仓库外。

验证时分别核对系统任务是否到点、钉钉是否进入前台，以及考勤是否有实际记录。一次启动日志或页面回执仍表示考勤待核实；确认实际签退后才将当班标为已确认。

## 与上游的关系

参考 [littleclaw/AutoDing](https://github.com/littleclaw/AutoDing) 的定时启动思路，审阅的上游提交为 `07f99cc19b748e625e2139e0c9f718a50a3aa7b2`。本仓库是独立的精简实现，沿用上游示例 applicationId `xcom.warof.chosen`，与上游安装包会冲突，并非上游完整功能的镜像或官方版本。

遵循 **GPL-3.0-or-later**；完整文本见 [LICENSE](LICENSE)。
