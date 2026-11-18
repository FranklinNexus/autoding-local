# 研究结果与验收记录

## 2026-11-18 更新：两日手机排程

新增限定日期的原生上/下班排程、电池运行、按真实签到计算下班和报告存档，说明见 [standalone/NATIVE-TRIAL.md](standalone/NATIVE-TRIAL.md)。11 月 18 日 10:00 连 USB 的锁屏闹钟任务通过：22.658s，签到 09:37 / 下班未打卡，主动作 0 次，自动创建 18:47 下班任务并重新锁屏。此前空卡识别会因勾图标遮盖挡住“未”字而停止，已用原始空卡的严格独立识别修正。

已部署试用日期 11 月 18–19 日，明早 07:00，晚间规则为 max(18:30, 实际签到 + 9h10m)，旧手机/电脑排程暂停。当前安装 dc2e89... 与本地哈希一致；首个定时读取试次使用 05a77...，具体哈希和检查见上述说明。首次纯电池试次只通过闹钟及 PIN 解锁，导航阶段挂起后超时；研究 App 的华为启动设置仍是自动管理，已更正为三项手动允许，同包只读复测待验收。电池单次、晚间真实提交与翌日首次签到分别取证，当前不推断隔夜通过。下方记录为 11 月 17 日及以前的历史试次。

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
| 手机本机 PIN 存储与输入 | 已实现 Android Keystore 加密保存、SystemUI 数字键识别、单次尝试及进度保护 | 本轮 v1 因进度观测不符停止，v2 锁屏息屏原生解锁单次通过；37 项隔离夹具通过 |
| 原生单次流程 | 系统闹钟、原生解锁、页面导航、截图 OCR、单次下班、已观察早退确认、成功弹窗识别及有限结果导航 | v27 一次真实拔线墙充完整闭环通过：USB `false`、AC、锁屏息屏，耗时 `32.722s`，签到保持 `09:25`、新签退 `15:37`；手机每日排程和长期可靠性仍未验收 |

## 本地研究产物

以下 SHA-256 包含历史探针和本轮最终单次流程 APK，按对应试次保留。重新构建应重新记录哈希；构建产物不入公开仓库。

| 产物路径 | 字节数 | SHA-256 |
| --- | --- | --- |
| 原生初版（历史试次） | 20960 | `09e72510032a77d9bfb4d039c43fac5162ec3a35b87603ae4611ca91928ea76f` |
| 原生任务关联版（历史试次） | 25056 | `c0d3f602d95eece3de367a4853edeb73c56ef3788519a8f181972c6f016c4a44` |
| 原生系统闹钟接口对照版（历史试次） | 25056 | `266337f975cbc0f8b3b86f9f8a19af040729d588fb26618cd24d6cbbcff0e6e4` |
| `research/ocr/build/distribution/AutoDing-OcrProbe-debug.apk` | 52482268 | `0d459df3c3bbbe1d8fd3baa936499f3149ce8e521a22f9b18e4040bd1e68ee57` |
| v21 / v22 `build/AutoDing-standalone-research.apk` | 51121441 | `bb61a88edc821d3fb0da1c9e65a0b5cecf813658d2cedb1fe228d80170c79924` |

提交前移除了 Bridge 中重复且不可达的 `success-current` 分支，重新构建、验签及模型/权限检查通过；重建产物同为 `51121441` 字节，SHA-256 为 `48c6a78c17b7d03d57a171b89513c33ebb6e136dc81b3192c2000872e2e1b9ea`。该重建包尚未安装或实测，v21 / v22 的手机安装包和验收哈希仍以上表为准。

后续 v24 安装包 SHA-256 为 `a59139f3a1a9149626703efb94dca07fd1d267712d1d76eb178cd5f8139d66bf`，主执行流程核对手机安装包与本地哈希一致。该版本的当前成功弹窗只读识别通过，实际拔线试验未在断开期间触发流程；不继承 `bb61...` 版本的完整通过结论。

v25 使用已安装并核对手机/本地哈希一致的手动安排版 `c16c4160891128e142fe18c1dcb82b46aa956e82ebf858874c67386488879b4a`。该版移除等待 USB 断开后自动安排的入口，用户实际拔线接充电头后在手机确认 180 秒试次；严格启动检查通过后才唤醒及输入 PIN。v25 实际通过墙充唤醒、原生解锁和导航，回执识别未通过且没有提交打卡，具体证据如下。

随后安装 `4b4d16dface0e88bb522fde1be00ca0b690b07fde9551039efe153278646c6e5`：只将已观察标题行归位手势调整为单次 `(150,625)→(950,625)`、`800ms`，完成后等待 `1000ms`。该版 v26 连电脑原生只读流程通过，回执解析判据保持不变；v27 墙充完整试次结果另行记录。

同一 `4b4d...` 安装包的 v27 真实拔线墙充完整流程通过，手机/本地哈希一致。最终 release 构建、验签、内置中文模型及无网络权限检查通过，归位参数版 lint 为 `0 errors, 17 warnings`；相关变更的 37 项解锁、39 项位置、29 项回执、28 项早退、17 项成功弹窗及 11 项墙充启动检查共 161 项通过，并非全数在最后一次试验重复运行。

## 测试矩阵

