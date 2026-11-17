package org.autoding.research;

import java.util.Arrays;
import java.util.List;

public final class EarlyCheckoutParserHarness {
    private static int checks;
    private static EarlyCheckoutParser.Result parse(String... lines) { return EarlyCheckoutParser.parse(Arrays.asList(lines)); }
    public static void main(String[] arguments) {
        String photo="拍照（选填）",note="备注（选填）",placeholder="请填写早退原因",button="早退打卡";
        EarlyCheckoutParser.Result result=parse(photo,note,placeholder,"AutoDing · 等待签到确认",button);
        check(result.accepted && result.buttonIndex == 4,"observed sheet including overlay");
        check(parse("拍照 ( 选填 )","备注（ 选填 ）",placeholder,"早 退 打 卡").accepted,"whitespace and parenthesis normalization");
        check(!parse(note,placeholder,button).accepted,"photo context required");
        check(!parse(photo,placeholder,button).accepted,"optional notes context required");
        check(!parse(photo,note,"already filled private reason",button).accepted,"empty reason placeholder required");
        check(!parse(photo,note,placeholder,button,button).accepted,"duplicate button rejected");
        check(!parse(photo,note,placeholder,"确认早退打卡").accepted,"button substring rejected");
        check(!parse(photo,note,placeholder,"正常打卡").accepted,"other popup rejected");
        check(!parse(photo,note,placeholder,button,"更新打卡").accepted,"additional action rejected");
        check(!parse(note,photo,placeholder,button).accepted,"observed context order required");
        check(!parse(photo,note,placeholder,photo,button).accepted,"duplicate context rejected");
        result=parse(photo,note,"private reason 12345",button);
        check(!result.diagnostics.toString().contains("private") && !result.diagnostics.toString().contains("12345"),"diagnostics omit raw reason");
        check(bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(placeholder),Arrays.asList(button)).accepted,"four exact isolated labels");
        check(bands(Arrays.asList("拍照","（选填）"),Arrays.asList("备注","(选填)"),Arrays.asList("请填写","早退原因"),Arrays.asList(button)).accepted,"exact context across line breaks");
        check(!bands(Arrays.asList(photo),Arrays.asList("备注(必填)"),Arrays.asList(placeholder),Arrays.asList(button)).accepted,"spelling never guessed");
        check(bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList("已填写个人原因"),Arrays.asList(button)).accepted,"optional reason is diagnostic only");
        check(bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList("低对比识别差异"),Arrays.asList(button)).accepted,"optional placeholder spelling not used as gate");
        check(bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(),Arrays.asList(button)).accepted,"missing optional placeholder allowed");
        check(!bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList("早退原因必填"),Arrays.asList(button)).accepted,"explicit required reason rejected");
        check(!bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList("更新打卡"),Arrays.asList(button)).accepted,"conflicting placeholder action rejected");
        check(!bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList("必","填"),Arrays.asList(button)).accepted,"split required conflict rejected");
        check(!bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(placeholder),Arrays.asList("早退打打卡")).accepted,"button OCR spelling never corrected");
        check(bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(placeholder),Arrays.asList("早退\u200B打卡")).accepted,"invisible Unicode format control removed");
        result=bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(placeholder),Arrays.asList("早退打打卡"));
        check(!result.accepted && result.diagnostics.toString().contains("expectedSubsequence=true"),"subsequence diagnostic never accepts noisy button");
        check(!bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(placeholder),Arrays.asList(button,button)).accepted,"isolated duplicate button rejected");
        check(!bands(Arrays.asList(photo),Arrays.asList(note),Arrays.asList(placeholder),Arrays.asList("早退","打卡")).accepted,"button requires single exact OCR line");
        result=bands(Arrays.asList(photo),Arrays.asList("private 12345"),Arrays.asList(placeholder),Arrays.asList(button));
        check(result.reason.equals("early-sheet-notes-label-unconfirmed"),"band failure precise");
        check(!result.diagnostics.toString().contains("private") && !result.diagnostics.toString().contains("12345"),"band diagnostics sanitized");
        System.out.println("PASS: "+checks+" early-checkout sheet parser checks");
    }
    private static void check(boolean value,String label) { if (!value) throw new AssertionError(label); checks++; }
    @SafeVarargs private static EarlyCheckoutParser.Result bands(List<String>... bands) { return EarlyCheckoutParser.parseBands(Arrays.asList(bands)); }
}
