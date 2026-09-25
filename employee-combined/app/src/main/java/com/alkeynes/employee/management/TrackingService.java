package com.alkeynes.employee.management;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackingService extends Service implements LocationListener {
    private static final String CHANNEL="employee_live_tracking";private static final int NOTIF=7301;
    private LocationManager lm;private ExecutorService net;private PowerManager.WakeLock wakeLock;
    @Override public void onCreate(){super.onCreate();createChannel();startForeground(NOTIF,notification("Starting location service…"));net=Executors.newSingleThreadExecutor();PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"EmployeeManagement:LiveTracking");wakeLock.setReferenceCounted(false);try{wakeLock.acquire(10*60*60*1000L);}catch(Exception ignored){}lm=(LocationManager)getSystemService(LOCATION_SERVICE);startLocations();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_STICKY;}
    private void startLocations(){if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){stopSelf();return;}try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,5000L,5f,this,Looper.getMainLooper());}catch(Exception ignored){}try{lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,10000L,10f,this,Looper.getMainLooper());}catch(Exception ignored){}update("Tracking active · waiting for a precise GPS fix");}
    @Override public void onLocationChanged(Location l){if(l==null)return;String token=getSharedPreferences(MainActivity.PREFS,MODE_PRIVATE).getString("tracking_token",null);if(token==null||token.trim().isEmpty()){stopSelf();return;}net.submit(()->sendLocation(l,token));}
    private void sendLocation(Location l,String token){try{JSONObject b=new JSONObject();b.put("lat",l.getLatitude());b.put("lng",l.getLongitude());b.put("accuracy",l.hasAccuracy()?l.getAccuracy():JSONObject.NULL);b.put("speed",l.hasSpeed()?l.getSpeed():JSONObject.NULL);b.put("heading",l.hasBearing()?l.getBearing():JSONObject.NULL);b.put("captured_at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date(l.getTime())));Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(battery!=null){int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(level>=0&&scale>0)b.put("battery_percent",Math.round(level*100f/scale));}JSONObject r=MainActivity.postJson(MainActivity.BASE+"/api/mobile-geo-ping.php",b,token,null);String code=r.optString("code","");if(r.optBoolean("ok")){String accuracy=l.hasAccuracy()?Math.round(l.getAccuracy())+" m":"GPS";if(r.optBoolean("skipped"))update("Tracking active · "+accuracy+" · monitoring movement");else{String synced=new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date());update("Last update "+synced+" · accuracy "+accuracy);}}else if("no_active_session".equals(code)||"outside_shift".equals(code)||"tracking_disabled".equals(code)){stopSelf();}else if("unauthorized".equals(code)){getSharedPreferences(MainActivity.PREFS,MODE_PRIVATE).edit().remove("tracking_token").apply();stopSelf();}else if("low_accuracy".equals(code)){update("Tracking active · improving GPS accuracy");}else update("Tracking active · sync will retry");}catch(Exception e){update("Tracking active · sync will retry");}}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);open.setData(Uri.parse(MainActivity.BASE+"/employee/attendance.php"));PendingIntent openPi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);SharedPreferences sp=getSharedPreferences(MainActivity.PREFS,MODE_PRIVATE);String since=sp.getString("clock_in_at","");String title="Employee Management · Location tracking active";if(since!=null&&!since.trim().isEmpty()){try{Date d=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).parse(since);if(d!=null)title="Working since "+new SimpleDateFormat("h:mm a",Locale.getDefault()).format(d);}catch(Exception ignored){}}Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return n.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text+". Tap to open Attendance.")).setContentIntent(openPi).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).build();}
    private void update(String text){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF,notification(text));}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Work location tracking",NotificationManager.IMPORTANCE_LOW);c.setDescription("Visible while your active attendance session is being tracked according to company settings.");c.setShowBadge(false);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    @Override public void onDestroy(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){}if(net!=null)net.shutdownNow();if(wakeLock!=null&&wakeLock.isHeld())try{wakeLock.release();}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