| ID | 测试条件与预期 | 状态 | 证据 / 实际结果 |
| --- | --- | --- | --- |
| B1 | 原生研究 APK 构建及签名校验成功 | 通过 | 原生构建脚本完成，含签名校验 |
| B2 | OCR APK 构建及离线重建、验签成功，合并 Manifest 无网络权限，内置模型和 ARM 库齐备 | 通过 | 应用无网络权限首次推理见 E2a，整机断网对照见 E2n |
| U1 | 位置 Java 夹具通过，异常路径清理专用源 | 通过 | 39 项隔离夹具检查 |
| U2 | 纯 Java 回执解析器测试通过 | 通过 | 21 项解析检查 |
| U3 | 原生解锁隔离夹具验证单次尝试、进度/窗口变化及取消保护 | 通过 | 37 项夹具检查；不访问真实设备或真实凭据 |
| U4 | 原生流程回执解析器及实际布局相关反例检查 | 通过 | 29 项严格解析检查；与早期独立 OCR App 的 21 项分开记录 |
| U5 | 成功弹窗精确解析及非法日期、重复或附加文字等反例 | 通过 | 17 项纯 Java 检查；不替代真实截图 OCR 或结果页导航验收 |
| U6 | 已观察早退确认页严格解析及冲突内容反例 | 通过 | 28 项纯 Java 检查；与实际 OCR 和确认动作分别验收 |
| U7 | 严格墙充试次启动前 USB、充电、锁屏息屏检查 | 夹具通过 | 11 项纯 Java 检查；v27 实际启动检查及完整墙充流程另有本次证据 |
| B3 | 最终研究版本 release lint | 通过（含警告） | `0 errors, 17 warnings`；APK SHA-256 见 v21 / v22 产物行 |
| E1a | 解锁状态窗口结构可读且遍历完成 | 通过 | `rootAvailable=true`、`scannedNodes=11`、`scanComplete=true`；目标考勤回执内容仍待验证 |
| E1b | 安全 PIN 页结构扫描，只记录空密码框候选与数字键计数 | 通过 | `rootAvailable=true`、`scannedNodes=49`、`emptyFocusedPinEntries=1`、`digitCount=10`、`pinUiCandidate=true`、`locked=true` |
| E1s | 解锁和锁屏 PIN 页均可取得截图缓冲区，图像未落盘 | 通过 | 两种状态均为 `available`、`1080×2400`、`imagePersisted=false`；无障碍已连接 |
| E1c | 截图安全窗口/间隔限制返回值可识别，图像未落盘 | 待测 | — |
| E2a | 安装后在应用无网络权限条件下首次运行内置合成样例，读取实际 09:18 / 18:22 | 通过 | 实机显示 `2026-11-05` / `09:18` / `18:22`；正确区分计划 `09:00` / `18:18`。固定样例时间，不作为当天记录 |
| E2n | 手机整机主动断网后运行合成样例，与 E2a 对照 | 待测 | 本轮保持手机原有网络状态，未物理断网 |
| E2b | 原生新采集当前真实考勤页，使用 APK 内置中文 OCR 读取实际时间 | 单次通过 | v6 `read-only-verified`；日期 `2026-11-17`、签到 `09:25`、签退 `10:34`、动作 `下班打卡`；未调用 PC OCR |
| E2c | 布局不支持、旧日期、未来或冲突记录保持待核实 | 解析夹具通过，OCR 实机待测 | 解析器反例覆盖见 U2；真实图片仍需验证 |
| E2s | 当前成功弹窗由本机模型精确读取日期和实际签退 | 只读通过 | v20 读取同日 `13:10`；v24 新版完整上下文及独立标题识别读取同日 `14:41`，`success-modal-read-only-verified`；均不计锁屏完整流程 |
| E3a | 接电脑且锁屏，闹钟触发后完成同次窗口扫描与截图 | 链路通过 | 新版 `alarmId=alarmFiredForId`，`alarmScanId=windowScan.scanId=screenshot.scanId`；截图 `available` / `1080×2400` / 未落盘；触发前被点亮，不作为保持息屏证据 |
| E3t | 普通 `setExactAndAllowWhileIdle` 按请求的 60 秒时点触发 | 未通过 | 新版第 1 次关联复测迟到 `29851ms`；保持息屏对照的系统排程后移 `175889ms`，最终迟到 `175970ms`，原因待诊断 |
| E3w | 保持息屏到触发后再读取结果，验证唤醒请求及同次扫描/截图 | 通过（USB 连接） | `alarmInteractiveBeforeWake=false`、`alarmLocked=true`；闹钟/扫描编号匹配，窗口和截图均完成；准点另见 E3t |
| E3c | `setAlarmClock` 系统闹钟接口在锁屏息屏时触发，完成同次窗口扫描与截图 | 单次通过（USB 连接） | 系统有效目标与请求相等，实际晚 `34ms`；保持息屏到计划后约 80 秒才读取，扫描/截图编号一致且无错误 |
| E3b | 拔 USB、接墙上电源且锁屏息屏，单次系统闹钟准时触发并开始本机流程 | v25 单次通过 | 原生 USB `false`、电源 `1` / AC，锁屏息屏；晚 `31ms`，后续解锁和导航通过；全部发生于电脑断开期间，完整回执闭环另见 E5w |
| E4 | 手机本机保留 PIN 的一次解锁实验 | USB 连接及墙充分别有单次通过 | v2 USB 试次约 `7.46s`；v25 墙充试次 `verified-unlocked`，约 `7.74s`；PC 安装解锁不计入 |
| E5a | 锁屏息屏开始的单次原生定时、解锁、提交、结果导航及新回执完整闭环 | v21 单次通过（USB 连接） | 闹钟晚 `34ms`，全程 `31.190s`；主动作、早退确认和查看结果各一次；同日签到保持 `09:25`，签退从 `13:10` 更新至 `13:21`，`checkout-update-verified` |
| E5w | 拔 USB、接墙充时重复 E5a，报告证明无电脑参与 | v27 单次完整通过 | USB `false`、AC、锁屏息屏，晚 `19ms`、耗时 `32.722s`；原生解锁、导航、单次提交、结果导航及新回执 `09:25` / `15:37` 通过，全部发生于电脑断开采样覆盖期间；单次结果不代表每日、隔夜或其他设备通过 |
| E5b | 手机本机日常排程及依实际上班时间计算签退 | 未接入、未验收 | 单次研究流程未接生产日常排程或工作时长规则；E5a 或 E5w 的单次通过均不代表连续每日运行 |
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

前一轮纯探针 App 报告 `hasPinStorage=false`、`hasNetworkPermission=false`，当时没有保存或输入 PIN。后续原生流程研究版本已加入本机凭据保存、单次解锁和真实页 OCR，结果见下一节；前一轮的能力字段不代表后续版本。当时尚未验证的拔 USB 接墙充完整单次闭环已在后续 v27 通过；整机主动断网 OCR 对照、每日排程、隔夜运行和重启首次解锁仍分别待验收。

安装新版 APK 期间，既有 PC 解锁助手按此前授权完成一次 `verified-unlocked`；临时暂停的旧后台任务已恢复启用和运行。该安装操作依赖电脑，单列为 PC 辅助步骤，未计入 E4 或手机独立解锁验收。

## 后续原生单次流程试验

