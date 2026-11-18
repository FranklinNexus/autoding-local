package org.autoding.research;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONObject;

/** Foreground-only, bounded Android mock-location experiment; no target-app hooks. */
public final class LocationLabActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private CancellationSignal request;
    private Location anchor;
    private TextView status,point,result;
    private Button capture,start,stop;
    private PowerManager.WakeLock wake;
    private boolean running,reading,observed,stopping;
    private long endsAt,startedAt;
    private int generation;
    private final LocationListener listener=new LocationListener(){
        @Override public void onLocationChanged(Location location){
            if(!running||anchor==null)return;
            observed=location.isMock()&&LocationManager.GPS_PROVIDER.equals(location.getProvider())&&location.distanceTo(anchor)<1f;
            if(observed)result.setText("GPS 位置回调已收到固定点\n系统明确标记为模拟位置 · isMock = true");
        }
    };
    private final Runnable inject=new Runnable(){public void run(){
        if(!running)return;
        if(SystemClock.elapsedRealtime()>=endsAt){finishTest("completed");return;}
        try {
            Location fixed=new Location(LocationManager.GPS_PROVIDER);
            fixed.setLatitude(anchor.getLatitude());fixed.setLongitude(anchor.getLongitude());
            fixed.setAccuracy(Math.max(5f,anchor.getAccuracy()));fixed.setTime(System.currentTimeMillis());
            fixed.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            locations.setTestProviderLocation(LocationManager.GPS_PROVIDER,fixed);
            status.setText("固定中 · 剩余 "+Math.max(1,(endsAt-SystemClock.elapsedRealtime()+999)/1000)+" 秒\n离开此页即恢复真实定位");
            main.postDelayed(this,1000);
        }catch(Exception error){finishTest("injection-failed");}
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density);page.setPadding(pad,pad,pad,pad);scroll.addView(page);setContentView(scroll);
        TextView title=new TextView(this);title.setText("定位实验");title.setTextSize(24);page.addView(title);
        TextView info=new TextView(this);info.setText("读取手机此刻的位置，再固定 GPS 30 秒并核对系统回调。坐标仅在本页内存中保留；离开页面或倒计时结束后恢复定位。\n\nAndroid 会标记模拟位置。网络定位、Wi-Fi 和平台校验另有来源，GPS 回调通过不代表考勤接受。");page.addView(info);
        status=new TextView(this);status.setTextSize(17);page.addView(status);
        point=new TextView(this);point.setSaveEnabled(false);point.setFreezesText(false);point.setText("尚未读取当前位置");page.addView(point);
        result=new TextView(this);page.addView(result);
        add(page,"1 · 位置权限",()->requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},41));
        add(page,"2 · 选择模拟位置应用",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        capture=add(page,"3 · 读取当前位置",this::captureLocation);
        start=add(page,"4 · 固定当前地点 30 秒",()->new AlertDialog.Builder(this).setTitle("短时 GPS 固定测试").setMessage("将 GPS 暂时固定在刚读取的位置，持续 30 秒。系统中的 GPS 使用者可能收到这个模拟点。测试只核对本页回调，请保持停留在此页。").setNegativeButton("取消",null).setPositiveButton("开始测试",(d,w)->startTest()).show());
        stop=add(page,"立即停止并恢复",()->finishTest("user-stopped"));
        add(page,"返回",this::finish);
        refresh();
    }
    @Override public void onResume(){super.onResume();
        if(getSharedPreferences("location_lab",0).getBoolean("cleanupPending",false)&&!running){
            boolean cleaned=LocationLabCleanupReceiver.cleanup(this);
            result.setText(cleaned?"上次定位实验已清理":"请先恢复定位；清理尚未完成");
        }
        refresh();
    }
    @Override public void onStop(){
        cancelRead();if(running)finishTest("page-left");
        anchor=null;if(point!=null)point.setText("坐标已清除，请重新读取当前位置");
        super.onStop();
    }
    @Override public void onDestroy(){cancelRead();main.removeCallbacks(inject);super.onDestroy();}
    private Button add(LinearLayout parent,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());parent.addView(b);return b;}
    private boolean fine(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private boolean selected(){return ((AppOpsManager)getSystemService(APP_OPS_SERVICE)).checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,android.os.Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;}
    private void refresh(){if(status==null||running||reading)return;status.setText("精确位置："+(fine()?"已允许":"待允许")+"\n模拟位置应用："+(selected()?"已选择 AutoDing":"待选择 AutoDing"));capture.setEnabled(true);start.setEnabled(anchor!=null);stop.setEnabled(getSharedPreferences("location_lab",0).getBoolean("cleanupPending",false));}
    private void cancelRead(){generation++;reading=false;if(request!=null)request.cancel();request=null;}
    private void captureLocation(){
        if(running)return;
        if(!fine()){status.setText("请先允许精确位置权限");return;}
        if(!locations.isLocationEnabled()){status.setText("请先打开手机定位开关");return;}
        if(getSharedPreferences("location_lab",0).getBoolean("cleanupPending",false)){status.setText("请先恢复真实定位，再读取当前位置");return;}
        cancelRead();anchor=null;reading=true;start.setEnabled(false);capture.setEnabled(false);point.setText("正在读取真实位置，最长等待 30 秒…");
        final int token=generation;
        String provider=locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
        request=new CancellationSignal();
        try{locations.getCurrentLocation(provider,request,getMainExecutor(),value->{
            if(token!=generation)return;
            cancelRead();
            long age=value==null?Long.MAX_VALUE:SystemClock.elapsedRealtimeNanos()-value.getElapsedRealtimeNanos();
            if(value==null||value.isMock()||!value.hasAccuracy()||age<0||age>120000000000L){point.setText("尚未取得最近两分钟的真实位置，请稍后重试。");refresh();return;}
            anchor=new Location(value);point.setText(String.format(Locale.CHINA,"当前地点  %.6f, %.6f\n来源 %s · 精度约 %.0f 米",value.getLatitude(),value.getLongitude(),value.getProvider(),value.getAccuracy()));result.setText("当前位置仅存在内存，尚未固定。");refresh();
        });}catch(SecurityException error){cancelRead();point.setText("位置权限已变化，请重新允许后读取。");refresh();}
        catch(Exception error){cancelRead();point.setText("位置读取未完成，请检查权限和系统定位。");refresh();}
        main.postDelayed(()->{if(reading&&token==generation){cancelRead();point.setText("位置读取超时，请稍后重试。");refresh();}},30000);
    }
    @SuppressWarnings("deprecation") private void startTest(){
        if(running||anchor==null)return;
        if(!fine()||!selected()){status.setText("请完成精确位置和模拟应用设置。");return;}
        long age=SystemClock.elapsedRealtimeNanos()-anchor.getElapsedRealtimeNanos();
        if(!locations.isLocationEnabled()||anchor.isMock()||!anchor.hasAccuracy()||age<0||age>120000000000L){
            anchor=null;point.setText("位置已过期或定位已关闭，请重新读取当前位置。");refresh();return;
        }
        long now=System.currentTimeMillis();
        if(OneShotRunner.isActive()||!NativeTrialScheduler.manualTestAllowed(this,now,now+600000)){status.setText("打卡任务即将执行，请稍后测试定位。");return;}
        JSONObject trial=ProbeState.saved(this).optJSONObject("trial");
        if(trial!=null&&"scheduled".equals(trial.optString("status"))){status.setText("请先完成当前读取测试。");return;}
        if(getSharedPreferences("location_lab",0).getBoolean("cleanupPending",false)){status.setText("请先清理上次测试。");return;}
        observed=false;startedAt=now;
        if(!getSharedPreferences("location_lab",0).edit().putBoolean("cleanupPending",true).commit()){status.setText("测试状态保存失败，尚未开始。");return;}
        try {
            AlarmManager alarms=(AlarmManager)getSystemService(ALARM_SERVICE);
            if(!alarms.canScheduleExactAlarms())throw new IllegalStateException();
            PendingIntent cleanup=LocationLabCleanupReceiver.pending(this,PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent show=PendingIntent.getActivity(this,511,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(now+35000,show),cleanup);
            locations.addTestProvider(LocationManager.GPS_PROVIDER,false,true,false,false,true,true,true,ProviderProperties.POWER_USAGE_LOW,ProviderProperties.ACCURACY_FINE);
            locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,0,0,listener,Looper.getMainLooper());
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"AutoDing:location-lab");wake.acquire(40000);
            running=true;endsAt=SystemClock.elapsedRealtime()+30000;
            capture.setEnabled(false);start.setEnabled(false);stop.setEnabled(true);result.setText("正在等待系统 GPS 位置回调…");main.post(inject);
        }catch(SecurityException error){finishTest("permission-changed");}
        catch(Exception error){finishTest("start-failed");}
    }
    private void finishTest(String reason){
        if(stopping)return;stopping=true;running=false;main.removeCallbacks(inject);
        try{locations.removeUpdates(listener);}catch(Exception ignored){}
        boolean cleaned=LocationLabCleanupReceiver.cleanup(this);
        if(wake!=null&&wake.isHeld())wake.release();wake=null;
        JSONObject report=new JSONObject();try{report.put("outcome",reason);report.put("startedAt",startedAt);report.put("finishedAt",System.currentTimeMillis());report.put("gpsCallbackMatched",observed);report.put("isMock",observed?true:JSONObject.NULL);report.put("cleanupSucceeded",cleaned);report.put("coordinatePersisted",false);report.put("provesAttendanceAcceptance",false);}catch(Exception ignored){}
        ProbeState.put(this,"gpsLocationLab",report);
        result.setText((observed?"固定点已通过 GPS 回调核对。":"尚未核对到匹配的 GPS 回调。")+"\n"+(cleaned?"测试已停止，GPS 模拟源已移除。":"清理尚未完成，请立即恢复定位。"));
        anchor=null;point.setText("坐标已从本页清除");stopping=false;refresh();
    }
}
