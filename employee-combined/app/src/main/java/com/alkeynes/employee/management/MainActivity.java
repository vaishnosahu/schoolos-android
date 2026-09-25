package com.alkeynes.employee.management;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
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
    private ProgressBar loading;
    private LinearLayout offlinePanel;
    private String currentRole = "";
    private boolean pendingStart = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setupSystemBars();
        buildShell();
        configureWebView();
        loadStartUrl(getIntent());
    }

    private void setupSystemBars() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()); top=i.top; bottom=i.bottom;
            } else {
                top=insets.getSystemWindowInsetTop(); bottom=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100); loading.setProgress(8);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3)); lp.gravity=Gravity.TOP;
        root.addView(loading, lp);

        offlinePanel = new LinearLayout(this); offlinePanel.setOrientation(LinearLayout.VERTICAL); offlinePanel.setGravity(Gravity.CENTER); offlinePanel.setPadding(dp(28),dp(28),dp(28),dp(28)); offlinePanel.setBackgroundColor(Color.rgb(244,247,251)); offlinePanel.setVisibility(View.GONE);
        TextView title=new TextView(this); title.setText("You're offline"); title.setTextSize(20); title.setTextColor(Color.rgb(20,32,51)); title.setGravity(Gravity.CENTER); title.setTypeface(null,1);
        TextView body=new TextView(this); body.setText("Check your internet connection and try again. Existing background tracking will retry automatically when connectivity returns."); body.setTextSize(13); body.setTextColor(Color.rgb(104,118,138)); body.setGravity(Gravity.CENTER); body.setPadding(0,dp(8),0,dp(18));
        Button retry=new Button(this); retry.setText("Try Again"); retry.setOnClickListener(v->{offlinePanel.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);webView.reload();});
        offlinePanel.addView(title,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));offlinePanel.addView(body,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));offlinePanel.addView(retry,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(offlinePanel,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root); root.requestApplyInsets();
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private void configureWebView() {
        WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setGeolocationEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setUserAgentString(s.getUserAgentString()+" EmployeeManagementAndroid/2.4.0");
        CookieManager cm=CookieManager.getInstance();cm.setAcceptCookie(true);cm.setAcceptThirdPartyCookies(webView,false);
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int progress){loading.setProgress(progress);loading.setVisibility(progress>=100?View.GONE:View.VISIBLE);}
            @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback cb){if(origin!=null&&origin.startsWith("https://alkeynesprjects.com")&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)cb.invoke(origin,true,false);else{cb.invoke(origin,false,false);ensureEmployeePermissions(false);}}
        });
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){Uri u=req.getUrl();if(isInternal(u))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){offlinePanel.setVisibility(View.GONE);loading.setVisibility(View.VISIBLE);}
            @Override public void onPageFinished(WebView view,String url){CookieManager.getInstance().flush();loading.setVisibility(View.GONE);if(url!=null&&url.contains("/login.php"))clearNativeSession(true);syncAppState();}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){loading.setVisibility(View.GONE);offlinePanel.setVisibility(View.VISIBLE);}}
        });
        webView.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{try{android.app.DownloadManager.Request r=new android.app.DownloadManager.Request(Uri.parse(url));r.setMimeType(mimeType);r.addRequestHeader("User-Agent",userAgent);String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)r.addRequestHeader("Cookie",cookie);r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,URLUtil.guessFileName(url,contentDisposition,mimeType));((android.app.DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);Toast.makeText(this,"Download started",Toast.LENGTH_SHORT).show();}catch(Exception e){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}}});
    }

    private boolean isInternal(Uri u){return u!=null&&"https".equalsIgnoreCase(u.getScheme())&&"alkeynesprjects.com".equalsIgnoreCase(u.getHost())&&u.getPath()!=null&&u.getPath().startsWith("/employee-management/");}
    private void loadStartUrl(Intent intent){String start=BASE+"/";if(intent!=null&&intent.getData()!=null&&isInternal(intent.getData()))start=intent.getData().toString();webView.loadUrl(start);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);loadStartUrl(intent);}

    private void syncAppState(){final String cookie=CookieManager.getInstance().getCookie(BASE+"/");if(cookie==null||cookie.trim().isEmpty())return;final SharedPreferences sp=getSharedPreferences(PREFS,MODE_PRIVATE);final boolean needsToken=!sp.contains("tracking_token");new Thread(()->{try{JSONObject body=new JSONObject();body.put("device_id",Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID));body.put("device_label",Build.MANUFACTURER+" "+Build.MODEL);body.put("issue_token",needsToken);JSONObject out=postJson(BASE+"/api/app-bootstrap.php",body,null,cookie);if(!out.optBoolean("ok"))return;currentRole=out.optString("role_key","");boolean employee=out.optInt("employee_id",0)>0;SharedPreferences.Editor ed=sp.edit().putString("role_key",currentRole).putInt("employee_id",out.optInt("employee_id",0));if(out.has("clock_in_at"))ed.putString("clock_in_at",out.optString("clock_in_at",""));else ed.remove("clock_in_at");if(employee&&out.has("token"))ed.putString("tracking_token",out.optString("token",""));ed.apply();boolean shouldTrack=employee&&out.optBoolean("should_track",false);runOnUiThread(()->{if(shouldTrack){pendingStart=true;ensureEmployeePermissions(true);}else{pendingStart=false;stopService(new Intent(this,TrackingService.class));}});}catch(Exception ignored){}}).start();}

    private void ensureEmployeePermissions(boolean startWhenReady){
        SharedPreferences sp=getSharedPreferences(PREFS,MODE_PRIVATE);pendingStart=pendingStart||startWhenReady;
        if(!"employee".equals(currentRole)&&sp.getInt("employee_id",0)<=0)return;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!sp.getBoolean("notification_permission_asked",false)){
            if(!sp.getBoolean("shown_notification_guide",false)){
                sp.edit().putBoolean("shown_notification_guide",true).apply();
                new AlertDialog.Builder(this).setTitle("Allow tracking notifications").setMessage("While you are clocked in, Employee Management shows an ongoing notification so you can see that work-location tracking is active.")
                        .setPositiveButton("Continue",(d,w)->{sp.edit().putBoolean("notification_permission_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);})
                        .setNegativeButton("Not now",(d,w)->{sp.edit().putBoolean("notification_permission_asked",true).apply();ensureEmployeePermissions(pendingStart);}).show();
            }else{sp.edit().putBoolean("notification_permission_asked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);}return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            if(!sp.getBoolean("location_permission_asked",false)){
                if(!sp.getBoolean("shown_location_guide",false)){
                    sp.edit().putBoolean("shown_location_guide",true).apply();
                    new AlertDialog.Builder(this).setTitle("Location for attendance").setMessage("Employee Management uses precise location for attendance and, while your active work session is running, for authorised live tracking. Tracking is stopped when the server says your work session or permitted shift is no longer active.")
                            .setPositiveButton("Continue",(d,w)->{sp.edit().putBoolean("location_permission_asked",true).apply();requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);})
                            .setNegativeButton("Not now",(d,w)->sp.edit().putBoolean("location_permission_asked",true).apply()).show();
                }else{sp.edit().putBoolean("location_permission_asked",true).apply();requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);}
            }else if(startWhenReady&&!sp.getBoolean("shown_location_settings",false)){
                sp.edit().putBoolean("shown_location_settings",true).apply();
                new AlertDialog.Builder(this).setTitle("Location permission is off").setMessage("Live work-location tracking cannot start until precise location is allowed for Employee Management.")
                        .setPositiveButton("Open App Settings",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Later",null).show();
            }
            return;
        }
        if(startWhenReady||pendingStart)startTrackingService();maybeGuideBackgroundLocation();
    }

    private void maybeGuideBackgroundLocation(){if(Build.VERSION.SDK_INT<29||checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)==PackageManager.PERMISSION_GRANTED)return;SharedPreferences sp=getSharedPreferences(PREFS,MODE_PRIVATE);if(sp.getBoolean("shown_background_guide",false))return;sp.edit().putBoolean("shown_background_guide",true).apply();new AlertDialog.Builder(this).setTitle("Keep tracking active with the screen off").setMessage("For reliable screen-off tracking during an active work session, allow background location. Android keeps this permission under App settings. You can review or change it at any time.").setPositiveButton("Open App Settings",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Later",null).show();}

    private void startTrackingService(){if(!getSharedPreferences(PREFS,MODE_PRIVATE).contains("tracking_token"))return;Intent i=new Intent(this,TrackingService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);pendingStart=false;}
    private void clearNativeSession(boolean revoke){SharedPreferences sp=getSharedPreferences(PREFS,MODE_PRIVATE);String token=sp.getString("tracking_token",null);stopService(new Intent(this,TrackingService.class));sp.edit().clear().apply();currentRole="";if(revoke&&token!=null)new Thread(()->{try{postJson(BASE+"/api/mobile-token-revoke.php",new JSONObject(),token,null);}catch(Exception ignored){}}).start();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQ_NOTIFICATIONS){ensureEmployeePermissions(pendingStart);return;}
        if(requestCode==REQ_LOCATION&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){if(pendingStart)startTrackingService();webView.reload();maybeGuideBackgroundLocation();}
    }
    @Override public void onBackPressed(){if(offlinePanel.getVisibility()==View.VISIBLE){offlinePanel.setVisibility(View.GONE);webView.reload();}else if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}

    public static JSONObject postJson(String endpoint,JSONObject body,String bearer,String cookie)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("X-Employee-App","android-combined-v1");if(bearer!=null&&!bearer.trim().isEmpty())c.setRequestProperty("Authorization","Bearer "+bearer);if(cookie!=null&&!cookie.trim().isEmpty())c.setRequestProperty("Cookie",cookie);c.setDoOutput(true);try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream is=(code>=200&&code<400)?c.getInputStream():c.getErrorStream();StringBuilder sb=new StringBuilder();if(is!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)sb.append(line);}JSONObject out=new JSONObject(sb.length()==0?"{}":sb.toString());out.put("http_status",code);c.disconnect();return out;}
}