以下为同日后续研究版本的独立试次，保留前一轮探针结果，不追认跨版本能力。v1、v2 和 v6 为只读流程；v3 启用了下班测试但未发出点击，v7 消费一次下班动作后停止核实。USB 保持连接，原生流程不调用 PC 解锁、PC OCR 或考勤执行脚本，且不写生产考勤状态。

| 试次 | 定时与开始状态 | 实际进展 | 结果 |
| --- | --- | --- | --- |
| native-read v1 | 计划 `1794884513862`，触发 `1794884513908`，晚 `46ms`；锁屏、息屏、USB 连接 | `stage=unlock`，`unlockOutcome=pin-input-state-changed` | `native-unlock-failed`；进度异常后停止，未到导航/OCR |
| native-read v2 | 计划 `1794885437791`，触发 `1794885437843`，晚 `52ms`；锁屏、息屏、USB 连接 | `unlockOutcome=verified-unlocked`，`unlockedAt=1794885445303`，触发至解锁约 `7.46s`；随后 `navigationOutcome=attendance-entry-not-found` | `attendance-navigation-failed`；原生解锁单次通过，流程停在华为桌面 |
| native-checkout v3 | 计划 `1794885870825`，触发 `1794885870854`，晚 `29ms`；锁屏、息屏、USB 连接 | 原生解锁约 `7.6s`，`navigationOutcome=attendance-page-ready`；`before.accepted=false`、`reason=card-unconfirmed` | `receipt-unverified`；桌面图标回退及导航通过，未发出下班点击 |
| read-current v6 | 当前页只读，`checkoutEnabled=false`、`currentPageOnly=true`；原生帧时间 `1794887053932` | `accepted=true`，日期 `2026-11-17`、上班 `09:25`、下班 `10:34`、动作 `下班打卡` | `read-only-verified`；新截图及本机内置模型真实页识别单次通过 |
| native-checkout v7 | 计划 `1794887134677`，触发 `1794887134708`，晚 `31ms`；锁屏、息屏、USB 连接 | 原生解锁、导航和点击前 OCR 通过；`clickCount=1`、`clickOutcome=checkout-gesture-completed`；随后出现考勤会话过期提示 | `frame-context-changed`；动作已消费，无新签退回执，未重复点击或确认其他弹窗 |
| native-checkout v8 | 计划 `1794887365740`，触发 `1794887365772`，晚 `32ms`；锁屏、息屏、USB 连接；手动预先退出旧考勤页 | 原生解锁和新页面导航通过；`before.accepted=false`、`reason=morning-heading-unconfirmed` | 卡片横向滚动位置偏移，固定裁剪缺少晨标题；保护性停止，未发出下班点击 |
| native-checkout v9 | 计划 `1794887735824`，触发 `1794887735859`，晚 `35ms`；锁屏、息屏 | 原生解锁和导航通过；归位后仍 `morning-heading-unconfirmed` | `receipt-unverified`；未点击。稳定页人工同坐标右划后，本机只读 OCR 恢复通过 |
| checkout-current v10 | 当前页单次动作，`currentPageOnly=true`；点击前原生 OCR 通过 `09:25` / `10:34` | `clickCount=1`、`checkout-gesture-completed`，随后出现早退确认底部弹层 | `frame-context-changed`；未确认弹层、无新回执，不计完整通路 |
| native-checkout v11 | 计划 `1794888875750`，触发 `1794888875794`，晚 `44ms`；锁屏、息屏、USB 连接 | `verified-unlocked`，约 `7.25s`；桌面当前页无“钉钉”图标，`attendance-entry-not-found` | `attendance-navigation-failed`；未发出下班动作 |
| checkout-current v12 | 当前页单次动作，点击前识别同日 `09:25` / `10:34` | `clickCount=1`、`checkout-gesture-completed`；早退确认识别返回 `early-sheet-context-unconfirmed` | `checkout-confirmation-unconfirmed`；未继续确认、无新签退回执 |
| native-checkout v13 | 计划 `1794889666449`，触发 `1794889666486`，晚 `37ms`；锁屏、息屏、USB 连接 | `verified-unlocked`，约 `7.35s`；导航返回 `layout-acceptance-required` | `attendance-navigation-failed`；未发出下班动作 |
| checkout-current v14 | 当前页单次动作，`clickCount=1`、`checkout-gesture-completed` | 四区早退识别中照片与备注标签精确匹配，占位提示及按钮标签不匹配，`early-sheet-placeholder-label-unconfirmed` | `checkout-confirmation-unconfirmed`；未发出早退继续确认、无新签退回执 |
| native-checkout v15 | 计划 `1794890151133`，触发 `1794890151181`，晚 `48ms`；锁屏、息屏、USB 连接 | 原生解锁约 `8.35s`，导航和点击前 `09:25` / `10:34` 识别通过；主动作一次，照片及备注精确，早退按钮失配 | `checkout-confirmation-unconfirmed`；未提交早退确认，无新签退回执 |
| native-checkout v16 | 计划 `1794890569793`，触发 `1794890569831`，晚 `38ms`；锁屏、息屏、USB 连接 | 原生解锁约 `8.07s`，导航及点击前 OCR 通过；主动作一次，早退标签及按钮精确识别，早退确认一次且手势完成 | `checkout-return-page-unconfirmed`；返回页核验未通过，不计完整闭环 |
| read-current after v16 | 当前页只读，`checkoutEnabled=false`、`currentPageOnly=true` | `stage=read-receipt`，目标画面不满足原有考勤页上下文 | `frame-context-changed`；未确认新的实际下班时间 |
| native-checkout v17 | 计划 `1794891534637`，触发 `1794891534670`，晚 `33ms`；锁屏、息屏、USB 连接 | 原生 `verified-unlocked`，约 `8.18s`；`navigationOutcome=attendance-reentry-not-found` | `attendance-navigation-failed`；无主动作记录，未到新增成功弹窗核验阶段 |
| native-checkout v18 | 计划 `1794891910486`，触发 `1794891910519`，晚 `33ms`；锁屏、息屏、USB 连接 | 原生解锁和 `attendance-page-ready`，读取前帧上下文变化 | `frame-context-changed`；未提交主动作 |
| native-checkout v19 | 计划 `1794892226333`，触发 `1794892226374`，晚 `41ms`；锁屏、息屏、USB 连接 | 本机 OCR 读取 `09:25` / `12:43`，主动作与早退确认各一次；主执行流程另观察到官方 `13:10` 成功弹窗 | `success-modal-context-changed`；无自动新回执，后续 v20 单独验证只读解析 |
| success-current v20 | `currentPageOnly=true`、`successReadOnly=true`、`checkoutEnabled=false` | 成功标题、唯一合法时间、结果链接三项精确识别，日期 `2026-11-17`、签退 `13:10` | `success-modal-read-only-verified`；未提交动作，不计锁屏完整流程 |
| native-checkout v21 | 计划 `1794892887304`，触发 `1794892887338`，晚 `34ms`；锁屏、息屏、USB 连接 | 原生 PIN、导航、点击前 OCR、主动作一次、早退确认一次、成功弹窗、结果链接一次、有界返回、重新进入和新 OCR 均完成 | `checkout-update-verified`；签到保持 `09:25`，旧签退 `13:10`、新签退 `13:21`，全程 `31.190s`；首次完整连电脑原生闭环通过 |
| wall-checkout v22 | 计划 `1794893145802`，触发 `1794893145832`，晚 `30ms`；锁屏、息屏；报告 USB 仍为 `true`、电源为 `2` | 主动作、早退确认和查看结果各一次，自动回读同日、同一签到 `09:25`，签退 `13:21→13:26`，全程 `31.515s` | `checkout-update-verified`；第二次连电脑完整流程通过，拔 USB 墙充条件未达成，E5w 保持未验收 |
| wall-checkout v23 | 计划 `1794897639711`，触发 `1794897639756`，晚 `45ms`；锁屏、息屏；USB `true`、电源 `2` | 点击前同日签到 `09:25`、签退 `13:26`；主动作、早退确认各一次；主执行流程另观察官方成功弹窗 `14:41` | `success-modal-context-changed`，全程 `28.825s`，无自动新回执；后续只读诊断标题失配；拔线条件未达成 |
| success-current v24 | 新版当前页只读，`checkoutEnabled=false`、`successReadOnly=true` | 独立标题一行精确匹配、完整上下文标题带一行、三个位置带通过，日期 `2026-11-17`、签退 `14:41` | `success-modal-read-only-verified`；未提交新动作，不计拔线完整流程 |
| wall-checkout v24 | `15:03:35.893` 开始等断开，电脑从 `15:04:19.776` 至 `15:11:09.613` 连续采样断开；`15:11:14.676` 首次采样重连 | 原生处理时间 `usbDisconnectedAt=1794899470109`，随后建立计划 `1794899650093`（`15:14:10.093`）；取回时只有 `scheduled`，无 `firedAt` | 触发前已取消，`status=cancelled`；没有本次 PIN、主动作或新回执，拔线完整试验未通过 |
| wall-checkout v25 | 手机手动安排 `15:20:57.587`，触发 `15:20:57.618`，晚 `31ms`；USB `false`、电源 `1` / AC、锁屏息屏，结束于电脑重连前 | 原生解锁 `verified-unlocked`、导航 `attendance-page-ready`；点击前 OCR `accepted=false`、`morning-heading-unconfirmed` | `receipt-unverified`，全程 `15.984s`；没有主动作、确认或新回执；墙充唤醒/解锁/导航单次通过，完整流程未通过 |
| v25 after-header read | 电脑重连后主执行流程单独执行已观察标题行归位手势，再启动当前页只读检查 | `checkoutEnabled=false`、`currentPageOnly=true`，同日签到 `09:25`、签退 `14:41` | `read-only-verified`；这是电脑辅助诊断，不计为原生 v25 新签退或完整流程通过 |
| native-read v26 | 计划 `1794900676443`，触发 `1794900676466`，晚 `23ms`；锁屏、息屏、USB 连接，`checkoutEnabled=false` | 原生解锁、导航、新截图及本机 OCR 通过，读取同日签到 `09:25`、签退 `14:41` | `read-only-verified`，全程 `20.075s`；未提交新动作，不计墙充完整流程 |
| wall-checkout v27 | 计划 `1794901010726`（`15:36:50.726`），触发 `1794901010745`（`15:36:50.745`），晚 `19ms`；USB `false`、AC、锁屏息屏 | 原生解锁、导航、点击前 `09:25` / `14:41`，主动作/早退确认/查看结果各一次，成功弹窗、结果返回、新进入及 `after` 同日 `09:25` / `15:37` 全通过 | `checkout-update-verified`，完成 `15:37:23.467`、耗时 `32.722s`；一次真实拔线墙充完整通过，早于电脑首次采样重连 `14.068s` |

