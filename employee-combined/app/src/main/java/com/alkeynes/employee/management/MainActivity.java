package com.alkeynes.employee.management;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    public static final String BASE = "https://alkeynesprjects.com/employee-management";
    public static final String PREFS = "employee_management_android";
    private static final int REQ_LOCATION = 401;
    private static final int REQ_NOTIFICATIONS = 402;
    private WebView webView;
    private String currentRole = "";
    private boolean pendingStart = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        configureWebView();
        String start = BASE + "/";
        if (getIntent() != null && getIntent().getData() != null) {
            String u = getIntent().getData().toString();
            if (u.startsWith(BASE + "/")) start = u;
        }
        webView.loadUrl(start);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (origin != null && origin.startsWith("https://alkeynesprjects.com") &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    cb.invoke(origin, false, false);
                    ensureEmployeePermissions(false);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u != null && "https".equalsIgnoreCase(u.getScheme()) &&
                        "alkeynesprjects.com".equalsIgnoreCase(u.getHost()) &&
                        u.getPath() != null && u.getPath().startsWith("/employee-management/")) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                CookieManager.getInstance().flush();
                if (url != null && (url.contains("/logout.php") || url.contains("/login.php"))) {
                    if (url.contains("/login.php")) clearNativeSession(true);
                }
                syncAppState();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                android.app.DownloadManager.Request r = new android.app.DownloadManager.Request(Uri.parse(url));
                r.setMimeType(mimeType);
                r.addRequestHeader("User-Agent", userAgent);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) r.addRequestHeader("Cookie", cookie);
                r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));
                ((android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }

    private void syncAppState() {
        final String cookie = CookieManager.getInstance().getCookie(BASE + "/");
        if (cookie == null || cookie.trim().isEmpty()) return;
        final SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        final boolean needsToken = !sp.contains("tracking_token");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("device_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
                body.put("device_label", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                body.put("issue_token", needsToken);
                JSONObject out = postJson(BASE + "/api/app-bootstrap.php", body, null, cookie);
                if (!out.optBoolean("ok")) return;
                currentRole = out.optString("role_key", "");
                boolean employee = out.optInt("employee_id", 0) > 0;
                if (employee && out.has("token")) {
                    sp.edit().putString("tracking_token", out.optString("token", ""))
                            .putString("role_key", currentRole)
                            .putInt("employee_id", out.optInt("employee_id", 0)).apply();
                }
                boolean shouldTrack = employee && out.optBoolean("should_track", false);
                runOnUiThread(() -> {
                    if (shouldTrack) {
                        pendingStart = true;
                        ensureEmployeePermissions(true);
                    } else {
                        pendingStart = false;
                        stopService(new Intent(this, TrackingService.class));
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void ensureEmployeePermissions(boolean startWhenReady) {
        if (!"employee".equals(currentRole) && getSharedPreferences(PREFS, MODE_PRIVATE).getInt("employee_id",0) <= 0) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = startWhenReady;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        if (startWhenReady) startTrackingService();
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "For screen-off tracking, set Location to Allow all the time in App settings.", Toast.LENGTH_LONG).show();
        }
    }

    private void startTrackingService() {
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).contains("tracking_token")) return;
        Intent i = new Intent(this, TrackingService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        pendingStart = false;
    }

    private void clearNativeSession(boolean revoke) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String token = sp.getString("tracking_token", null);
        stopService(new Intent(this, TrackingService.class));
        sp.edit().clear().apply();
        currentRole = "";
        if (revoke && token != null) new Thread(() -> { try { postJson(BASE + "/api/mobile-token-revoke.php", new JSONObject(), token, null); } catch(Exception ignored){} }).start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingStart) startTrackingService();
            webView.reload();
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public static JSONObject postJson(String endpoint, JSONObject body, String bearer, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("X-Employee-App", "android-combined-v1");
        if (bearer != null && !bearer.trim().isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (cookie != null && !cookie.trim().isEmpty()) c.setRequestProperty("Cookie", cookie);
        c.setDoOutput(true);
        try(OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = c.getResponseCode(); InputStream is = (code >= 200 && code < 400) ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) try(BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) { String line; while((line=r.readLine())!=null) sb.append(line); }
        JSONObject out = new JSONObject(sb.length()==0 ? "{}" : sb.toString()); out.put("http_status", code); c.disconnect(); return out;
    }
}
