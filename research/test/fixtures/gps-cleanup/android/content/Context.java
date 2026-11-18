package android.content;
public class Context {
 public static final String LOCATION_SERVICE="location",ALARM_SERVICE="alarm";
 public final android.location.LocationManager location=new android.location.LocationManager();
 public final android.app.AlarmManager alarms=new android.app.AlarmManager();
 public final Prefs prefs=new Prefs();
 public Object getSystemService(String name){return LOCATION_SERVICE.equals(name)?location:alarms;}
 public Prefs getSharedPreferences(String name,int mode){return prefs;}
 public static class Prefs {
  public boolean pending,fail;
  public boolean getBoolean(String k,boolean fallback){return pending;}
  public Editor edit(){return new Editor();}
  public class Editor {boolean value;public Editor putBoolean(String k,boolean v){value=v;return this;}public boolean commit(){if(fail)return false;pending=value;return true;}}
 }
}
