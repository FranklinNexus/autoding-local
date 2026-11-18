package org.autoding.research;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Phone-first controls. Opening this page never changes an attendance plan. */
public final class MainActivity extends Activity {
    private static final int INK=0xff172b35, MUTED=0xff637780, GREEN=0xff087e74, BG=0xfff3f6f6;
    private final Handler handler=new Handler();
    private TextView stateTitle,nextTime,nextDetail,dateWindow,todayReceipt,ruleSummary,testState,readiness;
    private TextView datedRecords,manualRecord,livePower;
    private Switch enabled;
    private Button testButton,cancelTest,moreButton;
    private LinearLayout moreContent;
    private boolean moreExpanded,manualBusy;
    private boolean refreshing;
    private AlertDialog testCountdownDialog;
    private String countdownTrialId;
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout page=column();page.setPadding(dp(20),dp(20),dp(20),dp(28));scroll.addView(page);setContentView(scroll);
        page.addView(text("AutoDing",29,INK,true));space(page,4);page.addView(text("手机独立运行 · 试用版",13,MUTED,false));
        buildHome(page);buildRecords(page);buildMore(page);
        setMoreExpanded(saved!=null&&saved.getBoolean("more_expanded",false));
        space(page,16);page.addView(text("以钉钉回执为准。试用仅在所示日期执行，到期自动停止。",12,MUTED,false));
        refresh();
        if(getIntent().getBooleanExtra("pin_setup",false))startActivity(new Intent(this,PinSetupActivity.class));
    }
    private void buildHome(LinearLayout page){
        LinearLayout hero=card(page,0xffe3f2ee),row=row();hero.addView(row);
        stateTitle=text("读取状态…",17,GREEN,true);row.addView(stateTitle,new LinearLayout.LayoutParams(0,-2,1));
        enabled=new Switch(this);enabled.setText("定时");enabled.setTextColor(GREEN);enabled.setThumbTintList(ColorStateList.valueOf(GREEN));
        enabled.setContentDescription("开启或关闭定时打卡");enabled.setMinHeight(dp(48));row.addView(enabled);
        enabled.setOnCheckedChangeListener((v,on)->{if(!refreshing){refresh();if(on)editSchedule();else stopSchedule();}});
        space(hero,12);hero.addView(text("下一次执行",12,MUTED,false));
        nextTime=text("—",40,INK,true);hero.addView(nextTime);nextDetail=text("",14,INK,false);hero.addView(nextDetail);
        dateWindow=text("",12,MUTED,false);space(hero,14);hero.addView(dateWindow);
        LinearLayout rules=card(page,Color.WHITE);rules.addView(text("打卡时间",18,INK,true));
        ruleSummary=text("",14,MUTED,false);space(rules,10);rules.addView(ruleSummary);
        todayReceipt=text("",15,INK,false);space(rules,14);rules.addView(todayReceipt);
        button(rules,"调整时间",false,this::editSchedule);
    }
    private void buildRecords(LinearLayout page){
        LinearLayout result=card(page,Color.WHITE);result.addView(text("最近结果",18,INK,true));
        space(result,12);result.addView(text("定时任务",13,MUTED,true));
        datedRecords=text("",14,INK,false);space(result,6);result.addView(datedRecords);
        space(result,16);result.addView(text("独立测试",13,MUTED,true));
        manualRecord=text("",14,INK,false);space(result,6);result.addView(manualRecord);
    }
    private void buildMore(LinearLayout page){
        moreButton=button(page,"更多设置与测试  ▾",false,()->{
            if(manualBusy&&moreExpanded){notice("当前测试的倒计时和取消按钮保留显示。");return;}
            setMoreExpanded(!moreExpanded);
        });
        moreContent=column();page.addView(moreContent,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout test=card(moreContent,Color.WHITE);test.addView(text("测试手机独立运行",18,INK,true));
        space(test,8);test.addView(text("启动倒计时后拔线、锁屏。支持电池或充电头。",13,MUTED,false));
        testState=text("",14,INK,false);space(test,12);test.addView(testState);
        testButton=button(test,"准备测试",true,this::prepareTest);cancelTest=button(test,"取消这次测试",false,this::cancelManualTest);
        LinearLayout conditions=card(moreContent,Color.WHITE);conditions.addView(text("运行条件",18,INK,true));
        readiness=text("",14,MUTED,false);space(conditions,10);conditions.addView(readiness);
        livePower=text("",13,MUTED,false);space(conditions,10);conditions.addView(livePower);
        button(conditions,"权限与解锁设置",false,this::showSettings);
        button(moreContent,"诊断与实验",false,()->startActivity(new Intent(this,DiagnosticsActivity.class)));
    }
    private void setMoreExpanded(boolean expanded){
        moreExpanded=expanded;
        if(moreContent!=null)moreContent.setVisibility(expanded?View.VISIBLE:View.GONE);
        if(moreButton!=null){label(moreButton,"更多设置与测试  "+(expanded?"▴":"▾"));moreButton.setContentDescription("更多设置与测试，"+(expanded?"已展开":"已折叠"));}
    }
    @Override public void onResume(){super.onResume();rememberManual();dismissStaleTestDialog();handler.removeCallbacks(tick);handler.post(tick);}
    @Override public void onPause(){handler.removeCallbacks(tick);rememberManual();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("more_expanded",moreExpanded);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);if(testCountdownDialog!=null)testCountdownDialog.dismiss();super.onDestroy();}
    private void dismissStaleTestDialog(){
        if(testCountdownDialog==null)return;
        JSONObject trial=ProbeState.saved(this).optJSONObject("trial");
        if(trial==null||!"scheduled".equals(trial.optString("status"))||!trial.optString("id").equals(countdownTrialId)){
            testCountdownDialog.dismiss();testCountdownDialog=null;countdownTrialId=null;
        }
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("pin_setup",false))startActivity(new Intent(this,PinSetupActivity.class));}

    private void refresh(){
        if(enabled==null)return;
        JSONObject status=ProbeState.status(this),s=NativeTrialScheduler.status(this);
        boolean on=s.optBoolean("enabled");refreshing=true;enabled.setChecked(on);refreshing=false;
        JSONArray jobs=s.optJSONArray("jobs");JSONObject next=null;boolean running=false,overdue=false;
        long now=System.currentTimeMillis();
        if(jobs!=null)for(int i=0;i<jobs.length();i++){
            JSONObject j=jobs.optJSONObject(i);if(j==null)continue;
            running|="running".equals(j.optString("status"));
            if("pending".equals(j.optString("status"))){
                if(j.optLong("scheduledAt")<=now)overdue=true;
                else if(next==null||j.optLong("scheduledAt")<next.optLong("scheduledAt"))next=j;
            }
        }
        label(stateTitle,on?(running?"正在执行":overdue?"有任务待核对":"定时已开启"):"定时已关闭");
        if(on&&running){label(nextTime,"执行中");label(nextDetail,"完成后请查看下方结果");}
        else if(on&&next!=null){label(nextTime,time(next.optLong("scheduledAt"),"HH:mm"));label(nextDetail,dayLabel(next.optString("workDate"))+" · "+kind(next)+"  /  "+remaining(next.optLong("scheduledAt")-now));}
        else {label(nextTime,on?"待核对":"已暂停");label(nextDetail,on?"请查看下方执行结果":"开启时将确认待执行的安排");}
        String window=s.has("startDate")?"试用日期  "+s.optString("startDate")+" — "+s.optString("endDate")+"\n北京时间 · 到期停止":"尚未设置试用日期";
        label(dateWindow,window);
        String rules="上班  "+hm(s.optInt("morningMinutes",420))+"\n下班  不早于 "+hm(s.optInt("eveningFloorMinutes",1110))+"\n且满足：实际签到 + "+duration(s.optInt("durationMinutes",540))+" + "+s.optInt("marginMinutes",10)+" 分钟余量";
        label(ruleSummary,rules);
        JSONObject receipts=s.optJSONObject("receipts"),r=receipts==null?null:receipts.optJSONObject(today());
        String receipt="今日签到待核实\n读到实际签到后，再计算下班时间。";
        if(r!=null&&!r.isNull("checkIn")&&!r.optString("checkIn").isEmpty()){
            receipt="已核实签到  "+r.optString("checkIn");
            try{receipt+="\n下班不早于  "+time(NativeTrialPlanner.eveningAt(today(),r.optString("checkIn"),s.optInt("eveningFloorMinutes",1110),s.optInt("durationMinutes",540),s.optInt("marginMinutes",10)),"HH:mm");}catch(Exception ignored){}
            if(!r.isNull("checkOut")&&!r.optString("checkOut").isEmpty())receipt+="\n已读取下班  "+r.optString("checkOut");
        }
        label(todayReceipt,receipt);
        JSONObject trial=status.optJSONObject("trial");boolean manual=trial!=null&&!trial.has("jobId");
        boolean queued=manual&&"scheduled".equals(trial.optString("status")),active=manual&&"running".equals(trial.optString("status"));
        manualBusy=queued||active;if(manualBusy&&!moreExpanded)setMoreExpanded(true);
        if(queued){long left=trial.optLong("scheduledAt")-now;label(testState,left>0?"已安排 · "+time(trial.optLong("scheduledAt"),"HH:mm:ss")+" 开始\n倒计时 "+countdown(left)+" · 请拔线并锁屏":"已到测试时间，等待手机执行");}
        else if(active)label(testState,"正在测试 · "+stage(trial.optString("stage"))+"\n请等待完成后再操作手机");
        else label(testState,manual?"上次测试："+outcome(trial.optString("outcome",trial.optString("status")))+"\n详情见上方最近结果":"仅打开考勤页并读取结果");
        if(testButton!=null)testButton.setEnabled(!queued&&!active&&!running);
        if(cancelTest!=null)cancelTest.setVisibility(queued||active?View.VISIBLE:View.GONE);
        String conditions=(status.optBoolean("accessibilityConnected")?"✓ 无障碍已连接":"○ 请开启无障碍服务")+"\n"+(status.optBoolean("exactAlarmAllowed")?"✓ 定时权限已开启":"○ 请开启精确闹钟权限")+"\n"+(status.optBoolean("hasPinStorage")?"✓ 本机解锁已配置":"○ 请设置本机解锁 PIN");
        label(readiness,conditions+"\n\n华为需允许自启动、关联启动和后台活动。");label(livePower,"当前状态  "+powerLabel());
        label(datedRecords,datedSummary(s));label(manualRecord,manualSummary(lastManual(trial)));
    }
    private JSONObject lastManual(JSONObject trial){
        if(trial!=null&&!trial.has("jobId"))return trial;
        try{return new JSONObject(getSharedPreferences("dashboard_view",MODE_PRIVATE).getString("last_manual_trial","{}"));}catch(Exception ignored){return null;}
    }
    private void rememberManual(){
        JSONObject trial=ProbeState.saved(this).optJSONObject("trial");
        if(trial==null||trial.has("jobId"))return;
        String state=trial.optString("status");
        if(!"finished".equals(state)&&!"cancelled".equals(state))return;
        android.content.SharedPreferences cache=getSharedPreferences("dashboard_view",MODE_PRIVATE);
        String value=trial.toString();
        if(!value.equals(cache.getString("last_manual_trial","")))cache.edit().putString("last_manual_trial",value).apply();
    }
    private String datedSummary(JSONObject s){
        JSONObject lastReport=null,lastJob=null;long latest=0;JSONArray reports=s.optJSONArray("reports");
        if(reports!=null)for(int i=0;i<reports.length();i++){
            JSONObject r=reports.optJSONObject(i);if(r==null)continue;
            long at=r.optLong("finishedAt",r.optLong("firedAt"));
            if(at>=latest){lastReport=r;latest=at;}
        }
        JSONArray jobs=s.optJSONArray("jobs");
        if(jobs!=null)for(int i=0;i<jobs.length();i++){
            JSONObject j=jobs.optJSONObject(i);if(j==null||"pending".equals(j.optString("status")))continue;
            boolean represented=false;
            if("finished".equals(j.optString("status"))&&reports!=null)for(int n=0;n<reports.length();n++){
                JSONObject r=reports.optJSONObject(n);
                if(r!=null&&j.optString("id").equals(r.optString("jobId"))&&j.optString("outcome").equals(r.optString("outcome")))represented=true;
            }
            if(represented)continue;
            long at=j.optLong("finishedAt",j.optLong("startedAt",j.optLong("scheduledAt")));
            if(at>latest){lastJob=j;latest=at;}
        }
        if(lastJob!=null)return time(latest,"MM-dd HH:mm:ss")+" · "+kind(lastJob)+"\n"+outcome(lastJob.optString("outcome",lastJob.optString("status")));
        if(lastReport!=null){
            String result=time(latest,"MM-dd HH:mm:ss")+" · "+kind(lastReport)+"\n"+outcome(lastReport.optString("outcome"))+"\n"+startPower(lastReport);
            String receipt=receiptSummary(lastReport);return result+(receipt.isEmpty()?"":"\n"+receipt);
        }
        return "尚无定时执行结果，完成后会显示在这里。";
    }
    private String manualSummary(JSONObject r){
        if(r==null||!r.has("id"))return "尚无独立测试记录。";
        String state=r.optString("status"),title=r.optBoolean("checkoutEnabled")?"下班实测":"读取考勤测试";
        long at=r.optLong("firedAt",r.optLong("scheduledAt"));
        String result=title+"\n"+time(at,"MM-dd HH:mm:ss")+"\n"+outcome(r.optString("outcome",state));
        if(r.has("firedAt"))result+="\n"+startPower(r);
        if(r.has("finishedAt")&&r.has("firedAt"))result+="\n耗时 "+String.format(Locale.CHINA,"%.1f",Math.max(0,r.optLong("finishedAt")-r.optLong("firedAt"))/1000.0)+" 秒";
        String receipt=receiptSummary(r);if(!receipt.isEmpty())result+="\n"+receipt;
        return result;
    }
    private static String receiptSummary(JSONObject r){
        JSONObject receipt=r.optJSONObject("after");if(receipt==null||!receipt.optBoolean("accepted"))receipt=r.optJSONObject("before");
        if(receipt==null||!receipt.optBoolean("accepted"))return "";
        String in=receipt.isNull("checkIn")?"待核实":receipt.optString("checkIn","待核实");
        String out=receipt.isNull("checkOut")?"未打卡":receipt.optString("checkOut","未打卡");
        return "回执  "+receipt.optString("date")+"\n上班 "+in+"  ·  下班 "+out;
    }
    private static String startPower(JSONObject r){
        int source=r.optInt("powerSourceAtStart",-1);String supply=source==0?"电池供电":source>0?"连接电源":"供电状态未记录";
        String usb=!r.has("usbConnectedAtStart")||r.isNull("usbConnectedAtStart")?"USB 未记录":r.optBoolean("usbConnectedAtStart")?"USB 已连接":"USB 已断开";
        int level=r.optInt("batteryLevelAtStart",-1),scale=r.optInt("batteryScaleAtStart",100);
        return "执行开始时："+supply+(level>=0&&scale>0?" "+level*100/scale+"%":"")+" · "+usb;
    }

    private void editSchedule(){
        if(OneShotRunner.isActive()){notice("当前任务正在执行，请完成后再设置。");return;}
        JSONObject s=NativeTrialScheduler.status(this);
        final int[] values={s.optInt("morningMinutes",420),s.optInt("eveningFloorMinutes",1110),s.optInt("durationMinutes",540),s.optInt("marginMinutes",10)};
        LocalDate a=LocalDate.now(NativeRunPolicy.ZONE),b=a.plusDays(1);
        try{LocalDate savedStart=LocalDate.parse(s.optString("startDate")),savedEnd=LocalDate.parse(s.optString("endDate"));if(!savedEnd.isBefore(a)){if(savedStart.isAfter(a))a=savedStart;b=savedEnd;}}catch(Exception ignored){}
        final String start=a.toString(),end=b.toString();
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);
        form.addView(text("试用日期："+start+" — "+end+"\n北京时间；到期停止。保存后开启此安排。",13,MUTED,false));
        Button morning=button(form,"",false,()->{}),floor=button(form,"",false,()->{}),hours=button(form,"",false,()->{}),margin=button(form,"",false,()->{});
        Runnable labels=()->{morning.setText("上班时间     "+hm(values[0])+"  ›");floor.setText("下班最早     "+hm(values[1])+"  ›");hours.setText("签到后间隔     "+duration(values[2])+"  ›");margin.setText("额外余量     "+values[3]+" 分钟  ›");};labels.run();
        morning.setOnClickListener(v->pickTime(values,0,labels));floor.setOnClickListener(v->pickTime(values,1,labels));
        hours.setOnClickListener(v->pickNumber("签到后间隔（分钟）",1,1440,values,2,labels));margin.setOnClickListener(v->pickNumber("额外余量（分钟）",0,120,values,3,labels));
        form.addView(text("下班取两者中较晚的时间。已核实的签到和已执行记录会保留，过期任务不会自动补打。",13,MUTED,false));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("设置打卡时间").setView(form).setNegativeButton("取消",null).setPositiveButton("保存并开启",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            if(OneShotRunner.isActive()){notice("当前任务正在执行，请完成后再保存。");return;}
            JSONObject current=ProbeState.status(this);
            if(!current.optBoolean("accessibilityConnected")||!current.optBoolean("exactAlarmAllowed")||!current.optBoolean("hasPinStorage")){notice("请先完成权限与解锁设置。");return;}
            JSONObject trial=current.optJSONObject("trial");if(trial!=null&&"scheduled".equals(trial.optString("status"))){notice("请先完成或取消当前独立测试。");return;}
            try{
                JSONObject live=NativeTrialScheduler.status(this),rs=live.optJSONObject("receipts"),r=rs==null?null:rs.optJSONObject(today());
                String in=r==null||r.isNull("checkIn")?null:r.optString("checkIn",null);
                boolean keep=in!=null&&today().compareTo(start)>=0&&today().compareTo(end)<=0;
                NativeTrialScheduler.configure(this,start,end,values[0],values[1],values[2],values[3],keep?today():null,keep?in:null);
                dialog.dismiss();notice(NativeTrialScheduler.status(this).optBoolean("enabled")?"时间已保存，定时已开启。":"时间已保存；所选日期已无待执行任务。");
            }catch(Exception error){notice(errorText(error));}finally{refresh();}
        }));dialog.show();
    }
    private void stopSchedule(){
        new AlertDialog.Builder(this).setTitle("关闭定时打卡？").setMessage("将取消待执行的定时任务并停止当前运行。已核实的记录和时间设置会保留。").setNegativeButton("保留开启",null).setPositiveButton("关闭定时",(d,w)->{
            try{NativeTrialScheduler.cancelChecked(this);notice("定时已关闭。");}catch(Exception error){notice("关闭未完成，请查看当前状态后重试。");}finally{refresh();}
        }).show();
    }
    private void prepareTest(){
        new AlertDialog.Builder(this).setTitle("只读取考勤测试").setMessage("1. 点下方按钮，开始 3 分钟倒计时。\n2. 拔掉电脑 USB；测试电池时也拔掉充电头。\n3. 按电源键锁屏，等满 6 分钟再接回电脑。\n\n此次仅导航和读取，App 不点击打卡；钉钉自身的极速打卡仍可能在打开时触发。").setNegativeButton("稍后",null).setPositiveButton("开始 3 分钟倒计时",(d,w)->{
            try{
                JSONObject state=ProbeState.status(this);
                if(!state.optBoolean("accessibilityConnected")||!state.optBoolean("hasPinStorage"))throw new IllegalStateException("setup-required");
                JSONObject s=NativeTrialScheduler.status(this);JSONArray jobs=s.optJSONArray("jobs");long now=System.currentTimeMillis();
                if(s.optBoolean("enabled")&&jobs!=null)for(int i=0;i<jobs.length();i++){JSONObject j=jobs.optJSONObject(i);if(j!=null&&("running".equals(j.optString("status"))||("pending".equals(j.optString("status"))&&j.optLong("scheduledAt")<=now+600000&&j.optLong("scheduledAt")>=now-900000)))throw new IllegalStateException("near-scheduled-job");}
                countdownTrialId=OneShotRunner.schedule(this,false,180,true);refresh();
                testCountdownDialog=new AlertDialog.Builder(this).setTitle("已安排 · 3 分钟后开始").setMessage("现在拔掉电脑 USB；测试电池时，也拔掉充电头，然后按电源键锁屏。\n\n等满 6 分钟再接回电脑。倒计时和结果显示在本页。").setPositiveButton("知道了",null).create();
                testCountdownDialog.show();
            }catch(Exception error){notice(errorText(error));refresh();}
        }).show();
    }
    private void cancelManualTest(){
        JSONObject t=ProbeState.saved(this).optJSONObject("trial");
        if(t!=null&&!t.has("jobId")&&("scheduled".equals(t.optString("status"))||"running".equals(t.optString("status")))){OneShotRunner.cancelManual(this);notice("本次独立测试已取消。");}refresh();
    }
    private void showSettings(){new AlertDialog.Builder(this).setTitle("权限与解锁设置").setItems(new String[]{"无障碍服务","精确闹钟权限","本机加密 PIN","应用设置 / 后台运行"},(d,w)->{
        if(w==0)startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        else if(w==1)startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));
        else if(w==2)startActivity(new Intent(this,PinSetupActivity.class));
        else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
    }).setNegativeButton("返回",null).show();}
    private void pickTime(int[] values,int at,Runnable labels){new TimePickerDialog(this,(v,h,m)->{values[at]=h*60+m;labels.run();},values[at]/60,values[at]%60,true).show();}
    private void pickNumber(String title,int min,int max,int[] values,int at,Runnable labels){NumberPicker picker=new NumberPicker(this);picker.setMinValue(min);picker.setMaxValue(max);picker.setValue(values[at]);picker.setWrapSelectorWheel(false);new AlertDialog.Builder(this).setTitle(title).setView(picker).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{picker.clearFocus();values[at]=picker.getValue();labels.run();}).show();}
    private String powerLabel(){Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)),u=registerReceiver(null,new IntentFilter("android.hardware.usb.action.USB_STATE"));int source=b==null?-1:b.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1),level=b==null?-1:b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b==null?100:b.getIntExtra(BatteryManager.EXTRA_SCALE,100);return (u==null?"USB 状态待确认":u.getBooleanExtra("connected",false)?"USB 已连接":"USB 已断开")+" · "+(source==0?"电池供电":source>0?"正在充电":"电源待确认")+(level>=0&&scale>0?" "+level*100/scale+"%":"");}
    private static String errorText(Exception e){String code=e.getMessage();if("near-scheduled-job".equals(code))return "定时任务即将执行，请在完成后测试。";if("setup-required".equals(code))return "请先完成无障碍和本机 PIN 设置。";if("exact-alarm-required".equals(code))return "请先开启精确闹钟权限。";if("no-future-jobs".equals(code))return "所选日期没有待安排任务，请调整时间。";if("evening-already-due".equals(code))return "今天的下班时间已过，请核对记录后调整安排。";if("trial-already-running".equals(code)||"job-running".equals(code))return "当前任务正在运行，请完成后再试。";return "设置未完成，请检查权限、时间和当前任务后重试。";}
    private static String outcome(String code){if("read-only-verified".equals(code))return "读取成功，回执已核实";if("morning-verified".equals(code))return "上班回执已核实";if("evening-verified".equals(code))return "下班回执已核实";if("evening-deferred".equals(code))return "已按实际签到顺延下班时间";if("cancelled".equals(code))return "已取消";if("trial-timeout".equals(code))return "执行超时，需检查后台运行";if("wall-trial-usb-still-connected".equals(code))return "测试未执行：USB 仍连接";if("wall-trial-lockscreen-unconfirmed".equals(code))return "测试未执行：到点时未锁屏息屏";if("native-unlock-failed".equals(code))return "解锁未完成，请检查本机设置";if("scheduled".equals(code))return "已安排，等待执行";if("running".equals(code))return "正在执行";if("overdue-not-replayed".equals(code)||"missed".equals(code))return "已过执行时间，请核对考勤";if("interrupted-not-replayed".equals(code))return "运行中断，请核对考勤";if("checkout-update-verified".equals(code))return "下班更新回执已核实";if("alarm-registration-failed".equals(code))return "定时未安排成功，请检查权限";if(code.isEmpty())return "尚无结果";return "结果待核实，请查看钉钉记录";}
    private static String stage(String s){if("unlock".equals(s))return "唤醒并解锁";if("navigate".equals(s))return "打开考勤页";if("read-receipt".equals(s))return "读取考勤回执";if("verify-receipt".equals(s))return "核实结果";return "执行中";}
    private static String today(){return LocalDate.now(NativeRunPolicy.ZONE).toString();}
    private static String dayLabel(String date){return date.equals(today())?"今天":date.equals(LocalDate.now(NativeRunPolicy.ZONE).plusDays(1).toString())?"明天":date;}
    private static String kind(JSONObject j){return "MORNING".equals(j.optString("kind"))?"上班打卡":"下班打卡";}
    private static String hm(int v){return String.format(Locale.CHINA,"%02d:%02d",v/60,v%60);}
    private static String duration(int v){return (v/60>0?v/60+" 小时":"")+(v%60>0?" "+v%60+" 分钟":"");}
    private static String time(long at,String pattern){return at>0?Instant.ofEpochMilli(at).atZone(NativeRunPolicy.ZONE).format(DateTimeFormatter.ofPattern(pattern)):"—";}
    private static String remaining(long ms){long minutes=Math.max(1,(ms+59999)/60000);return minutes>=60?minutes/60+" 小时 "+minutes%60+" 分钟后":minutes+" 分钟后";}
    private static String countdown(long ms){long seconds=Math.max(0,(ms+999)/1000);return String.format(Locale.CHINA,"%02d:%02d",seconds/60,seconds%60);}
    private static void label(TextView view,String value){if(view!=null&&!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
    private void notice(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private LinearLayout card(LinearLayout parent,int color){LinearLayout v=column();v.setPadding(dp(18),dp(16),dp(18),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(20));v.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(16);parent.addView(v,lp);return v;}
    private Button button(LinearLayout parent,String label,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.WHITE:GREEN);b.setMinHeight(dp(48));GradientDrawable bg=new GradientDrawable();bg.setColor(primary?GREEN:0xffedf5f3);bg.setCornerRadius(dp(12));b.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);parent.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    private void space(LinearLayout parent,int height){View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(height)));}
    static void scheduleProbe(Context c){DiagnosticsActivity.scheduleProbe(c);}
    static void scheduleProbe(Context c,boolean alarmClock){DiagnosticsActivity.scheduleProbe(c,alarmClock);}
}