证据均位于工作区 `outputs/AutoDing-capability-research/2026-11-17/`：v1 为 `native-read-result.json`，v2 为 `native-read-v2-result.json`，v3 为 `native-checkout-v3-result.json`，v6 为 `read-current-v6-result.json`，v7 为 `native-checkout-v7-result.json`，v8 为 `native-checkout-v8-result.json`。局部链路通过不计为完整无人值守考勤通过。

v2 使用 `getLaunchIntentForPackage` 及无障碍服务的 `startActivity` 后，界面仍留在华为桌面。本轮尚未捕获明确的系统拒绝日志，具体原因待诊断。导航模块新增有限回退：在已知桌面包中寻找唯一可见、文字精确为“钉钉”的图标，只操作该图标本身；节点点击未接受时，使用同一节点边界的单次手势，不点击全屏父节点。该回退在 v3 中已到达考勤页。

v3 的右卡裁剪扩至参考横坐标 `1040` 后混入早退/更新文案，严格解析器保持待核实。后续将右卡范围收窄为参考横坐标 `506–840`，并仅在已核对区域处理蓝色勾选标记、处理明确的文本拆行；v6 的结构诊断显示上下班各一条班次标签和一条实际记录，未出现未知行，日期区一个动作及一个日期，最终识别通过。原始完整页面及 OCR 文字未写入公开结果，本地排查仅使用三块既定裁剪区域。

v6 实测 APK SHA-256 为 `530aec55fecb834ba51f503366b833b618db6c90cf85317b80c685ccd3033c81`。v7 点击前识别同日签到 `09:25`、原签退 `10:34`，在 `1794887145910` 消费一次下班动作，手势完成；之后钉钉原生提示精确为“页面停留时间过长，请退出考勤重新进入”。Runner 的页面上下文检查停止，未核实新记录，也未继续点击提示或重复下班动作。

