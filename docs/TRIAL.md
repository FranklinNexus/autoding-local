# 实机试用与故障核对

运行证据保存在本地目录，仓库仅维护复现方法与去除个人信息后的结论。当前版本 0.9-local 已安装；验证范围限于一台华为 JEF-AN00、HarmonyOS 4.2、Android API 31、钉钉 9.0.5、1080×2400 竖屏布局。

## 从实际签到计算签退

默认规则为最早下班 18:00、最短间隔 540 分钟。先核对钉钉已打卡记录，再由首页“确认实际打卡记录”输入，或让 Attendance 组件读取当前考勤页。分钟精度的签到额外留 60 秒计划余量；准确到秒则保留真实秒数。最早触发取配置的最早下班与“实际签到＋间隔＋余量”中较晚者，浮动只向后。

例如仅显示 09:18 的真实签到对应最早 18:19，默认 0–5 分钟窗口为 18:19–18:24。随机值保存后保持稳定，重复保存同样设置和重复回执不会重新抽签。实际签退回执按其显示精度核验，计划余量只影响触发时间。

没有已确认签到时，不安排固定 18:00 签退；界面保持待确认。一次打开钉钉也不会产生“已确认”记录。冲突回执明确拒绝，当班已确认规则冻结，后续设置用于后续班次。按班次日期和签到/签退类型去重；错过已保存时刻后记录为错过，恢复不会重复补发同一日任务。跨午夜的同一班次可以保留签退约束。

“设置指定日期的上班 / 休息”可覆盖星期规则：`work` 指定上班，`rest` 指定休息，`default` 恢复星期规则。已有实际签到代表当班已经开始，随后改成休息仍保留签退。系统时区变化会提示，已保存任务保留绝对时刻，已确认班次沿用原时区约束。

## 三项 Windows 任务

电脑须开机且当前 Windows 用户已登录，手机持续 USB 供电并允许这台电脑调试；钉钉保持登录、极速打卡启用，手机 App 权限和后台设置已完成。脚本会请求避免空闲休眠；电脑显示器可关闭、Windows 桌面可锁定。注销、关机、手动休眠或 USB 断连仍会中断电脑辅助功能。

```powershell
./tools/Build-UiDump.ps1
pwsh -STA -File ./tools/Set-PhoneUnlock.ps1 -Serial SERIAL
./tools/Install-TrialTasks.ps1 -Serial SERIAL -LogDirectory 'D:\AutoDing-logs'
Start-ScheduledTask -TaskName AutoDingLocal-Observe
Start-ScheduledTask -TaskName AutoDingLocal-Unlock
Start-ScheduledTask -TaskName AutoDingLocal-Attendance
Get-ScheduledTask -TaskName 'AutoDingLocal-*' | Select-Object TaskName, State
```

安装前停止手动运行的同一手机副本。三个任务在当前用户登录时启动，不保存 Windows 密码；`Observe` 负责状态与事件，`Unlock` 负责有限 PIN 解锁与原请求恢复，`Attendance` 负责当前钉钉考勤页的实际记录。必要时可向安装脚本传入 `-CredentialPath`，应与本地 PIN 设置窗口使用同一路径且位于仓库外。

手动单次检查方式：

```powershell
./tools/Observe-Phone.ps1 -Serial SERIAL -Once -OutputDirectory 'D:\AutoDing-logs\AutoDing-trial'
./tools/Watch-Attendance.ps1 -Serial SERIAL -Once -OutputDirectory 'D:\AutoDing-logs\AutoDing-attendance'
```

Attendance 常规模式每约 30 秒检查一次。当钉钉首页前台、App 记录显示本次任务已经触发时，它可点击已核实的“打卡”快捷入口；`-NavigateToAttendance` 用于显式单次页面导航。它不会将“预计签退”或“未打卡”当成实际记录。

## 日志文件

| 目录/文件 | 内容 |
| --- | --- |
| `AutoDing-trial/current.json` | 最近一次连接、供电、屏幕、锁定、守候服务、未来闹钟与钉钉前台状态。 |
| `AutoDing-trial/health.jsonl` | 每分钟追加的状态，ISO 8601 时间。 |
| `AutoDing-trial/events-*.txt` | `AutoDingLocal` 标签实时事件；断线重连后新建文件。 |
| `AutoDing-trial/collector-errors.txt` | 电脑采集故障，区别于手机 App 故障。 |
| `AutoDing-unlock/status.json`、`events.jsonl` | 解锁状态与结果，不包含 PIN 或密码框内容。 |
| `AutoDing-attendance/status.json`、`events.jsonl` | 结构化考勤观察、确认结果与诊断状态。 |
| 各目录的 `task-events.jsonl` | Windows 任务启动、停止与异常。 |
| 手机 `Android/data/xcom.warof.chosen/files/logs/events.jsonl` | App 持久事件，按日轮转为 `events-YYYY-MM-DD.jsonl`，保留 14 天。 |

App 首页另保留最近约 6500 字符摘要。`adb logcat` 缓冲会覆盖旧内容，应在测试前启动持续采集。每分钟快照可能错过短暂前台页面，须与事件和实际考勤记录合看。

## PIN 与 120 秒恢复边界

PIN 默认由当前 Windows 账户 DPAPI 加密保存在 `%LOCALAPPDATA%\AutoDingLocal\phone-unlock.clixml`，通过本地掩码窗口设置。只有本人控制的当前账户和已允许调试的电脑才适合运行。PIN 不进入 Windows 命令参数和日志；通过 ADB 标准输入发送后，Android 端 `input` 进程参数会短暂包含输入内容。

