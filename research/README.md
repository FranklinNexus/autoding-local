# AutoDing 手机能力研究

本目录把“手机留在办公室、接电源、脱离电脑运行”和“Android 位置模拟能力”拆成独立实验。实验包与现有 AutoDing 使用不同包名。原生研究包已集成一次定时、PIN 解锁、导航、新截图、本机 OCR、下班操作及自动回执核验；v21、v22 两次完整连电脑原生单次流程通过。v22 触发时 USB 仍连接，脱离电脑条件尚未验收；日常排程和长期稳定性也分别验收，研究版本尚未代替现有生产日常流程。

截至 2026-11-17，实机结论以 [RESULTS.md](RESULTS.md) 的证据记录为准。编译成功、合成样例通过、定时器触发和官方考勤成功是四种不同结果。

本轮华为实机已验证：解锁状态和锁屏 PIN 页的窗口树可读，两种状态均能取得 `1080×2400` 截图缓冲区；专用合成位置源可以回读并被标记为 mock，测试源已清理、研究位置权限已撤销。

USB 连接下，`setAlarmClock` 单次对照在保持锁屏息屏时触发，比计划晚 `34ms`，同次窗口扫描与截图完成且编号匹配。普通 `setExactAndAllowWhileIdle` 在另一息屏试次中被系统后移，最终迟到 `2分55.970秒`。这些是本机单次能力测试，长期定时稳定性及其他机型仍待验收。

OCR 安装后首次推理在应用没有网络权限的条件下通过，正确读取合成样例的 `09:18` / `18:22` 实际时间。v21 从锁屏息屏开始，原生定时、PIN 解锁、导航、截图 OCR、单次主动作和早退确认、成功弹窗识别、查看结果、有界返回及新页面回读均完成：签到保持 `09:25`，签退从 `13:10` 更新到 `13:21`，全程 `31.190s`。v22 延迟 180 秒后重复闭环通过，签退更新至 `13:26`，全程 `31.515s`，但两次报告 USB 均为连接状态；v22 未达拔线墙充条件，当前没有新研究试次待触发。隔夜、重启及动态下班规则仍单列验收，具体证据见 [RESULTS.md](RESULTS.md)。

## 当前产物

| 产物 | 包名 / 入口 | 本阶段输出 |
| --- | --- | --- |
| 原生独立运行研究包 | `org.autoding.research` / AutoDing 能力研究 | 原有能力报告，以及 60 秒单次定时、原生 PIN 解锁、导航、本机 OCR 和一次下班回执核验 |
| 位置能力探针 | 同上，专用 `autoding_research` provider | 模拟应用选择状态、合成位置注入与回读、mock 标记、清理结果 |
| 离线中文 OCR | `org.autoding.ocrprobe` / AutoDing OCR Research | 用户选图或内置样例的日期与实际上下班时间候选 |

两个 App 的最低系统版本均为 Android API 31，当前 target API 为 33。华为、荣耀、小米、OPPO、vivo、三星等机型的后台管理和锁屏控件需要分别验收；品牌相同也应记录具体型号、系统构建及补丁版本。

当前带手势的原生考勤导航和早退确认限定已观察的华为 `JEF-AN00` / API 31 及 `1080×2400` 参考比例；其他配置返回 `layout-acceptance-required`。API 31 最低安装版本不代表所有机型均已通过原生操作验收。

原生研究包会按本次选择执行只读试用或一次下班实测。PIN 通过手机本地窗口设置，以 AndroidKeyStore AES-GCM 加密后保存在应用私有 credential-encrypted 存储；报告仅显示是否已保存。独立 OCR App 仍保留手工选图和合成样例入口。两个 App 均未申请网络权限，图片和原始 OCR 文字不持久化。

## 路径一：手机留在办公室独立运行

需要逐段验证：精确定时触发 → 唤醒与服务存活 → 保留 PIN 时解锁 → 打开目标页面 → 获取新画面 → 本机识别回执 → 核对官方考勤记录 → 按实际上班时间计算下班时间。

