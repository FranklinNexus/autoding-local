package android.app;
import android.content.*;
public class PendingIntent {
 public static final int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2,FLAG_NO_CREATE=4;
 public static PendingIntent current; public boolean cancelled;
 public static PendingIntent getBroadcast(Context c,int code,Intent i,int flags){if(current==null&&(flags&FLAG_NO_CREATE)==0)current=new PendingIntent();return current;}
 public void cancel(){cancelled=true;current=null;}
}
