package org.autoding.research;
import android.content.Context;import org.json.JSONObject;
public class OneShotRunner {
 public static int starts;public static boolean accept=true,active;public static JSONObject last;
 public static boolean startScheduled(Context c,JSONObject job){starts++;last=job;active=accept;return accept;}
 public static void cancel(Context c){active=false;}
 public static boolean isActive(){return active;}
}
