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
 static JSONObject job(JSONObject s,String id){
  JSONArray jobs=s.getJSONArray("jobs");for(int i=0;i<jobs.length();i++)if(id.equals(jobs.getJSONObject(i).optString("id")))return jobs.getJSONObject(i);return null;
 }
 static JSONObject fixtureJob(JSONObject s,String date,String kind,String status,long at){
  return new JSONObject().put("id",date+"-"+kind).put("planId",s.getString("planId")).put("workDate",date)
   .put("kind",kind).put("status",status).put("scheduledAt",at).put("durationMinutes",540)
   .put("eveningFloorMinutes",1110).put("marginMinutes",10);
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
  c=configured();s=state(c);
  JSONObject receipt=new JSONObject().put("accepted",true).put("date",today).put("checkIn","09:37")
    .put("checkOut","10:15").put("capturedAt",1234L).put("source","official-card");
  s.getJSONObject("receipts").put(today,receipt);
  s.getJSONArray("reports").put(new JSONObject().put("workDate",today).put("outcome","morning-verified").put("before",receipt));
  s.getJSONArray("jobs").put(fixtureJob(s,today,"MORNING","finished",System.currentTimeMillis()-1000).put("outcome","morning-verified"));
  write(c,s);
  NativeTrialScheduler.configure(c,today,tomorrow,430,1110,540,10,null,null);s=state(c);
  check(s.getJSONObject("receipts").getJSONObject(today).getString("checkIn").equals("09:37"),"settings save retains confirmed actual checkin without reimport");
  check(s.getJSONObject("receipts").getJSONObject(today).getString("checkOut").equals("10:15")&&s.getJSONObject("receipts").getJSONObject(today).getLong("capturedAt")==1234L,"settings save preserves full receipt and original evidence time");
  check(s.getJSONArray("reports").length()==1,"latest dated report remains visible after resave");
  check(job(s,today+"-MORNING").getString("status").equals("finished"),"confirmed morning is not recreated");
  check(job(s,tomorrow+"-MORNING").getLong("scheduledAt")==NativeTrialPlanner.morningAt(tomorrow,430),"future morning uses edited time");
  check(job(s,today+"-EVENING").getLong("scheduledAt")==NativeRunPolicy.receiptAt(today,"18:47"),"early checkout test receipt still needs actual dependent checkout");
  NativeTrialScheduler.cancelChecked(c);NativeTrialScheduler.configure(c,today,tomorrow,430,1110,540,10,null,null);
  check(state(c).getJSONObject("receipts").getJSONObject(today).getString("checkIn").equals("09:37"),"toggle off/on retains actual receipt");
  c=configured();s=state(c);
  s.getJSONArray("jobs").put(fixtureJob(s,today,"EVENING","finished",System.currentTimeMillis()-1000)
    .put("outcome","evening-verified").put("finishedAt",System.currentTimeMillis()));
  s.getJSONObject("receipts").put(today,new JSONObject().put("date",today).put("checkIn","00:00").put("checkOut","00:01"));
  write(c,s);NativeTrialScheduler.configure(c,today,tomorrow,440,0,1,0,today,"00:00");s=state(c);
  check(job(s,today+"-EVENING").getString("status").equals("finished")&&job(s,today+"-EVENING").getString("outcome").equals("evening-verified"),"completed evening survives edits after due time");
  check(s.optBoolean("enabled")&&job(s,tomorrow+"-MORNING").getLong("scheduledAt")==NativeTrialPlanner.morningAt(tomorrow,440),"tomorrow remains schedulable after completed evening");
  check(s.getJSONObject("receipts").getJSONObject(today).getString("checkOut").equals("00:01"),"same checkin import does not erase completed checkout");
  String preservedPlan=s.getString("planId");rejected=false;
  try{NativeTrialScheduler.configure(c,today,tomorrow,450,0,1,0,today,"00:00:01");}catch(Exception expected){rejected=true;}
  check(rejected&&state(c).getString("planId").equals(preservedPlan),"conflicting imported receipt does not replace current plan");
  NativeTrialScheduler.configure(c,today,today,0,0,1,0,today,"00:00");s=state(c);
  check(!s.optBoolean("enabled")&&s.optString("stoppedReason").equals("bounded-trial-ended")&&c.alarms.registered.isEmpty(),"completed-only settings save remains stopped with no duplicate alarm");
  c=configured();s=state(c);
  s.getJSONArray("jobs").put(fixtureJob(s,today,"EVENING","failed",System.currentTimeMillis()-1000).put("outcome","receipt-unverified"));
  s.getJSONArray("jobs").put(fixtureJob(s,today,"MORNING","pending",System.currentTimeMillis()-1000));write(c,s);
  NativeTrialScheduler.configure(c,today,tomorrow,1439,1110,540,10,null,null);s=state(c);
  check(job(s,today+"-EVENING").getString("status").equals("failed"),"failed uncertain evening is not recreated");
  check(job(s,today+"-MORNING").getString("status").equals("missed"),"overdue morning does not move into future after edit");
  c=configured();s=state(c);j=job(s,tomorrow+"-MORNING");j.put("status","finished").put("outcome","receipt-unverified").put("mutationPossible",true);write(c,s);
  NativeTrialScheduler.configure(c,today,tomorrow,430,1110,540,10,null,null);
  check(job(state(c),tomorrow+"-MORNING").getString("outcome").equals("receipt-unverified")&&!c.alarms.registered.containsValue(NativeTrialPlanner.morningAt(tomorrow,430)),"future uncertain job stays consumed across settings edit");
  c=configured();s=state(c);
  s.getJSONObject("receipts").put(today,new JSONObject().put("date",today).put("checkIn","00:00"));write(c,s);
  NativeTrialScheduler.configure(c,today,tomorrow,420,0,1,0,null,null);s=state(c);
  if(System.currentTimeMillis()>=NativeRunPolicy.receiptAt(today,"00:01"))
   check(job(s,today+"-EVENING").getString("status").equals("missed")&&s.optBoolean("enabled"),"overdue unverified evening retained without replay while tomorrow enabled");
  c=configured();s=state(c);int armed=c.alarms.registered.size();String samePlan=s.getString("planId");
  OneShotRunner.active=true;c.prefs.fail=true;rejected=false;
  try{NativeTrialScheduler.cancelChecked(c);}catch(Exception expected){rejected=true;}
  check(rejected&&state(c).optBoolean("enabled")&&OneShotRunner.active,"failed durable stop surfaced without claiming disabled");
  check(c.alarms.registered.size()==armed&&state(c).getString("planId").equals(samePlan),"failed stop keeps coherent prior state");
  c.prefs.fail=false;NativeTrialScheduler.cancelChecked(c);
  check(!state(c).optBoolean("enabled")&&!OneShotRunner.active&&c.alarms.registered.isEmpty(),"checked stop persists then cancels runner and alarms");
  c=configured();s=state(c);s.put("jobs",new JSONArray());
  long windowNow=System.currentTimeMillis(),windowEnd=windowNow+480000;
  s.getJSONArray("jobs").put(fixtureJob(s,today,"EVENING","pending",windowEnd));write(c,s);
  check(!NativeTrialScheduler.manualTestAllowed(c,windowNow,windowEnd),"manual diagnostic blocked at execution-window boundary");
  s.getJSONArray("jobs").getJSONObject(0).put("scheduledAt",windowEnd+1);write(c,s);
  check(NativeTrialScheduler.manualTestAllowed(c,windowNow,windowEnd),"nonoverlapping diagnostic permitted");
  s.getJSONArray("jobs").getJSONObject(0).put("status","running");write(c,s);
  check(!NativeTrialScheduler.manualTestAllowed(c,windowNow,windowEnd),"running dated job blocks diagnostics regardless of timestamp");
  s.put("enabled",false);write(c,s);
  check(NativeTrialScheduler.manualTestAllowed(c,windowNow,windowEnd),"paused schedule does not block manual test");
  c=configured();TimeZone.setDefault(TimeZone.getTimeZone("UTC"));NativeTrialScheduler.restore(c);
  check(!state(c).optBoolean("enabled")&&c.alarms.registered.isEmpty(),"zone change cancels trial");
  System.out.println("PASS: "+checks+" scheduler lifecycle checks");
 }
}
