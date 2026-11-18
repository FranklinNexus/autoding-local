package org.autoding.research;

public final class NativeRunPolicyHarness {
    private static int checks;
    private static final String DAY = "2026-11-18";
    private static long at(String value) { return NativeRunPolicy.receiptAt(DAY, value); }
    private static void check(boolean value) { if (!value) throw new AssertionError("Check " + (checks+1)); checks++; }
    public static void main(String[] args) {
        check(NativeRunPolicy.eveningAt(DAY,"09:25",1110,540,10)==at("18:35"));
        check(NativeRunPolicy.eveningAt(DAY,"07:00",1110,540,10)==at("18:30"));
        check(NativeRunPolicy.eveningAt(DAY,"09:25:42",1110,540,10)==at("18:35:42"));
        check(NativeRunPolicy.currentReceipt(DAY,DAY,"07:00",at("07:01"),at("07:01:05")));
        check(!NativeRunPolicy.currentReceipt(DAY,"2026-11-17","07:00",at("07:01"),at("07:01:05")));
        check(!NativeRunPolicy.currentReceipt(DAY,DAY,"07:00",at("07:01"),at("07:01:16")));
        check(!NativeRunPolicy.currentReceipt(DAY,DAY,"07:02",at("07:01"),at("07:01:05")));
        check(!NativeRunPolicy.currentReceipt(DAY,DAY,null,at("07:01"),at("07:01:05")));
        check(!NativeRunPolicy.currentReceipt(DAY,DAY,"07:00",at("07:01:06"),at("07:01:05")));
        check(NativeRunPolicy.newReceipt(DAY,DAY,"18:35",null,at("18:35:10"),at("18:35:15"),at("18:35:20")));
        check(!NativeRunPolicy.newReceipt(DAY,DAY,"18:34",null,at("18:35:10"),at("18:35:15"),at("18:35:20")));
        check(!NativeRunPolicy.newReceipt(DAY,DAY,"18:35","18:35",at("18:35:10"),at("18:35:15"),at("18:35:20")));
        check(!NativeRunPolicy.newReceipt(DAY,DAY,"18:35",null,at("18:35:10"),at("18:35:05"),at("18:35:20")));
        boolean rejected=false;
        try { NativeRunPolicy.eveningAt(DAY,"18:00",1110,540,10); } catch(IllegalArgumentException expected){rejected=true;}
        check(rejected);
        NativeTrialPlanner.validate(DAY,"2026-11-19",420,1110,540,10);checks++;
        rejected=false;
        try{NativeTrialPlanner.validate(DAY,"2026-11-20",420,1110,540,10);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected);
        check(NativeTrialPlanner.morningAt(DAY,420)==at("07:00"));
        check(NativeTrialPlanner.due(at("07:00"),at("07:15")));
        check(!NativeTrialPlanner.due(at("07:00"),at("07:15:01")));
        check(!NativeTrialPlanner.due(at("07:00"),at("06:59:59")));
        check(ReceiptParser.exactUnpunched("上班09:00\n未打卡","上班"));
        check(ReceiptParser.exactUnpunched("下班18:37\n未打卡","下班"));
        check(!ReceiptParser.exactUnpunched("下班18:37\n打卡","下班"));
        check(!ReceiptParser.exactUnpunched("下班18:37\n未打卡\n18:37已打卡","下班"));
        check(!ReceiptParser.exactUnpunched("上班09:00\n09:37已打卡","上班"));
        System.out.println("PASS: " + checks + " native runner policy checks");
    }
}
