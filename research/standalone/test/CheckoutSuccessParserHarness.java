package org.autoding.research;

import java.util.Arrays;

public final class CheckoutSuccessParserHarness {
    private static int checks;
    private static CheckoutSuccessParser.Result parse(String... lines) { return CheckoutSuccessParser.parse(Arrays.asList(lines)); }
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
        System.out.println("PASS: "+checks+" checkout-success parser checks");
    }
    private static void check(boolean value,String label) { if (!value) throw new AssertionError(label); checks++; }
}
