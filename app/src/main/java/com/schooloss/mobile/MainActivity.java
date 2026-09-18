package com.schooloss.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String NATIVE_VERSION = "3.0.4";
    private static final String HOME = "https://alkeynesprjects.com/schools/mobile/?native_app=android&native_version=3.0.4";
    private static final String APP_UA = " SchoolOSNative/3.0.4 Android";
    private static final int FILE_REQ = 4101;
    private static final int WEB_PERM_REQ = 4102;
    private static final int GEO_PERM_REQ = 4103;

    private WebView web;
    private ProgressBar progress;
    private View offlinePanel;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private final Set<String> internalHosts = new HashSet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        internalHosts.add("alkeynesprjects.com");
        internalHosts.add("www.alkeynesprjects.com");
        internalHosts.add("schooloss.com");
        internalHosts.add("www.schooloss.com");
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        offlinePanel = findViewById(R.id.offlinePanel);
        findViewById(R.id.retryButton).setOnClickListener(v -> retry());
        configureWebView();
        prepareNativeSession();
        if (state != null) web.restoreState(state); else load(resolve(getIntent()));
    }

    private void configureWebView() {
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + APP_UA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new NativeBridge(), "SchoolOSNative");
        installDocumentStartCleanup();

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl(), view.getUrl());
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                offlinePanel.setVisibility(View.GONE);
                web.setFocusable(false);
                web.setFocusableInTouchMode(false);
                web.setVisibility(View.INVISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                applyNativePresentation(url);
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.cancel();
                Toast.makeText(MainActivity.this, "Secure connection could not be verified.", Toast.LENGTH_LONG).show();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showOffline();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                launchFileChooser(params);
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> requestWebPermissions(request));
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geoOrigin = origin;
                geoCallback = callback;
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                    geoCallback = null;
                    geoOrigin = null;
                } else {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, GEO_PERM_REQ);
                }
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        route(r.getUrl(), web.getUrl());
                        v.destroy();
                        return true;
                    }
                    @Override public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        route(Uri.parse(url), web.getUrl());
                        v.stopLoading();
                        v.destroy();
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        web.setDownloadListener((url, ua, disposition, mime, len) -> download(url, ua, disposition, mime));
    }

    private void installDocumentStartCleanup() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        Set<String> origins = new HashSet<>();
        origins.add("https://alkeynesprjects.com");
        origins.add("https://www.alkeynesprjects.com");
        origins.add("https://schooloss.com");
        origins.add("https://www.schooloss.com");
        String script = "(function(){" +
                "var sel='.m-native-skip,.m-skip-link,.skip-link,a[href=\\\"#mainContent\\\"]';" +
                "var css=sel+'{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important}';" +
                "var st=document.createElement('style');st.id='schoolos-native-prepaint';st.textContent=css;" +
                "(document.head||document.documentElement).appendChild(st);" +
                "document.addEventListener('focusin',function(e){try{if(e.target&&e.target.matches&&e.target.matches(sel)){e.target.blur();var m=document.getElementById('mainContent');if(m&&m.focus)m.focus({preventScroll:true});}}catch(x){}},true);" +
                "document.addEventListener('DOMContentLoaded',function(){document.querySelectorAll(sel).forEach(function(x){x.remove();});},{once:true});" +
                "})();";
        WebViewCompat.addDocumentStartJavaScript(web, script, origins);
    }

    private void prepareNativeSession() {
        CookieManager cm = CookieManager.getInstance();
        cm.setCookie("https://alkeynesprjects.com", "schoolos_native=" + NATIVE_VERSION + "; Path=/; Secure; SameSite=Lax");
        cm.setCookie("https://www.alkeynesprjects.com", "schoolos_native=" + NATIVE_VERSION + "; Path=/; Secure; SameSite=Lax");
        cm.flush();
        android.content.SharedPreferences prefs = getSharedPreferences("schoolos_native", MODE_PRIVATE);
        String previous = prefs.getString("version", "");
        if (!NATIVE_VERSION.equals(previous)) {
            web.clearCache(true);
            web.clearHistory();
            prefs.edit().putString("version", NATIVE_VERSION).apply();
            Toast.makeText(this, "SchoolOS Native " + NATIVE_VERSION, Toast.LENGTH_SHORT).show();
        }
    }

    private Map<String,String> nativeHeaders() {
        Map<String,String> headers = new HashMap<>();
        headers.put("X-SchoolOS-Native", NATIVE_VERSION);
        headers.put("X-SchoolOS-Platform", "android");
        return headers;
    }

    private void requestWebPermissions(PermissionRequest request) {
        List<String> needed = new ArrayList<>();
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.CAMERA);
            }
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO);
            }
        }
        if (needed.isEmpty()) {
            request.grant(request.getResources());
            return;
        }
        pendingWebPermission = request;
        requestPermissions(needed.toArray(new String[0]), WEB_PERM_REQ);
    }

    private void launchFileChooser(WebChromeClient.FileChooserParams params) {
        Intent content = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType("*/*");
        if (params != null && params.getAcceptTypes() != null && params.getAcceptTypes().length > 0) {
            ArrayList<String> cleaned = new ArrayList<>();
            for (String t : params.getAcceptTypes()) if (t != null && !t.trim().isEmpty()) cleaned.add(t.trim());
            if (cleaned.size() == 1) content.setType(cleaned.get(0));
            else if (cleaned.size() > 1) content.putExtra(Intent.EXTRA_MIME_TYPES, cleaned.toArray(new String[0]));
        }
        boolean multiple = params != null && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);

        ArrayList<Intent> initial = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (camera.resolveActivity(getPackageManager()) != null) {
                try {
                    File dir = new File(getCacheDir(), "camera");
                    if (!dir.exists()) dir.mkdirs();
                    File photo = File.createTempFile("schoolos_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()), ".jpg", dir);
                    cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", photo);
                    camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    initial.add(camera);
                } catch (IOException ignored) {
                    cameraUri = null;
                }
            }
        }
        Intent chooser = Intent.createChooser(content, "Choose file");
        if (!initial.isEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, initial.toArray(new Intent[0]));
        try {
            startActivityForResult(chooser, FILE_REQ);
        } catch (Exception e) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
    }

    private boolean route(Uri uri, String fromUrl) {
        if (uri == null) return true;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if ("schoolos".equals(scheme)) {
            load(resolveDeepLink(uri));
            return true;
        }
        if (("https".equals(scheme) || "http".equals(scheme)) && internalHosts.contains(host)) {
            Uri target = normalizeNativeWorkspace(uri, fromUrl);
            web.loadUrl(target.toString(), nativeHeaders());
            return true;
        }
        if ("intent".equals(scheme)) {
            try {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
                else if (intent.getStringExtra("browser_fallback_url") != null)
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(intent.getStringExtra("browser_fallback_url"))));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open this action.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        openExternal(uri);
        return true;
    }

    private Uri normalizeNativeWorkspace(Uri uri, String fromUrl) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean cameFromWorkspace = fromUrl != null && fromUrl.contains("native_workspace=1");
        boolean desktopSchoolPage = path.startsWith("/schools/") &&
                !path.startsWith("/schools/mobile/") &&
                !path.contains("/assets/") &&
                !path.endsWith("download.php") &&
                !path.contains("gallery-media.php");
        if (cameFromWorkspace && desktopSchoolPage && path.endsWith(".php")) {
            Uri.Builder b = uri.buildUpon();
            if (uri.getQueryParameter("view") == null) b.appendQueryParameter("view", "desktop");
            if (uri.getQueryParameter("native_workspace") == null) b.appendQueryParameter("native_workspace", "1");
            return b.build();
        }
        return uri;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No installed app can open this link.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyNativePresentation(String url) {
        boolean workspace = url != null && url.contains("native_workspace=1");
        String css = ".sidebar,.mobile-sidebar-backdrop,.m-native-skip,.m-skip-link,.skip-link{display:none!important}" +
                ".app-shell{display:block!important}.main-area{margin-left:0!important;width:100%!important;max-width:none!important}" +
                ".topbar{position:sticky!important;top:0!important;z-index:30!important;padding:10px 12px!important}" +
                ".top-actions .desktop-action,.top-actions .help-open,.top-actions .command-open,.mobile-menu{display:none!important}" +
                ".content{padding:12px!important;max-width:none!important}.footer{padding:12px!important}" +
                "table{font-size:12px!important}.table-wrap,.table-card,.data-table-wrap{overflow-x:auto!important;-webkit-overflow-scrolling:touch}";
        String js = "(function(){" +
                "document.documentElement.classList.add('schoolos-native');" +
                "var kill=function(){" +
                    "document.querySelectorAll('.m-skip-link,.skip-link,a[href=\\\"#mainContent\\\"]').forEach(function(x){x.remove();});" +
                    "document.querySelectorAll('[data-install],#installSheet,#installBackdrop').forEach(function(x){x.style.display='none'});" +
                "};" +
                "kill();" +
                "if(!window.__schoolosNativeSkipObserver){" +
                    "window.__schoolosNativeSkipObserver=new MutationObserver(function(){kill();});" +
                    "window.__schoolosNativeSkipObserver.observe(document.documentElement||document,{subtree:true,childList:true,attributes:false});" +
                "}" +
                (workspace ? "var st=document.getElementById('schoolos-native-workspace');if(!st){st=document.createElement('style');st.id='schoolos-native-workspace';st.textContent=" + quoteJs(css) + ";document.head.appendChild(st);}" : "") +
                "kill();" +
                "return 'ready';" +
                "})();";
        web.evaluateJavascript(js, value -> {
            web.setFocusableInTouchMode(true);
            web.setFocusable(true);
            web.clearFocus();
            web.setVisibility(View.VISIBLE);
        });
    }

    private String quoteJs(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
    }

    private void download(String url, String ua, String disposition, String mime) {
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            if (ua != null) r.addRequestHeader("User-Agent", ua);
            if (mime != null) r.setMimeType(mime);
            String name = URLUtil.guessFileName(url, disposition, mime);
            r.setTitle(name);
            r.setDescription("SchoolOS download");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            openExternal(Uri.parse(url));
        }
    }

    private String resolve(Intent i) {
        Uri u = i == null ? null : i.getData();
        if (u == null) return HOME;
        if ("schoolos".equalsIgnoreCase(u.getScheme())) return resolveDeepLink(u);
        if ("https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && internalHosts.contains(u.getHost().toLowerCase(Locale.ROOT)))
            return u.toString();
        return HOME;
    }

    private String resolveDeepLink(Uri u) {
        String path = u.getPath();
        if (path == null || path.equals("/")) return HOME;
        if (path.startsWith("/schools/"))
            return "https://alkeynesprjects.com" + path + (u.getQuery() == null ? "" : "?" + u.getQuery());
        return HOME + path.replaceFirst("^/", "") + (u.getQuery() == null ? "" : "?" + u.getQuery());
    }

    private void load(String url) {
        if (!isOnline()) {
            showOffline();
            return;
        }
        offlinePanel.setVisibility(View.GONE);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setVisibility(View.INVISIBLE);
        web.loadUrl(url, nativeHeaders());
    }

    private void retry() {
        load(web.getUrl() == null ? HOME : web.getUrl());
    }

    private void showOffline() {
        web.setVisibility(View.GONE);
        offlinePanel.setVisibility(View.VISIBLE);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                c.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_REQ || fileCallback == null) return;
        ArrayList<Uri> result = new ArrayList<>();
        if (resultCode == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < clip.getItemCount(); i++) result.add(clip.getItemAt(i).getUri());
            } else if (data != null && data.getData() != null) {
                result.add(data.getData());
            } else if (cameraUri != null) {
                result.add(cameraUri);
            }
        }
        fileCallback.onReceiveValue(result.isEmpty() ? null : result.toArray(new Uri[0]));
        fileCallback = null;
        cameraUri = null;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean all = results.length > 0;
        for (int x : results) if (x != PackageManager.PERMISSION_GRANTED) all = false;
        if (requestCode == WEB_PERM_REQ && pendingWebPermission != null) {
            if (all) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
        if (requestCode == GEO_PERM_REQ && geoCallback != null) {
            geoCallback.invoke(geoOrigin, all, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    @Override public void onBackPressed() {
        if (offlinePanel.getVisibility() == View.VISIBLE) {
            retry();
            return;
        }
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        load(resolve(intent));
    }

    @Override protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        web.saveState(out);
        super.onSaveInstanceState(out);
    }

    public class NativeBridge {
        @JavascriptInterface public String getVersion() { return NATIVE_VERSION; }
        @JavascriptInterface public String getPlatform() { return "android"; }
        @JavascriptInterface public void share(String text, String url) {
            runOnUiThread(() -> {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("text/plain");
                String value = (text == null ? "" : text) + (url == null || url.trim().isEmpty() ? "" : "\n" + url);
                s.putExtra(Intent.EXTRA_TEXT, value);
                startActivity(Intent.createChooser(s, "Share from SchoolOS"));
            });
        }
        @JavascriptInterface public void openExternal(String url) {
            runOnUiThread(() -> {
                try { MainActivity.this.openExternal(Uri.parse(url)); } catch (Exception ignored) {}
            });
        }
    }
}
