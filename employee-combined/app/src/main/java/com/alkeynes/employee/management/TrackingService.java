package com.alkeynes.employee.management;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public class TrackingService extends Service implements LocationListener {
    private static final String CHANNEL="employee_native_tracking";
    private static final int NOTIFICATION_ID=9401;
    private LocationManager locationManager;
    private ExecutorService net;
    private PowerManager.WakeLock wakeLock;
    private SecureStore store;

    @Override public void onCreate(){
        super.onCreate();
        store=new SecureStore(this);
        createChannel();
        startForeground(NOTIFICATION_ID,notification("Starting work-location tracking…"));
        net=Executors.newSingleThreadExecutor();
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"EmployeeManagementNative:Tracking");
        wakeLock.setReferenceCounted(false);
        try{ wakeLock.acquire(10*60*60*1000L); }catch(Exception ignored){}
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        requestLocations();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){ return START_STICKY; }

    private void requestLocations(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){ stopSelf(); return; }
        try{ locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000L,5f,this,Looper.getMainLooper()); }catch(Exception ignored){}
        try{ locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,10000L,10f,this,Looper.getMainLooper()); }catch(Exception ignored){}
        update("Tracking active · waiting for a precise GPS fix");
    }

    @Override public void onLocationChanged(Location l){
        if(l==null)return;
        String token=store.get("tracking_token");
        if(token==null||token.isEmpty()){ stopSelf(); return; }
        net.submit(()->send(l,token));
    }

    private void send(Location l,String token){
        try{
            JSONObject b=new JSONObject();
            b.put("lat",l.getLatitude()); b.put("lng",l.getLongitude());
            b.put("accuracy",l.hasAccuracy()?l.getAccuracy():JSONObject.NULL);
            b.put("speed",l.hasSpeed()?l.getSpeed():JSONObject.NULL);
            b.put("heading",l.hasBearing()?l.getBearing():JSONObject.NULL);
            b.put("captured_at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date(l.getTime())));
            Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery!=null){
                int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                if(level>=0&&scale>0)b.put("battery_percent",Math.round(level*100f/scale));
            }
            JSONObject r=ApiClient.postBearer(ApiClient.GEO_API,b,token);
            String code=r.optString("code","");
            if(r.optBoolean("ok")){
                String accuracy=l.hasAccuracy()?Math.round(l.getAccuracy())+" m":"GPS";
                if(r.optBoolean("skipped")) update("Tracking active · "+accuracy+" · monitoring movement");
                else update("Last update "+new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date())+" · accuracy "+accuracy);
            } else if("no_active_session".equals(code)||"outside_shift".equals(code)||"tracking_disabled".equals(code)) {
                stopSelf();
            } else if("unauthorized".equals(code)) {
                store.remove("tracking_token"); stopSelf();
            } else if("low_accuracy".equals(code)) update("Tracking active · improving GPS accuracy");
            else update("Tracking active · sync will retry");
        }catch(ApiClient.ApiException e){
            if(e.status==401){ store.remove("tracking_token"); stopSelf(); }
            else if("no_active_session".equals(e.code)||"outside_shift".equals(e.code)||"tracking_disabled".equals(e.code)) stopSelf();
            else update("Tracking active · sync will retry");
        }catch(Exception e){ update("Tracking active · sync will retry"); }
    }

    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class);
        open.putExtra("open_screen","attendance");
        PendingIntent pi=PendingIntent.getActivity(this,31,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String since=store.get("clock_in_at");
        String title="Employee Management · Location tracking";
        if(since!=null&&!since.isEmpty()) title="Working since "+formatClockIn(since);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text+". Tap to open Attendance."))
                .setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).build();
    }

    private String formatClockIn(String raw){
        try{
            Date d=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(raw);
            return d==null?raw:new SimpleDateFormat("h:mm a",Locale.getDefault()).format(d);
        }catch(Exception e){ return raw; }
    }

    private void update(String text){ ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION_ID,notification(text)); }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Work location tracking",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Visible while an authorised active attendance session is being tracked.");
            c.setShowBadge(false);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    @Override public void onDestroy(){
        if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        if(net!=null)net.shutdownNow();
        if(wakeLock!=null&&wakeLock.isHeld())try{wakeLock.release();}catch(Exception ignored){}
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }
}
