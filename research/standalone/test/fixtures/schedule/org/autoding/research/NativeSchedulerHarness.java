package org.autoding.research;
import android.content.*;import org.json.*;import java.time.*;import java.util.*;
public class NativeSchedulerHarness {
 static int checks;static String today,tomorrow;
 static void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
 static JSONObject state(Context c){return NativeTrialScheduler.status(c);}
 static void write(Context c,JSONObject s){c.prefs.edit().putString("state",s.toString()).commit();}
 static Context configured() throws Exception {
  OneShotRunner.active=false;OneShotRunner.accept=true;Context c=new Context();
  NativeTrialScheduler.configure(c,today,tomorrow,420,1110,540,10,null,null);return c;
 }
 static JSONObject due(Context c) {
  JSONObject s=state(c),j=s.getJSONArray("jobs").getJSONObject(0);
  j.put("workDate",today).put("scheduledAt",System.currentTimeMillis()-1000);
  write(c,s);return j;
 }
 static Intent intent(Context c,JSONObject j) {
  return new Intent(c,NativeScheduleReceiver.class).putExtra("plan_id",j.getString("planId"))
    .putExtra("job_id",j.getString("id")).putExtra("scheduled_at",j.getLong("scheduledAt"));
 }
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
  today=LocalDate.now(NativeRunPolicy.ZONE).toString();tomorrow=LocalDate.now(NativeRunPolicy.ZONE).plusDays(1).toString();
  Context c=configured();JSONObject s=state(c);
  check(s.optBoolean("enabled")&&!s.optBoolean("chargingRequired"),"enabled without power condition");
  check(c.alarms.registered.size()==s.getJSONArray("jobs").length(),"all jobs armed");
  int starts=OneShotRunner.starts;JSONObject j=due(c);Intent alarm=intent(c,j);
  NativeTrialScheduler.onAlarm(c,new Intent(c,NativeScheduleReceiver.class).putExtra("job_id",j.getString("id")).putExtra("plan_id","stale").putExtra("scheduled_at",j.getLong("scheduledAt")));
  check(OneShotRunner.starts==starts,"stale plan ignored");
  NativeTrialScheduler.onAlarm(c,alarm);check(OneShotRunner.starts==starts+1,"runner starts once");
  check(NativeTrialScheduler.isJobRunning(c,j.getString("planId"),j.getString("id")),"consumed before input");
  NativeTrialScheduler.onAlarm(c,alarm);check(OneShotRunner.starts==starts+1,"duplicate intent ignored");
  JSONObject report=new JSONObject(j.toString()).put("jobId",j.getString("id")).put("outcome","morning-verified")
    .put("clickCount",0).put("before",new JSONObject().put("accepted",true).put("date",today).put("checkIn","09:37").put("capturedAt",System.currentTimeMillis()));
  OneShotRunner.active=false;NativeTrialScheduler.onRunnerFinished(c,report);s=state(c);
  check(s.getJSONArray("reports").length()==1,"evidence archived");
  JSONObject evening=s.getJSONArray("jobs").getJSONObject(s.getJSONArray("jobs").length()-1);
  check(evening.getString("kind").equals("EVENING"),"confirmed morning adds evening");
  check(evening.getLong("scheduledAt")==NativeRunPolicy.receiptAt(today,"18:47"),"evening uses actual checkin");
  NativeTrialScheduler.cancel(c);check(!state(c).optBoolean("enabled")&&c.alarms.registered.isEmpty(),"cancel removes alarms");
  NativeTrialScheduler.onAlarm(c,alarm);check(OneShotRunner.starts==starts+1,"cancelled intent ignored");
  c=configured();j=due(c);s=state(c);s.getJSONArray("jobs").getJSONObject(0).put("scheduledAt",System.currentTimeMillis()-900001);write(c,s);
  j=state(c).getJSONArray("jobs").getJSONObject(0);starts=OneShotRunner.starts;NativeTrialScheduler.onAlarm(c,intent(c,j));
  check(OneShotRunner.starts==starts&&state(c).getJSONArray("jobs").getJSONObject(0).getString("status").equals("missed"),"late job stopped");
  c=configured();j=due(c);c.prefs.fail=true;starts=OneShotRunner.starts;NativeTrialScheduler.onAlarm(c,intent(c,j));
  check(OneShotRunner.starts==starts,"failed consumption persistence blocks runner");
  c=configured();j=due(c);OneShotRunner.accept=false;NativeTrialScheduler.onAlarm(c,intent(c,j));
  check(state(c).getJSONArray("jobs").getJSONObject(0).getString("status").equals("failed"),"busy runner no replay");
  c=configured();j=due(c);NativeTrialScheduler.onAlarm(c,intent(c,j));OneShotRunner.active=false;NativeTrialScheduler.restore(c);
  check(state(c).getJSONArray("jobs").getJSONObject(0).getString("status").equals("interrupted"),"restarted running job not replayed");
  c=configured();j=due(c);c.user.unlocked=false;NativeTrialScheduler.restore(c);
  check(state(c).getJSONArray("jobs").getJSONObject(0).getString("status").equals("pending"),"credential storage requires first unlock");
  c.user.unlocked=true;NativeTrialScheduler.restore(c);
  check(state(c).getJSONArray("jobs").getJSONObject(0).getString("status").equals("missed"),"restore overdue job not replayed");
  c=configured();j=due(c);NativeTrialScheduler.onAlarm(c,intent(c,j));NativeTrialScheduler.restore(c);
  check(state(c).getJSONArray("jobs").getJSONObject(0).getString("status").equals("running"),"live run preserved by restore");
  OneShotRunner.active=false;c=configured();c.alarms.fail=true;boolean rejected=false;
  try{NativeTrialScheduler.configure(c,today,tomorrow,420,1110,540,10,null,null);}catch(Exception expected){rejected=true;}
  check(rejected&&!state(c).optBoolean("enabled"),"alarm registration failure disables plan");
  c=configured();j=due(c);NativeTrialScheduler.onAlarm(c,intent(c,j));OneShotRunner.active=false;
  int jobsBefore=state(c).getJSONArray("jobs").length();
  NativeTrialScheduler.onRunnerFinished(c,new JSONObject(j.toString()).put("jobId",j.getString("id"))
    .put("outcome","receipt-unverified").put("mutationPossible",true));
  check(state(c).getJSONArray("jobs").length()==jobsBefore,"ambiguous opening never fabricates evening or retry");
  c=configured();s=state(c);j=s.getJSONArray("jobs").getJSONObject(0);
  j.put("id",tomorrow+"-EVENING").put("kind","EVENING").put("workDate",tomorrow).put("status","running");write(c,s);
  report=new JSONObject(j.toString()).put("jobId",j.getString("id")).put("outcome","evening-deferred")
    .put("before",new JSONObject().put("accepted",true).put("date",tomorrow).put("checkIn","10:00"));
  NativeTrialScheduler.onRunnerFinished(c,report);j=state(c).getJSONArray("jobs").getJSONObject(0);
  check(j.getString("status").equals("pending")&&j.getLong("scheduledAt")==NativeRunPolicy.receiptAt(tomorrow,"19:10"),"fresh delayed checkin reschedules evening");
  check(state(c).getJSONArray("reports").length()==1&&j.optInt("deferrals")==1,"deferral retains report and bound");
  String priorPlan=state(c).getString("planId");NativeTrialScheduler.configure(c,today,tomorrow,420,1110,540,10,null,null);
  check(!c.prefs.getString("archive_"+priorPlan,"").isEmpty(),"replaced plan archived");
  c=configured();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));NativeTrialScheduler.restore(c);
  check(!state(c).optBoolean("enabled")&&c.alarms.registered.isEmpty(),"zone change cancels trial");
  System.out.println("PASS: "+checks+" scheduler lifecycle checks");
 }
}
