# 实机试用记录方法

将应用日志和考勤记录分别核对。运行证据保存在本地目录，仓库中只维护复现方法和去除个人信息后的结论。

## 采集文件

- `current.json`：最近一次采集的设备连接、供电、屏幕、守候服务、未来闹钟时间及钉钉是否处于前台。
- `health.jsonl`：每分钟追加一份状态，时间使用 ISO 8601。
- `events-*.txt`：仅 `AutoDingLocal` 标签的实时日志。断线重连时重新创建文件。
- `collector-errors.txt`：采集异常，区别于手机应用故障。
- `observer.pid`：电脑端采集器进程号。

`adb logcat` 的缓冲区会覆盖旧内容，持续采集器应在测试开始前启动。App 自己的界面还保留最近约 6500 字符记录，内容随着新事件增加而截断。持续模式运行期间会向 Windows 请求阻止空闲自动休眠，退出后释放；电脑屏幕仍可自动关闭。手动休眠、关机或断开 USB 会造成采集缺口。

## 检查一次定时启动

1. 查看 App 首页的下一次准确时间。
2. 持续采集正常后，安排“60 秒后测试一次”，将 App 退到后台。
3. 在 `events-*.txt` 中确认系统闹钟触发、启动页面显示和打开钉钉请求。
4. 查看 `health.jsonl` 中 `dingForeground` 的变化，独立核对钉钉考勤页。

每分钟健康快照可能错过只在前台短暂出现的页面；事件日志是主要启动证据。采集器自身不自动点击或打开钉钉。

## 停止采集

前台运行时按 Ctrl+C。后台运行时，在输出目录创建 `stop.request`，采集器会停止并结束自己的日志子进程：

```powershell
$trialDirectory = 'D:\AutoDing-trial'
New-Item -ItemType File (Join-Path $trialDirectory 'stop.request')
```

下次启动前删除这个停止标志。如果采集器失去响应，确认进程是本次采集器后，再执行：

```powershell
$trialDirectory = 'D:\AutoDing-trial'
$observerId = [int](Get-Content (Join-Path $trialDirectory 'observer.pid'))
Get-CimInstance Win32_Process -Filter "ProcessId=$observerId" | Select-Object ProcessId, CommandLine
Stop-Process -Id $observerId
```

退出采集器后如仍有 `adb logcat` 子进程，只停止命令行中包含 `AutoDingLocal:I` 的本次采集进程。无需停止 ADB 服务或手机日常排程。

## 当前结论

2026-11-04 在上述华为设备上有两次后台定时唤起成功的记录；考勤页面还出现一条下班“极速打卡”记录，其时间早于第二次启动测试，因此作为独立考勤证据记录。当晚另一次测试于 19:06:55（UTC+8）触发系统闹钟、19:06:56 启动页面、19:06:57 发出打开钉钉请求，事件已由电脑端采集器实时保存，并确认钉钉进入前台；本次实际考勤结果尚未核实。

2026-11-05 首次隔夜结果：08:58:28 系统闹钟按时触发并显示提醒，但没有随后页面启动或打开钉钉的记录。电脑状态采集显示前晚 20:49 手机为熄屏锁定，次日 07:51 虽已亮屏仍锁定，08:58 至 09:18 的采样持续锁定。09:19 首次采样为已解锁。用户报告手动打开钉钉后才成功打卡。因此此次日常任务验证失败，证据指向密码锁定下启动链路中断，不支持将“已显示提醒”记作“已打开钉钉”。锁屏期间底层启动限制的具体系统判定未保留下来。

同日 09:35 手动打开 AutoDing 时接收并执行了积压的上班启动请求，0.8 增加两分钟过期校验和服务超时清理。电脑端采集器在 09:19 后结束，已在 09:36 重启；这不影响对 08:58 事件的判断，但该空档内没有连续采集证据。

原型还缺少 App 内持久文件日志、结果回执、连续多日验证和对各厂商系统的覆盖。适合先作为实验仓库，持续收集启动成功率和真实考勤结果。

同日 USB PIN 组件验证：10:19:59 单次解锁验证成功；随后在锁屏状态下安排 60 秒任务，10:21:34 电脑输入 PIN 并确认解除锁定，10:21:57 系统闹钟触发，10:21:58 页面显示，10:21:59 发出打开钉钉请求，且系统前台状态确认为钉钉。本次证明电脑辅助解锁后的启动链路，未独立核实本次考勤结果。

同日移交给 Windows 后台任务后再次从锁屏测试：10:31:21 确认解锁，10:31:44 系统闹钟触发，10:31:45 页面显示，10:31:46 发出打开钉钉请求，系统前台状态再次确认为钉钉。两个任务均保持 Running；跨日志目录尝试启动同一手机的第二个解锁实例被拒绝。

## USB 解锁组件

Windows PowerShell 7 的运行方式见 README。`-TestOnce` 立即检查并最多输入一次，使用前应先在手机上确认 PIN；常规模式只在本 App 的已排定闹钟前 30 秒处理。

解锁输出目录的 `status.json` 和 `events.jsonl` 保存状态与结果，不保存 PIN、密码输入框文本或界面原文。在输出目录创建 `stop.request` 可停止。跨日志目录共用的阻断标志和每闹钟尝试记录在 `%LOCALAPPDATA%\AutoDingLocal\devices\<手机序列号的 SHA256>\`；同一手机使用命名互斥，避免重复实例输入。

`unlock.blocked.json` 表示尚未核实或失败的尝试，后续自动输入停止。先核对本地事件、手机界面和凭据；需要人工处理时在手机上操作。确认原因已解决后显式删除对应设备状态目录的阻断文件，再重新启动。不要通过换输出目录绕过阻断。

本次调试中原生 `uiautomator dump` 在动态锁屏提示等待空闲失败；附带的 `ReadWindow` 辅助工具不等待界面空闲，临时通过 Android Shell 连接 UiAutomation，输出密码框的空值标记并隐藏内容。仅在系统密码框已聚焦、确认空输入且数字键完整时输入 PIN。

为了让采集和解锁进程独立于本次 Codex 会话运行，可安装当前 Windows 用户的两个本地任务：

```powershell
./tools/Install-TrialTasks.ps1 -Serial SERIAL -LogDirectory 'D:\AutoDing-logs' -CredentialPath 'D:\AutoDing-local-state\phone-unlock.clixml'
Start-ScheduledTask -TaskName AutoDingLocal-Observe
Start-ScheduledTask -TaskName AutoDingLocal-Unlock
```

安装前应停止同一手机手动启动的采集/解锁副本。任务在当前用户登录后启动，保持电脑空闲不休眠；Windows 桌面可以锁定。注销、关机、手动休眠或断开 USB 仍会中断试用。任务不保存 Windows 密码。停止/停用任务可使用 `Stop-ScheduledTask` 和 `Disable-ScheduledTask`；这些操作不会停用手机自身的日常定时。

`-CredentialPath` 应指向本地设置窗口生成的 DPAPI 凭据，可在 `Set-PhoneUnlock.ps1` 中传入同一路径；该目录应在源码仓库外，并且 Windows 后台任务可读取。省略时使用当前账户的 LOCALAPPDATA 默认位置。`task-events.jsonl` 记录任务启动、停止与异常，方便区分后台任务故障和手机 App 故障。
