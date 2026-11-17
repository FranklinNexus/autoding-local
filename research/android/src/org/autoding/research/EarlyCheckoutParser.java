package org.autoding.research;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Exact text evidence for the one observed optional-reason early-checkout sheet. */
public final class EarlyCheckoutParser {
    private EarlyCheckoutParser() {}
    public static final class Result {
        public final boolean accepted;
        public final String reason;
        public final int buttonIndex;
        public final Map<String,Object> diagnostics;
        private Result(boolean accepted,String reason,int buttonIndex,Map<String,Object> diagnostics) {
            this.accepted=accepted; this.reason=reason; this.buttonIndex=buttonIndex; this.diagnostics=diagnostics;
        }
    }
    static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{Z}\\p{Cf}]+", "").replace('（','(').replace('）',')');
    }
    public static Result parse(List<String> lines) {
        int photo=0,notes=0,placeholder=0,button=0,unknown=0,otherAction=0;
        int photoAt=-1,notesAt=-1,placeholderAt=-1,buttonAt=-1;
        for (int i=0;i<lines.size();i++) {
            String text=normalize(lines.get(i));
            if (text.equals("拍照(选填)")) { photo++; photoAt=i; }
            else if (text.equals("备注(选填)")) { notes++; notesAt=i; }
            else if (text.equals("请填写早退原因")) { placeholder++; placeholderAt=i; }
            else if (text.equals("早退打卡")) { button++; buttonAt=i; }
            else if (!text.isEmpty()) { unknown++; if (text.contains("打卡")) otherAction++; }
        }
        Map<String,Object> diagnostics=new LinkedHashMap<>();
        diagnostics.put("schema",1); diagnostics.put("lineCount",lines.size());
        diagnostics.put("photoOptionalMatches",photo); diagnostics.put("notesOptionalMatches",notes);
        diagnostics.put("emptyReasonPlaceholderMatches",placeholder); diagnostics.put("earlyCheckoutMatches",button);
        diagnostics.put("otherActionLineCount",otherAction); diagnostics.put("unknownLineCount",unknown);
        String reason="";
        if (lines.size() > 32) reason="early-sheet-too-many-lines";
        else if (photo != 1 || notes != 1 || placeholder != 1) reason="early-sheet-context-unconfirmed";
        else if (button != 1) reason="early-sheet-button-unconfirmed";
        else if (otherAction != 0) reason="early-sheet-other-action-present";
        else if (!(photoAt < notesAt && notesAt < placeholderAt && placeholderAt < buttonAt)) reason="early-sheet-order-unconfirmed";
        diagnostics.put("contextOrderMatches",reason.isEmpty());
        return new Result(reason.isEmpty(),reason,reason.isEmpty() ? buttonAt : -1,diagnostics);
    }

    /** Four isolated observed regions. Concatenation removes line breaks only. */
    public static Result parseBands(List<List<String>> bands) {
        String[] expected={"拍照(选填)","备注(选填)","请填写早退原因","早退打卡"};
        String[] names={"photo","notes","placeholder","button"};
        Map<String,Object> diagnostics=new LinkedHashMap<>();
        diagnostics.put("schema",3); diagnostics.put("regionCount",bands.size());
        diagnostics.put("placeholderRequired",false);
        String reason=bands.size() == 4 ? "" : "early-sheet-region-count-unconfirmed";
        for (int i=0;i<Math.min(bands.size(),4);i++) {
            List<String> lines=bands.get(i); StringBuilder joined=new StringBuilder();
            List<String> kinds=new ArrayList<>(); int exact=0, conflicts=0;
            for (String raw:lines) {
                String value=normalize(raw); joined.append(value);
                if (value.equals(expected[i])) { exact++; kinds.add("expected-exact"); }
                else if (!value.isEmpty() && expected[i].contains(value)) kinds.add("expected-fragment");
                else kinds.add("unknown");
                if (i == 2 && (value.contains("打卡") || value.contains("必填") || value.contains("必选"))) conflicts++;
            }
            boolean matches=joined.toString().equals(expected[i]);
            boolean conflict=conflicts != 0 || (i == 2 && (joined.indexOf("打卡") >= 0 ||
                joined.indexOf("必填") >= 0 || joined.indexOf("必选") >= 0));
            Map<String,Object> region=new LinkedHashMap<>();
            region.put("lineCount",lines.size()); region.put("exactLineMatches",exact);
            region.put("fullLabelExact",matches); region.put("normalizedCharacterCount",joined.length());
            region.put("containsExpectedLabel",joined.indexOf(expected[i]) >= 0);
            region.put("expectedSubsequence",isSubsequence(expected[i],joined));
            region.put("conflictLineCount",conflicts);
            region.put("conflictPresent",conflict);
            region.put("lineKinds",kinds); diagnostics.put(names[i],region);
            if (reason.isEmpty()) {
                if (i == 2 && conflict) reason="early-sheet-placeholder-conflict";
                else if (i != 2 && (!matches || (i == 3 && (lines.size() != 1 || exact != 1))))
                    reason="early-sheet-"+names[i]+"-label-unconfirmed";
            }
        }
        return new Result(reason.isEmpty(),reason,reason.isEmpty() ? 3 : -1,diagnostics);
    }
    private static boolean isSubsequence(String expected,CharSequence observed) {
        int next=0;
        for (int i=0;i<observed.length() && next<expected.length();i++)
            if (observed.charAt(i) == expected.charAt(next)) next++;
        return next == expected.length();
    }
}
