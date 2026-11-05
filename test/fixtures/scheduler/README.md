# Scheduler state fixture

Run `./test/Test-SchedulerState.ps1` with JDK 17. The script compiles the actual `src/.../Scheduler.java` and `AttendancePlanner.java` against these small in-memory platform stubs, then runs 49 state checks. Outputs go to the ignored `build/test-scheduler` directory.

The stubs replace Android preferences, alarm registration, service requests and JSON containers. No phone, Android SDK, network, installed application or personal data is used. These checks validate scheduler state transitions; they do not verify Android lifecycle behavior, manufacturer background restrictions or an actual attendance result.

The harness selects a GMT offset whose local current hour is noon and explicitly marks its current test date as a workday. This keeps the same 09:18 and 18:19 examples valid at any test execution time, including weekends. It does not replace the business clock or scheduler implementation.
