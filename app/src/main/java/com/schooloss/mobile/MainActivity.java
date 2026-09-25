package com.schooloss.nativeapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private enum Role {
        ADMIN("Admin / Owner", "COMMAND CENTER", "School control, one place",
                "Operations, people, finance and academics in one focused workspace.",
                Color.rgb(54,87,214)),
        TEACHER("Teacher / Staff", "TEACHING WORKSPACE", "Your teaching day",
                "Classes, attendance, assignments and learning work without admin noise.",
                Color.rgb(16,151,111)),
        PARENT("Parent", "MY CHILD", "Stay close to school life",
                "Attendance, payments, results, transport and school updates.",
                Color.rgb(199,72,150)),
        STUDENT("Student", "MY LEARNING", "Your school day, simplified",
                "Schedule, learning, assignments, results and updates in one clean view.",
                Color.rgb(231,120,44));

        final String label;
        final String eyebrow;
        final String title;
        final String subtitle;
        final int color;

        Role(String label, String eyebrow, String title, String subtitle, int color) {
            this.label = label;
            this.eyebrow = eyebrow;
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
        }
    }

    private final int BG = Color.rgb(247,249,253);
    private final int TEXT = Color.rgb(25,33,50);
    private final int MUTED = Color.rgb(106,116,140);
    private final int LINE = Color.rgb(229,233,242);
    private final int BLUE = Color.rgb(54,87,214);

    private Role selectedRole = Role.ADMIN;
    private boolean inWorkspace = false;
    private String selectedTab = "home";
    private ApiClient api;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private JSONObject session;
    private JSONObject homeData;
    private final List<Integer> attendanceStudentIds = new ArrayList<>();
    private final List<Spinner> attendanceStatusInputs = new ArrayList<>();
    private final List<EditText> attendanceRemarkInputs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(248,250,255));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        api = new ApiClient(this);
        renderLoading("Opening SchoolOS", "Checking your secure school session…");
        restoreSession();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inWorkspace && !"home".equals(selectedTab)) {
            selectedTab = "home";
            renderWorkspace();
            return;
        }
        if (inWorkspace) {
            moveTaskToBack(true);
            return;
        }
        super.onBackPressed();
    }

    private void restoreSession() {
        io.execute(() -> {
            try {
                JSONObject s = api.get("session");
                ui.post(() -> acceptSession(s));
            } catch (Exception e) {
                ui.post(() -> renderLogin(null));
            }
        });
    }

    private void acceptSession(JSONObject s) {
        session = s;
        if (s.optBoolean("school_required", false)) {
            renderSchoolChooser(s.optJSONArray("memberships"));
            return;
        }
        setRoleFromSession();
        inWorkspace = true;
        renderLoading("Loading your workspace", "Syncing live SchoolOS data…");
        loadHome();
    }

    private void loadHome() {
        io.execute(() -> {
            try {
                JSONObject r = api.get("home");
                JSONObject d = r.optJSONObject("data");
                JSONObject s = r.optJSONObject("session");
                if (d == null) throw new Exception("SchoolOS did not return home data.");
                if (s != null) session = s;
                homeData = d;
                ui.post(() -> {
                    setRoleFromSession();
                    selectedTab = "home";
                    inWorkspace = true;
                    renderWorkspace();
                });
            } catch (Exception e) {
                ui.post(() -> renderConnectionError("Could not load SchoolOS", message(e)));
            }
        });
    }

    private void renderLogin(String initialError) {
        inWorkspace = false;
        session = null;
        homeData = null;
        selectedTab = "home";
        selectedRole = Role.ADMIN;

        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(16), dp(18), dp(24));
        page.setBackgroundColor(BG);
        page.addView(brandRow());
        page.addView(gap(14));
        page.addView(loginHero());
        page.addView(gap(16));

        LinearLayout form = card();
        form.setPadding(dp(18), dp(18), dp(18), dp(18));
        form.addView(label("WELCOME BACK", 10, BLUE, true));
        form.addView(title("Sign in to SchoolOS", 26));
        form.addView(body("Use your existing SchoolOS account. Your school, role and permissions are resolved by the existing SchoolOS authority.", 13));
        form.addView(gap(14));

        TextView error = text("", 11, Color.rgb(190,45,60), true);
        error.setPadding(dp(12), dp(10), dp(12), dp(10));
        error.setBackground(round(Color.rgb(255,244,245), 12, Color.rgb(250,210,216)));
        error.setVisibility(View.GONE);
        if (initialError != null && !initialError.isEmpty()) {
            error.setText(initialError);
            error.setVisibility(View.VISIBLE);
        }
        form.addView(error);
        form.addView(gap(10));

        form.addView(fieldLabel("Email address"));
        EditText email = input("name@school.com", false);
        form.addView(email);
        form.addView(gap(11));
        form.addView(fieldLabel("Password"));
        EditText password = input("Password", true);
        form.addView(password);
        form.addView(gap(15));

        Button open = button("Sign In", BLUE, Color.WHITE);
        open.setOnClickListener(v -> doLogin(email, password, open, error));
        form.addView(open);
        form.addView(gap(10));
        TextView note = text("Secure native session · existing SchoolOS database · role-aware access", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        form.addView(note);
        page.addView(form);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
    }

    private void doLogin(EditText email, EditText password, Button button, TextView error) {
        String e = email.getText().toString().trim();
        String p = password.getText().toString();
        if (e.isEmpty() || p.isEmpty()) {
            error.setText("Enter your SchoolOS email and password.");
            error.setVisibility(View.VISIBLE);
            return;
        }
        button.setEnabled(false);
        button.setText("Signing in…");
        error.setVisibility(View.GONE);
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", e);
                body.put("password", p);
                JSONObject s = api.post("login", body);
                ui.post(() -> acceptSession(s));
            } catch (Exception ex) {
                ui.post(() -> {
                    button.setEnabled(true);
                    button.setText("Sign In");
                    error.setText(message(ex));
                    error.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private View brandRow() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = text("S", 22, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(BLUE, 15, BLUE));
        row.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = vertical();
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(text("SchoolOS", 20, TEXT, true));
        copy.addView(label("SMART SCHOOL WORKSPACE", 9, MUTED, true));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = text("NATIVE UI", 9, BLUE, true);
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));
        badge.setBackground(round(Color.rgb(237,241,255), 99, Color.rgb(218,225,250)));
        row.addView(badge);
        return row;
    }

    private View loginHero() {
        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(20), dp(20), dp(20));
        box.setBackground(gradient(new int[]{
                Color.rgb(47,88,218),
                Color.rgb(90,74,229),
                Color.rgb(162,72,198)
        }, 25));
        box.setElevation(dp(5));

        box.addView(label("ONE APP · EVERY SCHOOL ROLE", 10, Color.argb(220,255,255,255), true));
        TextView big = text("A cleaner SchoolOS experience on Android", 24, Color.WHITE, true);
        big.setPadding(0, dp(7), 0, 0);
        box.addView(big);
        TextView small = text("Compact navigation, native controls and role-focused workspaces designed for daily school use.", 13, Color.argb(225,255,255,255), false);
        small.setPadding(0, dp(8), 0, 0);
        box.addView(small);

        LinearLayout chips = horizontal();
        chips.setPadding(0, dp(15), 0, 0);
        String[] names = {"Admin", "Teacher", "Parent", "Student"};
        for (int i = 0; i < names.length; i++) {
            TextView chip = text(names[i], 10, Color.WHITE, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(7), dp(8), dp(7));
            chip.setBackground(round(Color.argb(30,255,255,255), 99, Color.argb(45,255,255,255)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.leftMargin = dp(5);
            chips.addView(chip, lp);
        }
        box.addView(chips);
        return box;
    }

    private View roleGrid() {
        LinearLayout grid = vertical();
        Role[] roles = Role.values();
        for (int r = 0; r < 2; r++) {
            LinearLayout row = horizontal();
            for (int c = 0; c < 2; c++) {
                Role role = roles[r * 2 + c];
                boolean active = role == selectedRole;
                LinearLayout item = vertical();
                item.setPadding(dp(12), dp(11), dp(12), dp(11));
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setBackground(round(
                        active ? tint(role.color, 0.10f) : Color.rgb(250,251,254),
                        15,
                        active ? tint(role.color, 0.32f) : LINE
                ));
                item.setOnClickListener(v -> {
                    selectedRole = role;
                    renderLogin(null);
                });
                item.addView(text(role.label, 12, active ? role.color : TEXT, true));
                item.addView(text(roleHint(role), 10, MUTED, false));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(66), 1f);
                if (c > 0) lp.leftMargin = dp(8);
                if (r > 0) lp.topMargin = dp(8);
                row.addView(item, lp);
            }
            grid.addView(row);
        }
        return grid;
    }

    private String roleHint(Role role) {
        switch (role) {
            case ADMIN: return "Operations & control";
            case TEACHER: return "Classes & teaching";
            case PARENT: return "Child & school";
            default: return "Learning & updates";
        }
    }

    private void renderWorkspace() {
        inWorkspace = true;
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        root.addView(topBar());

        FrameLayout contentFrame = new FrameLayout(this);
        if ("home".equals(selectedTab)) contentFrame.addView(homeScreen());
        else contentFrame.addView(moduleLoadingScreen());
        root.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav());
        setContentView(root);
        if (!"home".equals(selectedTab)) loadModule(selectedTab);
    }

    private View topBar() {
        LinearLayout outer = vertical();
        outer.setPadding(dp(16), dp(11), dp(16), dp(10));
        outer.setBackgroundColor(Color.WHITE);

        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = text("S", 18, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(selectedRole.color, 13, selectedRole.color));
        row.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout copy = vertical();
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text("SchoolOS", 17, TEXT, true));
        JSONObject school = session == null ? null : session.optJSONObject("school");
        String schoolName = school == null ? selectedRole.label + " workspace" : school.optString("name", selectedRole.label + " workspace");
        copy.addView(text(schoolName + " · " + selectedRole.label, 10, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String name = session == null ? selectedRole.label : session.optString("name", selectedRole.label);
        TextView profile = text(initials(name), 11, selectedRole.color, true);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(round(tint(selectedRole.color, 0.10f), 99, tint(selectedRole.color, 0.18f)));
        profile.setOnClickListener(v -> openTab("profile"));
        row.addView(profile, new LinearLayout.LayoutParams(dp(38), dp(38)));

        outer.addView(row);
        return outer;
    }

    private String initials(String s) {
        String value = s == null ? "" : s.trim();
        if (value.isEmpty()) return "SO";
        String[] parts = value.split("\\s+");
        String out = "";
        for (int i=0; i<Math.min(2,parts.length); i++) if (!parts[i].isEmpty()) out += parts[i].substring(0,1).toUpperCase();
        return out.isEmpty() ? "SO" : out;
    }

    private View homeScreen() {
        LinearLayout page = vertical();
        page.setPadding(dp(16), dp(14), dp(16), dp(20));

        JSONObject heroData = homeData == null ? null : homeData.optJSONObject("hero");
        LinearLayout hero = vertical();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(gradient(new int[]{selectedRole.color, tint(selectedRole.color, -0.18f)}, 23));
        hero.addView(label(heroData == null ? selectedRole.eyebrow : heroData.optString("eyebrow", selectedRole.eyebrow), 9, Color.argb(220,255,255,255), true));
        TextView h = text(heroData == null ? selectedRole.title : heroData.optString("title", selectedRole.title), 23, Color.WHITE, true);
        h.setPadding(0, dp(5), 0, 0);
        hero.addView(h);
        TextView sub = text(heroData == null ? selectedRole.subtitle : heroData.optString("subtitle", selectedRole.subtitle), 12, Color.argb(225,255,255,255), false);
        sub.setPadding(0, dp(7), 0, 0);
        hero.addView(sub);

        JSONArray metrics = homeData == null ? null : homeData.optJSONArray("metrics");
        if (metrics != null && metrics.length() > 0) hero.addView(metricGrid(metrics, true));
        page.addView(hero);

        addContextSwitcher(page);

        page.addView(gap(15));
        page.addView(sectionHeading("Quick access", "Live modules mapped to your current SchoolOS role and permissions."));
        JSONArray modules = homeData == null ? null : homeData.optJSONArray("modules");
        if (modules != null) page.addView(moduleGrid(modules));

        JSONArray rows = homeData == null ? null : homeData.optJSONArray("rows");
        if (rows != null && rows.length() > 0) {
            page.addView(gap(14));
            page.addView(sectionHeading("Today", "Live SchoolOS workspace snapshot"));
            page.addView(rowsList(rows));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

    private void addContextSwitcher(LinearLayout page) {
        if (session == null) return;
        String role = session.optString("role", "admin");
        if ("parent".equals(role)) {
            JSONArray kids = session.optJSONArray("children");
            if (kids != null && kids.length() > 1) {
                page.addView(gap(12));
                page.addView(sectionHeading("Child context", "Choose which linked child you are viewing."));
                LinearLayout holder = vertical();
                for (int i=0;i<kids.length();i++) {
                    JSONObject k = kids.optJSONObject(i); if (k == null) continue;
                    TextView item = text((k.optBoolean("selected",false) ? "✓  " : "") + k.optString("name","Child"), 11, k.optBoolean("selected",false) ? selectedRole.color : TEXT, true);
                    item.setPadding(dp(12),dp(11),dp(12),dp(11));
                    item.setBackground(round(k.optBoolean("selected",false) ? tint(selectedRole.color,.10f) : Color.WHITE,13,k.optBoolean("selected",false) ? tint(selectedRole.color,.25f) : LINE));
                    final int id = k.optInt("id");
                    item.setOnClickListener(v -> selectChild(id));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i>0) lp.topMargin=dp(6);
                    holder.addView(item,lp);
                }
                page.addView(holder);
            }
        } else if ("teacher".equals(role)) {
            JSONArray contexts = session.optJSONArray("teaching_contexts");
            if (contexts != null && contexts.length() > 1) {
                page.addView(gap(12));
                page.addView(sectionHeading("Teaching context", "Switch your current allocation."));
                LinearLayout holder = vertical();
                for (int i=0;i<contexts.length();i++) {
                    JSONObject x=contexts.optJSONObject(i); if(x==null)continue;
                    TextView item=text(x.optString("label","Teaching context"),11,TEXT,true);
                    item.setPadding(dp(12),dp(11),dp(12),dp(11));
                    item.setBackground(round(Color.WHITE,13,LINE));
                    final String key=x.optString("key","");
                    item.setOnClickListener(v -> selectTeachingContext(key));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
                    if(i>0)lp.topMargin=dp(6);
                    holder.addView(item,lp);
                }
                page.addView(holder);
            }
        }
    }

    private View metricGrid(JSONArray metrics, boolean heroStyle) {
        LinearLayout wrap=vertical();
        wrap.setPadding(0,dp(12),0,0);
        for(int i=0;i<metrics.length();i+=2){
            LinearLayout row=horizontal();
            for(int j=0;j<2;j++){
                int idx=i+j;
                if(idx>=metrics.length()){row.addView(new Space(this),new LinearLayout.LayoutParams(0,1,1f));continue;}
                JSONObject m=metrics.optJSONObject(idx); if(m==null)continue;
                LinearLayout box=vertical(); box.setGravity(Gravity.CENTER);
                box.setBackground(round(heroStyle?Color.argb(25,255,255,255):Color.WHITE,13,heroStyle?Color.argb(38,255,255,255):LINE));
                box.addView(centerText(m.optString("value","—"),17,heroStyle?Color.WHITE:TEXT,true));
                box.addView(centerText(m.optString("label",""),9,heroStyle?Color.argb(220,255,255,255):MUTED,true));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(60),1f);
                if(j>0)lp.leftMargin=dp(7);
                if(i>0)lp.topMargin=dp(7);
                row.addView(box,lp);
            }
            wrap.addView(row);
        }
        return wrap;
    }

    private TextView centerText(String value,int sp,int color,boolean bold){
        TextView t=text(value,sp,color,bold);t.setGravity(Gravity.CENTER);return t;
    }

    private View moduleGrid(JSONArray items) {
        LinearLayout wrap = vertical();
        for (int i = 0; i < items.length(); i += 2) {
            LinearLayout row = horizontal();
            for (int col = 0; col < 2; col++) {
                int idx = i + col;
                if (idx >= items.length()) {
                    row.addView(new Space(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
                    continue;
                }
                JSONObject m=items.optJSONObject(idx); if(m==null)continue;
                String key=m.optString("key","");
                String name=m.optString("label",key);
                LinearLayout item = card();
                item.setPadding(dp(13), dp(13), dp(13), dp(13));
                item.setOnClickListener(v -> openTab(key));
                TextView icon = text(moduleInitial(name), 12, selectedRole.color, true);
                icon.setGravity(Gravity.CENTER);
                icon.setBackground(round(tint(selectedRole.color, 0.10f), 12, tint(selectedRole.color, 0.16f)));
                item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
                TextView nameTv = text(name, 12, TEXT, true);
                nameTv.setPadding(0, dp(8), 0, 0);
                item.addView(nameTv);
                item.addView(text(m.optString("subtitle","Open"), 9, MUTED, false));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(112), 1f);
                if (col > 0) lp.leftMargin = dp(8);
                if (i > 0) lp.topMargin = dp(8);
                row.addView(item, lp);
            }
            wrap.addView(row);
        }
        return wrap;
    }

    private String[] homeModules(Role role) {
        switch (role) {
            case ADMIN:
                return new String[]{"Attendance","Students","Staff","Fees & Payments","Academics","Exams & Results","Study From Home","Transport","Reports","Settings"};
            case TEACHER:
                return new String[]{"Today Classes","Class Attendance","Students","Assignments","Study From Home","Notices","Results","Profile"};
            case PARENT:
                return new String[]{"My Child","Attendance","Fees & Payments","Results","Timetable","Notices","Transport","Study From Home"};
            default:
                return new String[]{"Learning","Attendance","Timetable","Assignments","Exams & Results","Notices","Study From Home","Profile"};
        }
    }

    private View moduleGrid(String[] items) {
        LinearLayout wrap = vertical();
        for (int i = 0; i < items.length; i += 2) {
            LinearLayout row = horizontal();
            for (int c = 0; c < 2; c++) {
                int idx = i + c;
                if (idx >= items.length) {
                    Space blank = new Space(this);
                    row.addView(blank, new LinearLayout.LayoutParams(0, dp(1), 1f));
                    continue;
                }
                String name = items[idx];
                LinearLayout item = card();
                item.setPadding(dp(13), dp(13), dp(13), dp(13));
                item.setOnClickListener(v -> {
                    selectedTab = name;
                    renderWorkspace();
                });

                TextView icon = text(moduleInitial(name), 12, selectedRole.color, true);
                icon.setGravity(Gravity.CENTER);
                icon.setBackground(round(tint(selectedRole.color, 0.10f), 12, tint(selectedRole.color, 0.16f)));
                item.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
                TextView nameTv = text(name, 12, TEXT, true);
                nameTv.setPadding(0, dp(9), 0, 0);
                item.addView(nameTv);
                item.addView(text("Open", 10, MUTED, false));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(105), 1f);
                if (c > 0) lp.leftMargin = dp(8);
                if (i > 0) lp.topMargin = dp(8);
                row.addView(item, lp);
            }
            wrap.addView(row);
        }
        return wrap;
    }

    private String moduleInitial(String name) {
        String clean = name.replace("&", "").trim();
        if (clean.length() == 0) return "•";
        String[] parts = clean.split("\\s+");
        if (parts.length > 1) return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return ("" + parts[0].charAt(0)).toUpperCase();
    }

    private View tabScreen(String titleText) {
        return moduleLoadingScreen();
    }

    private View moduleLoadingScreen() {
        LinearLayout page=vertical();
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(24),dp(24),dp(24),dp(24));
        ProgressBar progress=new ProgressBar(this);
        page.addView(progress);
        TextView t=text("Loading live SchoolOS data…",12,MUTED,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0,dp(12),0,0);
        page.addView(t);
        return page;
    }

    private void loadModule(String name) {
        io.execute(() -> {
            try {
                JSONObject r=api.getModule(name);
                JSONObject data=r.optJSONObject("data");
                if(data==null)throw new Exception("SchoolOS did not return module data.");
                ui.post(() -> renderModuleScreen(data));
            } catch(Exception e) {
                ui.post(() -> renderConnectionError("Could not load this module",message(e)));
            }
        });
    }

    private void renderModuleScreen(JSONObject data) {
        LinearLayout root=vertical();
        root.setBackgroundColor(BG);
        root.addView(topBar());

        LinearLayout page=vertical();
        page.setPadding(dp(16),dp(15),dp(16),dp(22));
        TextView back=text("‹  Home",12,selectedRole.color,true);
        back.setPadding(0,dp(5),0,dp(12));
        back.setOnClickListener(v -> openTab("home"));
        page.addView(back);
        page.addView(label(selectedRole.eyebrow,9,selectedRole.color,true));
        page.addView(title(data.optString("title",pretty(selectedTab)),25));
        page.addView(body(data.optString("subtitle","Live SchoolOS data"),12));

        JSONArray metrics=data.optJSONArray("metrics");
        if(metrics!=null&&metrics.length()>0){page.addView(gap(12));page.addView(metricGrid(metrics,false));}

        if("messages".equals(selectedTab)&&data.optBoolean("can_compose",false)){
            page.addView(gap(12));
            Button compose=button("New Conversation",selectedRole.color,Color.WHITE);
            compose.setOnClickListener(v -> loadMessageCompose());
            page.addView(compose);
        }
        if(("updates".equals(selectedTab)||"alerts".equals(selectedTab))){
            page.addView(gap(12));
            Button readAll=button("Mark All Notifications Read",Color.WHITE,selectedRole.color);
            readAll.setBackground(round(Color.WHITE,14,tint(selectedRole.color,.22f)));
            readAll.setOnClickListener(v -> markAllNotifications());
            page.addView(readAll);
        }
        if("profile".equals(selectedTab)||"more".equals(selectedTab))addAccountControls(page);

        JSONArray rows=data.optJSONArray("rows");
        page.addView(gap(13));
        page.addView(sectionHeading("Overview",rows==null||rows.length()==0?"No records are available for this context.":rows.length()+" live records"));
        if(rows!=null)page.addView(rowsList(rows));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        root.addView(bottomNav());
        setContentView(root);
    }

    private View rowsList(JSONArray rows) {
        LinearLayout list=vertical();
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i); if(r==null)continue;
            LinearLayout item=card();item.setPadding(dp(13),dp(12),dp(13),dp(12));
            LinearLayout line=horizontal();line.setGravity(Gravity.CENTER_VERTICAL);
            TextView lead=text(moduleInitial(r.optString("title","•")),11,selectedRole.color,true);
            lead.setGravity(Gravity.CENTER);lead.setBackground(round(tint(selectedRole.color,.10f),11,tint(selectedRole.color,.17f)));
            line.addView(lead,new LinearLayout.LayoutParams(dp(34),dp(34)));
            LinearLayout copy=vertical();copy.setPadding(dp(10),0,dp(5),0);
            copy.addView(text(r.optString("title","Record"),12,TEXT,true));
            if(!r.optString("subtitle","").isEmpty())copy.addView(text(r.optString("subtitle",""),10,MUTED,false));
            if(!r.optString("meta","").isEmpty())copy.addView(text(r.optString("meta",""),9,selectedRole.color,true));
            line.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            item.addView(line);

            String type=r.optString("type","");
            String target=r.optString("module","");
            int id=r.optInt("id",0);

            if("attendance_context".equals(type)){
                final int classId=r.optInt("class_id",id);
                final int sectionId=r.optInt("section_id",0);
                item.setOnClickListener(v -> loadAttendanceRegister(classId,sectionId,currentDate()));
            }else if("conversation".equals(type)&&id>0){
                final int conversationId=id;
                item.setOnClickListener(v -> loadMessageThread(conversationId));
            }else if(!target.isEmpty()){
                item.setOnClickListener(v -> openTab(target));
            }

            if("notification".equals(type)&&!r.optBoolean("read",false)&&id>0){
                Button mark=button("Mark read",tint(selectedRole.color,.08f),selectedRole.color);
                mark.setBackground(round(tint(selectedRole.color,.08f),12,tint(selectedRole.color,.20f)));
                mark.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)));
                final int notificationId=id;
                mark.setOnClickListener(v -> markNotification(notificationId));
                item.addView(gap(7));item.addView(mark);
            }
            if("announcement".equals(type)&&r.optBoolean("requires_ack",false)&&!r.optBoolean("acknowledged",false)&&id>0){
                Button ack=button("Acknowledge",tint(selectedRole.color,.08f),selectedRole.color);
                ack.setBackground(round(tint(selectedRole.color,.08f),12,tint(selectedRole.color,.20f)));
                ack.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(42)));
                final int announcementId=id;
                ack.setOnClickListener(v -> ackAnnouncement(announcementId));
                item.addView(gap(7));item.addView(ack);
            }
            if("transport_trip".equals(type)&&id>0){
                JSONArray actions=r.optJSONArray("actions");
                if(actions!=null&&actions.length()>0){
                    LinearLayout controls=horizontal();controls.setPadding(0,dp(8),0,0);
                    for(int a=0;a<actions.length();a++){
                        JSONObject action=actions.optJSONObject(a);if(action==null)continue;
                        String key=action.optString("key","");
                        String labelText=action.optString("label",pretty(key));
                        Button b=button(labelText,"trip_cancel".equals(key)?Color.WHITE:selectedRole.color,"trip_cancel".equals(key)?Color.rgb(180,55,65):Color.WHITE);
                        if("trip_cancel".equals(key))b.setBackground(round(Color.WHITE,12,Color.rgb(245,205,212)));
                        final int tripId=id;final String actionKey=key;
                        b.setOnClickListener(v -> confirmTransportAction(tripId,actionKey,labelText));
                        LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(0,dp(42),1f);if(a>0)blp.leftMargin=dp(6);controls.addView(b,blp);
                    }
                    item.addView(controls);
                }
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            if(i>0)lp.topMargin=dp(8);
            list.addView(item,lp);
        }
        return list;
    }

    private String currentDate(){
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);
        return f.format(new java.util.Date());
    }

    private void loadAttendanceRegister(int classId,int sectionId,String date){
        renderLoading("Opening attendance","Loading the class register…");
        io.execute(() -> {
            try{
                String q="class_id="+classId+"&section_id="+sectionId+"&date="+date;
                JSONObject r=api.get("attendance_register",q);
                JSONObject data=r.optJSONObject("data");
                if(data==null)throw new Exception("Attendance register is unavailable.");
                ui.post(() -> renderAttendanceRegister(data));
            }catch(Exception e){ui.post(() -> renderConnectionError("Could not open attendance",message(e)));}
        });
    }

    private void renderAttendanceRegister(JSONObject data){
        attendanceStudentIds.clear();attendanceStatusInputs.clear();attendanceRemarkInputs.clear();
        LinearLayout root=vertical();root.setBackgroundColor(BG);root.addView(topBar());
        LinearLayout page=vertical();page.setPadding(dp(16),dp(14),dp(16),dp(24));

        TextView back=text("‹  Attendance",12,selectedRole.color,true);back.setPadding(0,dp(4),0,dp(12));back.setOnClickListener(v -> openTab("attendance"));page.addView(back);
        page.addView(label("ATTENDANCE REGISTER",9,selectedRole.color,true));
        page.addView(title(data.optString("class_name","Attendance"),24));

        LinearLayout dateCard=card();dateCard.setPadding(dp(13),dp(12),dp(13),dp(12));
        dateCard.addView(fieldLabel("Attendance date"));
        EditText date=input(data.optString("date",currentDate()),false);date.setText(data.optString("date",currentDate()));date.setInputType(InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE);
        dateCard.addView(date);
        Button reload=button("Load Date",Color.WHITE,selectedRole.color);reload.setBackground(round(Color.WHITE,12,tint(selectedRole.color,.22f)));reload.setOnClickListener(v -> loadAttendanceRegister(data.optInt("class_id"),data.optInt("section_id"),date.getText().toString().trim()));
        dateCard.addView(gap(7));dateCard.addView(reload);page.addView(dateCard);

        String state=data.optString("submission_status","open");
        page.addView(gap(12));page.addView(sectionHeading("Students",data.optInt("student_count",0)+" students · "+pretty(state)));

        JSONArray valid=data.optJSONArray("valid_statuses");List<String> options=new ArrayList<>();
        if(valid!=null)for(int i=0;i<valid.length();i++)options.add(valid.optString(i));
        if(options.isEmpty()){options.add("present");options.add("absent");options.add("late");options.add("half_day");options.add("leave");options.add("holiday");}
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options);

        JSONArray rows=data.optJSONArray("rows");
        if(rows!=null)for(int i=0;i<rows.length();i++){
            JSONObject s=rows.optJSONObject(i);if(s==null)continue;
            LinearLayout item=card();item.setPadding(dp(13),dp(12),dp(13),dp(12));
            item.addView(text(s.optString("name","Student"),13,TEXT,true));
            String idLine=s.optString("admission_no","");if(!s.optString("roll_no","").isEmpty())idLine+=(idLine.isEmpty()?"":" · ")+"Roll "+s.optString("roll_no","");
            if(!idLine.isEmpty())item.addView(text(idLine,10,MUTED,false));

            Spinner status=new Spinner(this);status.setAdapter(adapter);int pos=options.indexOf(s.optString("status","present"));status.setSelection(pos<0?0:pos);
            item.addView(status,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)));
            EditText remarks=input("Remarks (optional)",false);remarks.setText(s.optString("remarks",""));item.addView(remarks);

            attendanceStudentIds.add(s.optInt("id"));attendanceStatusInputs.add(status);attendanceRemarkInputs.add(remarks);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=dp(8);page.addView(item,lp);
        }

        if(data.optBoolean("can_mark",false)){
            page.addView(gap(12));Button save=button("Submit Attendance",selectedRole.color,Color.WHITE);
            save.setOnClickListener(v -> submitAttendance(data,date.getText().toString().trim()));page.addView(save);
        }else{
            page.addView(gap(12));page.addView(body(data.optBoolean("locked",false)?"This register is locked.":"Attendance editing is not available for this date or role.",11));
        }

        if(data.optBoolean("can_manage",false)){
            page.addView(gap(8));
            if(data.optBoolean("locked",false)){
                Button reopen=button("Reopen Register",Color.WHITE,Color.rgb(180,55,65));reopen.setBackground(round(Color.WHITE,14,Color.rgb(245,205,212)));
                reopen.setOnClickListener(v -> showReopenDialog(data,date.getText().toString().trim()));page.addView(reopen);
            }else{
                Button lock=button("Lock Register",Color.WHITE,selectedRole.color);lock.setBackground(round(Color.WHITE,14,tint(selectedRole.color,.22f)));
                lock.setOnClickListener(v -> confirmAttendanceLock(data,date.getText().toString().trim()));page.addView(lock);
            }
        }

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));root.addView(bottomNav());setContentView(root);
    }

    private void submitAttendance(JSONObject register,String date){
        renderLoading("Submitting attendance","Applying SchoolOS attendance rules…");
        io.execute(() -> {
            try{
                JSONObject b=new JSONObject();b.put("class_id",register.optInt("class_id"));b.put("section_id",register.optInt("section_id"));b.put("attendance_date",date);
                JSONArray statuses=new JSONArray();
                for(int i=0;i<attendanceStudentIds.size();i++){
                    JSONObject row=new JSONObject();row.put("student_id",attendanceStudentIds.get(i));row.put("status",String.valueOf(attendanceStatusInputs.get(i).getSelectedItem()));row.put("remarks",attendanceRemarkInputs.get(i).getText().toString().trim());statuses.put(row);
                }
                b.put("statuses",statuses);JSONObject r=api.post("attendance_save",b);JSONObject updated=r.optJSONObject("register");
                ui.post(() -> {Toast.makeText(this,r.optString("message","Attendance saved."),Toast.LENGTH_SHORT).show();if(updated!=null)renderAttendanceRegister(updated);else loadAttendanceRegister(register.optInt("class_id"),register.optInt("section_id"),date);});
            }catch(Exception e){ui.post(() -> renderConnectionError("Attendance was not saved",message(e)));}
        });
    }

    private void confirmAttendanceLock(JSONObject register,String date){
        new AlertDialog.Builder(this).setTitle("Lock attendance register?").setMessage("After locking, normal attendance editing is blocked until an attendance manager reopens it.")
            .setNegativeButton("Cancel",null).setPositiveButton("Lock",(d,w) -> attendanceControl("attendance_lock",register,date,null)).show();
    }

    private void showReopenDialog(JSONObject register,String date){
        EditText reason=input("Reason to reopen",false);
        LinearLayout box=vertical();box.setPadding(dp(18),dp(8),dp(18),0);box.addView(reason);
        new AlertDialog.Builder(this).setTitle("Reopen attendance register").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Reopen",(d,w) -> {
            String value=reason.getText().toString().trim();if(value.isEmpty()){Toast.makeText(this,"Reopen reason is required.",Toast.LENGTH_SHORT).show();return;}attendanceControl("attendance_reopen",register,date,value);
        }).show();
    }

    private void attendanceControl(String action,JSONObject register,String date,String reason){
        renderLoading("Updating register","Applying attendance manager controls…");
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("class_id",register.optInt("class_id"));b.put("section_id",register.optInt("section_id"));b.put("attendance_date",date);if(reason!=null)b.put("reason",reason);JSONObject r=api.post(action,b);JSONObject updated=r.optJSONObject("register");ui.post(() -> {Toast.makeText(this,r.optString("message","Attendance updated."),Toast.LENGTH_SHORT).show();if(updated!=null)renderAttendanceRegister(updated);});}
            catch(Exception e){ui.post(() -> renderConnectionError("Register update failed",message(e)));}
        });
    }

    private void loadMessageThread(int id){
        renderLoading("Opening conversation","Loading the secure message thread…");
        io.execute(() -> {
            try{JSONObject r=api.get("message_thread","id="+id);JSONObject data=r.optJSONObject("data");if(data==null)throw new Exception("Conversation is unavailable.");ui.post(() -> renderMessageThread(data));}
            catch(Exception e){ui.post(() -> renderConnectionError("Could not open conversation",message(e)));}
        });
    }

    private void renderMessageThread(JSONObject data){
        LinearLayout root=vertical();root.setBackgroundColor(BG);root.addView(topBar());
        LinearLayout page=vertical();page.setPadding(dp(16),dp(14),dp(16),dp(24));
        TextView back=text("‹  Messages",12,selectedRole.color,true);back.setPadding(0,dp(4),0,dp(12));back.setOnClickListener(v -> openTab("messages"));page.addView(back);
        page.addView(label(data.optString("category","MESSAGE").toUpperCase(),9,selectedRole.color,true));page.addView(title(data.optString("subject","Conversation"),24));
        page.addView(body(pretty(data.optString("status","open"))+(data.optString("student","").isEmpty()?"":" · "+data.optString("student","")),11));

        JSONArray messages=data.optJSONArray("messages");page.addView(gap(12));
        if(messages!=null)for(int i=0;i<messages.length();i++){
            JSONObject m=messages.optJSONObject(i);if(m==null)continue;LinearLayout bubble=card();bubble.setPadding(dp(13),dp(11),dp(13),dp(11));
            bubble.addView(text((m.optBoolean("mine",false)?"You":m.optString("sender","User"))+" · "+m.optString("created_at",""),9,m.optBoolean("mine",false)?selectedRole.color:MUTED,true));
            TextView bodyText=text(m.optString("body",""),12,TEXT,false);bodyText.setPadding(0,dp(5),0,0);bubble.addView(bodyText);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=dp(7);page.addView(bubble,lp);
        }

        if(data.optBoolean("can_reply",false)){
            page.addView(gap(12));page.addView(fieldLabel("Reply"));
            EditText reply=new EditText(this);reply.setHint("Write a reply…");reply.setTextSize(13);reply.setTextColor(TEXT);reply.setHintTextColor(MUTED);reply.setMinLines(3);reply.setGravity(Gravity.TOP);reply.setPadding(dp(12),dp(10),dp(12),dp(10));reply.setBackground(round(Color.WHITE,14,LINE));page.addView(reply);
            Button send=button("Send Reply",selectedRole.color,Color.WHITE);send.setOnClickListener(v -> sendMessageReply(data.optInt("id"),reply.getText().toString()));page.addView(gap(8));page.addView(send);
        }
        if(data.optBoolean("can_resolve",false)){
            Button resolve=button("Resolve Conversation",Color.WHITE,selectedRole.color);resolve.setBackground(round(Color.WHITE,14,tint(selectedRole.color,.22f)));resolve.setOnClickListener(v -> resolveConversation(data.optInt("id")));
            page.addView(gap(8));page.addView(resolve);
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));root.addView(bottomNav());setContentView(root);
    }

    private void sendMessageReply(int id,String textValue){
        String value=textValue.trim();if(value.isEmpty()){Toast.makeText(this,"Message cannot be blank.",Toast.LENGTH_SHORT).show();return;}
        renderLoading("Sending reply","Delivering through SchoolOS communication authority…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("conversation_id",id);b.put("body",value);JSONObject r=api.post("message_reply",b);JSONObject thread=r.optJSONObject("thread");ui.post(() -> {Toast.makeText(this,r.optString("message","Reply sent."),Toast.LENGTH_SHORT).show();if(thread!=null)renderMessageThread(thread);else loadMessageThread(id);});}catch(Exception e){ui.post(() -> renderConnectionError("Reply was not sent",message(e)));}});
    }

    private void resolveConversation(int id){
        new AlertDialog.Builder(this).setTitle("Resolve conversation?").setMessage("The thread will remain available and can reopen if another reply is sent.").setNegativeButton("Cancel",null).setPositiveButton("Resolve",(d,w) -> {
            renderLoading("Resolving conversation","Updating SchoolOS message status…");
            io.execute(() -> {try{JSONObject b=new JSONObject();b.put("conversation_id",id);JSONObject r=api.post("message_resolve",b);JSONObject thread=r.optJSONObject("thread");ui.post(() -> {Toast.makeText(this,r.optString("message","Conversation resolved."),Toast.LENGTH_SHORT).show();if(thread!=null)renderMessageThread(thread);});}catch(Exception e){ui.post(() -> renderConnectionError("Conversation was not resolved",message(e)));}});
        }).show();
    }

    private void loadMessageCompose(){
        renderLoading("New conversation","Loading permitted SchoolOS contacts…");
        io.execute(() -> {try{JSONObject r=api.get("message_compose");JSONObject data=r.optJSONObject("data");if(data==null)throw new Exception("New conversations are unavailable.");ui.post(() -> renderMessageCompose(data));}catch(Exception e){ui.post(() -> renderConnectionError("Could not start a conversation",message(e)));}});
    }

    private void renderMessageCompose(JSONObject data){
        if(!data.optBoolean("can_start",false)){Toast.makeText(this,"Starting new conversations is disabled for this role.",Toast.LENGTH_SHORT).show();openTab("messages");return;}
        LinearLayout root=vertical();root.setBackgroundColor(BG);root.addView(topBar());
        LinearLayout page=vertical();page.setPadding(dp(16),dp(14),dp(16),dp(24));
        TextView back=text("‹  Messages",12,selectedRole.color,true);back.setPadding(0,dp(4),0,dp(12));back.setOnClickListener(v -> openTab("messages"));page.addView(back);
        page.addView(label("NEW CONVERSATION",9,selectedRole.color,true));page.addView(title("Start a SchoolOS message",24));page.addView(body("Contacts and student context are limited by your existing communication scope.",11));

        page.addView(gap(12));page.addView(fieldLabel("Subject"));EditText subject=input("Conversation subject",false);page.addView(subject);

        JSONArray categories=data.optJSONArray("categories");List<String> catLabels=new ArrayList<>();if(categories!=null)for(int i=0;i<categories.length();i++)catLabels.add(categories.optString(i));if(catLabels.isEmpty())catLabels.add("general");
        Spinner category=new Spinner(this);category.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,catLabels));int defaultPos=catLabels.indexOf(data.optString("default_category",""));if(defaultPos>=0)category.setSelection(defaultPos);
        page.addView(gap(10));page.addView(fieldLabel("Category"));page.addView(category);

        JSONArray participants=data.optJSONArray("participants");List<String> participantLabels=new ArrayList<>();List<Integer> participantIds=new ArrayList<>();
        if(participants!=null)for(int i=0;i<participants.length();i++){JSONObject u=participants.optJSONObject(i);if(u==null)continue;participantIds.add(u.optInt("id"));participantLabels.add(u.optString("name","User")+" · "+u.optString("role","User"));}
        Spinner participant=new Spinner(this);participant.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,participantLabels));
        page.addView(gap(10));page.addView(fieldLabel("Participant"));page.addView(participant);

        JSONArray students=data.optJSONArray("students");List<String> studentLabels=new ArrayList<>();List<Integer> studentIds=new ArrayList<>();studentLabels.add("None");studentIds.add(0);
        if(students!=null)for(int i=0;i<students.length();i++){JSONObject s=students.optJSONObject(i);if(s==null)continue;studentIds.add(s.optInt("id"));studentLabels.add(s.optString("admission_no","")+" · "+s.optString("name","Student"));}
        Spinner student=new Spinner(this);student.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,studentLabels));
        page.addView(gap(10));page.addView(fieldLabel("Student context (optional)"));page.addView(student);

        page.addView(gap(10));page.addView(fieldLabel("First message"));EditText bodyInput=new EditText(this);bodyInput.setHint("Write your message…");bodyInput.setTextSize(13);bodyInput.setTextColor(TEXT);bodyInput.setHintTextColor(MUTED);bodyInput.setMinLines(4);bodyInput.setGravity(Gravity.TOP);bodyInput.setPadding(dp(12),dp(10),dp(12),dp(10));bodyInput.setBackground(round(Color.WHITE,14,LINE));page.addView(bodyInput);

        Button send=button("Open Conversation",selectedRole.color,Color.WHITE);send.setOnClickListener(v -> {
            if(participantIds.isEmpty()){Toast.makeText(this,"No permitted participant is available.",Toast.LENGTH_SHORT).show();return;}
            createConversation(subject.getText().toString(),String.valueOf(category.getSelectedItem()),participantIds.get(participant.getSelectedItemPosition()),studentIds.get(student.getSelectedItemPosition()),bodyInput.getText().toString());
        });page.addView(gap(12));page.addView(send);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));root.addView(bottomNav());setContentView(root);
    }

    private void createConversation(String subject,String category,int participantId,int studentId,String bodyText){
        if(subject.trim().isEmpty()){Toast.makeText(this,"Conversation subject is required.",Toast.LENGTH_SHORT).show();return;}
        renderLoading("Opening conversation","Applying SchoolOS communication scope…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("subject",subject.trim());b.put("category",category);b.put("participant_user_id",participantId);b.put("student_id",studentId);b.put("body",bodyText.trim());JSONObject r=api.post("message_create",b);JSONObject thread=r.optJSONObject("thread");ui.post(() -> {Toast.makeText(this,r.optString("message","Conversation opened."),Toast.LENGTH_SHORT).show();if(thread!=null)renderMessageThread(thread);else openTab("messages");});}catch(Exception e){ui.post(() -> renderConnectionError("Conversation was not opened",message(e)));}});
    }

    private void confirmTransportAction(int tripId,String actionKey,String labelText){
        String status="trip_start".equals(actionKey)?"running":("trip_complete".equals(actionKey)?"completed":"cancelled");
        new AlertDialog.Builder(this).setTitle(labelText+"?").setMessage("This will update the live SchoolOS trip status.").setNegativeButton("Cancel",null).setPositiveButton(labelText,(d,w) -> transportTripStatus(tripId,status)).show();
    }

    private void transportTripStatus(int tripId,String status){
        renderLoading("Updating trip","Applying SchoolOS transport controls…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("trip_id",tripId);b.put("status",status);JSONObject r=api.post("transport_trip_status",b);ui.post(() -> {Toast.makeText(this,r.optString("message","Trip updated."),Toast.LENGTH_SHORT).show();loadModule("transport");});}catch(Exception e){ui.post(() -> renderConnectionError("Trip update failed",message(e)));}});
    }

    private void markAllNotifications(){
        io.execute(() -> {try{JSONObject r=api.post("notification_read_all",new JSONObject());ui.post(() -> {Toast.makeText(this,r.optString("message","Notifications updated."),Toast.LENGTH_SHORT).show();loadModule("updates");});}catch(Exception e){ui.post(() -> Toast.makeText(this,message(e),Toast.LENGTH_SHORT).show());}});
    }

    private void addAccountControls(LinearLayout page){
        if(session==null)return;
        JSONArray memberships=session.optJSONArray("memberships");
        if(memberships!=null&&memberships.length()>1){
            page.addView(gap(14));
            page.addView(sectionHeading("School workspace","Switch between schools linked to this account."));
            for(int i=0;i<memberships.length();i++){
                JSONObject m=memberships.optJSONObject(i);if(m==null)continue;
                boolean active=m.optBoolean("selected",false);
                Button b=button((active?"✓  ":"")+m.optString("name","School"),active?selectedRole.color:Color.WHITE,active?Color.WHITE:TEXT);
                if(!active)b.setBackground(round(Color.WHITE,14,LINE));
                final int schoolId=m.optInt("school_id",0);
                b.setEnabled(!active);
                b.setOnClickListener(v -> switchSchool(schoolId));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48));
                if(i>0)lp.topMargin=dp(7);
                page.addView(b,lp);
            }
        }
        page.addView(gap(14));
        Button logout=button("Sign out of SchoolOS",Color.WHITE,Color.rgb(180,50,60));
        logout.setBackground(round(Color.WHITE,14,Color.rgb(245,210,215)));
        logout.setOnClickListener(v -> logout());
        page.addView(logout);
    }

    private void openTab(String key){
        selectedTab=key==null||key.isEmpty()?"home":key;
        renderWorkspace();
    }

    private void markNotification(int id){
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("id",id);api.post("notification_read",b);ui.post(() -> loadModule("updates"));}
            catch(Exception e){ui.post(() -> Toast.makeText(this,message(e),Toast.LENGTH_SHORT).show());}
        });
    }

    private void ackAnnouncement(int id){
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("id",id);api.post("ack_announcement",b);ui.post(() -> loadModule("updates"));}
            catch(Exception e){ui.post(() -> Toast.makeText(this,message(e),Toast.LENGTH_SHORT).show());}
        });
    }

    private void selectChild(int id){
        renderLoading("Switching child","Loading the selected student context…");
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("child_id",id);session=api.post("select_child",b);JSONObject h=api.get("home");homeData=h.optJSONObject("data");JSONObject s=h.optJSONObject("session");if(s!=null)session=s;ui.post(() -> {selectedTab="home";setRoleFromSession();renderWorkspace();});}
            catch(Exception e){ui.post(() -> renderConnectionError("Could not switch child",message(e)));}
        });
    }

    private void selectTeachingContext(String key){
        renderLoading("Switching class","Applying your teaching allocation…");
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("key",key);session=api.post("select_teaching_context",b);JSONObject h=api.get("home");homeData=h.optJSONObject("data");JSONObject s=h.optJSONObject("session");if(s!=null)session=s;ui.post(() -> {selectedTab="home";setRoleFromSession();renderWorkspace();});}
            catch(Exception e){ui.post(() -> renderConnectionError("Could not switch class",message(e)));}
        });
    }

    private void switchSchool(int id){
        renderLoading("Switching school","Applying your SchoolOS workspace…");
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("school_id",id);JSONObject s=api.post("switch_school",b);ui.post(() -> acceptSession(s));}
            catch(Exception e){ui.post(() -> renderConnectionError("School switch failed",message(e)));}
        });
    }

    private void logout(){
        renderLoading("Signing out","Closing the secure SchoolOS session…");
        io.execute(() -> {try{api.post("logout",new JSONObject());}catch(Exception ignore){}api.clearSession();ui.post(() -> renderLogin(null));});
    }

    private void renderSchoolChooser(JSONArray memberships){
        LinearLayout page=vertical();page.setPadding(dp(18),dp(18),dp(18),dp(24));page.setBackgroundColor(BG);
        page.addView(brandRow());page.addView(gap(16));page.addView(title("Select your SchoolOS workspace",25));page.addView(body("Choose the school you want to open.",12));
        if(memberships!=null)for(int i=0;i<memberships.length();i++){JSONObject m=memberships.optJSONObject(i);if(m==null)continue;LinearLayout item=card();item.setPadding(dp(15),dp(14),dp(15),dp(14));item.addView(text(m.optString("name","School"),15,TEXT,true));item.addView(text(m.optString("role_name","School member"),10,MUTED,false));final int id=m.optInt("school_id",0);item.setOnClickListener(v -> switchSchool(id));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=dp(8);page.addView(item,lp);}
        ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
    }

    private void renderLoading(String heading,String sub){
        LinearLayout page=vertical();page.setGravity(Gravity.CENTER);page.setPadding(dp(28),dp(28),dp(28),dp(28));page.setBackgroundColor(BG);
        TextView mark=text("S",24,Color.WHITE,true);mark.setGravity(Gravity.CENTER);mark.setBackground(round(BLUE,18,BLUE));page.addView(mark,new LinearLayout.LayoutParams(dp(58),dp(58)));
        page.addView(gap(15));page.addView(centerText(heading,20,TEXT,true));TextView s=centerText(sub,11,MUTED,false);s.setPadding(0,dp(6),0,dp(15));page.addView(s);page.addView(new ProgressBar(this));setContentView(page);
    }

    private void renderConnectionError(String heading,String detail){
        LinearLayout page=vertical();page.setGravity(Gravity.CENTER);page.setPadding(dp(24),dp(24),dp(24),dp(24));page.setBackgroundColor(BG);
        LinearLayout box=card();box.setPadding(dp(20),dp(20),dp(20),dp(20));box.addView(label("SCHOOLOS CONNECTION",9,Color.rgb(190,55,70),true));box.addView(title(heading,22));box.addView(body(detail,12));box.addView(gap(14));
        Button retry=button("Try Again",BLUE,Color.WHITE);retry.setOnClickListener(v -> {if(inWorkspace)loadHome();else restoreSession();});box.addView(retry);
        Button sign=button("Return to Sign In",Color.WHITE,TEXT);sign.setBackground(round(Color.WHITE,14,LINE));sign.setOnClickListener(v -> {api.clearSession();renderLogin(null);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50));lp.topMargin=dp(8);box.addView(sign,lp);
        page.addView(box,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));setContentView(page);
    }

    private String message(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?"SchoolOS could not complete this request. Check your connection and try again.":m;}

    private void setRoleFromSession(){
        String role=session==null?"admin":session.optString("role","admin");
        if("teacher".equals(role))selectedRole=Role.TEACHER;
        else if("parent".equals(role))selectedRole=Role.PARENT;
        else if("student".equals(role))selectedRole=Role.STUDENT;
        else selectedRole=Role.ADMIN;
    }

    private String pretty(String s){if(s==null)return"";String[] parts=s.replace('_',' ').split(" ");StringBuilder b=new StringBuilder();for(String p:parts){if(p.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));}return b.toString();}

    private String tabSubtitle(String title) {
        if ("Profile".equals(title)) return "Account identity and role context.";
        if (title.toLowerCase().contains("payment") || title.toLowerCase().contains("fee")) return "Clear payment status and school finance presentation.";
        if (title.toLowerCase().contains("attendance")) return "Fast, compact attendance presentation for daily use.";
        if (title.toLowerCase().contains("study")) return "SchoolOS Study From Home in the same native app shell.";
        if (title.toLowerCase().contains("transport")) return "Routes and transport information in a mobile-first presentation.";
        return "A native Android presentation aligned with the existing SchoolOS workflow.";
    }

    private String[] sampleRows(String title) {
        if (title.toLowerCase().contains("attendance")) return new String[]{"Today summary","Class / child view","Attendance history"};
        if (title.toLowerCase().contains("payment") || title.toLowerCase().contains("fee")) return new String[]{"Current dues","Payment history","Receipt access"};
        if (title.toLowerCase().contains("study")) return new String[]{"Lessons","Assignments","Live classes","Tests & reports"};
        if (title.toLowerCase().contains("transport")) return new String[]{"Vehicle / route","Stop information","Tracking status"};
        if (title.toLowerCase().contains("class")) return new String[]{"Today schedule","Class roster","Class actions"};
        return new String[]{"Current status","Recent activity","Available actions"};
    }

    private View bottomNav() {
        LinearLayout bar = horizontal();
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(4), dp(7), dp(4), dp(7));
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(10));

        JSONArray nav=session==null?null:session.optJSONArray("nav");
        if(nav==null)return bar;
        for(int i=0;i<nav.length();i++){
            JSONObject n=nav.optJSONObject(i);if(n==null)continue;
            String key=n.optString("key","home");
            String caption=n.optString("label",pretty(key));
            boolean active=key.equals(selectedTab);
            LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);
            TextView dot=text(active?"●":"○",12,active?selectedRole.color:Color.rgb(157,165,184),true);dot.setGravity(Gravity.CENTER);
            TextView label=text(caption,9,active?selectedRole.color:MUTED,active);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(3),0,0);
            cell.addView(dot);cell.addView(label);cell.setOnClickListener(v -> openTab(key));
            bar.addView(cell,new LinearLayout.LayoutParams(0,dp(50),1f));
        }
        return bar;
    }

    private View heroMetric(String cap, String value) {
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(5), dp(6), dp(5));
        box.setBackground(round(Color.argb(25,255,255,255), 13, Color.argb(38,255,255,255)));
        TextView v = text(value, 18, Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        TextView c = text(cap, 8, Color.argb(220,255,255,255), true);
        c.setGravity(Gravity.CENTER);
        box.addView(v);
        box.addView(c);
        return box;
    }

    private View sectionHeading(String heading, String subtitle) {
        LinearLayout box = vertical();
        box.setPadding(0, 0, 0, dp(9));
        box.addView(text(heading, 16, TEXT, true));
        box.addView(text(subtitle, 10, MUTED, false));
        return box;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout l = vertical();
        l.setBackground(round(Color.WHITE, 18, LINE));
        l.setElevation(dp(2));
        return l;
    }

    private TextView title(String value, int sp) {
        TextView t = text(value, sp, TEXT, true);
        t.setPadding(0, dp(5), 0, dp(4));
        return t;
    }

    private TextView body(String value, int sp) {
        TextView t = text(value, sp, MUTED, false);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    private TextView fieldLabel(String value) {
        TextView t = text(value, 11, TEXT, true);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private EditText input(String hint, boolean password) {
        EditText e = new EditText(this);
        e.setTextSize(13);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(155,163,182));
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(dp(13), 0, dp(13), 0);
        e.setBackground(round(Color.rgb(250,251,254), 14, LINE));
        e.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return e;
    }

    private Button button(String value, int bg, int fg) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(round(bg, 14, bg));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        return b;
    }

    private TextView label(String value, int sp, int color, boolean bold) {
        TextView t = text(value, sp, color, bold);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private View gap(int h) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return s;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private GradientDrawable gradient(int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int tint(int color, float amount) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        if (amount >= 0) {
            r = (int) (r + (255 - r) * amount);
            g = (int) (g + (255 - g) * amount);
            b = (int) (b + (255 - b) * amount);
        } else {
            float f = 1f + amount;
            r = (int) (r * f);
            g = (int) (g * f);
            b = (int) (b * f);
        }
        return Color.rgb(clamp(r), clamp(g), clamp(b));
    }

    private int clamp(int n) {
        return Math.max(0, Math.min(255, n));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
