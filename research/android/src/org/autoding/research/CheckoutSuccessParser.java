package org.autoding.research;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Exact evidence for the one observed checkout-success modal. No raw output. */
public final class CheckoutSuccessParser {
    private CheckoutSuccessParser() {}
    private static final Pattern TIMESTAMP=Pattern.compile("^打卡时间:(\\d{4})-(\\d{2})-(\\d{2})(\\d{2}):(\\d{2})$");
    public static final class Result {
        public final boolean accepted;
        public final String reason,date,checkOut;
        public final int headingIndex,timeIndex,actionIndex;
        public final Map<String,Object> diagnostics;
        private Result(boolean accepted,String reason,String date,String checkOut,int headingIndex,int timeIndex,int actionIndex,Map<String,Object> diagnostics) {
            this.accepted=accepted; this.reason=reason; this.date=accepted ? date : null; this.checkOut=accepted ? checkOut : null;
            this.headingIndex=headingIndex; this.timeIndex=timeIndex; this.actionIndex=actionIndex; this.diagnostics=diagnostics;
        }
    }
    static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{Z}\\p{Cf}]+", "").replace('：',':');
    }
    public static Result parse(List<String> lines) {
        int heading=0,time=0,action=0,unknown=0,otherActions=0;
        int headingAt=-1,timeAt=-1,actionAt=-1;
        String date=null,checkOut=null; boolean invalidTime=false;
        List<String> kinds=new ArrayList<>();
        for (int i=0;i<lines.size();i++) {
            String value=normalize(lines.get(i)); Matcher stamp=TIMESTAMP.matcher(value);
            String kind="unknown";
            if (value.equals("下班打卡成功")) { heading++; headingAt=i; kind="success-heading-exact"; }
            else if (value.equals("查看打卡结果")) { action++; actionAt=i; kind="view-result-exact"; }
            else if (stamp.matches()) {
                time++; timeAt=i; kind="timestamp-exact-shape";
                try {
                    LocalDate parsedDate=LocalDate.of(Integer.parseInt(stamp.group(1)),Integer.parseInt(stamp.group(2)),Integer.parseInt(stamp.group(3)));
                    LocalTime parsedTime=LocalTime.of(Integer.parseInt(stamp.group(4)),Integer.parseInt(stamp.group(5)));
                    date=parsedDate.toString(); checkOut=parsedTime.toString();
                } catch (DateTimeException | NumberFormatException exception) { invalidTime=true; }
            } else if (!value.isEmpty()) { unknown++; if (value.contains("打卡")) otherActions++; }
            else kind="empty";
            if (kinds.size() < 24) kinds.add(kind);
        }
        Map<String,Object> diagnostics=new LinkedHashMap<>();
        diagnostics.put("schema",1); diagnostics.put("lineCount",lines.size());
        diagnostics.put("successHeadingMatches",heading); diagnostics.put("timestampShapeMatches",time);
        diagnostics.put("viewResultMatches",action); diagnostics.put("invalidTimestamp",invalidTime);
        diagnostics.put("unknownLineCount",unknown); diagnostics.put("otherActionLineCount",otherActions); diagnostics.put("lineKinds",kinds);
        String reason="";
        if (lines.size() > 16) reason="success-modal-too-many-lines";
        else if (heading != 1) reason="success-modal-heading-unconfirmed";
        else if (time != 1 || invalidTime) reason="success-modal-time-unconfirmed";
        else if (action != 1) reason="success-modal-action-unconfirmed";
        else if (unknown != 0 || otherActions != 0) reason="success-modal-extra-content";
        else if (!(headingAt < timeAt && timeAt < actionAt)) reason="success-modal-order-unconfirmed";
        return new Result(reason.isEmpty(),reason,date,checkOut,headingAt,timeAt,actionAt,diagnostics);
    }
}
