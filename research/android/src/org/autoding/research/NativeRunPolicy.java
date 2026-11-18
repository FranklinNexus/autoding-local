package org.autoding.research;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Pure rules shared by pre-input and post-input receipt validation. */
public final class NativeRunPolicy {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private NativeRunPolicy() {}
    public static long receiptAt(String date, String time) {
        if (date == null || time == null || !time.matches("\\d{2}:\\d{2}(:\\d{2})?"))
            throw new IllegalArgumentException("receipt-time-invalid");
        return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZONE).toInstant().toEpochMilli();
    }
    public static long eveningAt(String date, String checkIn, int floorMinutes, int duration, int margin) {
        if (floorMinutes < 0 || floorMinutes >= 1440 || duration < 1 || duration > 1440 || margin < 0 || margin > 120)
            throw new IllegalArgumentException("trial-rule-invalid");
        long actual = receiptAt(date, checkIn);
        long floor = LocalDate.parse(date).atStartOfDay(ZONE).plusMinutes(floorMinutes).toInstant().toEpochMilli();
        long result = Math.max(floor, actual + (duration + margin) * 60000L);
        if (!Instant.ofEpochMilli(result).atZone(ZONE).toLocalDate().toString().equals(date))
            throw new IllegalArgumentException("cross-day-checkout-unsupported");
        return result;
    }
    public static boolean currentReceipt(String expectedDate, String actualDate, String time, long capturedAt, long now) {
        try {
            return expectedDate.equals(actualDate) && expectedDate.equals(Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate().toString())
                && capturedAt > 0 && capturedAt <= now && now - capturedAt <= 15000
                && receiptAt(actualDate, time) <= capturedAt;
        } catch (RuntimeException error) { return false; }
    }
    public static boolean newReceipt(String expectedDate, String actualDate, String time, String previous,
                                     long clickedAt, long capturedAt, long now) {
        try {
            return clickedAt > 0 && capturedAt >= clickedAt && time != null && !time.equals(previous)
                && currentReceipt(expectedDate, actualDate, time, capturedAt, now)
                && receiptAt(actualDate, time) >= (clickedAt / 60000L) * 60000L;
        } catch (RuntimeException error) { return false; }
    }
}
