package com.alkeynes.employee.management;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackingService extends Service implements LocationListener {
    private static final String CHANNEL = "employee_live_tracking";
    private static final int NOTIF = 7301;
    private LocationManager lm;
    private ExecutorService net;
    private PowerManager.WakeLock wakeLock;
    private volatile long lastAcceptedAt = 0L;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF, notification("Starting secure live tracking…"));
        net = Executors.newSingleThreadExecutor();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EmployeeManagement:LiveTracking");
        wakeLock.setReferenceCounted(false);
        try { wakeLock.acquire(10 * 60 * 60 * 1000L); } catch(Exception ignored) {}
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        startLocations();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    private void startLocations() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return; }
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this, Looper.getMainLooper()); } catch(Exception ignored) {}
        try { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 10f, this, Looper.getMainLooper()); } catch(Exception ignored) {}
        update("Live tracking active · waiting for GPS");
    }

    @Override public void onLocationChanged(Location l) {
        if (l == null) return;
        String token = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString("tracking_token", null);
        if (token == null || token.trim().isEmpty()) { stopSelf(); return; }
        net.submit(() -> sendLocation(l, token));
    }

    private void sendLocation(Location l, String token) {
        try {
            JSONObject b = new JSONObject();
            b.put("lat", l.getLatitude()); b.put("lng", l.getLongitude());
            b.put("accuracy", l.hasAccuracy() ? l.getAccuracy() : JSONObject.NULL);
            b.put("speed", l.hasSpeed() ? l.getSpeed() : JSONObject.NULL);
            b.put("heading", l.hasBearing() ? l.getBearing() : JSONObject.NULL);
            b.put("captured_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date(l.getTime())));
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) b.put("battery_percent", Math.round(level * 100f / scale));
            }
            JSONObject r = MainActivity.postJson(MainActivity.BASE + "/api/mobile-geo-ping.php", b, token, null);
            String code = r.optString("code", "");
            if (r.optBoolean("ok")) {
                if (!r.optBoolean("skipped")) lastAcceptedAt = System.currentTimeMillis();
                String accuracy = l.hasAccuracy() ? Math.round(l.getAccuracy()) + " m" : "GPS";
                double movement = r.optDouble("movement_m", 0);
                if (r.optBoolean("skipped")) update("Live tracking active · " + accuracy + " · monitoring movement");
                else update("Live tracking active · " + accuracy + " · moved " + Math.round(movement) + " m");
            } else if ("no_active_session".equals(code) || "outside_shift".equals(code) || "tracking_disabled".equals(code)) {
                stopSelf();
            } else if ("unauthorized".equals(code)) {
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().remove("tracking_token").apply();
                stopSelf();
            } else if ("low_accuracy".equals(code)) {
                update("Live tracking active · improving GPS accuracy…");
            } else update("Live tracking active · sync retrying");
        } catch(Exception e) { update("Live tracking active · sync retrying"); }
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder n = new Notification.Builder(this, CHANNEL);
        return n.setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Employee Management · Location tracking")
                .setContentText(text)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void update(String text) {
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF, notification(text));
    }

    private void createChannel() {
        NotificationChannel c = new NotificationChannel(CHANNEL, "Employee live tracking", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Visible while an employee is clocked in and shift location tracking is active.");
        c.setShowBadge(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
    }

    @Override public void onDestroy() {
        if (lm != null) try { lm.removeUpdates(this); } catch(Exception ignored) {}
        if (net != null) net.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld()) try { wakeLock.release(); } catch(Exception ignored) {}
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent intent) { return null; }
}
