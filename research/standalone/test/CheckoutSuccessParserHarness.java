package org.autoding.research;

import java.util.Arrays;

public final class CheckoutSuccessParserHarness {
    private static int checks;
    private static CheckoutSuccessParser.Result parse(String... lines) { return CheckoutSuccessParser.parse(Arrays.asList(lines)); }
    private static CheckoutSuccessParser.Result morning(String... lines) { return CheckoutSuccessParser.parseMorning(Arrays.asList(lines)); }
    public static void main(String[] arguments) {
        String heading="下班打卡成功",time="打卡时间:2026-11-17 12:43",action="查看打卡结果";
        CheckoutSuccessParser.Result result=parse(heading,time,action);
        check(result.accepted && "2026-11-17".equals(result.date) && "12:43".equals(result.checkOut),"observed success modal");
        check(result.actionIndex == 2,"actual action line index");
        check(parse(heading,"打卡时间： 2026-11-17 12:43", "查看打卡\u200B结果").accepted,"visible label normalization only");
        check(!parse("上班打卡成功",time,action).accepted,"checkin success rejected");
        check(!parse("下班打卡失败",time,action).accepted,"failed status rejected");
        check(!parse(heading,time,"查看结果").accepted,"action exact required");
        check(!parse(heading,time,action,action).accepted,"duplicate action rejected");
        check(!parse(heading,time,time,action).accepted,"duplicate timestamp rejected");
        check(!parse(heading,"打卡时间:2026-02-30 12:43",action).accepted,"invalid calendar date");
        check(!parse(heading,"打卡时间:2026-11-17 24:00",action).accepted,"invalid hour");
        check(!parse(heading,"打卡时间:2026-11-17 12:60",action).accepted,"invalid minute");
        check(!parse(heading,"打卡时间:2026-11-17 12:43:01",action).accepted,"unobserved second precision rejected");
        check(!parse(heading,time,action,"更新打卡").accepted,"other action rejected");
        check(!parse(action,time,heading).accepted,"observed modal order required");
        result=parse(heading,time,action,"private person 123456");
        check(!result.accepted && !result.diagnostics.toString().contains("private") && !result.diagnostics.toString().contains("123456"),"diagnostics omit raw lines");
        check(result.date == null && result.checkOut == null,"rejected result has no receipt values");
        check(!parse("✓下班打卡成功",time,action).accepted,"unknown prefix not stripped");
        check("12:43".equals(parse(heading,time,action).actualTime),"checkout actualTime preserves legacy time");
        String morningHeading="上班打卡成功",morningTime="打卡时间:2026-11-18 07:00";
        result=morning(morningHeading,morningTime,action);
        check(result.accepted && "2026-11-18".equals(result.date) && "07:00".equals(result.actualTime),"exact morning success");
        check(result.actualTime.equals(result.checkOut) && result.headingIndex == 0 && result.timeIndex == 1 && result.actionIndex == 2,"morning legacy alias and actual indexes");
        check(morning("上 班 打 卡 成 功","打卡时间：2026-11-18 07:00",action).accepted,"morning visible normalization");
        check(!morning(heading,morningTime,action).accepted,"morning rejects checkout heading");
        check(!morning(morningHeading,heading,morningTime,action).accepted,"mixed punch headings rejected");
        check(!morning(morningHeading,morningHeading,morningTime,action).accepted,"duplicate morning heading rejected");
        check(!morning(morningHeading,morningTime,time,action).accepted,"mixed old and current timestamp rejected");
        check(!morning(morningHeading,morningTime,action,"上班时间:07:00").accepted,"legacy receipt field rejected");
        check(!morning(morningHeading,"打卡时间:2026-11-18 07:00:01",action).accepted,"morning second precision rejected");
        check(!morning(morningHeading,"打卡时间:2026-11-18 25:00",action).accepted,"morning invalid time rejected");
        check(!morning(morningHeading,morningTime).accepted,"morning requires result action");
        check(!morning(morningHeading,morningTime,action,action).accepted,"morning duplicate action rejected");
        check(!morning(action,morningTime,morningHeading).accepted,"morning ordering required");
        check(!morning("✓上班打卡成功",morningTime,action).accepted,"morning unknown prefix rejected");
        result=morning(morningHeading,morningTime,action,"private morning 654321");
        check(!result.accepted && result.date == null && result.actualTime == null && result.checkOut == null,"rejected morning erases all receipt aliases");
        check(!result.diagnostics.toString().contains("private") && !result.diagnostics.toString().contains("654321") && !result.diagnostics.toString().contains("07:00"),"morning diagnostics omit raw content");
        System.out.println("PASS: "+checks+" checkout-success parser checks");
    }
    private static void check(boolean value,String label) { if (!value) throw new AssertionError(label); checks++; }
}
