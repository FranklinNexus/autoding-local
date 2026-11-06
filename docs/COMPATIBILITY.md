# 兼容矩阵与适配反馈

代码的系统下限、合成测试通过与设备实测是不同证据。以下仅将实际完成的项目列为已验证，空缺项保持待验证。

## 平台与设备矩阵

| 范围 | 基线或依赖 | 当前证据 |
| --- | --- | --- |
| Android App | Android 6.0 / API 23 以上；当前 target API 33、compile API 35 | 代码基线；其他 API/厂商需实测。 |
| 实测手机 | 华为 JEF-AN00，HarmonyOS 4.2，Android API 31 | 定时唤起、Windows PIN 辅助、实际签到/签退、幂等回执已有证据。 |
| 实测钉钉 | Android 9.0.5，中文界面 | 当前考勤页 1080×2400 竖屏布局；升级后需复验。 |
| Windows 辅助 | PowerShell 7、Android SDK Platform-Tools/ADB、当前用户登录 | Observe/Unlock/Attendance 三任务已现场运行；Windows 本机须具备所用API及中文OCR。 |
| 构建 | Windows、JDK 17、SDK Platform 35、Build-Tools 35.0.0 | 当前脚本与 Windows CI 验证。 |
| iOS | 本仓库无 iOS App 或对应手机控制实现 | 当前方案未覆盖。 |
| macOS/Linux 辅助 | 现有脚本依赖 DPAPI、Windows Task Scheduler、WinRT OCR | 当前方案未覆盖，纯 Java 排程测试可独立移植。 |

