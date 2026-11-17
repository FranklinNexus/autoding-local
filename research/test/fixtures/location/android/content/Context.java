package android.content;
import android.app.AppOpsManager;
import android.location.LocationManager;
public class Context {
    public static final String APP_OPS_SERVICE = "appops", LOCATION_SERVICE = "location";
    public final AppOpsManager operations = new AppOpsManager();
    public LocationManager locations = new LocationManager();
    public int permission = 0;
    public Object getSystemService(String name) { return APP_OPS_SERVICE.equals(name) ? operations : locations; }
    public int checkSelfPermission(String name) { return permission; }
    public String getPackageName() { return "org.autoding.research"; }
}
