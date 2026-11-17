This JVM fixture compiles and executes the production `NativeUnlock.java` against
minimal Android API substitutes. All keypad nodes and credentials are synthetic.
It never connects to a phone, reads a saved credential, or submits attendance.

The checks cover strict keypad recognition, one attempt with a persistent guard,
input length changes after automatic submission, cancellation during input,
unknown/redacted input state, service loss, concurrency, and missing gesture
callbacks. Fresh-node and delayed acknowledgement cases also check that cached
password nodes are refreshed, progress can arrive late without resending a key,
and an acknowledged input reset stops further input. The fake store models the
attempt guard only; these checks do not
certify AndroidKeyStore, OEM node semantics, physical unlock behavior, or a full
attendance workflow. Those need separate on-device evidence.

Run from the repository root:

```powershell
pwsh -File research/test/Test-NativeUnlock.ps1
```
