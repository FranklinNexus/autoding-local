package org.autoding.research;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.os.UserManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Two-day phone-only trial. No automatic retries after uncertain attendance input. */
public final class NativeTrialScheduler {
    private static final String PREF="native_trial_schedule", KEY="state";
    private NativeTrialScheduler() {}
    private static JSONObject read(Context c) {
        try {return new JSONObject(c.getSharedPreferences(PREF,0).getString(KEY,"{}"));}
        catch(Exception error){return new JSONObject();}
    }
    private static void save(Context c,JSONObject s) throws Exception {
        s.put("updatedAt",System.currentTimeMillis());
        if(!c.getSharedPreferences(PREF,0).edit().putString(KEY,s.toString()).commit())
            throw new IllegalStateException("schedule-save-failed");
    }
    public static synchronized JSONObject status(Context c) {return read(c);}
    public static synchronized boolean isJobRunning(Context c,String plan,String id) {
        JSONObject s=read(c), j=find(s,id);
        return s.optBoolean("enabled") && plan.equals(s.optString("planId")) && j!=null && "running".equals(j.optString("status"));
    }
    public static synchronized void configure(Context c,String start,String end,int morning,int floor,int duration,int margin,
                                               String verifiedDate,String verifiedCheckIn) throws Exception {
        NativeTrialPlanner.validate(start,end,morning,floor,duration,margin);
        long now=System.currentTimeMillis();
        LocalDate today=LocalDate.now(NativeRunPolicy.ZONE);
        if(LocalDate.parse(start).isBefore(today) || LocalDate.parse(start).isAfter(today.plusDays(1)))
            throw new IllegalArgumentException("trial-start-not-current");
        if(!NativeRunPolicy.ZONE.equals(ZoneId.systemDefault())) throw new IllegalStateException("office-zone-required");
        AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(!alarms.canScheduleExactAlarms()) throw new IllegalStateException("exact-alarm-required");
        JSONObject old=read(c);
        JSONArray oldJobs=old.optJSONArray("jobs");
        if(oldJobs!=null) for(int i=0;i<oldJobs.length();i++)
            if("running".equals(oldJobs.getJSONObject(i).optString("status"))) throw new IllegalStateException("job-running");
        JSONObject s=new JSONObject();
        s.put("planId",UUID.randomUUID().toString()); s.put("enabled",true); s.put("startDate",start); s.put("endDate",end);
        s.put("zone","Asia/Shanghai"); s.put("morningMinutes",morning); s.put("eveningFloorMinutes",floor);
        s.put("durationMinutes",duration); s.put("marginMinutes",margin); s.put("chargingRequired",false);
        s.put("jobs",new JSONArray()); s.put("receipts",new JSONObject()); s.put("reports",new JSONArray());
        for(LocalDate date=LocalDate.parse(start);!date.isAfter(LocalDate.parse(end));date=date.plusDays(1)) {
            long at=NativeTrialPlanner.morningAt(date.toString(),morning);
            if(at>now) add(s,date.toString(),"MORNING",at);
        }
        if(verifiedDate!=null || verifiedCheckIn!=null) {
            if(verifiedDate==null || verifiedCheckIn==null || !verifiedDate.equals(today.toString()) ||
                verifiedDate.compareTo(start)<0 || verifiedDate.compareTo(end)>0 || NativeRunPolicy.receiptAt(verifiedDate,verifiedCheckIn)>now)
                throw new IllegalArgumentException("verified-receipt-invalid");
            JSONObject receipt=new JSONObject();receipt.put("date",verifiedDate);receipt.put("checkIn",verifiedCheckIn);
            receipt.put("source","setup-verified-official-card");receipt.put("capturedAt",now);
            s.getJSONObject("receipts").put(verifiedDate,receipt);
            long at=evening(s,verifiedDate,verifiedCheckIn);
            if(at<=now) throw new IllegalArgumentException("evening-already-due");
            add(s,verifiedDate,"EVENING",at);
        }
        if(s.getJSONArray("jobs").length()==0)throw new IllegalArgumentException("no-future-jobs");
        if(old.has("planId") && !c.getSharedPreferences(PREF,0).edit()
            .putString("archive_"+old.getString("planId"),old.toString()).commit())
            throw new IllegalStateException("archive-save-failed");
        cancelAlarms(c,old);
        old.put("enabled",false); old.put("stoppedReason","replaced"); save(c,old);
        OneShotRunner.cancel(c);
        save(c,s);
        try {armPending(c,s);} catch(Exception error){stopFailed(c,s,"alarm-registration-failed");throw error;}
    }
    private static long evening(JSONObject s,String date,String checkIn) throws Exception {
        return NativeTrialPlanner.eveningAt(date,checkIn,s.getInt("eveningFloorMinutes"),s.getInt("durationMinutes"),s.getInt("marginMinutes"));
    }
    private static void add(JSONObject s,String date,String kind,long at) throws Exception {
        JSONObject j=new JSONObject();
        j.put("id",date+"-"+kind);j.put("planId",s.getString("planId"));j.put("kind",kind);j.put("workDate",date);
        j.put("scheduledAt",at);j.put("scheduledElapsed",SystemClock.elapsedRealtime()+at-System.currentTimeMillis());
        j.put("status","pending");j.put("eveningFloorMinutes",s.getInt("eveningFloorMinutes"));
        j.put("durationMinutes",s.getInt("durationMinutes"));j.put("marginMinutes",s.getInt("marginMinutes"));
        s.getJSONArray("jobs").put(j);
    }
    private static JSONObject find(JSONObject s,String id) {
        if(id==null)return null;
        JSONArray jobs=s.optJSONArray("jobs");if(jobs==null)return null;
        for(int i=0;i<jobs.length();i++){JSONObject j=jobs.optJSONObject(i);if(j!=null && id.equals(j.optString("id")))return j;}
        return null;
    }
    private static PendingIntent pending(Context c,JSONObject j,int flags) {
        Intent i=new Intent(c,NativeScheduleReceiver.class).setAction("org.autoding.research.NATIVE_TRIAL")
            .setData(Uri.parse("autoding-trial://"+j.optString("planId")+"/"+j.optString("id")))
            .putExtra("plan_id",j.optString("planId")).putExtra("job_id",j.optString("id")).putExtra("scheduled_at",j.optLong("scheduledAt"));
        return PendingIntent.getBroadcast(c,0,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void arm(Context c,JSONObject j) {
        AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(!alarms.canScheduleExactAlarms())throw new IllegalStateException("exact-alarm-required");
        PendingIntent show=PendingIntent.getActivity(c,303,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(j.optLong("scheduledAt"),show),pending(c,j,PendingIntent.FLAG_UPDATE_CURRENT));
    }
    private static void armPending(Context c,JSONObject s) throws Exception {
        JSONArray jobs=s.getJSONArray("jobs");
        for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if("pending".equals(j.optString("status")))arm(c,j);}
    }
    private static void cancelAlarms(Context c,JSONObject s) {
        JSONArray jobs=s.optJSONArray("jobs");if(jobs==null)return;
        AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        for(int i=0;i<jobs.length();i++){JSONObject j=jobs.optJSONObject(i);if(j==null)continue;
            PendingIntent p=pending(c,j,PendingIntent.FLAG_NO_CREATE);if(p!=null){alarms.cancel(p);p.cancel();}}
    }
    private static void stopFailed(Context c,JSONObject s,String reason) {
        cancelAlarms(c,s);
        try{s.put("enabled",false);s.put("stoppedReason",reason);save(c,s);}catch(Exception ignored){}
    }
    public static synchronized void cancel(Context c) {
        JSONObject s=read(c);cancelAlarms(c,s);
        try{s.put("enabled",false);s.put("stoppedReason","user-stopped");save(c,s);}catch(Exception ignored){}
    }
    public static synchronized void onAlarm(Context c,Intent intent) {
        JSONObject s=read(c),j=find(s,intent.getStringExtra("job_id"));
        if(!s.optBoolean("enabled") || j==null || !s.optString("planId").equals(intent.getStringExtra("plan_id")) ||
            !"pending".equals(j.optString("status")) || j.optLong("scheduledAt")!=intent.getLongExtra("scheduled_at",0))return;
        try {
            long now=System.currentTimeMillis();
            if(!NativeRunPolicy.ZONE.equals(ZoneId.systemDefault()) || !NativeTrialPlanner.due(j.getLong("scheduledAt"),now) ||
                !j.getString("workDate").equals(LocalDate.now(NativeRunPolicy.ZONE).toString())){
                j.put("status","missed");j.put("outcome","date-zone-or-lateness-rejected");save(c,s);return;
            }
            j.put("status","running");j.put("startedAt",now);save(c,s);
            if(!OneShotRunner.startScheduled(c,new JSONObject(j.toString()))){
                j.put("status","failed");j.put("outcome","runner-busy-or-save-failed");save(c,s);
            }
        } catch(Exception error){stopFailed(c,s,"alarm-dispatch-failed");}
    }
    public static synchronized void onRunnerFinished(Context c,JSONObject evidence) {
        JSONObject s=read(c),j=find(s,evidence.optString("jobId"));
        if(j==null || !s.optString("planId").equals(evidence.optString("planId")))return;
        try {
            JSONArray reports=s.getJSONArray("reports");reports.put(new JSONObject(evidence.toString()));
            String outcome=evidence.optString("outcome");j.put("status","finished");j.put("outcome",outcome);
            j.put("finishedAt",System.currentTimeMillis());
            JSONObject r=evidence.optJSONObject("after");if(r==null || !r.optBoolean("accepted"))r=evidence.optJSONObject("before");
            boolean verified=outcome.equals("morning-verified") || outcome.equals("evening-verified") || outcome.equals("evening-deferred");
            if(verified && r!=null && r.optBoolean("accepted") && j.getString("workDate").equals(r.optString("date")) &&
                !r.isNull("checkIn")) {
                String date=r.getString("date");
                s.getJSONObject("receipts").put(date,new JSONObject(r.toString()));
                if(s.optBoolean("enabled") && "MORNING".equals(j.optString("kind"))) {
                    if(find(s,date+"-EVENING")==null) add(s,date,"EVENING",evening(s,date,r.getString("checkIn")));
                } else if(s.optBoolean("enabled") && outcome.equals("evening-deferred")) {
                    long at=evening(s,date,r.getString("checkIn"));
                    if(at<=System.currentTimeMillis() || j.optInt("deferrals")>=2)throw new IllegalStateException("invalid-deferral");
                    j.put("scheduledAt",at);j.put("scheduledElapsed",SystemClock.elapsedRealtime()+at-System.currentTimeMillis());
                    j.put("deferrals",j.optInt("deferrals")+1);j.put("status","pending");
                }
            }
            boolean pending=false;
            JSONArray jobs=s.getJSONArray("jobs");
            for(int i=0;i<jobs.length();i++) {
                String status=jobs.getJSONObject(i).optString("status");
                pending |= status.equals("pending") || status.equals("running");
            }
            if(!pending){s.put("enabled",false);s.put("stoppedReason","bounded-trial-ended");}
            save(c,s);if(s.optBoolean("enabled"))armPending(c,s);
        } catch(Exception error){stopFailed(c,s,"completion-save-or-plan-failed");}
    }
    public static synchronized void restore(Context c) {
        if(!((UserManager)c.getSystemService(Context.USER_SERVICE)).isUserUnlocked())return;
        JSONObject s=read(c);if(!s.optBoolean("enabled"))return;
        try {
            if(!NativeRunPolicy.ZONE.equals(ZoneId.systemDefault())){stopFailed(c,s,"office-zone-changed");OneShotRunner.cancel(c);return;}
            if(OneShotRunner.isActive())return;
            long now=System.currentTimeMillis();JSONArray jobs=s.getJSONArray("jobs");boolean future=false;
            for(int i=0;i<jobs.length();i++) {
                JSONObject j=jobs.getJSONObject(i);String status=j.optString("status");
                if(status.equals("running")){j.put("status","interrupted");j.put("outcome","interrupted-not-replayed");}
                else if(status.equals("pending")) {
                    if(j.getLong("scheduledAt")<=now){j.put("status","missed");j.put("outcome","overdue-not-replayed");}
                    else {future=true;j.put("scheduledElapsed",SystemClock.elapsedRealtime()+j.getLong("scheduledAt")-now);}
                }
            }
            if(!future){s.put("enabled",false);s.put("stoppedReason","bounded-trial-ended");}
            save(c,s);cancelAlarms(c,s);if(future)armPending(c,s);
        }catch(Exception error){stopFailed(c,s,"restore-failed");}
    }
}
