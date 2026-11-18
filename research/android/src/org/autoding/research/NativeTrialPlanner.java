package org.autoding.research;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Bounded trial calendar, always interpreted in the office time zone. */
public final class NativeTrialPlanner {
    private NativeTrialPlanner() {}
    public static void validate(String start, String end, int morning, int floor, int duration, int margin) {
        LocalDate a=LocalDate.parse(start), b=LocalDate.parse(end);
        long days=ChronoUnit.DAYS.between(a,b);
        if(days<0 || days>1 || morning<0 || morning>=1440 || floor<0 || floor>=1440 ||
            duration<1 || duration>1440 || margin<0 || margin>120)
            throw new IllegalArgumentException("invalid-bounded-trial");
    }
    public static long morningAt(String date,int minutes) {
        if(minutes<0 || minutes>=1440) throw new IllegalArgumentException("invalid-morning");
        return LocalDate.parse(date).atStartOfDay(NativeRunPolicy.ZONE).plusMinutes(minutes).toInstant().toEpochMilli();
    }
    public static long eveningAt(String date,String checkIn,int floor,int duration,int margin) {
        return NativeRunPolicy.eveningAt(date,checkIn,floor,duration,margin);
    }
    public static boolean due(long at,long now) {return now>=at && now-at<=900000;}
}