`OneShotRunner` 已集成定时唤醒、原生 PIN 输入、目标页面导航、新截图与内置中文 OCR；下班实测在上下文核对通过后最多提交一次下班点击。若本次已启用的下班测试随后严格识别到“早退打卡”弹层，仅允许继续确认该动作一次，再读取新回执。成功弹窗及唯一结果链接精确识别后，有界返回并重新打开考勤页，核对同日、同一实际上班及新的实际下班时间。该完整链路在 v21 的 USB 连接试次通过，拔 USB 接墙充试次单独验收。研究包不写生产 AutoDing 状态，未接入日常排程或动态工作时长规则。手机重启后首次凭据解锁与日常息屏解锁分别验收；当前 PIN 密文使用 credential-encrypted 存储，重启后首次解锁需人工完成。

### 一次独立运行试用

1. 安装新版研究包，启用其无障碍服务，授予精确闹钟权限；按机型设置自启动及后台活动。
2. 在手机“设置本机加密 PIN”窗口填写并保存 PIN，确认报告 `hasPinStorage=true`。PIN 只在本机设置，不放入命令行或日志。
3. 先选择“60 秒后独立读取考勤（不点击）”，锁屏后等待。流程会尝试一次本机解锁、打开考勤页并识别当日实际时间，报告 `trial` 中记录阶段、锁屏/USB/电源状态及结果。
4. 如当次试用需实际操作下班卡，再使用界面的一次下班实测入口。此入口会尝试产生或更新当天的下班记录，也可能产生早退记录；它仅处理明确的“下班打卡”，以及本次已启用流程中严格匹配的“早退打卡”继续确认，各最多一次。其他弹层或失配页面停止；结果由后续新截图和官方记录核对。
5. 测试结束核对官方记录。试用执行完即停止；需要中止时选择“取消本次独立测试”。只读通过、点击请求完成、OCR 新回执通过、官方记录确认分别记证据。

原生 PIN 输入仅针对已核实的系统数字键盘，一次输入失败后停止并保留本地阻断状态。PIN 保存使用本机 AndroidKeyStore AES-GCM；凭据及原始页面不写入报告。保留 PIN 的日常解锁、拔 USB 接墙充、隔夜和重启首次解锁分别测试。

### 原生探针操作

1. 安装研究 APK，打开“AutoDing 能力研究”。
2. 点击“打开无障碍设置”，在系统页面启用本研究 App 的无障碍服务，返回并刷新能力报告，检查 `accessibilityConnected=true`。
3. 在桌面、目标 App 页面和锁屏 PIN 页分别执行“检查当前窗口与截图能力”。本步骤只读取结构计数与截图可用性。
4. 点击“60 秒后运行一次唤醒检查”。首次可能进入精确闹钟授权页面，授权并返回后重新点击一次才会安排测试。
5. 测试息屏/锁屏状态；等待一次测试结束后回到 App 查看 `alarmScheduledAt`、`alarmFiredAt`、`alarmLocked`、`alarmScanRequested` 及后续截图结果。
6. 在使用墙上电源且 USB 已拔出的条件下重复测试，人工记录测试前后状态及时间。此步骤应与连电脑测试单列记录。

另有“60 秒系统闹钟接口对照”按钮，使用 `setAlarmClock`，报告 `alarmMode=alarm-clock`；普通检查使用 `setExactAndAllowWhileIdle`，报告 `alarmMode=exact-allow-while-idle`。对照测试需分别记录 App 请求时刻、系统有效目标（本轮由 `dumpsys` 观测）和实际触发时刻；探针自身未自动读取系统有效排程。系统闹钟接口可能在系统中显示下一闹钟信息。探针共用一个单次定时目标，安排下一次检查会替换前一次，按顺序完成对照。

`alarmScanRequested=true` 只表示向已连接的无障碍服务提交了扫描请求。扫描及截图异步完成，应继续核对以下字段：

- `alarmId` 与 `alarmFiredForId` 一致，触发记录对应本次计划；`alarmFiredForScheduledAt` 对应本次计划时间。
- `alarmScanId`、顶层 `scanId`、`windowScan.scanId`、`screenshot.scanId` 一致，窗口和截图来自同一次请求。
- 对应的 `requestedAt`、`completedAt` 记录请求和完成的墙上时钟；`scanRequestedElapsedRealtime`、`completedElapsedRealtime` 等单调时钟字段用于检查时序，避免只依赖系统时间。
- `screenshot.outcome=available` 才表示取得缓冲区；`captureTimestamp` 仅作为截图 API 的原始时钟字段保留，不与墙上时钟或 `elapsedRealtime` 直接相减。关联判断使用同次 `scanId`、请求/完成时间及同源的单调时钟字段。

