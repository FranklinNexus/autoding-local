package android.app;
import java.util.*;
public class AlarmManager {
 public boolean allowed=true,fail;public final Map<PendingIntent,Long> registered=new HashMap<>();
 public boolean canScheduleExactAlarms(){return allowed;}
 public static class AlarmClockInfo {final long at;public AlarmClockInfo(long at,PendingIntent show){this.at=at;}}
 public void setAlarmClock(AlarmClockInfo clock,PendingIntent p){if(fail)throw new IllegalStateException();registered.put(p,clock.at);}
 public void cancel(PendingIntent p){registered.remove(p);}
}
