package org.autoding.ocrprobe;
import java.time.ZonedDateTime;
public final class ReceiptParserHarness {
    private static int checks;
    private static void check(boolean result, String label) { if (!result) throw new AssertionError(label); checks++; }
    private static ReceiptParser.Result parse(String in, String out, String dates) {
        return ReceiptParser.parse(in,out,dates,ZonedDateTime.parse("2026-11-05T18:25:00+08:00"));
    }
    public static void main(String[] arguments) {
        String in="上班09:00\n09:18已打卡", out="下班18:18\n18:22已打卡", date="下班打卡\n11月5日周四\n18:25:00";
        ReceiptParser.Result result=parse(in,out,date);
        check(result.accepted,"complete"); check("2026-11-05".equals(result.date),"date");
        check("09:18".equals(result.checkIn)&&"18:22".equals(result.checkOut),"actual times rather than shift labels");
        check(parse("上 班 09 ： 00\n． 09 ： 18 已 打 卡",out,date).accepted,"known OCR whitespace/glyph");
        result=parse(in,"下班18:18\n未打卡",date);
        check(result.accepted&&result.checkOut==null,"unpunched is no actual time");
        check(!parse("上班09:00\n19:00已打卡",out,date).accepted,"future checkin");
        check(!parse(in,"下班18:18\n19:00已打卡",date).accepted,"future checkout");
        check(!parse(in,out,"下班打卡\n11月4日周三").accepted,"yesterday");
        check(!parse(in,out,"下班打卡\n11月5日周五").accepted,"weekday");
        check(!parse(in,out,"11月5日周四").accepted,"missing action");
        check(!parse(in,out,"上班打卡\n11月5日周四").accepted,"action conflict");
        check(!parse("09:18已打卡",out,date).accepted,"missing heading");
        check(!parse(in,"下班18:18\n18:22已打卡更新打卡",date).accepted,"contaminated receipt");
        check(!parse(in,"下班18:18\n08:00已打卡",date).accepted,"out before in");
        check(!parse("上班09:00\n09:18已打卡\n09:19已打卡",out,date).accepted,"ambiguous receipts");
        check(!parse("上班09:00\n09:18已打卡\n未打卡",out,date).accepted,"conflicting receipt");
        check(!parse(in,out,"下班打卡\n11月35日周四").accepted,"invalid date");
        check(!parse("上班09:00\n25:18已打卡",out,date).accepted,"invalid time");
        check(!parse(in,out,date+"\n11月4日周三").accepted,"multiple dates");
        result=parse("private person\nprivate location\n"+in,out,date);
        check(result.accepted&&!result.display().contains("private"),"no raw personal text in output");
        check(!parse("private",out,date).display().contains("private"),"rejected output sanitized");
        System.out.println("PASS: "+checks+" on-device receipt parser checks");
    }
}