新版开始扫描时清除上一请求的窗口、截图和错误字段；迟到回调按 `scanId` 丢弃，避免覆盖更新请求的结果。旧版报告缺少这些关联字段，旧版成功请求仅作为当时触发和提交请求的证据。

本轮华为实机还观察到：锁屏后台直接通过 shell 广播读取报告时返回 `result=0`；先启动研究 App 的 `MainActivity` 后，以显式 action 调用可读取成功。广播失败原因仍待日志诊断，尚未确定为华为系统拦截。判断闹钟是否触发应使用原生保存的 `alarmFiredAt` 等字段，避免将 shell 入口失败当作闹钟失败；`alarmInteractiveBeforeWake` 记录唤醒前的交互状态，配合锁屏状态单独验证息屏测试条件。

精确闹钟权限与触发时刻应分开验收：本轮普通接口保持息屏的对照中，`dumpsys` 显示请求的 `origWhen` 保留原计划时间，但 `whenElapsed` 已后移 `175889ms`，尽管 `window=0`、`exactAllowReason=permission`。具体系统/OEM 根因待诊断。系统闹钟接口的另一次对照中，系统有效目标与 App 请求的单调时钟目标相等，实际触发晚 `34ms`；该结果仅计为一次通过，尚未完成隔夜、重复运行或跨机型稳定性验收。

| 字段 | 解释与验收重点 |
| --- | --- |
| `locked` / `secure` / `interactive` / `powered` | 当前锁屏、安全凭据、交互及接电状态 |
| `rootAvailable` / `scanComplete` | 窗口树是否可读、是否在 1024 节点上限内完成遍历 |
| `emptyFocusedPinEntries` / `digitCount` / `pinUiCandidate` | 空且聚焦的密码控件及十个数字键的结构候选；候选成立仍未执行解锁 |
| `attendanceTabCount` / `punchLabelCount` | 目标页面结构计数；计数为正尚不证明存在有效回执 |
| `screenshot.outcome` | `available` 表示得到图像缓冲区；`rejected` 连同 `errorCode` 记录系统结果 |
| `imagePersisted=false` | 截图缓冲区在回调中释放，未保存图像 |
| `hasPinStorage` / `hasNetworkPermission=false` | 本机是否已有加密 PIN；研究包无网络权限 |
| `alarmId` / `alarmFiredForId` / `alarmScanId` | 定时计划、实际触发及其扫描请求的关联 |
| `scanId` / `requestedAt` / `completedAt` | 每次扫描与异步完成结果的编号、请求及完成时间 |
| `alarmInteractiveBeforeWake` | 闹钟唤醒前屏幕是否处于交互状态；`false` 用于息屏条件核对 |

截图接口可能因安全窗口、权限或调用间隔返回错误。保留错误码与具体测试状态，不将截屏失败解释为解锁失败，也不将亮屏解释为解锁成功。

### 离线 OCR 操作

1. 打开“AutoDing OCR Research”，点击“运行内置合成样例（可断网测试）”。样例使用固定的 `2026-11-05 18:25:00 +08:00` 时钟，验证读取实际 `09:18` / `18:22`，而非班次标签 `09:00` / `18:18`。
2. 断开网络后重新启动 App，再运行样例，单独记录首次离线推理结果。内置依赖为 `com.google.mlkit:text-recognition-chinese:16.0.1`，APK 内携带中文模型，未选用动态下载模型的 Play Services 版本。
3. 点击“选择一张本机图片”，由 Android 文档选择器选图。真实图片按手机当前日期和时区解析，测试时将手机日期、时间和时区与办公室实际情况对齐。
4. 核对候选结果与原图、官方记录。识别失败或出现 `unsupported-layout` 时保持待核实。

当前裁剪区域仅对应 `1080×2400` 华为竖屏参考布局，支持相同比例缩放，宽高比偏差超过 `0.005` 时停止识别。原生集成的下午卡右边界为 `840`，覆盖完整“已打卡”并避开“早退/更新”标签；两个卡片的软件副本在 OCR 前仅遮盖蓝圈勾图标区域，不遮实际时间或计划标签，详见 [集成说明](standalone/README.md)。未知文字前缀仍拒绝。独立 OCR App 保留原样例裁剪。即使宽高比相同，字体、显示缩放、导航栏或页面改版也可能让裁剪区域偏移，仍需按布局验证。选图任一边超过 5000 像素时退出读取。

