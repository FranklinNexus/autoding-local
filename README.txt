AutoDing Local 0.9-local

See README.md for setup, validation, limitations, and GPL-3.0-or-later licensing.
See docs/TRIAL.md for the reproducible local trial procedure.

The Android app schedules DingTalk launches. Evening launch times depend on a
confirmed actual check-in and the shift's saved minimum duration. A minute-only
receipt adds a 60-second planning margin. Opening DingTalk is not an attendance
confirmation.

The optional Windows companion uses three tasks: Observe, Unlock, Attendance.
It requires an active Windows login, a powered USB-connected phone, a locally
encrypted PIN credential, PowerShell 7, and Windows Chinese OCR support.
Automatic attendance observation is limited to the tested Huawei portrait
1080x2400 layout and current-day receipts. Overnight receipts need manual review.

This is a single-device trial, based on the launcher approach in
https://github.com/littleclaw/AutoDing (reviewed upstream commit 07f99cc).
It keeps applicationId xcom.warof.chosen and is a separate minimal implementation.
Do not publish local credentials, signing keys, screenshots, APKs, or event logs.
