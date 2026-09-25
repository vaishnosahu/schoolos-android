package com.schooloss.nativeapp.core;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.schooloss.nativeapp.ApiClient;
import com.schooloss.nativeapp.MainActivity;
import com.schooloss.nativeapp.features.attendance.AttendanceActivity;
import com.schooloss.nativeapp.features.gallery.GalleryActivity;
import com.schooloss.nativeapp.features.messages.MessagesActivity;
import com.schooloss.nativeapp.features.results.ResultsActivity;
import com.schooloss.nativeapp.features.sfh.StudyFromHomeActivity;
import com.schooloss.nativeapp.features.transport.TransportActivity;
import com.schooloss.nativeapp.features.common.ModuleActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class SchoolOSActivity extends Activity {
    protected ApiClient api;
    protected final ExecutorService io = Executors.newSingleThreadExecutor();
    protected final Handler ui = new Handler(Looper.getMainLooper());
    protected JSONObject session;
    protected int roleColor = UiKit.BLUE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(248,250,255));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        api = new ApiClient(this);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    protected void requireSession(Runnable next) {
        showLoading("Opening SchoolOS","Checking your secure school session…");
        io.execute(() -> {
            try {
                JSONObject s = api.get("session");
                ui.post(() -> {
                    session = s;
                    applyRole();
                    if (s.optBoolean("school_required",false)) {
                        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                        finish();
                        return;
                    }
                    next.run();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    api.clearSession();
                    startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                });
            }
        });
    }

    protected void applyRole() {
        String role = session == null ? "admin" : session.optString("role","admin");
        if ("teacher".equals(role)) roleColor = Color.rgb(16,151,111);
        else if ("parent".equals(role)) roleColor = Color.rgb(199,72,150);
        else if ("student".equals(role)) roleColor = Color.rgb(231,120,44);
        else roleColor = UiKit.BLUE;
    }

    protected String roleLabel() {
        String role = session == null ? "admin" : session.optString("role","admin");
        if ("teacher".equals(role)) return "Teacher / Staff";
        if ("parent".equals(role)) return "Parent";
        if ("student".equals(role)) return "Student";
        return "Admin / Owner";
    }

    protected View topBar() {
        LinearLayout outer = UiKit.vertical(this);
        outer.setPadding(UiKit.dp(this,16),UiKit.dp(this,11),UiKit.dp(this,16),UiKit.dp(this,10));
        outer.setBackgroundColor(Color.WHITE);
        LinearLayout row = UiKit.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = UiKit.center(this,"S",18,Color.WHITE,true);
        mark.setBackground(UiKit.round(this,roleColor,13,roleColor));
        row.addView(mark,new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40)));

        LinearLayout copy = UiKit.vertical(this);
        copy.setPadding(UiKit.dp(this,10),0,0,0);
        copy.addView(UiKit.text(this,"SchoolOS",17,UiKit.TEXT,true));
        JSONObject school = session == null ? null : session.optJSONObject("school");
        String schoolName = school == null ? roleLabel() : school.optString("name",roleLabel());
        copy.addView(UiKit.text(this,schoolName+" · "+roleLabel(),10,UiKit.MUTED,false));
        row.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        TextView home = UiKit.center(this,"⌂",18,roleColor,true);
        home.setBackground(UiKit.round(this,UiKit.tint(roleColor,.10f),99,UiKit.tint(roleColor,.18f)));
        home.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        row.addView(home,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,38)));
        outer.addView(row);
        return outer;
    }

    protected LinearLayout page(String back, View.OnClickListener backAction, String kicker, String title, String subtitle) {
        LinearLayout p = UiKit.vertical(this);
        p.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,24));
        if (back != null) {
            TextView b = UiKit.text(this,"‹  "+back,12,roleColor,true);
            b.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,12));
            b.setOnClickListener(backAction);
            p.addView(b);
        }
        if (kicker != null && !kicker.isEmpty()) p.addView(UiKit.label(this,kicker,9,roleColor));
        p.addView(UiKit.title(this,title,24));
        if (subtitle != null && !subtitle.isEmpty()) p.addView(UiKit.body(this,subtitle,11));
        return p;
    }

    protected void setScrollable(LinearLayout page) {
        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(UiKit.BG);
        root.addView(topBar());
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(root);
    }

    protected void showLoading(String title, String subtitle) {
        LinearLayout page = UiKit.vertical(this);
        page.setGravity(Gravity.CENTER);
        page.setPadding(UiKit.dp(this,28),UiKit.dp(this,28),UiKit.dp(this,28),UiKit.dp(this,28));
        page.setBackgroundColor(UiKit.BG);
        TextView mark = UiKit.center(this,"S",24,Color.WHITE,true);
        mark.setBackground(UiKit.round(this,UiKit.BLUE,18,UiKit.BLUE));
        page.addView(mark,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,58)));
        page.addView(UiKit.gap(this,15));
        page.addView(UiKit.center(this,title,20,UiKit.TEXT,true));
        TextView s = UiKit.center(this,subtitle,11,UiKit.MUTED,false);
        s.setPadding(0,UiKit.dp(this,6),0,UiKit.dp(this,15));
        page.addView(s);
        page.addView(new ProgressBar(this));
        setContentView(page);
    }

    protected void showError(String heading, Exception e, Runnable retry) {
        LinearLayout page = UiKit.vertical(this);
        page.setGravity(Gravity.CENTER);
        page.setPadding(UiKit.dp(this,24),UiKit.dp(this,24),UiKit.dp(this,24),UiKit.dp(this,24));
        page.setBackgroundColor(UiKit.BG);
        LinearLayout card = UiKit.card(this);
        card.setPadding(UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20));
        card.addView(UiKit.label(this,"SCHOOLOS",9,Color.rgb(190,55,70)));
        card.addView(UiKit.title(this,heading,22));
        card.addView(UiKit.body(this,message(e),12));
        card.addView(UiKit.gap(this,14));
        android.widget.Button b = UiKit.button(this,"Try Again",UiKit.BLUE,Color.WHITE);
        b.setOnClickListener(v -> retry.run());
        card.addView(b);
        page.addView(card,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);
    }

    protected String message(Exception e) {
        String m = e == null ? null : e.getMessage();
        return m == null || m.trim().isEmpty() ? "SchoolOS could not complete this request." : m;
    }

    protected void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    public void openModule(String key) {
        Intent i;
        switch (key) {
            case "attendance": i = new Intent(this, AttendanceActivity.class); break;
            case "messages":
            case "updates":
            case "alerts": i = new Intent(this, MessagesActivity.class).putExtra("mode",key); break;
            case "study_from_home":
            case "learning":
            case "teaching": i = new Intent(this, StudyFromHomeActivity.class).putExtra("mode",key); break;
            case "results":
            case "exams": i = new Intent(this, ResultsActivity.class).putExtra("mode",key); break;
            case "gallery": i = new Intent(this, GalleryActivity.class); break;
            case "transport": i = new Intent(this, TransportActivity.class); break;
            default: i = new Intent(this, ModuleActivity.class).putExtra("module",key); break;
        }
        startActivity(i);
    }

    protected LinearLayout simpleRow(String title, String subtitle) {
        LinearLayout c = UiKit.card(this);
        c.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));
        c.addView(UiKit.text(this,title,12,UiKit.TEXT,true));
        if (subtitle != null && !subtitle.isEmpty()) c.addView(UiKit.text(this,subtitle,10,UiKit.MUTED,false));
        return c;
    }

    protected String pretty(String s) {
        if (s == null) return "";
        String[] parts = s.replace('_',' ').split(" ");
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (b.length()>0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }

    protected JSONArray rows(JSONObject data) {
        JSONArray a = data == null ? null : data.optJSONArray("rows");
        return a == null ? new JSONArray() : a;
    }
}