这次失败说明新采集截图不代表 WebView 考勤会话仍有效。导航已补充会话刷新：若接手一个已有考勤页面，最多返回一次，再通过已观察的唯一“打卡”快捷入口重新进入；从本次快捷入口刚打开的页面直接继续。仅精确匹配上述过期原生提示时允许点击其 `android:id/button1`“确定”一次，其他确认对话框不处理。该会话刷新修复尚待后续实测，本页未将手势完成标为考勤成功。

v8 在人工预先退出旧考勤页后，用已安装的 v6 识别版本重新导航；真实卡片裁剪显示横向滚动偏移：晨标题与时间在左边界外，晚标题从参考屏幕约 `x425` 开始，原晚卡 `x506` 的裁剪也截掉标题。该结果属于页面布局与裁剪不匹配，未判定为 OCR 引擎故障，且未点击下班。

当时的候选导航为 `JEF-AN00` / API 31 参考比例补充一次标题行右划归位：参考坐标 `(250,625)→(850,625)`，`450ms`；完成后等待 `600ms`，重新核对解锁与三项考勤页签后才继续。每次导航最多一次，不触及约 `y700` 的更新区域；其他机型返回 `layout-acceptance-required`。拒绝、取消或超时均停止；后续 v9 继续诊断时机，最终 v21 / v22 完整流程的导航和回执通过单独记于后文。

v9 实机仍显示标题裁剪失配；在稳定页面人工执行相同坐标右划后，原生只读识别恢复为 `2026-11-17`、`09:25` / `10:34`。这支持手势坐标有效，页面加载或自动滚动尚未结束是时机问题的候选解释，尚未据此确定内部根因。当时导航改为首次确认考勤页后仅安排一次 `2500ms` 等待，再核对窗口、锁屏及页签后归位；总导航上限 `24s`，归位后仍等待 `600ms`。后续 v26 的归位参数另列记录。本地证据为 `native-checkout-v9-result.json`、`header-normalize-v9-manual-read.json`。

v10 在稳定当前页读取签到 `09:25`、原签退 `10:34`，`1794887969071` 消费一次下班动作；随后出现“早退打卡”底部弹层，当前版本因页面上下文变化停止，未确认。证据为 `checkout-current-v10-result.json` 和本地裁剪图 `checkout-v10-bottom-sheet.png`。后续仅为当前已启用的一次下班测试加入精确弹层识别及一次“早退打卡”继续动作：独立核对新鲜上下文、明确标签及边界，限定当前华为配置与唯一已观察 WebView，拒绝其他确认或原因填写；该新增确认路径待后续实测。

v11 证据 `native-checkout-v11-result.json` 显示闹钟与原生解锁成功，停在桌面导航。后续对桌面取证确认当前参考配置共两屏：一次 HOME 后第一页无图标；一次已观察的左划后第二屏出现唯一“钉钉”图标。工作区描述中的当前页号可能缓存，搜索不依赖其页号，只核对已观察的两页配置。导航已加最多一次 HOME、一次翻页及每次 `800ms` 后重新读取图标的有限搜索，固定坐标仅用于已观察的桌面翻页，不用于按位置猜测启动 App。其他配置停止；候选修复待后续原生全流程复测。取证为 `launcher-two-page-profile.json`。

v12 证据 `checkout-current-v12-result.json` 仅证明主下班动作已完成一次；旧版早退弹层识别得到一个“早退打卡”候选，但备注及空原因上下文不完整、顺序不符，因此返回 `early-sheet-context-unconfirmed`，未继续确认，也未生成新的回执通过结果。后续识别已转为四块明确区域并补充画面内容版本校验，仍待下一完整试次，不把旧版识别停止追认为新版本通过。

v13 的 `native-checkout-v13-result.json` 显示原生解锁后在桌面导航布局核对处停止。实机结构比较另观察到：相同早退弹层，原生无障碍扫描仅见 `8` 个节点，UiAutomation 辅助读取可见 `35` 个节点；原生服务当时未启用 `flagIncludeNotImportantViews`。该节点可见性差异支持容器暴露范围为导航/弹层结构核对失败的候选原因，尚未用最终复测确定因果。后续已加入该标准 flag，同时保留限定参考机型、桌面包、全屏根边界下不依赖工作区容器的一次翻页回退；不扩大至未知机型。

v14 证据 `checkout-current-v14-result.json` 的四区诊断显示：照片、备注标签各一条且精确匹配；占位提示及按钮各一条，但都不匹配预期精确标签。识别在占位提示处返回固定失败码，`earlyClickCount` 和 `earlyClickOutcome` 未记录，表示未提交早退确认。后续扩大对应裁剪及节点可见性修复尚待 v15 等独立试次验收，本页未提前记录新版本通过。

v15 证据 `native-checkout-v15-result.json` 验证了锁屏息屏下的定时、原生解锁、桌面导航、考勤会话刷新、标题归位及实际回执识别；点击前日期为 `2026-11-17`，实际上班 `09:25`、原下班 `10:34`。`clickCount=1`、`clickOutcome=checkout-gesture-completed`；早退按钮只得到一行五字符的未知类别，精确匹配数为零，返回 `early-sheet-button-label-unconfirmed`，未提交早退确认。此结果没有补写或猜测按钮文案。

v16 证据 `native-checkout-v16-result.json` 使用完整底部上下文识别按钮、仅接受落在已观察按钮带的实际 OCR 行；照片、备注及“早退打卡”均精确匹配，原因提示仅作辅助诊断。主动作和早退确认均记录次数 `1`，`earlyConfirmationOutcome=early-checkout-gesture-completed`；随后 `checkout-return-page-unconfirmed`，无 `after` 新回执，因此该 JSON 本身只证明确认动作及手势完成。`read-current-after-v16-manual-result.json` 的只读复核仍返回 `frame-context-changed`。

主执行流程在 v16 后另行观察到官方“下班打卡成功”弹窗，显示日期 `2026-11-17`、打卡时间 `12:43`，并人工点击已观察的“查看打卡结果”。官方统计中签到为 `09:25`，旧签退 `10:34` 标为已更新，新签退为 `12:43`。此人工核对记录保存在本地 `PROGRESS.md` 的“v16 submission and v17 final verification candidate”段；通用成功弹窗底部裁剪为 `checkout-v16-after-confirm-bottom.png`。私人统计页仅在内存查看，未持久化。这支持本次提交确已生效，但人工查看不计为程序自动核验闭环，也不替代拔 USB 接墙充验收。

