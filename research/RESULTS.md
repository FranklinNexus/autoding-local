# 研究结果与验收记录

记录日期：2026-11-17（Asia/Shanghai）。本页依据本轮主执行流程复核的构建输出、Java 夹具输出及华为实机原生报告更新；未完成项目继续标注待测或待开发。当前实机结论只适用于本轮测试的手机和所述试次。

## 实机环境

| 项目 | 取证值 |
| --- | --- |
| 机型 | 华为 `JEF-AN00` |
| Android / API | Android 12 / API 31 |
| `hw_sc.build.platform.version` | `4.2.0` |
| `ro.build.version.incremental` | `104.2.0.121C00` |
| `ro.build.version.security_patch` | `2021-10-05`（系统属性报告值） |
| 时区 | `Asia/Shanghai` |

以上系统字段按设备属性原样记录，不据此推断其他系统版本或补丁状态。本地脱敏环境证据为工作区 `outputs/AutoDing-capability-research/2026-11-17/device-metadata.json`。

## 实现状态

| 项目 | 当前已具备 | 结论 |
| --- | --- | --- |
| `org.autoding.research` | 无障碍结构扫描、截图接口检查、两种 60 秒闹钟接口、带任务关联的 JSON 报告 | 华为 USB 连接下系统闹钟接口单次晚 34ms，同次窗口/截图通过；普通接口实测被系统后移 |
| `autoding_research` 位置源 | 专用合成源注入、回读、mock 标记及清理；Java 夹具 | 39 项夹具通过；华为成功路径通过，系统设置及研究权限已恢复 |
| `org.autoding.ocrprobe` | APK 内置中文模型、手工选图、合成图片、严格时间解析器及 Java 夹具 | 构建、离线重建、验签、模型及 ARM 库核查、网络权限检查和 21 项解析检查通过；华为应用无网络权限下首次合成推理通过，整机断网对照和真实图片待测 |
| 手机本机 PIN 存储与输入 | 当前研究 App 未实现或测试 | 待单独设计与验收；PC 安装解锁不计入 |
| 截图至 OCR、调度至官方记录闭环 | 当前探针之间尚未集成 | 待开发 |

## 本地研究产物

以下 SHA-256 为本轮核实的研究 APK。原生当前安装版本增加系统闹钟接口对照，并保留历史试次哈希；OCR APK 未变。后续重新构建应重新记录哈希；构建产物不入公开仓库。

| 产物路径 | 字节数 | SHA-256 |
| --- | --- | --- |
| 原生初版（历史试次） | 20960 | `09e72510032a77d9bfb4d039c43fac5162ec3a35b87603ae4611ca91928ea76f` |
| 原生任务关联版（历史试次） | 25056 | `c0d3f602d95eece3de367a4853edeb73c56ef3788519a8f181972c6f016c4a44` |
| `build/AutoDing-research.apk`（当前系统闹钟接口对照版） | 25056 | `266337f975cbc0f8b3b86f9f8a19af040729d588fb26618cd24d6cbbcff0e6e4` |
| `research/ocr/build/distribution/AutoDing-OcrProbe-debug.apk` | 52482268 | `0d459df3c3bbbe1d8fd3baa936499f3149ce8e521a22f9b18e4040bd1e68ee57` |

## 测试矩阵

