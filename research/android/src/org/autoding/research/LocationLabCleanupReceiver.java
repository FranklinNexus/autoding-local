package org.autoding.research;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;

/** Independent bounded cleanup alarm; only removes this experiment's GPS override. */
public final class LocationLabCleanupReceiver extends BroadcastReceiver {
    static PendingIntent pending(Context c,int flags){return PendingIntent.getBroadcast(c,510,new Intent(c,LocationLabCleanupReceiver.class),flags|PendingIntent.FLAG_IMMUTABLE);}
    static synchronized boolean cleanup(Context c){
        if(!c.getSharedPreferences("location_lab",0).getBoolean("cleanupPending",false))return true;
        try{
            ((LocationManager)c.getSystemService(Context.LOCATION_SERVICE)).removeTestProvider(LocationManager.GPS_PROVIDER);
            if(!c.getSharedPreferences("location_lab",0).edit().putBoolean("cleanupPending",false).commit())return false;
            PendingIntent p=pending(c,PendingIntent.FLAG_NO_CREATE);
            if(p!=null){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(p);p.cancel();}
            return true;
        }catch(Exception ignored){return false;}
    }
    @Override public void onReceive(Context c,Intent i){boolean clean=cleanup(c);ProbeState.putValues(c,"gpsLabCleanupSucceeded",clean,"gpsLabCleanupAt",System.currentTimeMillis());}
}
