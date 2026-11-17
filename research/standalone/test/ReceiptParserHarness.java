package org.autoding.research;

import java.time.ZonedDateTime;
import java.util.Map;

public final class ReceiptParserHarness {
    private static int checks;
    public static void main(String[] arguments) {
        ZonedDateTime now=ZonedDateTime.parse("2026-11-17T10:40:09+08:00");
        String in="上班09:00\n09:25已打卡", out="下班18:25\n10:34已打卡";
        ReceiptParser.Result current=ReceiptParser.parse(in,out,"下班打卡\n11月17日周二\n10:40:09",now);
        check(current.accepted && "09:25".equals(current.checkIn) && "10:34".equals(current.checkOut),"current fixture");
        check("2026-11-17".equals(current.date),"current date");
        check(ReceiptParser.parse(in,out,"更新打卡\n11月17日周二",now).accepted,"existing receipt update");
        check(!ReceiptParser.parse(in,"下班18:25\n未打卡","更新打卡\n11月17日周二",now).accepted,"update without receipt");
        check(!ReceiptParser.parse(in,out,"下班打卡\n更新打卡\n11月17日周二",now).accepted,"ambiguous action");
        check(!ReceiptParser.parse(in,out,"下班打卡\n11月17日周三",now).accepted,"weekday");
        check(!ReceiptParser.parse(in,"下班18:25\n10:45已打卡","下班打卡\n11月17日周二",now).accepted,"future receipt");
        check(!ReceiptParser.parse("上班09:00\n09:25已打卡\n09:26已打卡",out,"下班打卡\n11月17日周二",now).accepted,"multiple records");
        String date="下班打卡\n11月17日周二";
        check(ReceiptParser.parse("上班\n09:00\n09:25\n已打卡",out,date,now).accepted,"split heading and actual");
        check(ReceiptParser.parse(in,"下班\n18:25\n已打卡\n10:34",date,now).accepted,"status before actual time");
        check(ReceiptParser.parse("✓\n上班09:00\n✔09:25已打卡","下班18:25\n（√）10:34已打卡",date,now).accepted,"explicit checkmarks");
        check(ReceiptParser.parse("上班09:00\n09:25\n✓\n已打卡",out,date,now).accepted,"separate checkmark between split receipt");
        check(!ReceiptParser.parse("上班\n09:00\n已打卡",out,date,now).accepted,"planned time never reused as actual");
        check(!ReceiptParser.parse(in,"下班\n18:25\n已打卡",date,now).accepted,"evening planned time never reused");
        check(!ReceiptParser.parse("上班09:00\nV09:25已打卡",out,date,now).accepted,"unknown prefix stays rejected");
        check(!ReceiptParser.parse(in,"下班18:25\n10:34已打卡早退更新打卡",date,now).accepted,"inline tags stay rejected");
        check(!ReceiptParser.parse(in,"下班18:25\n10:34已打卡\n早退",date,now).accepted,"extra unknown line stays rejected");
        check(!ReceiptParser.parse("上班09:00\n09:25已打卡\n09:25已打卡",out,date,now).accepted,"duplicate actual not deduplicated");
        check(!ReceiptParser.parse("上班09:00\n09:25\n已打卡\n未打卡",out,date,now).accepted,"split status conflict");
        check(!ReceiptParser.parse("上班09:00\n已打卡",out,date,now).accepted,"status without actual time");
        check(!ReceiptParser.parse("上班09:00\n09:25",out,date,now).accepted,"time without actual status");
        check(ReceiptParser.parse("上 班 09 ： 00\n． 09 ： 25 已 打 卡",out,date,now).accepted,"known whitespace and colon");
        check(ReceiptParser.parse("上班09:00\n未打卡","下班18:25\n未打卡","上班打卡\n11月17日周二",now).accepted,"explicit unpunched");
        ReceiptParser.Result bad=ReceiptParser.parse(in,"下班18:25\n10:34已打卡早退",date,now);
        check("evening-actual-format-unconfirmed".equals(bad.reason),"evening reason specific");
        bad=ReceiptParser.parse("上班\n09:00\n已打卡",out,date,now);
        check(bad.reason.startsWith("morning-"),"morning reason specific");
        Map<String,Object> diag=ReceiptParser.diagnostics("上班\n09:00\n09:25\n已打卡",out,date);
        @SuppressWarnings("unchecked") Map<String,Object> morning=(Map<String,Object>)diag.get("morning");
        check(Integer.valueOf(4).equals(morning.get("lineCount")) && Integer.valueOf(1).equals(morning.get("headingOnlyMatches")),"diagnostic split labels");
        check(Integer.valueOf(2).equals(morning.get("timeTokenCount")) && Integer.valueOf(1).equals(morning.get("actualStatusOnlyMatches")),"diagnostic receipt shapes");
        String shaped=ReceiptParser.diagnostics("private person\nV09:25已打卡",out,date).toString();
        check(!shaped.contains("private") && !shaped.contains("09:25") && !shaped.contains("10:34") && !shaped.contains("11月"),"diagnostics omit raw values");
        check(shaped.contains("unknownActualPrefix=true"),"diagnostics flag unknown actual format");
        System.out.println("PASS: "+checks+" standalone receipt parser checks");
    }
    private static void check(boolean value,String label) { if (!value) throw new AssertionError(label); checks++; }
}