| ID | 测试条件与预期 | 状态 | 证据 / 实际结果 |
| --- | --- | --- | --- |
| B1 | 原生研究 APK 构建及签名校验成功 | 通过 | 原生构建脚本完成，含签名校验 |
| B2 | OCR APK 构建及离线重建、验签成功，合并 Manifest 无网络权限，内置模型和 ARM 库齐备 | 通过 | 应用无网络权限首次推理见 E2a，整机断网对照见 E2n |
| U1 | 位置 Java 夹具通过，异常路径清理专用源 | 通过 | 39 项隔离夹具检查 |
| U2 | 纯 Java 回执解析器测试通过 | 通过 | 21 项解析检查 |
| E1a | 解锁状态窗口结构可读且遍历完成 | 通过 | `rootAvailable=true`、`scannedNodes=11`、`scanComplete=true`；目标考勤回执内容仍待验证 |
| E1b | 安全 PIN 页结构扫描，只记录空密码框候选与数字键计数 | 通过 | `rootAvailable=true`、`scannedNodes=49`、`emptyFocusedPinEntries=1`、`digitCount=10`、`pinUiCandidate=true`、`locked=true` |
| E1s | 解锁和锁屏 PIN 页均可取得截图缓冲区，图像未落盘 | 通过 | 两种状态均为 `available`、`1080×2400`、`imagePersisted=false`；无障碍已连接 |
| E1c | 截图安全窗口/间隔限制返回值可识别，图像未落盘 | 待测 | — |
| E2a | 安装后在应用无网络权限条件下首次运行内置合成样例，读取实际 09:18 / 18:22 | 通过 | 实机显示 `2026-11-05` / `09:18` / `18:22`；正确区分计划 `09:00` / `18:18`。固定样例时间，不作为当天记录 |
| E2n | 手机整机主动断网后运行合成样例，与 E2a 对照 | 待测 | 本轮保持手机原有网络状态，未物理断网 |
| E2b | 本机选择当前真实页面图片，候选与官方记录一致 | 待测 | — |
| E2c | 布局不支持、旧日期、未来或冲突记录保持待核实 | 解析夹具通过，OCR 实机待测 | 解析器反例覆盖见 U2；真实图片仍需验证 |
| E3a | 接电脑且锁屏，闹钟触发后完成同次窗口扫描与截图 | 链路通过 | 新版 `alarmId=alarmFiredForId`，`alarmScanId=windowScan.scanId=screenshot.scanId`；截图 `available` / `1080×2400` / 未落盘；触发前被点亮，不作为保持息屏证据 |
| E3t | 普通 `setExactAndAllowWhileIdle` 按请求的 60 秒时点触发 | 未通过 | 新版第 1 次关联复测迟到 `29851ms`；保持息屏对照的系统排程后移 `175889ms`，最终迟到 `175970ms`，原因待诊断 |
| E3w | 保持息屏到触发后再读取结果，验证唤醒请求及同次扫描/截图 | 通过（USB 连接） | `alarmInteractiveBeforeWake=false`、`alarmLocked=true`；闹钟/扫描编号匹配，窗口和截图均完成；准点另见 E3t |
| E3c | `setAlarmClock` 系统闹钟接口在锁屏息屏时触发，完成同次窗口扫描与截图 | 单次通过（USB 连接） | 系统有效目标与请求相等，实际晚 `34ms`；保持息屏到计划后约 80 秒才读取，扫描/截图编号一致且无错误 |
| E3b | 拔 USB、接墙上电源且锁屏息屏，60 秒闹钟触发并完成扫描 | 待测 | 与 E3a 独立验收 |
| E4 | 手机本机保留 PIN 的一次解锁实验 | 待开发 | 研究 App 未实现或测试本机 PIN 输入；PC 安装解锁不计入 |
| E5 | 拔 USB 后定时、解锁、打开页面、识别与官方记录完整闭环 | 待集成 | 真实截图 OCR、按实际上班时间安排动态下班及官方记录闭环尚未接通 |
| E6a | 隔夜及系统后台管理下服务、闹钟与观测仍有效 | 待测 | — |
| E6b | 网络中断/恢复、服务终止、权限撤回有明确状态 | 待设计 | — |
| E6c | 手机重启后首次凭据解锁与登录数据可用性 | 待测 | 与日常息屏解锁分开验收 |
| L1a | 未选择模拟应用/未授精确权限时只给出前置条件 | 夹具通过，实机待补 | 前置条件分支由 U1 覆盖 |
| L1b | 选定研究 App 后合成位置源注入、回读、`isMock=true` | 通过 | 仅合成源 `autoding_research`，`readBack=true`、`isMock=true`；未动标准位置源 |
| L1c | 成功及失败后 `cleanupSucceeded=true`、专用源不存在 | 实机成功路径通过 | 实机 `cleanupSucceeded=true`；异常清理由夹具覆盖，实机失败路径待补 |
| L1d | 试验结束后恢复系统选择并撤销研究位置权限 | 通过 | mock app-op 已恢复 `default`；研究位置权限已撤销 |

