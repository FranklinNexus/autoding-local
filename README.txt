AutoDing Local 0.10.0-preview.1 — public trial (Android: 0.10-preview)

See README.md for setup, validation, limitations, and GPL-3.0-or-later licensing.
See docs/QUICKSTART.md for a reproducible Windows build and phone installation.
See docs/COMPATIBILITY.md for the tested device matrix and vendor setup checklist.
See docs/TRIAL.md for the reproducible local trial procedure.

The Android app schedules DingTalk launches. Evening launch times depend on a
confirmed actual check-in and the shift's saved minimum duration. A minute-only
receipt adds a 60-second planning margin. Opening DingTalk is not an attendance
confirmation.

The optional Windows companion uses three tasks: Observe, Unlock, Attendance.
It requires an active Windows login, a powered USB-connected phone, PowerShell 7,
and Windows Chinese OCR support. PIN credentials are needed only for Unlock;
SkipUnlock registers Observe and Attendance without credentials. Disable any
existing Unlock task explicitly when switching to an already-unlocked phone.
The Android code baseline is Android 6.0/API 23+, not a verified device list.
Only Huawei JEF-AN00 / HarmonyOS 4.2 / API 31 / DingTalk 9.0.5 has been tested.
Automatic attendance observation uses a LayoutPath JSON profile; the tested
reference is Huawei portrait 1080x2400 and current-day receipts. Custom profiles
and scaled sizes require local validation and explicit AcceptUnverifiedLayout.
The same resolution on another model also needs validation. Overnight receipts
need manual review. iOS and non-Windows companion operation are outside this release.

The 2026-11-05 evening 18:22 and 2026-11-06 morning 07:00 punches were confirmed
by official DingTalk records. The morning receipt cancelled the planned 08:56
launch; it was not caused by that later launch. Long-term reliability is pending.

This is a single-device trial, based on the launcher approach in
https://github.com/littleclaw/AutoDing (reviewed upstream commit 07f99cc).
It keeps applicationId xcom.warof.chosen and is a separate minimal implementation.
Keep local credentials, signing keys, original attendance screenshots and raw
event logs out of source contributions. Release APKs, if present, are separate
build artifacts; release status and checksums must be verified on GitHub Releases.