v16 后新增成功弹窗专用解析：要求唯一“下班打卡成功”、合法且唯一的 `打卡时间:YYYY-MM-DD HH:mm`、唯一“查看打卡结果”，并由实际 OCR 框核对已观察的位置带。上层还需核对当天日期、不同于原下班时间、时间不早于本次提交所在分钟及新鲜上下文，才消费一次查看结果动作。导航只在精确的原生“统计”标题出现后尝试有界返回，随后重新打开考勤页并核对同一天和同一实际上班记录；弹窗解析通过或导航完成均不单独标记最终成功。该路径在后续 v21 / v22 完整试次通过，不追认 v16 为自动闭环通过。

v17 使用候选 APK `93188c9ae110d22f46e366c30eebad91da8704b1243752e38c7738db9852e83b`，证据为 `native-checkout-v17-result.json`。闹钟与原生解锁通过，重新进入考勤页时返回 `attendance-reentry-not-found`，未记录主点击、早退确认或成功弹窗核验。该试次没有验证新增结果导航路径，具体页面状态与后续修复需另行记录。

v17 后主执行流程确认目标页面是只读工作通知页，不是包含快捷入口的钉钉首页。候选导航针对已观察通知布局、且无编辑框、密码框或对话框时允许一次有限返回。v18 的 `native-checkout-v18-result.json` 已到达 `attendance-page-ready`，随后在主动作前因 `frame-context-changed` 停止。后续加入最多两次重新截图读取，每次重新核对新帧；这些只读重采集不重发主下班动作。

v19 的 `native-checkout-v19-result.json` 记录主动作及早退确认各一次，随后在成功弹窗读取处因 `success-modal-context-changed` 停止。主执行流程另以只读画面观察到 `13:10` 成功弹窗。全局动态文字使内容版本失效是候选原因，后续仅在只读成功弹窗及结果链接导航中移除全局文字内容版本比较，保留同一窗口、布局、当天日期及新帧时效检查。主下班动作以新截图、窗口、布局、当天日期及实际上班记录校验，早退确认另绑定 `contentVersion`；两者不混称为同一保护条件。v20 的 `success-current-v20-result.json` 用本机模型成功读取同日 `13:10` 弹窗，三个精确标签及位置带通过；此当前页只读诊断不包含闹钟、锁屏或实际提交。

v21 的 `native-checkout-v21-result.json` 是首次完整连电脑原生闭环通过。原生报告在触发时记录 `lockedAtStart=true`、`interactiveAtStart=false`、`usbConnectedAtStart=true`、`powerSourceAtStart=2`。闹钟从计划 `1794892887304` 到触发 `1794892887338` 晚 `34ms`；从触发到 `finishedAt=1794892918528` 为 `31.190s`。点击前 OCR 识别日期 `2026-11-17`、签到 `09:25`、原签退 `13:10`；主动作、早退确认、查看结果均各消费一次，`viewResultOutcome=checkout-result-returned`。重新进入考勤页返回 `attendance-page-ready`，新帧识别同日、同一签到 `09:25` 及新签退 `13:21`，与成功弹窗 `13:21` 一致，最终 `checkout-update-verified`。该试次 APK SHA-256 为 `bb61a88edc821d3fb0da1c9e65a0b5cecf813658d2cedb1fe228d80170c79924`，主执行流程已核对手机安装哈希。

v22 证据为 `wall-checkout-v22-scheduled.json`、`wall-checkout-v22-result.json`。原计划验证拔 USB 接墙充，实际报告记录 `usbConnectedAtStart=true`、`powerSourceAtStart=2`，表明触发时仍有 USB 连接，因此本次没有达成脱离电脑条件。锁屏、息屏均成立，180 秒等待后的闹钟晚 `30ms`，完整原生流程耗时 `31.515s`；主动作、早退确认、查看结果各一次，自动回读同日、同一签到 `09:25` 及新签退 `13:26`，此前签退为 `13:21`，结果 `checkout-update-verified`。这是同一 APK 的第二次连电脑完整闭环通过，结束该轮时尚未安排下一次物理拔线试次；后续 v23 起另列记录。日常排程、动态工作时长、隔夜及重启后首次解锁仍独立待验收。

### 后续 v23：成功弹窗自动核验未通过，USB 仍连接

v23 使用手机上已实测的 `bb61a88edc821d3fb0da1c9e65a0b5cecf813658d2cedb1fe228d80170c79924` 安装包，未安装本地 `48c6...` 重建包。证据在本地 `wall-v23/`：`scheduled.json`、`result.json`、`connection-samples.jsonl`、`success-readonly-result.json`、`tasks-restored.json`。原生试次 ID 为 `481528a8-8396-4908-8dbe-4a46f1c6a224`，从触发 `1794897639756` 到完成 `1794897668581` 为 `28.825s`。原生 PIN 解锁和导航通过，点击前 OCR 识别同日、签到 `09:25`、原签退 `13:26`；主动作和早退确认各消费一次。

本次原生结果为 `success-modal-context-changed`，没有自动 `after` 新回执。主执行流程另目视核对到官方成功弹窗中的 `14:41`，该观察与程序自动核验分开记录；后续当前弹窗只读诊断返回 `success-modal-heading-unconfirmed` / `success-modal-unverified`，成功标题精确命中为零，日期和签退结构字段保持空，未用合法时间行或结果链接单独推断成功。程序停止是核验失败，不据此认定先前提交未生效。

原生报告的 `lockedAtStart=true`、`interactiveAtStart=false` 成立，但 `usbConnectedAtStart=true`、`powerSourceAtStart=2`。电脑连接采样从 `14:39:10.854` 至 `14:43:44.465` 共 `55` 条，全部 `connected=true`，没有观察到断开。因此 v23 未达拔线墙充条件，也未通过自动完整闭环。结束该次后研究任务已取消，`wall-v23/tasks-restored.json` 显示原有三项 PC 任务均已恢复 Enabled / Running，当时没有待执行研究试次；后续任务暂停和新试次另列记录。入口保护或识别修复按后续代码及独立试次验收，不追认此版本已有该能力。

### 后续 v24：真实断开已观察，断开处理延迟至重连附近

