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

2026-11-04 在上述华为设备上有两次后台定时唤起成功的记录；考勤页面还出现一条下班“极速打卡”记录，其时间早于第二次启动测试，因此作为独立考勤证据记录。原型还缺少 App 内持久文件日志、结果回执、隔夜测试和对各厂商系统的覆盖。适合先作为实验仓库，持续收集启动成功率和真实考勤结果。
