AutoDing local test 0.7

Local-only Android timer prototype, inspired by the launcher approach in
https://github.com/littleclaw/AutoDing (reviewed commit 07f99cc).
Keeps upstream applicationId xcom.warof.chosen. This is a separate minimal
implementation, not the upstream full-feature APK.
Licensed under GPL-3.0-or-later; see LICENSE.

Requires Android 6+, Android SDK build-tools 35.0.0 and platform android-35,
and JDK 17 for the included Windows build.ps1.
No Internet permission, push SDK, email credentials, accessibility service,
or attendance API integration is included. It opens the installed DingTalk
app; the actual attendance outcome must be checked in DingTalk.

First grant exact-alarm, overlay and notification permissions using the app
buttons. The timer uses AlarmManager.setAlarmClock and a visible alarm
notification with a fullscreen intent; vendor background-start rules still
apply. The notification needs explicit permission on this Huawei firmware.
Huawei: allow automatic/associated startup and background operation, and
lock the app card in recent tasks. Password-locked devices need local unlock.
Use the 60-second test first. Daily schedules are disabled by default.
The default example is 09:00 and 18:00 with a configurable random window:
morning 0-5 minutes early and evening 0-5 minutes late. Random slots are saved
and preserved across app restart and normal boot. Each enabled day gets one
morning and one evening slot; the app shows the exact next scheduled times.
The weekday option means Monday-Friday only; holidays and shifted workdays
must be adjusted manually. Saved daily schedules restore after normal boot
and unlock, app update, and clock/timezone changes. Force-stop prevents alarms
until the app is reopened. A missed launch does not stop the next daily alarm
from being scheduled. Keep the device powered and check actual records.

The standby foreground service displays a visible "AutoDing" waiting badge.
This keeps the app visible while other apps are open. It uses the already
granted overlay permission and stops when daily scheduling is disabled and
no test is pending. Keep this badge present during the trial.
The trial phone is configured to stay awake while connected to AC/USB power,
with manual low brightness, while retaining its lock-screen credentials.
After restarting the phone, unlock it locally once. Pressing the power button
to lock it, unplugging it, or battery/thermal policies can change this state.
Screen-off operation on this Huawei was blocked by the firmware during tests.
The verified mode is powered, awake, unlocked once, and showing the waiting
badge. The prototype opens DingTalk without modifying DingTalk or its rules.

Build: powershell -ExecutionPolicy Bypass -File .\build.ps1
Planner tests: javac -encoding UTF-8 -d build/test-classes
  src/xcom/warof/chosen/SchedulePlanner.java
  test/xcom/warof/chosen/SchedulePlannerTest.java
Then: java -cp build/test-classes xcom.warof.chosen.SchedulePlannerTest
The generated key is only for this local prototype. Save the same key for
future updates; keep it private. APKs are produced under build/.
