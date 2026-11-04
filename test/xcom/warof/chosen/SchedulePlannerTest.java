package xcom.warof.chosen;
import java.util.*;

public final class SchedulePlannerTest {
    static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    static long at(int year, int month, int day, int hour, int minute, int second) {
        Calendar c = Calendar.getInstance(ZONE); c.clear(); c.set(year, month-1, day, hour, minute, second); return c.getTimeInMillis();
    }
    static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); }
    public static void main(String[] args) {
        for (int seed=0; seed<1000; seed++) {
            long am = SchedulePlanner.next(at(2026,11,4,7,0,0), ZONE,540,true,5,true,false,new Random(seed));
            check(am >= at(2026,11,4,8,55,0) && am <= at(2026,11,4,9,0,0), "morning window");
            long pm = SchedulePlanner.next(at(2026,11,4,7,0,0), ZONE,1080,false,5,true,false,new Random(seed));
            check(pm >= at(2026,11,4,18,0,0) && pm <= at(2026,11,4,18,5,0), "evening window");
            long lastMinute = SchedulePlanner.next(at(2026,11,4,8,59,20), ZONE,540,true,5,true,false,new Random(seed));
            check(lastMinute > at(2026,11,4,8,59,20) && lastMinute <= at(2026,11,4,9,0,0), "remaining window");
            long following = SchedulePlanner.next(am, ZONE,540,true,5,true,true,new Random(seed));
            check(following >= at(2026,11,5,8,55,0) && following <= at(2026,11,5,9,0,0), "one slot per day");
            long weekend = SchedulePlanner.next(at(2026,11,6,18,6,0), ZONE,1080,false,5,true,false,new Random(seed));
            check(weekend >= at(2026,11,9,18,0,0) && weekend <= at(2026,11,9,18,5,0), "skip weekends");
            long daily = SchedulePlanner.next(at(2026,11,6,18,6,0), ZONE,1080,false,5,false,false,new Random(seed));
            check(daily >= at(2026,11,7,18,0,0) && daily <= at(2026,11,7,18,5,0), "every day");
        }
        check(SchedulePlanner.next(at(2026,11,4,7,0,0),ZONE,540,true,0,true,false,new Random()) == at(2026,11,4,9,0,0), "fixed morning");
        check(SchedulePlanner.next(at(2026,11,4,18,1,0),ZONE,1080,false,0,true,false,new Random()) == at(2026,11,5,18,0,0), "expired fixed evening");
        check(SchedulePlanner.next(at(2026,11,4,9,0,0),ZONE,540,true,0,true,false,new Random()) == at(2026,11,5,9,0,0), "no past firing");
        System.out.println("PASS: 6003 schedule checks (windows, repeats, weekends, fixed times)");
    }
}