v24 新安装包先在当前官方成功弹窗执行只读检查，`wall-v24/success-readonly-result.json` 返回 `success-modal-read-only-verified`，日期 `2026-11-17`、签退 `14:41`；`headingLineCount=1`、`independentHeadingExact=true`、`contextHeadingLineCount=1`、`observedBandMatches=3`。此检查没有产生新签退，也没有验证锁屏或脱离电脑执行。

随后墙充入口按 `wall-v24/armed.json` 在 `15:03:35.893`（`1794899015893`）进入 `waiting-usb-disconnect`，等待 ID 为 `c43b284e-f636-40ff-852b-d63761f0bddb`，预定等待截止 `15:11:35.893`。`wall-v24/connection-samples.jsonl` 记录 `82` 条断开采样：首条 `15:04:19.776`，末条 `15:11:09.613`；下一条 `15:11:14.676` 为已连接。该观测支持本次确有断开期间，不再归类为 v22 / v23 那种全程连接试次。

但 `wall-v24/result.json` 取回时只记录新试次 `c2079ef3-656b-460d-bfce-4b17322a4913` 为 `scheduled`：关联原 `armedId`，`usbDisconnectedAt=1794899470109`（`15:11:10.109`），`scheduledAt=1794899650093`（`15:14:10.093`），没有 `firedAt`、PIN 解锁、考勤点击或新回执。`usbDisconnectedAt` 是 App 处理该事件时写入的墙钟，非已保存的系统原始断开事件发生时间；它落在电脑末次断开采样与首次重连采样之间，与首次观测到实际断开相隔约 `6分50秒`。证据说明等待断开逻辑在实际断开期间没有及时建立并触发预期流程，处理延迟至重连附近；没有事件入队及调度日志，据此尚未确定系统冻结、广播投递或进程调度的具体内部原因。

主执行流程在该新计划触发前取消，`wall-v24/cancelled.json` 为同一试次 `status=cancelled`，没有新签到或签退。`wall-v24/tasks-restored.json` 显示三项原 PC 任务均已恢复 Enabled / Running。本次实际断开条件虽已观察，完整墙充流程仍未通过；后续入口变更及修复需另有构建和实测，不在本试次记录中提前宣称成功。

### 后续 v25：墙充唤醒、解锁和导航通过，动作前 OCR 停止

v25 的本地证据为 `wall-v25/result.json`、`connection-samples.jsonl`、`user-scheduled-confirmation.json` 和后续只读诊断。用户实际拔电脑线并接充电头后，在手机界面确认安排。试次 ID 为 `9d154896-ff74-4bde-b735-8c5d7d192f3b`，`requiresDetachedCharger=true`；计划 `1794900057587`（`15:20:57.587`），触发 `1794900057618`（`15:20:57.618`），完成 `1794900073602`（`15:21:13.602`），晚 `31ms`，耗时 `15.984s`。

原生报告记录 `usbConnectedAtStart=false`、`powerSourceAtStart=1` / AC、`lockedAtStart=true`、`interactiveAtStart=false`，严格墙充启动检查通过。电脑采样的首条断开为 `15:17:17.472`，末条断开为 `15:25:43.207`，首次随后重连为 `15:25:48.254`；原生触发和结束均在这一断开区间内。原生解锁为 `verified-unlocked`，触发至 `unlockFinishedAt=1794900065361` 约 `7.74s`，导航为 `attendance-page-ready`。这些证据支持本机本次拔线接墙充时的定时、保留 PIN 解锁和页面导航通过。

随后主动作前的回执 OCR 为 `accepted=false`、`reason=morning-heading-unconfirmed`，日期与时间候选保持空，最终 `receipt-unverified`。报告没有打卡点击、早退确认或 `after` 回执，本次没有产生新的打卡记录，完整墙充闭环未通过。主执行流程在电脑重连后的裁剪观察到卡片横向位置被截断，再单独经 ADB 执行标题行归位手势 `(150,625)→(950,625)`、`800ms`；`wall-v25/after-header-read-result.json` 的当前页只读检查随后通过，读取同日签到 `09:25`、签退 `14:41`。该诊断支持横向位置影响裁剪的判断，不把电脑手势后的读取算入 v25 原生流程。延长原生归位手势和等待的后续候选改动仍需新构建与独立复测。

归档 v25 时，三项 PC 任务仍按 v25 原始快照临时停用，停止标记保留以继续 v26 / v27 试验；最终恢复见后文 v27 收尾，此处不与此前 v24 的恢复记录混用。日常排程、动态工作时长、隔夜及重启首次解锁仍未验收。

### 后续 v26：延长归位手势后的连电脑原生只读通过

`native-read-v26/result.json` 记录试次 `5b263d69-3708-4ba6-ad1b-118ae2b003c3`，`checkoutEnabled=false`、`requiresDetachedCharger=false`；原生报告为锁屏、息屏、USB 连接。计划 `1794900676443`，触发 `1794900676466`，晚 `23ms`，完成 `1794900696541`，总耗时 `20.075s`。原生 PIN 解锁与导航通过，新的考勤页 OCR 接受同日签到 `09:25`、签退 `14:41`，最终 `read-only-verified`。没有主动作或新签退，亦不计墙充完整流程通过。

该版使用 v25 后已观察的较长单次归位手势 `(150,625)→(950,625)`、`800ms`，等待 `1000ms` 后继续；修改只涉及导航手势和稳定等待，没有放宽回执解析标签、日期、时间或冲突判据。旧版 `(250,625)→(850,625)`、`450ms` 加 `600ms` 等待的历史记录保留在对应试次中。

后续 v27 使用同一已安装 `4b4d...` 版本安排手动墙充试验，`wall-v27/user-scheduled-confirmation.json` 记录收到手机“已安排”确认于 `15:34:44.853`，当时建议保守取回时间不早于 `15:40:44.853`。实际结果及较早重连限制见下一节。三项 PC 任务从 v25 原始快照临时停用，直至 v27 结果取回后再恢复；最终收尾与较早轮次分别记录。

### v27：一次真实拔线墙充完整流程通过

本地 `wall-v27/result.json` 为在任何后续诊断之前保存的原始报告，试次 ID `03e6f598-e358-4104-a5a9-bf3b5c2b27a8`。手机安装包 `4b4d16dface0e88bb522fde1be00ca0b690b07fde9551039efe153278646c6e5` 已与本地哈希核对一致。计划 `15:36:50.726`（`1794901010726`），触发 `15:36:50.745`（`1794901010745`），晚 `19ms`；完成 `15:37:23.467`（`1794901043467`），耗时 `32.722s`。

