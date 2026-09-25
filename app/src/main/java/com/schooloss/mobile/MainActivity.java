package com.schooloss.nativepreview;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

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
    private String selectedTab = "Home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(248,250,255));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        renderLogin();
    }

    @Override
    public void onBackPressed() {
        if (inWorkspace && !"Home".equals(selectedTab)) {
            selectedTab = "Home";
            renderWorkspace();
            return;
        }
        if (inWorkspace) {
            inWorkspace = false;
            renderLogin();
            return;
        }
        super.onBackPressed();
    }

    private void renderLogin() {
        inWorkspace = false;
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(16), dp(18), dp(24));
        page.setBackgroundColor(BG);

        page.addView(brandRow());
        page.addView(gap(14));
        page.addView(loginHero());
        page.addView(gap(16));

        LinearLayout form = card();
        form.setPadding(dp(18), dp(18), dp(18), dp(18));
        form.addView(label("WELCOME BACK", 10, selectedRole.color, true));
        form.addView(title("Sign in to SchoolOS", 26));
        form.addView(body("Native Android UI preview. Choose a workspace below and review the complete mobile direction before live connectivity is added.", 13));
        form.addView(gap(16));

        form.addView(fieldLabel("Email address"));
        form.addView(input("name@school.com", false));
        form.addView(gap(11));
        form.addView(fieldLabel("Password"));
        form.addView(input("Password", true));
        form.addView(gap(15));
        form.addView(fieldLabel("Preview workspace"));
        form.addView(roleGrid());
        form.addView(gap(16));

        Button open = button("Open " + selectedRole.label + " UI", selectedRole.color, Color.WHITE);
        open.setOnClickListener(v -> {
            inWorkspace = true;
            selectedTab = "Home";
            renderWorkspace();
        });
        form.addView(open);
        form.addView(gap(10));
        TextView note = text("UI-only preview · no API · no database · no WebView", 11, MUTED, false);
        note.setGravity(Gravity.CENTER);
        form.addView(note);

        page.addView(form);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
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
                    renderLogin();
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
        View content = "Home".equals(selectedTab) ? homeScreen() : tabScreen(selectedTab);
        contentFrame.addView(content);
        root.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav());
        setContentView(root);
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
        copy.addView(text(selectedRole.label + " workspace", 10, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView profile = text(initials(selectedRole.label), 11, selectedRole.color, true);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(round(tint(selectedRole.color, 0.10f), 99, tint(selectedRole.color, 0.18f)));
        profile.setOnClickListener(v -> {
            selectedTab = "Profile";
            renderWorkspace();
        });
        row.addView(profile, new LinearLayout.LayoutParams(dp(38), dp(38)));

        outer.addView(row);
        return outer;
    }

    private String initials(String s) {
        if (s.startsWith("Admin")) return "AO";
        if (s.startsWith("Teacher")) return "TS";
        if (s.startsWith("Parent")) return "P";
        return "S";
    }

    private View homeScreen() {
        LinearLayout page = vertical();
        page.setPadding(dp(16), dp(14), dp(16), dp(20));

        LinearLayout hero = vertical();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(gradient(new int[]{selectedRole.color, tint(selectedRole.color, -0.18f)}, 23));
        hero.addView(label(selectedRole.eyebrow, 9, Color.argb(220,255,255,255), true));
        TextView h = text(selectedRole.title, 23, Color.WHITE, true);
        h.setPadding(0, dp(5), 0, 0);
        hero.addView(h);
        TextView sub = text(selectedRole.subtitle, 12, Color.argb(225,255,255,255), false);
        sub.setPadding(0, dp(7), 0, 0);
        hero.addView(sub);

        LinearLayout status = horizontal();
        status.setPadding(0, dp(14), 0, 0);
        status.addView(heroMetric("TODAY", "—"), new LinearLayout.LayoutParams(0, dp(58), 1f));
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(0, dp(58), 1f);
        mid.leftMargin = dp(7);
        mid.rightMargin = dp(7);
        status.addView(heroMetric("UPDATES", "—"), mid);
        status.addView(heroMetric("PENDING", "—"), new LinearLayout.LayoutParams(0, dp(58), 1f));
        hero.addView(status);
        page.addView(hero);

        page.addView(gap(15));
        page.addView(sectionHeading("Quick access", "Native modules mapped to the current SchoolOS role structure."));
        page.addView(moduleGrid(homeModules(selectedRole)));

        page.addView(gap(14));
        LinearLayout notice = card();
        notice.setPadding(dp(15), dp(14), dp(15), dp(14));
        notice.addView(label("PREVIEW STATUS", 9, selectedRole.color, true));
        notice.addView(text("Live data is intentionally not connected", 15, TEXT, true));
        notice.addView(text("This APK is for UI review only. API, database, payments, uploads and notifications remain disabled until design approval.", 11, MUTED, false));
        page.addView(notice);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
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
        LinearLayout page = vertical();
        page.setPadding(dp(16), dp(15), dp(16), dp(22));

        TextView back = text("‹  Back", 12, selectedRole.color, true);
        back.setPadding(0, dp(5), 0, dp(12));
        back.setOnClickListener(v -> {
            selectedTab = "Home";
            renderWorkspace();
        });
        page.addView(back);

        page.addView(label(selectedRole.eyebrow, 9, selectedRole.color, true));
        page.addView(title(titleText, 25));
        page.addView(body(tabSubtitle(titleText), 12));
        page.addView(gap(14));

        LinearLayout summary = card();
        summary.setPadding(dp(15), dp(15), dp(15), dp(15));
        summary.addView(label("LIVE DATA", 9, selectedRole.color, true));
        summary.addView(text("Not connected in UI preview", 16, TEXT, true));
        summary.addView(text("This section shows the approved native presentation structure only. Values, records and actions will be connected to existing SchoolOS authority in the next phase.", 11, MUTED, false));
        page.addView(summary);

        page.addView(gap(12));
        page.addView(sectionHeading("Overview", "Representative native rows for this module."));
        String[] rows = sampleRows(titleText);
        for (String row : rows) {
            LinearLayout item = card();
            item.setPadding(dp(14), dp(13), dp(14), dp(13));
            LinearLayout line = horizontal();
            line.setGravity(Gravity.CENTER_VERTICAL);

            TextView dot = text("•", 18, selectedRole.color, true);
            dot.setGravity(Gravity.CENTER);
            line.addView(dot, new LinearLayout.LayoutParams(dp(28), dp(36)));

            LinearLayout copy = vertical();
            copy.addView(text(row, 13, TEXT, true));
            copy.addView(text("Live data will appear here after connectivity approval.", 10, MUTED, false));
            line.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = text("›", 22, MUTED, false);
            line.addView(arrow);
            item.addView(line);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            page.addView(item, lp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        return scroll;
    }

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

        String[] items;
        switch (selectedRole) {
            case ADMIN:
                items = new String[]{"Home","Operations","Actions","Alerts","More"};
                break;
            case TEACHER:
                items = new String[]{"Home","Classes","Attendance","Teaching","More"};
                break;
            case PARENT:
                items = new String[]{"Home","My Child","Payments","Updates","More"};
                break;
            default:
                items = new String[]{"Home","Learn","Schedule","Updates","More"};
                break;
        }

        for (String item : items) {
            LinearLayout cell = vertical();
            cell.setGravity(Gravity.CENTER);
            boolean active = item.equals(selectedTab) || ("Home".equals(item) && "Home".equals(selectedTab));

            TextView dot = text(active ? "●" : "○", 12, active ? selectedRole.color : Color.rgb(157,165,184), true);
            dot.setGravity(Gravity.CENTER);
            TextView label = text(item, 9, active ? selectedRole.color : MUTED, active);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(3), 0, 0);
            cell.addView(dot);
            cell.addView(label);
            cell.setOnClickListener(v -> {
                selectedTab = item;
                renderWorkspace();
            });
            bar.addView(cell, new LinearLayout.LayoutParams(0, dp(50), 1f));
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
