package org.autoding.ocrprobe;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parser. It returns only times/status and never raw OCR text. */
public final class ReceiptParser {
    private ReceiptParser() {}
    public static final class Result {
        public final boolean accepted;
        public final String reason, date, checkIn, checkOut;
        private Result(boolean accepted, String reason, String date, String in, String out) {
            this.accepted = accepted; this.reason = reason; this.date = date;
            this.checkIn = in; this.checkOut = out;
        }
        public String display() {
            if (!accepted) return "识别保持待核实：" + reason;
            return "班次日期：" + date + "\n实际上班：" + (checkIn == null ? "未打卡" : checkIn)
                + "\n实际下班：" + (checkOut == null ? "未打卡" : checkOut)
                + "\n来源：本机离线 OCR；仍需核对官方记录。";
        }
    }
    private static final class Punch {
        final String text;
        final LocalTime time;
        Punch(String text, LocalTime time) { this.text = text; this.time = time; }
    }
    private static final Pattern DATE = Pattern.compile("^(?:(\\d{4})年)?(\\d{1,2})月(\\d{1,2})日(?:周|星期)([一二三四五六日天])$");
    private static final Pattern ACTUAL = Pattern.compile("^(?:[．.•✓])?(?:(\\d{2}:\\d{2}(?::\\d{2})?)已打卡|已打卡(\\d{2}:\\d{2}(?::\\d{2})?))$");
    private static List<String> lines(String input) {
        List<String> result = new ArrayList<>();
        for (String line : input.split("[\\r\\n]+")) {
            String clean = line.replaceAll("\\s+", "").replace('：', ':');
            if (!clean.isEmpty() && !result.contains(clean)) result.add(clean);
        }
        return result;
    }
    private static Result rejected(String reason) { return new Result(false, reason, null, null, null); }
    public static Result parse(String morning, String evening, String dateText, ZonedDateTime now) {
        try {
            List<String> dates = lines(dateText);
            int actionCount = 0, dateCount = 0;
            String action = null; LocalDate date = null;
            for (String line : dates) {
                if (line.equals("上班打卡") || line.equals("下班打卡")) { action = line; actionCount++; }
                Matcher matcher = DATE.matcher(line);
                if (!matcher.matches()) continue;
                dateCount++;
                int year = matcher.group(1) == null ? now.getYear() : Integer.parseInt(matcher.group(1));
                date = LocalDate.of(year, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                String weekday = "一二三四五六日".substring(date.getDayOfWeek().getValue() - 1, date.getDayOfWeek().getValue());
                if (!matcher.group(4).equals(weekday) && !(weekday.equals("日") && matcher.group(4).equals("天"))) return rejected("weekday-mismatch");
            }
            if (actionCount != 1) return rejected("action-unconfirmed");
            if (dateCount != 1 || date == null) return rejected("date-unconfirmed");
            if (!date.equals(now.toLocalDate())) return rejected("date-not-today");
            Punch in = punch(lines(morning), "上班");
            Punch out = punch(lines(evening), "下班");
            if (in != null && action.equals("上班打卡")) return rejected("action-record-conflict");
            if (in != null && LocalDateTime.of(date, in.time).isAfter(now.toLocalDateTime())) return rejected("check-in-in-future");
            if (out != null && LocalDateTime.of(date, out.time).isAfter(now.toLocalDateTime())) return rejected("check-out-in-future");
            if (in != null && out != null && out.time.isBefore(in.time)) return rejected("checkout-before-checkin");
            return new Result(true, "", date.toString(), in == null ? null : in.text, out == null ? null : out.text);
        } catch (DateTimeException | IllegalArgumentException exception) {
            return rejected("card-unconfirmed");
        }
    }
    private static Punch punch(List<String> lines, String heading) {
        Pattern label = Pattern.compile("^" + heading + "(\\d{2}:\\d{2})$");
        int labels = 0, empty = 0, actual = 0; Punch result = null;
        for (String line : lines) {
            Matcher lm = label.matcher(line);
            if (lm.matches()) { LocalTime.parse(lm.group(1)); labels++; }
            if (line.equals("未打卡")) empty++;
            Matcher match = ACTUAL.matcher(line);
            if (match.matches()) {
                String time = match.group(1) == null ? match.group(2) : match.group(1);
                result = new Punch(time, LocalTime.parse(time)); actual++;
            } else if (line.contains("已打卡")) throw new IllegalArgumentException();
        }
        if (labels != 1 || actual > 1 || (actual > 0 && empty > 0) || (actual == 0 && empty != 1)) throw new IllegalArgumentException();
        return result;
    }
}