原生报告为 `requiresDetachedCharger=true`、`usbConnectedAtStart=false`、`powerSourceAtStart=1` / AC、`lockedAtStart=true`、`interactiveAtStart=false`；解锁 `verified-unlocked`，导航 `attendance-page-ready`。点击前新截图接受日期 `2026-11-17`、签到 `09:25`、原签退 `14:41`。主下班动作、早退确认和查看结果各消费一次，成功弹窗识别 `15:37`；`viewResultOutcome=checkout-result-returned`、`verificationNavigationOutcome=attendance-page-ready`。新打开考勤页的 `after.accepted=true`，同一日期、签到保持 `09:25`、新签退为 `15:37`，与成功弹窗一致，最终 `checkout-update-verified`。本次提交、结果导航及新回执完整闭环通过。

电脑独立证据为 `wall-v27/pc-isolation.json` 和 `connection-samples.jsonl`：三项 PC 配套任务在试验期间均停用，工作进程已停止；连接采样的首条断开为 `15:34:45.506`，末条断开为 `15:37:32.493`，随后首次连接采样为 `15:37:37.535`。原生触发和结束均在断开采样覆盖期间，结束后 `9.026s` 仍有断开采样，早于首次连接采样 `14.068s`；后者按原始含毫秒采样重算。结合原生 USB 断开、AC、锁屏息屏及本机回执证据，验收本机这一次真实拔线墙充完整流程。

本次取回早于建议的 6 分钟保守等待；监测也在实际拔线后开始，所以没有精确物理拔线时刻，且 5 秒左右间隔的采样不等于持续连接记录。报告不宣称本次完整脱离电脑 6 分钟。上述限制不改变原生流程已在重连前完成的证据，只将结论限定为本机、此安装版本、此单次流程。手机本机每日排程、实际签到派生签退、隔夜、重启首次解锁、网络故障以及其他设备仍未验收。

### v27 最终收尾及生产计划

主执行流程在 `15:39:39` 前完成收尾：按 v25 原始快照恢复三项 PC 任务，`wall-v27/tasks-restored.json` 均为 Enabled / Running；只移除本轮创建的停止标记。研究任务已取消，`after-cancel.json` 保留该次已完成的成功报告，没有新研究闹钟待触发。

`wall-v27/production-state-restored.json` 显示生产仍为当日 `09:25` 签到已确认、分钟精度、来源 `attendance-screen`，`checkOut=0`，最早正常签退 `18:26:00`，计划 `18:26:49`，晚班 `scheduled`，无 pending。研究 `15:37` 签退未满足生产 9 小时保护，不计作今日生产正常下班完成；研究 App 未写生产状态，现有正式日常运行继续依赖 PC 配套，晚间实际结果待后续回执。模拟位置 app-op 和已撤销的位置权限保持既有状态。本节为当前收尾，下面 v21 / v22 的恢复表仅作历史。

## v21 / v22 轮次结束时恢复记录

结束 v21 / v22 轮次时，主执行流程恢复既有 PC 配套任务并移除该轮创建的停止标记，取消研究单次任务且保留已完成的成功报告。该轮系统闹钟快照中的 `18:26:49` 属于生产计划；后续新试次及任务暂停/恢复状态按相应小节记录。

| 项目 | 收尾结果与证据 |
| --- | --- |
| PC 任务 | 三项均已恢复启用；`pc-tasks-restored.json` 中 Observe / Unlock 为 Running，Attendance 为 Ready，均非 Disabled；该快照后运行状态可随任务调度变化 |
| 研究定时 | 已执行 `trial-cancel`；没有待执行研究单次流程，v22 成功报告保留 |
| 模拟位置设置 | app-op 保持 `default`，研究位置权限保持撤销；没有修改标准位置源 |
| 生产当天计划 | `production-state-restored.json`：签到 `09:25`、来源 `attendance-screen`、分钟精度，早班 `confirmed`，下班仍为 `0`，最早 `18:26:00`，计划 `18:26:49`，晚班 `scheduled` |

生产计划恢复是本轮研究结束后的单独 PC 配套操作：原生产状态没有保存签到，主执行流程把两次原生核对一致的官方 `09:25` 通过现有生产 Bridge 导入。研究 App 仍未写生产状态，也未因此获得本机日常排程能力。中午研究签退未满足生产最短工作时长，不计作生产今日正常下班完成；实际晚间回执仍需按生产流程核实。

原生解锁的 37 项 Java 夹具验证使用测试替身和合成凭据；它们支持结构、进度和取消保护的实现判断，不替代华为或其他机型的实测。原生下班/更新的成功条件仍要求新采集页面、同一日期与实际上班记录、点击后的实际下班时间变化及官方记录核对。

## 前轮探针结束时恢复检查

下表对应前一轮纯能力探针的收尾状态。后续原生试验会临时停用 PC 辅助任务，最终恢复状态应在该轮结束时重新记录。

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

## 交互首页更新（2026-11-18 下午）

新增首页定时开关、下次执行、时间规则编辑、实际签到及推算下班、单次只读测试倒计时/取消、权限状态和最近结果。旧实验按钮移入诊断页。实机已验证时间选择后取消、关闭确认后保留开启、单次只读倒计时与取消；打开首页及取消编辑均保留原计划 ID、当天实际签到和未来两个闹钟。

设置保存保留回执、已消费任务与历史报告；独立诊断统一检查与日期任务的执行窗口冲突，取消独立测试不终止日期任务。排程生命周期夹具 47 项通过。手机与本地 APK SHA-256 一致：`32acb68b4a6434cf42ac951fe181320bf1bc2a1a22df36232bb6cc2f876c824b`。最终构建、签名、模型和无网络权限检查通过。新增主页仅对变化的内容更新文本，避免持续发出无障碍内容变化事件。

此前纯电池测试超时的原始报告已保留。研究 App 的华为三项后台权限已开启；此次新版纯电池对照待完成，首页交互通过不代表独立运行及隔夜验收通过。当前手机定时仍为 11 月 18 日 18:47、11 月 19 日 07:00，电脑三项旧任务及原生产手机定时继续暂停。
