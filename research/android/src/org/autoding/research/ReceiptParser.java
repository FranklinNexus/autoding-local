package org.autoding.research;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
    private static final String TIME = "\\d{2}:\\d{2}(?::\\d{2})?";
    private static final Pattern ACTUAL = Pattern.compile("^(?:("+TIME+")已打卡|已打卡("+TIME+"))$");
    private static final Pattern TIME_ONLY = Pattern.compile("^"+TIME+"$");
    private static final Pattern TIME_TOKEN = Pattern.compile(TIME);
    private static final Pattern MARK = Pattern.compile("^(?:[．.•✓✔√☑✅]|[（(\\[](?:✓|✔|√)[）)\\]])\\uFE0F?");
    private static final class CardException extends IllegalArgumentException {
        final String reason;
        CardException(String reason) { this.reason=reason; }
    }
    private static String withoutMark(String line) { return MARK.matcher(line).replaceFirst(""); }
    private static List<String> lines(String input) {
        List<String> result = new ArrayList<>();
        for (String line : input.split("[\\r\\n]+")) {
            String clean = line.replaceAll("[\\s\\p{Z}]+", "").replace('：', ':');
            if (!clean.isEmpty()) result.add(clean);
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
                if (line.equals("上班打卡") || line.equals("下班打卡") || line.equals("更新打卡")) { action = line; actionCount++; }
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
            if (out == null && action.equals("更新打卡")) return rejected("update-record-missing");
            if (in != null && LocalDateTime.of(date, in.time).isAfter(now.toLocalDateTime())) return rejected("check-in-in-future");
            if (out != null && LocalDateTime.of(date, out.time).isAfter(now.toLocalDateTime())) return rejected("check-out-in-future");
            if (in != null && out != null && out.time.isBefore(in.time)) return rejected("checkout-before-checkin");
            return new Result(true, "", date.toString(), in == null ? null : in.text, out == null ? null : out.text);
        } catch (CardException exception) {
            return rejected(exception.reason);
        } catch (DateTimeException | IllegalArgumentException exception) {
            return rejected("card-unconfirmed");
        }
    }
    private static Punch punch(List<String> lines, String heading) {
        String prefix=heading.equals("上班") ? "morning-" : "evening-";
        try {
            List<String> card=new ArrayList<>();
            for (String line:lines) {
                String content=withoutMark(line);
                if (!content.isEmpty()) card.add(content);
            }
            Pattern label=Pattern.compile("^"+heading+"(\\d{2}:\\d{2})$");
            boolean[] used=new boolean[card.size()];
            int labels=0, empty=0, actual=0; Punch result=null;
            // Consume the planned time before looking for actual receipt pairs.
            // This keeps a split heading / planned time / 已打卡 from producing
            // a false actual time when the real receipt time is absent.
            for (int i=0;i<card.size();i++) {
                if (used[i]) continue;
                String line=card.get(i); Matcher lm=label.matcher(line);
                if (lm.matches()) { LocalTime.parse(lm.group(1)); labels++; used[i]=true; }
                else if (line.equals(heading)) {
                    if (i+1 >= card.size() || !card.get(i+1).matches("\\d{2}:\\d{2}"))
                        throw new CardException(prefix+"heading-time-unconfirmed");
                    LocalTime.parse(card.get(i+1)); labels++; used[i]=true; used[i+1]=true;
                }
            }
            if (labels != 1) throw new CardException(prefix+"heading-unconfirmed");
            for (int i=0;i<card.size();i++) {
                if (used[i]) continue;
                String line=card.get(i); Matcher match=ACTUAL.matcher(line);
                String time=null;
                if (match.matches()) time=match.group(1) == null ? match.group(2) : match.group(1);
                else if (line.equals("未打卡")) { empty++; used[i]=true; continue; }
                else if (i+1 < card.size() && !used[i+1] &&
                    ((TIME_ONLY.matcher(line).matches() && card.get(i+1).equals("已打卡")) ||
                     (line.equals("已打卡") && TIME_ONLY.matcher(card.get(i+1)).matches()))) {
                    time=line.equals("已打卡") ? card.get(i+1) : line;
                    used[i+1]=true;
                } else throw new CardException(prefix+(line.contains("已打卡") ? "actual-format-unconfirmed" : "unexpected-line"));
                result=new Punch(time,LocalTime.parse(time)); actual++; used[i]=true;
            }
            if (actual > 1) throw new CardException(prefix+"actual-ambiguous");
            if (actual > 0 && empty > 0) throw new CardException(prefix+"status-conflict");
            if (actual == 0 && empty != 1) throw new CardException(prefix+"actual-unconfirmed");
            return result;
        } catch (DateTimeException exception) { throw new CardException(prefix+"time-invalid"); }
    }

    /** Fixed categories and counts only. Never includes input text or time values. */
    public static Map<String,Object> diagnostics(String morning,String evening,String dateText) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("schema",1); result.put("morning",describe(morning,"上班"));
        result.put("evening",describe(evening,"下班")); result.put("date",describe(dateText,null));
        return result;
    }
    private static Map<String,Object> describe(String input,String heading) {
        Map<String,Object> out=new LinkedHashMap<>(); List<String> kinds=new ArrayList<>();
        List<String> source=lines(input);
        int labels=0, splitLabels=0, actual=0, status=0, empty=0, times=0, seconds=0, unknown=0, dates=0, actions=0;
        boolean unknownPrefix=false;
        Pattern label=heading == null ? null : Pattern.compile("^"+heading+"\\d{2}:\\d{2}$");
        for (String raw:source) {
            String line=withoutMark(raw), kind="unknown";
            if (line.isEmpty()) kind="checkmark";
            else if (label != null && label.matcher(line).matches()) { labels++; kind="heading-with-time"; }
            else if (heading != null && line.equals(heading)) { splitLabels++; kind="heading-only"; }
            else if (ACTUAL.matcher(line).matches()) { actual++; kind="actual-with-time"; }
            else if (line.equals("已打卡")) { status++; kind="actual-status-only"; }
            else if (line.equals("未打卡")) { empty++; kind="unpunched"; }
            else if (TIME_ONLY.matcher(line).matches()) kind="time-only";
            else if (DATE.matcher(line).matches()) { dates++; kind="date-with-weekday"; }
            else if (line.equals("上班打卡") || line.equals("下班打卡") || line.equals("更新打卡")) { actions++; kind="action-exact"; }
            else { unknown++; if (line.contains("已打卡")) unknownPrefix=true; }
            Matcher token=TIME_TOKEN.matcher(line);
            while (token.find()) { times++; if (token.group().length() > 5) seconds++; }
            if (kinds.size() < 24) kinds.add(kind);
        }
        out.put("lineCount",source.size()); out.put("headingMatches",labels); out.put("headingOnlyMatches",splitLabels);
        out.put("actualMatches",actual); out.put("actualStatusOnlyMatches",status); out.put("unpunchedMatches",empty);
        out.put("timeTokenCount",times); out.put("secondPrecisionTimeCount",seconds);
        out.put("unknownActualPrefix",unknownPrefix); out.put("unknownLineCount",unknown);
        out.put("dateMatches",dates); out.put("actionMatches",actions); out.put("lineKinds",kinds);
        return out;
    }
}
