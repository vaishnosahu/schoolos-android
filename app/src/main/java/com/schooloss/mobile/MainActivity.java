package com.schooloss.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String HOME = "https://alkeynesprjects.com/schools/mobile/";
    private static final int FILE_REQ = 4101;
    private static final int PERM_REQ = 4102;
    private WebView web;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private final Set<String> internal = new HashSet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        internal.add("alkeynesprjects.com");
        internal.add("www.alkeynesprjects.com");
        internal.add("schooloss.com");
        internal.add("www.schooloss.com");
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        configure();
        web.loadUrl(resolve(getIntent()));
    }

    private String resolve(Intent i) {
        Uri u = i == null ? null : i.getData();
        if (u == null) return HOME;
        if ("schoolos".equalsIgnoreCase(u.getScheme())) {
            String p = u.getPath();
            return HOME + (p == null ? "" : p.replaceFirst("^/",""));
        }
        if ("https".equalsIgnoreCase(u.getScheme()) && internal.contains(u.getHost())) return u.toString();
        return HOME;
    }

    private void configure() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
                if ("https".equals(scheme) && internal.contains(u.getHost())) return false;
                if ("http".equals(scheme) || "https".equals(scheme) || "tel".equals(scheme) ||
                    "mailto".equals(scheme) || "sms".equals(scheme) || "whatsapp".equals(scheme) ||
                    "intent".equals(scheme) || "market".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
                    catch (ActivityNotFoundException e) { Toast.makeText(MainActivity.this,"No app can open this link",Toast.LENGTH_SHORT).show(); }
                    return true;
                }
                return true;
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) { h.cancel(); }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? android.view.View.GONE : android.view.View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try { startActivityForResult(params.createIntent(), FILE_REQ); return true; }
                catch (Exception e) { fileCallback = null; return false; }
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                pendingPermission = request;
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERM_REQ);
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geoOrigin = origin; geoCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERM_REQ);
            }
        });

        web.setDownloadListener((url, ua, disposition, mime, len) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) r.addRequestHeader("Cookie", cookie);
                if (ua != null) r.addRequestHeader("User-Agent", ua);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SchoolOS-download");
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(this,"Download started",Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == FILE_REQ && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        boolean ok = true;
        for (int x: results) if (x != PackageManager.PERMISSION_GRANTED) ok = false;
        if (pendingPermission != null) {
            if (ok) pendingPermission.grant(pendingPermission.getResources()); else pendingPermission.deny();
            pendingPermission = null;
        }
        if (geoCallback != null) {
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null; geoOrigin = null;
        }
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        web.loadUrl(resolve(intent));
    }
}
