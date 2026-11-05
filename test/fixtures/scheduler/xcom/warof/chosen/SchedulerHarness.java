/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;
import android.content.*;import java.util.*;import org.json.*;
public class SchedulerHarness {
static int passed,failed;
static void check(boolean ok,String msg){if(ok){passed++;System.out.println("PASS "+msg);}else{failed++;System.out.println("FAIL "+msg);}}
static void rejected(Runnable r,String msg){try{r.run();check(false,msg);}catch(IllegalArgumentException e){check(true,msg);}}
static String date(long t){return AttendancePlanner.dateKey(t,TimeZone.getDefault());}
static long local(String d,int min){return AttendancePlanner.atLocalTime(d,min,TimeZone.getDefault());}
static Context enabled(){Context c=new Context();c.prefs.edit().putString("calendar."+date(System.currentTimeMillis()),"work").commit();Scheduler.applySettings(c,540,1080,540,5,true,true);return c;}
public static void main(String[] args){
// Keep 09:18 in the past and 18:19 in the future at any CI execution time.
Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
int offsetHours = 12 - utc.get(Calendar.HOUR_OF_DAY);
String zone = String.format(Locale.ROOT, "GMT%+03d:00", offsetHours);
TimeZone.setDefault(TimeZone.getTimeZone(zone));
long now=System.currentTimeMillis();String day=date(now);long checkIn=local(day,558);
Context c=enabled();check(c.prefs.getLong("next_101",0)==0,"no confirmed check-in => no evening alarm");
long morning=c.prefs.getLong("next_100",0);Scheduler.applySettings(c,540,1080,540,5,true,true);check(c.prefs.getLong("next_100",0)==morning,"same settings retain morning slot");
check("confirmed".equals(Scheduler.confirm(c,100,day,checkIn,"minute","attendance-screen")),"09:18 confirmed");
long evening=c.prefs.getLong("next_101",0);check(evening>=local(day,1099)&&evening<=local(day,1104),"09:18 minute =>18:19..18:24");
long morning2=c.prefs.getLong("next_100",0);check("unchanged".equals(Scheduler.confirm(c,100,day,checkIn,"minute","attendance-screen")),"duplicate receipt unchanged");check(c.prefs.getLong("next_101",0)==evening&&c.prefs.getLong("next_100",0)==morning2,"duplicate keeps all slots");
rejected(()->Scheduler.confirm(c,100,day,checkIn+60000,"minute","user"),"conflicting receipt rejected");
Scheduler.applySettings(c,540,1080,600,1,true,true);check(c.prefs.getInt("duration_"+day,0)==540,"confirmed duration frozen");check(c.prefs.getLong("next_101",0)==evening,"confirmed evening slot frozen");
check(!Scheduler.claim(c,101,evening-1000),"old broadcast rejected");
Scheduler.setDayOverride(c,day,"rest");check(c.prefs.getLong("next_101",0)==evening,"rest edit retains checkout of confirmed shift");Scheduler.setDayOverride(c,day,"work");check(c.prefs.getLong("next_101",0)==evening,"work override retains same slot");
Context claim=enabled();long token=System.currentTimeMillis()-1000;claim.prefs.edit().putLong("next_100",token).putString("next_date_100",day).commit();check(Scheduler.claim(claim,100,token),"current token claimed");check(!Scheduler.claim(claim,100,token),"duplicate token rejected");claim.prefs.edit().putLong("next_100",token).putString("next_date_100",day).commit();check(!Scheduler.claim(claim,100,token),"same day duplicate blocked despite restored token");check("resumed".equals(Scheduler.resumePending(claim,100,token)),"live pending resume valid");check(claim.serviceStarts==1,"resume starts service once");check("not-pending".equals(Scheduler.resumePending(claim,100,token-1)),"resume stale token rejected");
 // A lockscreen may show our activity but still reject launching DingTalk.
 // Page acceptance must leave the exact request available for the USB companion.
 Context lockedPage=enabled();long pageToken=System.currentTimeMillis()-1000;
 lockedPage.prefs.edit().putLong("next_100",pageToken).putString("next_date_100",day).commit();
 check(Scheduler.claim(lockedPage,100,pageToken),"locked page request claimed");
 Scheduler.scheduleDaily(lockedPage,100,true);long futureMorning=lockedPage.prefs.getLong("next_100",0);
 check(Scheduler.acceptPendingPage(lockedPage,100,pageToken),"pending page accepted without acknowledging launch");
 check("page-opened".equals(lockedPage.prefs.getString("stage_"+day+"_100","")),"locked page stage recorded");
 check(lockedPage.prefs.getLong("fired_100",0)==pageToken,"page opening retains recoverable token");
 check(((JSONArray)Scheduler.status(lockedPage).get("pending")).data.size()==1,"locked page remains visible to companion recovery");
 check(!Scheduler.acknowledgeLaunch(lockedPage,100,pageToken-1),"stale launch acknowledgement leaves live request intact");
 check("resumed".equals(Scheduler.resumePending(lockedPage,100,pageToken)),"locked page recovers after companion reconnects");
 check(Scheduler.pendingLaunchValid(lockedPage,100,pageToken),"recovered request remains valid immediately before launch");
 check(Scheduler.acknowledgeLaunch(lockedPage,100,pageToken),"successful launch acknowledges exact token");
 check(lockedPage.prefs.getLong("fired_100",0)==0&&lockedPage.prefs.getString("fired_date_100","").isEmpty(),"successful launch clears recovery token and date");
 check("launch-requested".equals(lockedPage.prefs.getString("stage_"+day+"_100","")),"successful launch remains unconfirmed attendance");
 check(((JSONArray)Scheduler.status(lockedPage).get("pending")).data.isEmpty(),"acknowledged launch disappears from companion recovery");
 check("not-pending".equals(Scheduler.resumePending(lockedPage,100,pageToken)),"acknowledged launch cannot be resumed");
 check(lockedPage.prefs.getLong("attempted_"+day+"_100",0)==pageToken,"launch acknowledgement preserves daily deduplication");
 check(lockedPage.prefs.getLong("next_100",0)==futureMorning,"launch acknowledgement preserves future morning alarm");
 lockedPage.prefs.edit().putLong("next_100",pageToken).putString("next_date_100",day).commit();
 check(!Scheduler.claim(lockedPage,100,pageToken),"completed launch cannot claim the same daily request again");
 Context delayedPage=enabled();long delayedToken=System.currentTimeMillis()-1000;
 delayedPage.prefs.edit().putLong("next_100",delayedToken).putString("next_date_100",day).commit();
 check(Scheduler.claim(delayedPage,100,delayedToken)&&Scheduler.acceptPendingPage(delayedPage,100,delayedToken),"delayed page begins with a live request");
 Scheduler.applySettings(delayedPage,540,1080,540,5,true,false);
 check(!Scheduler.pendingLaunchValid(delayedPage,100,delayedToken),"disabling during wake delay prevents stale launch");
 Context timedOutPage=enabled();long expiredPageToken=System.currentTimeMillis()-121000;
 timedOutPage.prefs.edit().putLong("fired_100",expiredPageToken).putString("fired_date_100",day)
     .putLong("attempted_"+day+"_100",expiredPageToken).putString("stage_"+day+"_100","page-opened").commit();
 long nextAfterTimeout=timedOutPage.prefs.getLong("next_100",0);
 check(!Scheduler.pendingLaunchValid(timedOutPage,100,expiredPageToken),"expired page fails final launch validation");
 check(timedOutPage.prefs.getLong("fired_100",0)==0&&"missed".equals(timedOutPage.prefs.getString("stage_"+day+"_100","")),"expired page clears token and records missed stage");
 check(timedOutPage.prefs.getLong("next_100",0)==nextAfterTimeout,"expired page retains future alarm");
 Context stale=enabled();long old=System.currentTimeMillis()-121000;stale.prefs.edit().putLong("next_100",old).putString("next_date_100",day).commit();check(!Scheduler.claim(stale,100,old),"expired claim rejected");check("missed".equals(stale.prefs.getString("stage_"+day+"_100","")),"expired stage missed");check(stale.prefs.getLong("next_100",0)>System.currentTimeMillis(),"expired morning schedules future day");
Context missing=enabled();rejected(()->Scheduler.confirm(missing,101,day,checkIn,"minute","user"),"checkout requires check-in");rejected(()->Scheduler.confirm(missing,100,day,System.currentTimeMillis()+60000,"second","user"),"future record rejected");rejected(()->Scheduler.confirm(c,101,day,checkIn+3600000,"minute","user"),"short duration checkout rejected");
Context expired=enabled();Scheduler.confirm(expired,100,day,checkIn,"minute","user");long expiredSlot=System.currentTimeMillis()-1000;expired.prefs.edit().putLong("slot_"+day+"_101",expiredSlot).putLong("next_101",expiredSlot).putString("next_date_101",day).commit();Scheduler.restore(expired);check(expired.prefs.getLong("next_101",0)==0,"expired evening never silently retried");check("missed".equals(expired.prefs.getString("stage_"+day+"_101","")),"restore expired evening missed");
JSONObject s=Scheduler.status(c);check(((Number)s.get("checkIn")).longValue()==checkIn,"status carries confirmed checkin");check(s.get("pending") instanceof JSONArray,"status includes pending");
System.out.println("RESULT passed="+passed+" failed="+failed);if(failed>0)System.exit(1);
}}
