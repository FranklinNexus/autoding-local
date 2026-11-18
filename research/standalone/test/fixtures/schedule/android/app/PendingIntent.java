package android.app;
import android.content.*;import java.util.*;
public class PendingIntent {
 public static final int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2,FLAG_NO_CREATE=4;
 static final Map<String,PendingIntent> entries=new HashMap<>();public Intent intent;public String key;
 public static PendingIntent getBroadcast(Context c,int code,Intent i,int flags){
  String k=System.identityHashCode(c)+":"+i.data;PendingIntent p=entries.get(k);
  if(p==null && (flags&FLAG_NO_CREATE)!=0)return null;
  if(p==null){p=new PendingIntent();p.key=k;entries.put(k,p);}p.intent=i;return p;
 }
 public static PendingIntent getActivity(Context c,int code,Intent i,int flags){return new PendingIntent();}
 public void cancel(){entries.remove(key);}
}