解析器检查当天日期与星期、页面动作、上下班卡片标签、“已打卡”实际时间、未来时间、重复或冲突记录及先后顺序。它仅返回时间和状态，不保存图片、原始 OCR 文本，也不向生产 AutoDing 回传记录。手工选图尚未绑定“刚刚采集的新页面”，因此同日旧截图的内容也可能成为候选；原生集成单独绑定新截图时间，点击前再核对窗口编号、布局版本和画面年龄。模块接入与实机读回正确性分别验收。

华为首次安装后的合成样例推理已显示班次日期 `2026-11-05`、实际上班 `09:18`、实际下班 `18:22`。安装包及设备包信息均未含 `INTERNET` / `ACCESS_NETWORK_STATE` 权限。本次保留手机原有网络状态，因此仅将“应用无网络权限下首次推理”计为通过；整机主动断网后的对照仍单列待测。

## 路径二：Android 位置模拟能力

当前实验回答“此系统能否为选定研究 App 创建一个专用模拟位置源，并观察 mock 标记”。它不读取手机实际坐标，不替换 `gps`、`network`、`fused` 等标准位置源，不向钉钉传入位置。专用源成功也不证明其他 App 会使用该源，更不证明考勤平台接受该位置。

1. 打开研究 App，点击“授予本实验位置权限”，选择精确位置权限。
2. 在系统开发者选项中，将“选择模拟位置信息应用”设为“AutoDing 能力研究”。仅安装 App 或授予位置权限尚未完成此步骤。
3. 点击“检查位置模拟能力”，预期状态为 `ready-for-local-fixture`。
4. 点击“运行专用位置源实验”。它创建专用 `autoding_research` provider，写入内置合成坐标，读取该专用源一次，最后移除专用源。
5. 核对 `injectionRequested=true`、`readBack=true`、`isMock=true`、`cleanupSucceeded=true`、`testProviderPresent=false`。
6. 测试结束后将系统“模拟位置信息应用”恢复到测试前的选择；该系统设置不由探针自动改动。

`mock-marker-observed` 是预期能力结论，说明 Android 明确将样例标记为模拟位置。若出现 `test-provider-cleanup-required`，先执行下述 `stop` 操作并刷新检查，确认专用源已移除后结束测试。此时不应通过更改标准位置源来清理。

## 构建与本地验证

在仓库根目录使用 PowerShell 7。两个研究 App 均使用 JDK 17、Gradle 8.14.3、Android SDK Platform 35、Build Tools 35.0.0 及 Android Gradle Plugin 8.13.2。脚本从 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或默认 SDK 目录解析 SDK，也支持显式传参。

```powershell
pwsh -File research/tools/Build-Standalone.ps1 -SdkPath 'PATH_TO_ANDROID_SDK'
pwsh -File research/tools/Build-OcrProbe.ps1 -GradlePath 'PATH_TO_GRADLE/gradle.bat' -SdkPath 'PATH_TO_ANDROID_SDK'
pwsh -File research/test/Test-LocationProbe.ps1
pwsh -File research/ocr/test/Test-ReceiptParser.ps1
pwsh -File research/standalone/test/Test-ReceiptParser.ps1
```

集成 APK 输出为 `build/AutoDing-standalone-research.apk` 及 SHA-256 文件，包名 `org.autoding.research`，versionCode 2，使用本地研究测试签名。旧入口 `Build-Research.ps1` 已兼容委托同一 Gradle 流程，并将相同 APK 复制到旧路径 `build/AutoDing-research.apk`。OCR APK 输出为 `research/ocr/build/distribution/AutoDing-OcrProbe-debug.apk`，同目录生成 SHA-256 文件。它们均为研究构建。

首次克隆如尚无 `build/research-test.jks`，集成构建会生成本机测试密钥；后续构建复用已有文件，绝不覆盖。该密钥被 Git 忽略，应保留备份。更新手机现有安装要求使用原安装的同一签名，因此另一台电脑新生成的测试密钥只适合首次安装到自己的设备；更新已有设备需复用其原构建密钥。该测试签名不作为正式发行凭据，密钥也不加入仓库。

首次构建需要下载 Gradle/Maven 依赖，构建机联网与手机 App 联网是独立条件。缓存完整时可给构建脚本加 `-Offline`，也可通过 `-GradleUserHome` 指定已有研究缓存。集成和 OCR 构建均核查合并 APK 的网络权限、内置中文模型与 ARM 库；集成包还核对签名证书。

