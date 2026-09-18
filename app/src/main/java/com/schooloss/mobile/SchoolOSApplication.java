package com.schooloss.mobile;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

public final class SchoolOSApplication extends Application {
    public static final String PREFS = "schoolos_push";

    @Override public void onCreate() {
        super.onCreate();
        initializeFromStoredConfig(this);
        SchoolOSMessagingService.ensureChannels(this);
    }

    public static synchronized boolean configureFirebase(Context context, JSONObject cfg) {
        try {
            String projectId = cfg.optString("project_id", "").trim();
            String applicationId = cfg.optString("application_id", "").trim();
            String apiKey = cfg.optString("api_key", "").trim();
            String senderId = cfg.optString("sender_id", "").trim();
            if (projectId.isEmpty() || applicationId.isEmpty() || apiKey.isEmpty() || senderId.isEmpty()) return false;

            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String previousProject = p.getString("project_id", "");
            p.edit()
                    .putString("project_id", projectId)
                    .putString("application_id", applicationId)
                    .putString("api_key", apiKey)
                    .putString("sender_id", senderId)
                    .apply();

            List<FirebaseApp> apps = FirebaseApp.getApps(context);
            if (!apps.isEmpty() && !previousProject.isEmpty() && !previousProject.equals(projectId)) {
                try { FirebaseApp.getInstance().delete(); } catch (Exception ignored) {}
            }
            return initializeFromStoredConfig(context);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized boolean initializeFromStoredConfig(Context context) {
        try {
            if (!FirebaseApp.getApps(context).isEmpty()) return true;
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String projectId = p.getString("project_id", "");
            String applicationId = p.getString("application_id", "");
            String apiKey = p.getString("api_key", "");
            String senderId = p.getString("sender_id", "");
            if (projectId == null || projectId.isEmpty() || applicationId == null || applicationId.isEmpty() ||
                    apiKey == null || apiKey.isEmpty() || senderId == null || senderId.isEmpty()) return false;

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .setGcmSenderId(senderId)
                    .build();
            return FirebaseApp.initializeApp(context, options) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean firebaseReady(Context context) {
        try { return !FirebaseApp.getApps(context).isEmpty() || initializeFromStoredConfig(context); }
        catch (Exception ignored) { return false; }
    }

    public static String installationId(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = p.getString("installation_id", "");
        if (id != null && !id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        p.edit().putString("installation_id", id).apply();
        return id;
    }

    public static void savePendingToken(Context context, String token) {
        if (token == null || token.trim().isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("pending_token", token.trim()).apply();
    }

    public static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
}
