package org.autoding.research;
import android.content.*;import android.app.*;
public class GpsCleanupHarness {
 static int checks;static void check(boolean b,String label){if(!b)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  Context c=new Context();check(LocationLabCleanupReceiver.cleanup(c)&&c.location.calls==0,"no pending test leaves providers untouched");
  c.prefs.pending=true;PendingIntent.current=new PendingIntent();PendingIntent p=PendingIntent.current;
  check(LocationLabCleanupReceiver.cleanup(c),"pending mock cleanup succeeds");
  check(c.location.calls==1&&"gps".equals(c.location.provider),"only GPS mock removed");
  check(!c.prefs.pending&&p.cancelled&&c.alarms.cancels==1,"successful cleanup durable and cancels fallback");
  check(LocationLabCleanupReceiver.cleanup(c)&&c.location.calls==1,"repeated cleanup idempotent");
  c=new Context();c.prefs.pending=true;c.location.fail=true;PendingIntent.current=new PendingIntent();p=PendingIntent.current;
  check(!LocationLabCleanupReceiver.cleanup(c)&&c.prefs.pending&&!p.cancelled,"removal failure retains recovery marker and alarm");
  c.location.fail=false;check(LocationLabCleanupReceiver.cleanup(c)&&!c.prefs.pending,"failed removal can be retried");
  c=new Context();c.prefs.pending=true;c.prefs.fail=true;PendingIntent.current=new PendingIntent();p=PendingIntent.current;
  check(!LocationLabCleanupReceiver.cleanup(c)&&c.prefs.pending&&!p.cancelled,"save failure does not falsely finish cleanup");
  c.prefs.fail=false;new LocationLabCleanupReceiver().onReceive(c,new Intent(c,LocationLabCleanupReceiver.class));
  check(ProbeState.clean&&!c.prefs.pending,"fallback receiver reports successful cleanup");
  System.out.println("PASS: "+checks+" GPS cleanup lifecycle checks");
 }
}