Unlock 在本 App 闹钟前 30 秒唤醒手机，确认系统安全锁屏、空且聚焦的密码框和完整数字键盘后最多输入一次。它核对锁屏已解除，再处理后续启动；输入失败、界面不符或结果未确认会保存阻断并停止后续尝试，不反复输入。

同一手机跨日志目录共享尝试记录和命名互斥。状态目录为 `%LOCALAPPDATA%\AutoDingLocal\devices\<序列号的 SHA256>\`，其中 `unlock.blocked.json` 表示待人工核查。先核对手机、凭据和事件；确认原因解决后，再显式删除对应阻断文件并重启。更换输出目录不会清除阻断。

原任务触发后，页面已显示但钉钉启动请求尚未发出时保留 token，Unlock 可在 120 秒有效窗口内恢复同一请求；它不重新 claim 或创建当天第二次任务。App 会校验 token、任务状态、日期和签退约束，钉钉启动请求发出后才消费 token。超过窗口清除请求并记录错过，稍后打开首页也不执行陈旧启动。触发后 10 秒仍未完成钉钉启动请求会记录诊断；这些状态仍需独立核对实际考勤。

`-TestOnce` 用于现场确认后立即检查并最多输入一次；正常运行只在原闹钟及有效待处理窗口内动作。界面读取辅助工具临时通过 Android Shell 的 UiAutomation 获取控件，隐藏密码内容；不等待界面空闲，避免动态锁屏提示导致常规 dump 超时。

## OCR 范围与临时数据

考勤读取使用 Windows 本地简体中文 OCR，须已安装 `zh-Hans` 识别语言；当前只适配已验证的华为 **1080×2400 竖屏钉钉考勤布局**。先核对钉钉前台和“打卡 / 统计 / 设置”页签，再识别签到、签退、日期三个指定区域。页面、布局或日期不匹配时保持待核实；其他手机、分辨率和钉钉版本需另外适配。

自动 OCR 仅核对当天回执；昨日签到、今晨跨夜签退需人工核对并在 App 确认。Android 排程算法支持跨夜，自动读取组件尚未覆盖跨夜回执布局。

截图在设备和电脑临时创建，处理结束后均删除；清理失败会停止该读取器并报告。原始 OCR、完整控件文本不写入持续日志，输出仅保留日期、时间、精度、来源、结果和诊断码。姓名、地点、PIN、凭据、截图及现场日志均不纳入源码上传。`Read-AttendanceOcr.ps1 -ImagePath PATH` 是低层本地适配器，独立调用时由调用者管理输入图片；常规 watcher 负责临时图清理。

## 停止与恢复

前台脚本可按 Ctrl+C。后台任务可在对应输出目录创建 `stop.request`，等待脚本退出并释放子进程；下次启动前删除停止标志。例如：

```powershell
New-Item -ItemType File 'D:\AutoDing-logs\AutoDing-trial\stop.request'
Stop-ScheduledTask -TaskName AutoDingLocal-Observe
Disable-ScheduledTask -TaskName AutoDingLocal-Observe
```

对 Unlock、Attendance 使用各自目录与任务名。安装脚本还设置每两分钟的重新启动触发器，运行中的任务由 IgnoreNew 保持单实例。需要有意停止时停用对应计划任务，避免停止标志引起周期性空转；优先让脚本看到停止标志后退出。任务停用只影响电脑组件，手机日常排程须在 App 内另行停用。凭据阻断文件不是停止标志，应按前述步骤单独处理。

## 实机结论与验证范围

2026-11-04 在已解锁且供电的设备上，多次定时后台唤起钉钉通过；独立考勤页也有极速打卡记录，但并非每次启动测试均对应一次新打卡。

2026-11-05 首次隔夜：系统闹钟按时触发并显示提醒，但手机保持密码锁定，未完成启动页面和钉钉前台链路；用户手动打开钉钉后才成功打卡。锁屏期间底层启动限制的具体判定未保留下来。当天手动打开 AutoDing 还消费了积压请求，后续版本增加 120 秒过期保护。电脑采集曾有短暂空档，已重启。

随后两次 USB PIN 辅助实测均从锁屏解除到定时打开钉钉通过，其中一次由 Windows 后台任务接管；两次均确认钉钉前台，未将启动证据当成独立实际打卡结果。0.9 已安装，已从当前考勤页确认实际签到，按签到与 540 分钟规则保存签退时间；当前界面读取与回执链路已通过现场核对。新版另完成两次锁屏启动测试，其中一次在闹钟触发后恢复已停用的电脑解锁组件，在原 120 秒窗口内解锁并续接，重复恢复请求返回 not-pending；该测试模拟组件中断，未实际拔插 USB。重复读取签到返回 unchanged，保存的签退时间保持不变。今晚真实签退、连续多日、实际 USB 重连、整机重启和其他设备仍待验证。

代码回归包括 **6003 项原排程＋2031 项实际考勤排程、49 项持久状态、60 项考勤观察解析、10 项 PIN 界面识别、11 项有限恢复、16 项广播解析、23 项完整班次预演**。预演使用合成的 09:18/18:22 回执和隔离测试时钟，经过排程、闹钟领取、锁屏续接、启动请求和实际回执确认，不会打开真实钉钉。这些数量未代表相同次数的实机打卡，今晚真实签退仍待单独验收。