## 定时证据与观测限制

### 初版触发记录

初版原生报告保存以下字段，`dumpsys` 还观察到 1 次 wakeup；这组记录对应更新任务关联字段前的研究 APK：

```json
{
  "alarmScheduledAt": 1794880737250,
  "alarmFiredAt": 1794880737325,
  "alarmLocked": true,
  "alarmScanRequested": true,
  "updatedAt": 1794880737430
}
```

计划时间为北京时间 `2026-11-17 09:58:57.250`，触发时间为 `09:58:57.325`，差值 `+75ms`。这证明本次锁屏定时触发及扫描请求已发生。初版 `windowScan` 和 `screenshot` 没有各自的采集时间或任务编号，报告中的旧值可能保留，因此这组记录不计为扫描/截图完成证据。新版已补齐任务编号及采集时间，并记录 `alarmInteractiveBeforeWake`。

### 新版关联链路复测

计划北京时间 `10:10:22.173`（`1794881422173`），实际触发 `10:10:52.024`（`1794881452024`），迟到 `29851ms`。本次 `alarmId` 与 `alarmFiredForId` 一致，`alarmScanId`、`windowScan.scanId`、`screenshot.scanId` 一致；扫描请求为 `1794881452044`，窗口完成为 `1794881452099`，截图完成为 `1794881452122`。截图为 `available`、`1080×2400`、`imagePersisted=false`，证明同次异步链路完成。

本次在计划后约 20 秒为了读报告而启动研究界面，屏幕被提前点亮；触发时 `alarmInteractiveBeforeWake=true`、`alarmLocked=true`。因此该记录通过锁屏状态的定时至扫描/截图链路，保持息屏唤醒另行验收。

### 保持息屏对照的系统排程证据

后续对照请求 `origWhen=1794881576115`（北京时间 `10:12:56.115`），App 计划的 `elapsed=169024111`，系统即时 `dumpsys` 已显示 `whenElapsed=169200000`，后移 `175889ms`；同时 `window=0`、`exactAllowReason=permission`，屏幕状态 `Asleep`。这说明本次有精确闹钟权限时仍出现系统层调度后移，具体系统/OEM 根因待日志诊断。

最终保存的 `alarmFiredAt=1794881752085`（北京时间 `10:15:52.085`），比 App 计划迟 `175970ms`（`2分55.970秒`），与系统后移后的有效目标吻合。`alarmInteractiveBeforeWake=false`、`alarmLocked=true`；`alarmId=alarmFiredForId`，`alarmScanId=windowScan.scanId=screenshot.scanId`；窗口完成 `1794881752306`，截图完成 `1794881752379`，截图为 `available`、`1080×2400`、`imagePersisted=false`。本次保持息屏、唤醒请求及同次扫描/截图链路通过，准点触发未通过。

本地脱敏证据归档于工作区 `outputs/AutoDing-capability-research/2026-11-17/alarm-v3-result.json` 及 `alarm-v3-system-deferral.json`。这些路径为工作区研究证据，不作为公开仓库内置数据。

### 系统闹钟接口单次对照

最后一次改用 `setAlarmClock`：计划北京时间 `10:21:39.685`（`1794882099685`），系统有效目标 `whenElapsed=169547681` 与 App 请求的单调时钟目标相等。实际触发 `10:21:39.719`（`1794882099719`），差值 `+34ms`。

