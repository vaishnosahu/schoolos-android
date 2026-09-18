package com.schooloss.mobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public final class SchoolOSMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_UPDATES = "schoolos_updates";
    private static final String CHANNEL_URGENT = "schoolos_urgent";

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel updates = new NotificationChannel(
                CHANNEL_UPDATES, "School updates", NotificationManager.IMPORTANCE_DEFAULT);
        updates.setDescription("Messages, results, fees, attendance and learning updates from SchoolOS.");
        nm.createNotificationChannel(updates);

        NotificationChannel urgent = new NotificationChannel(
                CHANNEL_URGENT, "Urgent school alerts", NotificationManager.IMPORTANCE_HIGH);
        urgent.setDescription("Urgent and emergency alerts from your school.");
        urgent.enableVibration(true);
        nm.createNotificationChannel(urgent);
    }

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        SchoolOSApplication.savePendingToken(this, token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        ensureChannels(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Map<String,String> data = message.getData();
        String title = data.get("title");
        String body = data.get("body");
        if (message.getNotification() != null) {
            if (title == null || title.trim().isEmpty()) title = message.getNotification().getTitle();
            if (body == null || body.trim().isEmpty()) body = message.getNotification().getBody();
        }
        if (title == null || title.trim().isEmpty()) title = "SchoolOS";
        if (body == null) body = "";

        String target = data.get("target_url");
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (target != null && !target.trim().isEmpty()) {
            try { intent.setAction(Intent.ACTION_VIEW); intent.setData(Uri.parse(target.trim())); }
            catch (Exception ignored) {}
        }

        int requestCode = (int)(System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean urgent = "high".equalsIgnoreCase(data.get("urgency"));
        String channel = urgent ? CHANNEL_URGENT : CHANNEL_UPDATES;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_schoolos)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(urgent ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(urgent ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_MESSAGE);

        String idRaw = data.get("outbox_id");
        int notificationId;
        try { notificationId = idRaw == null ? requestCode : Math.max(1, Integer.parseInt(idRaw)); }
        catch (Exception ignored) { notificationId = requestCode; }

        try { NotificationManagerCompat.from(this).notify(notificationId, builder.build()); }
        catch (SecurityException ignored) {}
    }
}
