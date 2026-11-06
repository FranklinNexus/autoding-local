These isolated JVM fixtures compile and run the real tools/android-ui/ReadWindow.java.
API 23 deliberately exposes only UiAutomation.connect(); API 24+ exposes connect(int)
and rejects use of the no-argument overload. The harness checks both connection
paths, password text/description redaction, and finally cleanup on normal output,
missing active windows, and serializer errors.

The classes simulate just the APIs the helper uses. They do not certify Android
runtime behavior, OEM lock screens, hidden API access, or device compatibility.
All strings are synthetic; no phone, screenshot, PIN, or attendance record is read.