`alarmId=alarmFiredForId=0e0822c3-c94f-4602-b34f-b1d5eebe72b1`；`alarmLocked=true`、`alarmInteractiveBeforeWake=false`、`alarmScanRequested=true`。`alarmScanId=windowScan.scanId=screenshot.scanId`，请求时间 `1794882099736`，窗口完成 `1794882100047`，截图完成 `1794882100067`；截图为 `available`、`1080×2400`、`imagePersisted=false`，`scanError` 和 `screenshotError` 均为 `null`。

测试全程 USB 连接，安排后返回桌面并息屏，保持到计划时间后约 80 秒才启动研究界面读取报告，未提前点亮。本次锁屏息屏下的系统闹钟接口及同次窗口/截图链路通过；这只是一轮对照，不外推隔夜、重复执行、跨机型、拔 USB、本机 PIN 解锁或完整考勤。

本地脱敏证据：工作区 `outputs/AutoDing-capability-research/2026-11-17/alarmclock-v4-scheduled.json`、`alarmclock-v4-result.json`、`alarmclock-v4-verified.json`。`captureTimestamp` 仅保留为截图 API 原始时钟字段；本轮关联使用任务编号、请求/完成时间和同源单调时钟，未将其与墙上时钟或 `elapsedRealtime` 直接相减。

本轮华为手机锁屏后台首次执行 shell `am broadcast` 读取报告时返回 `result=0`；使用 `am start` 启动研究 `MainActivity` 后，显式携带 `-a org.autoding.research.PROBE` 的调用读取成功。广播失败原因待日志诊断，尚未确定为华为系统拦截；桥接读取失败不代表原生闹钟未触发。

OCR 安装后首次启动合成样例，限定只读取 `org.autoding.ocrprobe` 的界面节点，实机显示班次日期 `2026-11-05`、实际上班 `09:18`、实际下班 `18:22`。设备包信息确认没有 `INTERNET` / `ACCESS_NETWORK_STATE` 权限。此结果证明应用无网络权限下的首次模型推理成功；手机网络保持原状，整机主动断网对照仍待执行。

本轮研究 App 仍未实现或测试手机本机 PIN 输入；整机主动断网 OCR 对照、OCR 真实图片、拔 USB 并接墙上电源、完整独立考勤闭环、隔夜运行、重启后的首次解锁也尚未执行。原生研究 App 报告确认 `hasPinStorage=false`、`hasNetworkPermission=false`，未在研究 App 中保存或输入 PIN。

安装新版 APK 期间，既有 PC 解锁助手按此前授权完成一次 `verified-unlocked`；临时暂停的旧后台任务已恢复启用和运行。该安装操作依赖电脑，单列为 PC 辅助步骤，未计入 E4 或手机独立解锁验收。

## 结束时恢复检查

| 检查项 | 最终结果 |
| --- | --- |
| 研究 RTC 待触发闹钟 | `pendingResearchRtcAlarms=0` |
| 研究模拟位置 app-op | `default` |
| 研究 App 精确 / 粗略位置权限 | `fine=false` / `coarse=false` |
| 既有 PC 解锁任务 | 已恢复 `enabled` / `running` |

## 单次记录模板

```text
测试 ID：
时间（Asia/Shanghai）：
设备型号 / Android API / 系统构建 / 安全补丁：
APK 包名 / SHA-256：
USB：已接 / 已拔；电源：USB / 墙上电源 / 电池
锁屏 / 屏幕状态：
无障碍 / 精确闹钟 / 精确位置 / 模拟应用选择：
操作与预期：
触发前后的相关报告字段：
实际结果：通过 / 失败 / 待核实
脱敏证据路径：
清理及恢复结果：
剩余限制：
```

候选窗口、截图接口可用或合成源成功均不足以替代官方考勤回执。完整闭环验收还需要绑定新采集页面、明确当天实际上班时间、计算下班最早允许时刻，并核对官方记录。