2026-11-05 官方钉钉通知确认 18:22 极速签退成功；11-06 官方通知和统计确认 07:00 极速签到成功。第二天原定 08:56 的任务已因实际签到确认取消，提前签到不代表 AutoDing 在 07:00 触发。多日、重启、实际 USB 重连及其他机型尚未形成稳定性结论，详见 [现场记录](TRIAL.md#实机结论与验证范围)。

## 厂商设置检查表

菜单名称随系统版本变化，下表是检查入口，只有上述华为设备已实测；请按设备当前设置确认，不将菜单名称视为兼容保证。

| 系统系列 | 需要检查的项目 |
| --- | --- |
| 华为 / 荣耀 | 应用启动管理改为手动，允许自启动、关联启动、后台活动；电池优化与最近任务保留。 |
| 小米 / Redmi / HyperOS | 后台自启动、应用省电策略、后台弹出界面/悬浮窗、最近任务保留。 |
| OPPO / realme / OnePlus | 自启动、允许后台活动、应用耗电管理、悬浮窗。 |
| vivo / iQOO | 自启动、后台高耗电/后台活动、悬浮窗、后台保留。 |
| Samsung | 睡眠/深度睡眠应用、未受限电池模式、后台权限、通知与悬浮窗。 |
| Pixel / AOSP | 精确闹钟、通知/全屏提醒、悬浮窗、电池优化和后台启动限制。 |

共同检查：App 精确定时、通知、悬浮窗；钉钉登录与所需位置权限；手机供电和系统时间/时区。Android 版本及厂商策略可能限制后台启动，全屏通知不保证打开页面。重启后先在手机解锁并核对下一次任务，强行停止应用后重新启用排程。

## PIN 适配

Unlock 只在确认标准系统数字 PIN 控件后输入一次：当前识别 `com.android.systemui` / `com.huawei.systemui` 的 PIN 密码框，要求已聚焦、内容为空、控件启用且十个数字键完整。合成控件测试覆盖若干资源名，其他厂商实机仍需验证。

图案、字母密码、第三方锁屏、输入框不明确或键盘不完整均应保持阻断。错误输入或结果未确认会停止自动尝试，凭据和阻断状态保存在仓库外。本人应在现场先验收一次，详见 [快速开始](QUICKSTART.md#5-先单次检查-windows-辅助组件)。

UI 读取 helper 在 API 24 以上使用 `connect(1)`，API 23 回退到 `connect()`。API 23 路径只有合成测试证据，连接期间会临时抑制其他无障碍服务；该版本应先现场验证再决定是否使用 Windows 辅助。API 23 的代码基线不表示解锁和考勤读取已在 Android 6 实机通过。

## OCR 布局适配

Windows 读取组件要求本地简体中文 OCR。参考布局为华为 JEF-AN00 的 1080×2400 竖屏；仅截取签到、签退、日期区域并校验页面、日期、状态语义。分辨率缩放和布局参数不能覆盖不同的语义或控件结构，布局变化仍需验证。

默认配置是 [`layouts/huawei-attendance-portrait-v1.json`](../layouts/huawei-attendance-portrait-v1.json)。`Watch-Attendance.ps1`、`Read-AttendanceOcr.ps1` 和任务安装支持 `-LayoutPath PATH`；裁剪按 `referenceWidth` / `referenceHeight` 对当前图片等比换算，宽高比例须满足 `aspectTolerance`。结构示例：

```json
{
  "schemaVersion": 1,
  "id": "local-attendance-layout",
  "referenceWidth": 1080,
  "referenceHeight": 2400,
  "aspectTolerance": 0.005,
  "crops": {
    "morning": [44, 590, 518, 730],
    "evening": [506, 590, 806, 730],
    "date": [310, 1320, 780, 1605]
  }
}
```

每个区域使用 `[左, 上, 右, 下]` 参考像素；这是实测布局参数示例，新的界面需要现场调整和验证。辅助诊断命令为：

```powershell
./tools/Test-DeviceCompatibility.ps1 -Serial SERIAL -LayoutPath 'C:\Local\my-attendance-layout.json' > 'C:\Local\compatibility.json'
```

也可让生成器校验字段和裁剪边界后保存配置（输出父目录须已存在）：

```powershell
./tools/New-AttendanceLayout.ps1 -Id LocalAttendance -ReferenceWidth 1080 -ReferenceHeight 2400 -MorningBounds 44,590,518,730 -EveningBounds 506,590,806,730 -DateBounds 310,1320,780,1605 -OutputPath 'C:\Local\my-attendance-layout.json'
```

参考宽高须为 1–16384 的整数，`aspectTolerance` 为 0–0.05，三块裁剪必须在参考图像内且有正面积。JSON 使用严格语法，避免重复字段、注释或尾逗号。生成成功只代表格式与边界有效，仍需核对实际界面语义。

非内置配置或缩放后的尺寸默认阻断自动回传，输出诊断结果。先用本地合成图和现场单次读取核对，再显式传 `-AcceptUnverifiedLayout` 给 watcher / 任务安装，表示接受这份尚未纳入实测矩阵的配置。该参数不会新增已验证机型；**同分辨率的其他机型也须人工验收**。暂未适配时可在 App 人工确认真实签到，继续使用签退排程。自动回执仅适用于当天，跨夜班次由 Android 排程支持，实际回执需人工核对。

未来候选、跨午夜停留页面和画面缓存属于单独的一致性边界。发现未来记录时先保持待确认；即使后续时间已到，也应结合刷新后的页面或官方通知/统计独立核对。11-06 的 07:00 已有官方成功证据，仍保留此项读取器边界作为待改进事项。

## 贡献一条可复核的兼容记录

使用 [兼容反馈表](https://github.com/FranklinNexus/autoding-local/issues/new?template=compatibility.yml)，每个设备/系统/钉钉组合分开记录。未知项写“未知”，失败与未测试也有价值。

```text
型号：MODEL（不要序列号或IMEI）
Android/API：VERSION/API，厂商系统：VERSION
钉钉：VERSION，界面语言：LANGUAGE，分辨率：WIDTH×HEIGHT
电脑：Windows版本 / PowerShell版本 / ADB版本 / 中文OCR可用性
模式：Android单独 / Observe / Unlock / Attendance
后台设置：已完成项；锁屏：标准数字PIN或其他类型（不要密码）
布局：默认或脱敏LayoutPath；只附配置，不附原始考勤截图
验证：单次定时、锁屏续接、实际官方回执、隔夜、重启、USB重连
结果：通过/失败/未测；时间使用相对时刻或脱敏示例
证据：脱敏阶段/错误码；官方结果与启动日志分别记录
```

请删除姓名、组织、地点、序列号、账号、PIN、签名密钥及原始截图，优先提交合成界面。日志只保留定位问题所需的少量脱敏事件，不把日志目录直接打包进 Issue。贡献不自动代表该机型普遍可用，须说明具体版本与完成的验证步骤。