位置夹具测试使用 Java 测试替身验证分支、mock 标记、异常路径和专用源清理；OCR 夹具测试直接验证纯 Java 解析器。这些测试不调用真实手机或 OCR 引擎。位置夹具里的旧 API 分支测试也不扩大 APK 的 API 31 最低版本范围。

前序原生探针构建、39 项位置夹具检查、独立 OCR 构建和离线重建、APK 验签、网络权限检查、内置模型及 ARM 库核查、21 项独立回执解析检查已通过；最终原生版本另记录 37 项解锁、29 项集成回执、28 项早退确认及 17 项成功弹窗纯 Java 检查，release lint 为 0 错误、17 警告。这些夹具不访问真实手机，也不代替 OCR、提交及回执的实机验收；试次证据见 [RESULTS.md](RESULTS.md)。离线重建证明构建缓存完整，与手机首次断网推理是独立验收项。

### 可选 ADB 研究接口

先用 `adb devices` 确认设备，再给每条命令指定 `-s SERIAL`。桥接接收器由 Android `DUMP` 权限保护，供本机 ADB 研究调用；普通第三方 App 不应据此假定可以调用生产接口。

```powershell
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation scan
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation alarm
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation alarmclock
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation trial-read
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation trial-cancel
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation location
adb -s SERIAL shell am broadcast -a org.autoding.research.PROBE -n org.autoding.research/.ProbeBridgeReceiver --es operation stop
```

示例统一使用显式 action `org.autoding.research.PROBE`。省略 `operation` 时返回报告；`scan` 提交异步窗口与截图检查，`alarm` 安排 60 秒后的普通单次检查，`alarmclock` 安排系统闹钟接口的单次对照，`trial-read` 安排独立只读试用，`trial-cancel` 取消本次试用，`location` 运行专用位置源实验，`stop` 仅清理该专用位置源。实际下班操作优先使用手机界面的当次确认入口。ADB 控制用于研究观测，不计入“脱离电脑运行”的验收证据。

## 后续验收门槛

| 阶段 | 通过条件 | 本阶段范围 |
| --- | --- | --- |
| E1 锁屏和截图 | 指定机型的窗口结构、PIN 页候选与截图结果明确 | 本轮华为解锁、PIN 页和真实考勤页采集通过；截图失败路径待测 |
| E2 离线中文识别 | 应用无网络权限下首次推理成功；整机断网对照、真实布局与反例分别验证 | 华为首次合成推理通过；新版已接原生截图，真实识别结果见 RESULTS |
| E3 单次定时唤醒 | 锁屏息屏时按计划触发、扫描结束；墙上电源与 USB 拔出条件独立复测 | USB 连接下系统闹钟接口单次晚 34ms，同次扫描/截图通过；普通接口实测后移，拔 USB 接墙充待测 |
| E4 手机本机 PIN 解锁 | 用户当次启用、一次输入、失败即停止；验证凭据保护及正确窗口 | 已实现 AndroidKeyStore 加密存储和原生一次输入；实机验收见 RESULTS，PC 解锁不计入 |
| E5a 单次完整流程 | 锁屏息屏开始，原生定时至提交、结果导航及新回执闭环 | v21、v22 两次连电脑通过，自动确认签退 `13:21`、`13:26`；v22 仍有 USB 连接，拔线墙充另行验收 |
| E5b 手机日常排程 | 手机本机每日排程、依实际上班时间决定下班时刻 | 研究原型尚未接入，单次完整流程通过不代表每日无人值守运行 |
| E6 长时与故障 | 隔夜、断网恢复、服务终止、权限撤回、重启后首次解锁分别验证，失败保持待核实 | 待设计与验收 |
| L1 专用位置源 | 注入、回读、mock 标记和清理均有实机证据 | 本轮华为成功路径通过；已恢复 mock app-op 并撤销研究位置权限 |

记录结果时使用北京时间（Asia/Shanghai），附设备型号、Android API、系统版本、研究 APK SHA-256、前置权限、USB/电源/锁屏状态、预期与实际结果及脱敏证据路径。PIN、完整私人页面、姓名、组织信息和真实坐标不进入公开日志或仓库。

## 参考

- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Android Direct Boot](https://developer.android.com/privacy-and-security/direct-boot)
- [Android KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager)
- [ML Kit 中文文字识别](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
