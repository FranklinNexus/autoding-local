package android.content;
import java.util.*;
public class Context {
 public static final String ALARM_SERVICE="alarm",USER_SERVICE="user";
 public final android.app.AlarmManager alarms=new android.app.AlarmManager();
 public final android.os.UserManager user=new android.os.UserManager();
 public final SharedPreferences prefs=new SharedPreferences();
 public Object getSystemService(String name){return ALARM_SERVICE.equals(name)?alarms:user;}
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
 public static class SharedPreferences {
  final Map<String,String> data=new HashMap<>(); public boolean fail;
  public String getString(String k,String fallback){return data.getOrDefault(k,fallback);}
  public Editor edit(){return new Editor();}
  public class Editor {final Map<String,String> updates=new HashMap<>();
   public Editor putString(String k,String v){updates.put(k,v);return this;}
   public boolean commit(){if(fail)return false;data.putAll(updates);return true;}
  }
 }
}
