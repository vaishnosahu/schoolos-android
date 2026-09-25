package com.alkeynes.employee.management;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(15,39,71);
    private static final int BLUE = Color.rgb(23,105,224);
    private static final int BG = Color.rgb(244,247,251);
    private static final int TEXT = Color.rgb(20,32,51);
    private static final int MUTED = Color.rgb(104,118,138);
    private static final int BORDER = Color.rgb(222,229,238);
    private static final int GREEN = Color.rgb(24,139,86);
    private static final int AMBER = Color.rgb(199,132,20);
    private static final int RED = Color.rgb(190,63,63);

    private FrameLayout root;
    private String role = "";
    private String screen = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars());
                top = i.top; bottom = i.bottom;
            } else {
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        showRoleSelector();
    }

    private void showRoleSelector() {
        role = ""; screen = "";
        root.removeAllViews();

        ScrollView sv = new ScrollView(this);
        LinearLayout page = column();
        page.setPadding(dp(22), dp(34), dp(22), dp(32));
        sv.addView(page, matchWrap());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_employee_management);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(72), dp(72));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        page.addView(logo, ilp);

        TextView app = tv("Employee Management", 27, NAVY, true);
        app.setGravity(Gravity.CENTER);
        app.setPadding(0, dp(14), 0, 0);
        page.addView(app, matchWrap());

        TextView subtitle = tv("Native Android UI Preview", 14, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(5), 0, dp(22));
        page.addView(subtitle, matchWrap());

        page.addView(infoBanner("DESIGN PREVIEW", "100% native Android interface. No WebView, live API, database write, attendance punch or GPS tracking is enabled in this preview build."), matchWrap());

        LinearLayout login = card();
        login.addView(label("Sign in"));
        login.addView(muted("Final native login layout preview"));
        EditText email = input("Work email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        login.addView(email, matchWrapMargin(0, 14, 0, 0));
        login.addView(password, matchWrapMargin(0, 10, 0, 0));
        Button signIn = primaryButton("Sign in");
        signIn.setOnClickListener(v -> previewToast("Authentication wiring will be connected after the native design is locked."));
        login.addView(signIn, matchWrapMargin(0, 14, 0, 0));
        page.addView(login, matchWrapMargin(0, 18, 0, 0));

        TextView choose = section("Preview role");
        page.addView(choose, matchWrapMargin(0, 24, 0, 10));

        Button employee = primaryButton("Open Employee UI Preview");
        employee.setOnClickListener(v -> render("employee", "home"));
        page.addView(employee, matchWrap());

        Button admin = secondaryButton("Open Admin UI Preview");
        admin.setOnClickListener(v -> render("admin", "dashboard"));
        page.addView(admin, matchWrapMargin(0, 10, 0, 0));

        TextView note = muted("This preview intentionally uses placeholder presentation states so no sample value can be mistaken for live company data.");
        note.setGravity(Gravity.CENTER);
        page.addView(note, matchWrapMargin(8, 20, 8, 0));

        root.addView(sv, matchMatch());
    }

    private void render(String newRole, String newScreen) {
        role = newRole; screen = newScreen;
        root.removeAllViews();

        LinearLayout shell = column();
        shell.setBackgroundColor(BG);
        root.addView(shell, matchMatch());

        shell.addView(topBar(), new LinearLayout.LayoutParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(16), dp(14), dp(16), dp(22));
        scroll.addView(content, matchWrap());
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        if ("employee".equals(role)) buildEmployeeScreen(content, screen);
        else buildAdminScreen(content, screen);

        shell.addView(bottomNav(), new LinearLayout.LayoutParams(-1, dp(68)));
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(12), dp(8));
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(dp(2));

        LinearLayout titles = column();
        TextView brand = tv("Employee Management", 12, MUTED, true);
        TextView title = tv(pageTitle(screen), 20, NAVY, true);
        titles.addView(brand, matchWrap());
        titles.addView(title, matchWrap());
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView preview = pill("UI PREVIEW", BLUE, Color.WHITE);
        bar.addView(preview, wrapWrapMargin(6,0,8,0));

        TextView r = pill("employee".equals(role) ? "EMPLOYEE" : "ADMIN", NAVY, Color.WHITE);
        bar.addView(r, wrapWrap());
        return bar;
    }

    private View bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(5), dp(4), dp(5));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));

        if ("employee".equals(role)) {
            nav.addView(navItem("Home", "home", "⌂"), weight());
            nav.addView(navItem("Attendance", "attendance", "◎"), weight());
            nav.addView(navItem("Leave", "leave", "◫"), weight());
            nav.addView(navItem("More", "more", "☷"), weight());
            nav.addView(navItem("Profile", "profile", "○"), weight());
        } else {
            nav.addView(navItem("Home", "dashboard", "⌂"), weight());
            nav.addView(navItem("People", "people", "♙"), weight());
            nav.addView(navItem("Attendance", "attendance", "◎"), weight());
            nav.addView(navItem("Live Map", "map", "⌖"), weight());
            nav.addView(navItem("More", "more", "☷"), weight());
        }
        return nav;
    }

    private View navItem(String label, String target, String icon) {
        boolean active = target.equals(screen) || ("more".equals(target) && isMoreScreen(screen));
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(3), dp(2), dp(2));
        TextView i = tv(icon, 20, active ? BLUE : MUTED, false);
        i.setGravity(Gravity.CENTER);
        TextView l = tv(label, 10, active ? NAVY : MUTED, active);
        l.setGravity(Gravity.CENTER);
        item.addView(i, matchWrap());
        item.addView(l, matchWrap());
        item.setOnClickListener(v -> render(role, target));
        return item;
    }

    private boolean isMoreScreen(String s) {
        return Arrays.asList("timesheets","field","expenses","payroll","notifications","security","settings","reports","organisation","locations","shifts","holidays","admin_leave").contains(s);
    }

    private void buildEmployeeScreen(LinearLayout c, String s) {
        addPreviewNotice(c);
        switch (s) {
            case "attendance": employeeAttendance(c); break;
            case "leave": employeeLeave(c); break;
            case "profile": employeeProfile(c); break;
            case "more": employeeMore(c); break;
            case "timesheets": employeeTimesheets(c); break;
            case "field": employeeField(c); break;
            case "expenses": employeeExpenses(c); break;
            case "payroll": employeePayroll(c); break;
            case "notifications": employeeNotifications(c); break;
            case "security": employeeSecurity(c); break;
            default: employeeHome(c);
        }
    }

    private void buildAdminScreen(LinearLayout c, String s) {
        addPreviewNotice(c);
        switch (s) {
            case "people": adminPeople(c); break;
            case "attendance": adminAttendance(c); break;
            case "map": adminMap(c); break;
            case "more": adminMore(c); break;
            case "organisation": adminOrganisation(c); break;
            case "locations": adminLocations(c); break;
            case "shifts": adminShifts(c); break;
            case "holidays": genericAdmin(c, "Holidays", "Holiday calendar", new String[]{"Upcoming holidays","Branch scope","Paid / unpaid"}); break;
            case "admin_leave": adminLeave(c); break;
            case "timesheets": adminTimesheets(c); break;
            case "field": adminField(c); break;
            case "expenses": adminExpenses(c); break;
            case "payroll": adminPayroll(c); break;
            case "reports": genericAdmin(c, "Reports", "Operational reports", new String[]{"Attendance report","Leave report","Payroll report","Location report"}); break;
            case "notifications": adminNotifications(c); break;
            case "security": adminSecurity(c); break;
            case "settings": adminSettings(c); break;
            default: adminDashboard(c);
        }
    }

    private void addPreviewNotice(LinearLayout c) {
        c.addView(infoBanner("NATIVE UI", "Design preview only · values marked with — are intentionally not connected to live server data."), matchWrap());
    }

    private void employeeHome(LinearLayout c) {
        LinearLayout hero = darkCard();
        hero.addView(tv("Good afternoon", 14, Color.rgb(206,218,235), false));
        hero.addView(tv("Preview Employee", 25, Color.WHITE, true), matchWrapMargin(0,4,0,0));
        hero.addView(pill("ACTIVE EMPLOYEE", Color.rgb(38,82,130), Color.WHITE), wrapWrapMargin(0,12,0,0));
        c.addView(hero, matchWrapMargin(0,12,0,0));

        c.addView(section("Today"), matchWrapMargin(0,20,0,10));
        LinearLayout shift = card();
        shift.addView(rowTitle("Today's Shift", "—"));
        shift.addView(keyValue("Work location", "—"));
        shift.addView(keyValue("Attendance", "Not connected"));
        Button attendance = primaryButton("Open Attendance");
        attendance.setOnClickListener(v -> render("employee","attendance"));
        shift.addView(attendance, matchWrapMargin(0,14,0,0));
        c.addView(shift, matchWrap());

        c.addView(section("Quick snapshot"), matchWrapMargin(0,20,0,10));
        addMetricPair(c, "Worked today", "—", "Month present", "—");
        addMetricPair(c, "Leave balance", "—", "Pending items", "—");

        c.addView(section("Quick actions"), matchWrapMargin(0,20,0,10));
        c.addView(actionGrid(new String[][]{
            {"Attendance","attendance"},{"Request Leave","leave"},{"Timesheet","timesheets"},{"Expense","expenses"}
        }), matchWrap());
    }

    private void employeeAttendance(LinearLayout c) {
        LinearLayout status = darkCard();
        TextView time = tv("--:--", 42, Color.WHITE, true); time.setGravity(Gravity.CENTER);
        TextView state = tv("ATTENDANCE STATUS", 12, Color.rgb(191,209,232), true); state.setGravity(Gravity.CENTER);
        TextView value = tv("Not connected", 20, Color.WHITE, true); value.setGravity(Gravity.CENTER);
        status.addView(time, matchWrap());
        status.addView(state, matchWrapMargin(0,6,0,0));
        status.addView(value, matchWrapMargin(0,3,0,0));
        c.addView(status, matchWrapMargin(0,12,0,0));

        LinearLayout gps = card();
        gps.addView(rowTitle("Location", "Android native"));
        gps.addView(keyValue("GPS accuracy", "—"));
        gps.addView(keyValue("Geofence", "—"));
        gps.addView(keyValue("Tracker", "Not active in UI preview"));
        c.addView(gps, matchWrapMargin(0,12,0,0));

        Button clock = primaryButton("Clock In");
        clock.setOnClickListener(v -> previewToast("Clock action is intentionally disabled in the design preview."));
        c.addView(clock, matchWrapMargin(0,14,0,0));
        Button breakBtn = secondaryButton("Start Break");
        breakBtn.setOnClickListener(v -> previewToast("Break action will be wired after design approval."));
        c.addView(breakBtn, matchWrapMargin(0,8,0,0));

        c.addView(section("Today's summary"), matchWrapMargin(0,22,0,10));
        addMetricPair(c, "Clock in", "—", "Worked", "—");
        addMetricPair(c, "Break", "—", "Clock out", "—");

        LinearLayout help = softCard();
        help.addView(label("How attendance will work"));
        help.addView(muted("The final native app will request Android location permission, validate the server-authorised shift/location, and show a visible tracking notification only while the permitted work session is active."));
        c.addView(help, matchWrapMargin(0,14,0,0));
    }

    private void employeeLeave(LinearLayout c) {
        c.addView(section("Leave balance"), matchWrapMargin(0,14,0,10));
        addMetricPair(c, "Available", "—", "Used", "—");
        Button request = primaryButton("Request Leave");
        request.setOnClickListener(v -> previewToast("Leave form UI is preview-only."));
        c.addView(request, matchWrapMargin(0,12,0,0));

        c.addView(section("My requests"), matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live leave data", "Approved, pending and rejected requests will appear here from the server."));
    }

    private void employeeProfile(LinearLayout c) {
        LinearLayout profile = darkCard();
        TextView avatar = tv("PE", 26, NAVY, true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(circle(Color.WHITE));
        profile.addView(avatar, new LinearLayout.LayoutParams(dp(62),dp(62)));
        profile.addView(tv("Preview Employee", 23, Color.WHITE, true), matchWrapMargin(0,12,0,0));
        profile.addView(tv("Employee code · —", 13, Color.rgb(203,216,234), false));
        c.addView(profile, matchWrapMargin(0,12,0,0));

        LinearLayout details = card();
        details.addView(keyValue("Department", "—"));
        details.addView(keyValue("Designation", "—"));
        details.addView(keyValue("Branch", "—"));
        details.addView(keyValue("Manager", "—"));
        details.addView(keyValue("Joining date", "—"));
        c.addView(details, matchWrapMargin(0,12,0,0));

        Button security = secondaryButton("Security & Sessions");
        security.setOnClickListener(v -> render("employee","security"));
        c.addView(security, matchWrapMargin(0,12,0,0));
        Button switchRole = secondaryButton("Switch to Admin UI Preview");
        switchRole.setOnClickListener(v -> render("admin","dashboard"));
        c.addView(switchRole, matchWrapMargin(0,8,0,0));
        Button exit = textButton("Back to role selector");
        exit.setOnClickListener(v -> showRoleSelector());
        c.addView(exit, matchWrapMargin(0,8,0,0));
    }

    private void employeeMore(LinearLayout c) {
        c.addView(section("Work"), matchWrapMargin(0,14,0,10));
        addMenu(c,"Timesheets","Daily work time and overtime","timesheets");
        addMenu(c,"Field Visits","Client and field assignments","field");
        addMenu(c,"Expenses","Submit and review claims","expenses");
        addMenu(c,"Payroll","Monthly pay summary","payroll");
        c.addView(section("Account"), matchWrapMargin(0,22,0,10));
        addMenu(c,"Notifications","Updates that need your attention","notifications");
        addMenu(c,"Security","Sessions and authorised devices","security");
    }

    private void employeeTimesheets(LinearLayout c) {
        addMetricPair(c,"Worked","—","Overtime","—");
        c.addView(section("Daily timesheet"), matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live timesheet loaded","Daily work, break and approved overtime will appear here."));
    }

    private void employeeField(LinearLayout c) {
        addMetricPair(c,"Assigned","—","Completed","—");
        c.addView(section("Field assignments"), matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live field assignments","Assigned visits and GPS check-in/out actions will appear here."));
    }

    private void employeeExpenses(LinearLayout c) {
        addMetricPair(c,"Pending","—","Approved","—");
        Button add = primaryButton("Submit Expense");
        add.setOnClickListener(v -> previewToast("Expense submission is disabled in design preview."));
        c.addView(add, matchWrapMargin(0,14,0,0));
        c.addView(section("Claims"), matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live expense claims","Submitted claims and review status will appear here."));
    }

    private void employeePayroll(LinearLayout c) {
        LinearLayout net = darkCard();
        net.addView(tv("Latest net pay", 13, Color.rgb(204,219,237), false));
        net.addView(tv("₹ —", 34, Color.WHITE, true), matchWrapMargin(0,5,0,0));
        net.addView(tv("Generated payroll only · no local recalculation", 11, Color.rgb(204,219,237), false), matchWrapMargin(0,7,0,0));
        c.addView(net, matchWrapMargin(0,12,0,0));
        c.addView(section("Calculation"), matchWrapMargin(0,20,0,10));
        LinearLayout calc=card();
        calc.addView(keyValue("Base pay","—"));
        calc.addView(keyValue("Overtime","—"));
        calc.addView(keyValue("Deductions","—"));
        calc.addView(keyValue("Net pay","—"));
        c.addView(calc, matchWrap());
    }

    private void employeeNotifications(LinearLayout c) {
        addMetricPair(c,"Unread","—","All","—");
        c.addView(section("Updates"), matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live notifications","Attendance, leave, expense and system updates will be grouped here."));
    }

    private void employeeSecurity(LinearLayout c) {
        LinearLayout current=card();
        current.addView(rowTitle("This device","Current session"));
        current.addView(keyValue("App","Native Android"));
        current.addView(keyValue("Last active","—"));
        c.addView(current, matchWrapMargin(0,12,0,0));
        c.addView(section("Other sessions"), matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live session data","Other authorised sessions and tracking-device status will appear here without exposing secret tokens."));
    }

    private void adminDashboard(LinearLayout c) {
        LinearLayout hero=darkCard();
        hero.addView(tv("Operations overview",13,Color.rgb(203,216,234),false));
        hero.addView(tv("Today",28,Color.WHITE,true),matchWrapMargin(0,3,0,0));
        hero.addView(tv("Native admin dashboard preview",12,Color.rgb(203,216,234),false),matchWrapMargin(0,6,0,0));
        c.addView(hero,matchWrapMargin(0,12,0,0));

        c.addView(section("Workforce"),matchWrapMargin(0,20,0,10));
        addMetricPair(c,"Present","—","Absent","—");
        addMetricPair(c,"Late","—","On leave","—");
        addMetricPair(c,"Working now","—","GPS stale","—");

        c.addView(section("Needs attention"),matchWrapMargin(0,20,0,10));
        c.addView(attentionRow("Attendance corrections","—","Review original and corrected evidence",AMBER));
        c.addView(attentionRow("Pending leave","—","Review employee requests",BLUE));
        c.addView(attentionRow("Pending expenses","—","Review submitted claims",BLUE));
        c.addView(attentionRow("Stale locations","—","Check GPS/network freshness",RED));

        c.addView(section("Quick access"),matchWrapMargin(0,20,0,10));
        c.addView(actionGrid(new String[][]{
            {"Employees","people"},{"Attendance","attendance"},{"Live Map","map"},{"Payroll","payroll"}
        }),matchWrap());
    }

    private void adminPeople(LinearLayout c) {
        LinearLayout search=card();
        EditText q=input("Search employee",InputType.TYPE_CLASS_TEXT);
        search.addView(q,matchWrap());
        Button add=primaryButton("Add Employee");
        add.setOnClickListener(v->previewToast("Employee create flow will use the approved native form design."));
        search.addView(add,matchWrapMargin(0,10,0,0));
        c.addView(search,matchWrapMargin(0,12,0,0));

        c.addView(section("Employees"),matchWrapMargin(0,20,0,10));
        c.addView(employeePreviewRow("Employee name","Employee code · —","Active"));
        c.addView(employeePreviewRow("Employee name","Department · —","Clocked out"));
        c.addView(employeePreviewRow("Employee name","Branch · —","On leave"));
    }

    private void adminAttendance(LinearLayout c) {
        c.addView(section("Selected period"),matchWrapMargin(0,14,0,10));
        addMetricPair(c,"Open sessions","—","Needs review","—");
        addMetricPair(c,"Inside geofence","—","Corrected","—");

        LinearLayout filters=card();
        filters.addView(input("Search employee",InputType.TYPE_CLASS_TEXT),matchWrap());
        filters.addView(secondaryButton("Date & filters"),matchWrapMargin(0,10,0,0));
        c.addView(filters,matchWrapMargin(0,12,0,0));

        c.addView(section("Attendance records"),matchWrapMargin(0,20,0,10));
        c.addView(recordRow("Employee name","Clock In —  ·  Clock Out —","GPS evidence · —","View"));
        c.addView(recordRow("Employee name","Worked —  ·  Break —","Correction state · —","Review"));
    }

    private void adminMap(LinearLayout c) {
        addMetricPair(c,"Working","—","Live","—");
        addMetricPair(c,"Stale","—","Waiting","—");

        EditText search=input("Search employee on map",InputType.TYPE_CLASS_TEXT);
        c.addView(search,matchWrapMargin(0,14,0,10));

        MapPreviewView map=new MapPreviewView(this);
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-1,dp(285));
        map.setBackground(round(Color.rgb(235,241,247),18));
        c.addView(map,mlp);

        c.addView(section("People on map"),matchWrapMargin(0,20,0,10));
        c.addView(statusPerson("Employee name","Live · last seen —",GREEN));
        c.addView(statusPerson("Employee name","Stale · last seen —",AMBER));
        c.addView(statusPerson("Employee name","Waiting for first GPS update",MUTED));
    }

    private void adminMore(LinearLayout c) {
        c.addView(section("Organisation"),matchWrapMargin(0,14,0,10));
        addMenu(c,"Organisation","Company, branch, department and designation","organisation");
        addMenu(c,"Work Locations","Geofence locations and radius","locations");
        addMenu(c,"Shifts","Work schedules and assignments","shifts");
        addMenu(c,"Holidays","Holiday calendar","holidays");

        c.addView(section("Operations"),matchWrapMargin(0,22,0,10));
        addMenu(c,"Leave","Requests and approvals","admin_leave");
        addMenu(c,"Timesheets & OT","Work time and overtime review","timesheets");
        addMenu(c,"Field Visits","Field assignments and visit status","field");
        addMenu(c,"Expenses","Claims and approvals","expenses");
        addMenu(c,"Payroll","Generated payroll and locks","payroll");
        addMenu(c,"Reports","Operational reporting","reports");

        c.addView(section("Control"),matchWrapMargin(0,22,0,10));
        addMenu(c,"Notifications","Operational updates","notifications");
        addMenu(c,"Security","Sessions and devices","security");
        addMenu(c,"Settings","Attendance and tracking policy","settings");

        Button switchRole=secondaryButton("Switch to Employee UI Preview");
        switchRole.setOnClickListener(v->render("employee","home"));
        c.addView(switchRole,matchWrapMargin(0,18,0,0));
        Button exit=textButton("Back to role selector");
        exit.setOnClickListener(v->showRoleSelector());
        c.addView(exit,matchWrapMargin(0,8,0,0));
    }

    private void adminOrganisation(LinearLayout c) {
        c.addView(section("Organisation structure"),matchWrapMargin(0,14,0,10));
        c.addView(structureCard("Company","Primary organisation"));
        c.addView(structureCard("Branches","Office / operational branches"));
        c.addView(structureCard("Departments","Branch-aware departments"));
        c.addView(structureCard("Designations","Employee roles and titles"));
        Button add=primaryButton("Add Organisation Item");
        add.setOnClickListener(v->previewToast("Native organisation form is design-only."));
        c.addView(add,matchWrapMargin(0,14,0,0));
    }

    private void adminLocations(LinearLayout c) {
        addMetricPair(c,"Locations","—","Active","—");
        LinearLayout form=card();
        form.addView(label("Work location form"));
        form.addView(input("Location name",InputType.TYPE_CLASS_TEXT),matchWrapMargin(0,12,0,0));
        form.addView(input("Address",InputType.TYPE_CLASS_TEXT),matchWrapMargin(0,10,0,0));
        form.addView(input("Allowed radius (metres)",InputType.TYPE_CLASS_NUMBER),matchWrapMargin(0,10,0,0));
        Button current=secondaryButton("Use Current GPS");
        current.setOnClickListener(v->previewToast("GPS capture is intentionally disabled in the UI preview."));
        form.addView(current,matchWrapMargin(0,10,0,0));
        c.addView(form,matchWrapMargin(0,12,0,0));
    }

    private void adminShifts(LinearLayout c) {
        addMetricPair(c,"Shifts","—","Assignments","—");
        LinearLayout form=card();
        form.addView(label("Shift setup"));
        form.addView(input("Shift name",InputType.TYPE_CLASS_TEXT),matchWrapMargin(0,12,0,0));
        form.addView(input("Start time",InputType.TYPE_CLASS_DATETIME),matchWrapMargin(0,10,0,0));
        form.addView(input("End time",InputType.TYPE_CLASS_DATETIME),matchWrapMargin(0,10,0,0));
        form.addView(input("Grace period (minutes)",InputType.TYPE_CLASS_NUMBER),matchWrapMargin(0,10,0,0));
        c.addView(form,matchWrapMargin(0,12,0,0));
    }

    private void adminLeave(LinearLayout c) {
        addMetricPair(c,"Pending","—","Approved","—");
        c.addView(section("Requests"),matchWrapMargin(0,20,0,10));
        c.addView(recordRow("Employee name","Leave type · —","Requested days · —","Review"));
        c.addView(recordRow("Employee name","Status · —","Dates · —","View"));
    }

    private void adminTimesheets(LinearLayout c) {
        addMetricPair(c,"Work hours","—","Pending OT","—");
        c.addView(section("Timesheets"),matchWrapMargin(0,20,0,10));
        c.addView(recordRow("Employee name","Worked —","Auto OT —","Review"));
        c.addView(recordRow("Employee name","Break —","Approved OT —","View"));
    }

    private void adminField(LinearLayout c) {
        addMetricPair(c,"Assigned","—","In progress","—");
        addMetricPair(c,"Completed","—","Needs review","—");
        c.addView(section("Visits"),matchWrapMargin(0,20,0,10));
        c.addView(recordRow("Employee name","Client / site · —","GPS check-in · —","Open"));
    }

    private void adminExpenses(LinearLayout c) {
        addMetricPair(c,"Pending amount","₹ —","Approved amount","₹ —");
        c.addView(section("Claims"),matchWrapMargin(0,20,0,10));
        c.addView(recordRow("Employee name","Expense type · —","Amount · ₹ —","Review"));
    }

    private void adminPayroll(LinearLayout c) {
        LinearLayout state=darkCard();
        state.addView(tv("Payroll period",13,Color.rgb(203,216,234),false));
        state.addView(tv("Not connected",25,Color.WHITE,true),matchWrapMargin(0,4,0,0));
        state.addView(tv("Server-generated payroll remains the calculation authority.",11,Color.rgb(203,216,234),false),matchWrapMargin(0,7,0,0));
        c.addView(state,matchWrapMargin(0,12,0,0));
        addMetricPair(c,"Gross","₹ —","Net","₹ —");
        addMetricPair(c,"Overtime","₹ —","Deductions","₹ —");
        Button generate=primaryButton("Generate Payroll");
        generate.setOnClickListener(v->previewToast("Payroll generation is disabled in design preview."));
        c.addView(generate,matchWrapMargin(0,14,0,0));
    }

    private void adminNotifications(LinearLayout c) {
        addMetricPair(c,"Unread","—","Needs attention","—");
        c.addView(section("Today"),matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live notifications","Operational events will appear here grouped by date and source."));
    }

    private void adminSecurity(LinearLayout c) {
        LinearLayout current=card();
        current.addView(rowTitle("Current session","Native UI preview"));
        current.addView(keyValue("Device","Android"));
        current.addView(keyValue("Session","—"));
        c.addView(current,matchWrapMargin(0,12,0,0));
        c.addView(section("Authorised devices"),matchWrapMargin(0,20,0,10));
        c.addView(emptyCard("No live device data","Final UI will show readable device/session state without exposing hashes or secret tokens."));
    }

    private void adminSettings(LinearLayout c) {
        c.addView(section("Attendance"),matchWrapMargin(0,14,0,10));
        c.addView(settingRow("Require geofence","Employees must be within an authorised work location","—"));
        c.addView(settingRow("Maximum GPS accuracy","Reject or warn on weak location evidence","—"));
        c.addView(settingRow("Offline punch","Policy for temporary connectivity loss","—"));

        c.addView(section("Live tracking"),matchWrapMargin(0,22,0,10));
        c.addView(settingRow("Tracking enabled","Server-controlled employee tracking","—"));
        c.addView(settingRow("Shift only","Limit tracking to permitted shift/session","—"));
        c.addView(settingRow("Stale after","Live Map freshness threshold","—"));

        Button save=primaryButton("Save Settings");
        save.setOnClickListener(v->previewToast("Settings writes are intentionally disabled in design preview."));
        c.addView(save,matchWrapMargin(0,16,0,0));
    }

    private void genericAdmin(LinearLayout c,String title,String subtitle,String[] items){
        LinearLayout head=darkCard();
        head.addView(tv(title,25,Color.WHITE,true));
        head.addView(tv(subtitle,13,Color.rgb(203,216,234),false),matchWrapMargin(0,5,0,0));
        c.addView(head,matchWrapMargin(0,12,0,0));
        c.addView(section("Available views"),matchWrapMargin(0,20,0,10));
        for(String item:items)c.addView(structureCard(item,"Native list/detail presentation"));
    }

    private void addMenu(LinearLayout c,String title,String subtitle,String target){
        LinearLayout row=card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout txt=column();
        txt.addView(label(title));
        txt.addView(muted(subtitle),matchWrapMargin(0,3,0,0));
        row.addView(txt,new LinearLayout.LayoutParams(0,-2,1f));
        TextView arrow=tv("›",30,MUTED,false);
        row.addView(arrow,wrapWrap());
        row.setOnClickListener(v->render(role,target));
        c.addView(row,matchWrapMargin(0,0,0,9));
    }

    private View actionGrid(String[][] actions){
        LinearLayout outer=column();
        for(int i=0;i<actions.length;i+=2){
            LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=i;j<Math.min(i+2,actions.length);j++){
                final String target=actions[j][1];
                Button b=secondaryButton(actions[j][0]);
                b.setOnClickListener(v->render(role,target));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1f);
                if(j>i)lp.leftMargin=dp(8);
                r.addView(b,lp);
            }
            outer.addView(r,matchWrapMargin(0,i==0?0:8,0,0));
        }
        return outer;
    }

    private View employeePreviewRow(String name,String sub,String status){
        LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=tv("E",18,Color.WHITE,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(circle(NAVY));
        row.addView(avatar,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout tx=column();tx.addView(label(name));tx.addView(muted(sub),matchWrapMargin(0,3,0,0));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1f));((LinearLayout.LayoutParams)tx.getLayoutParams()).leftMargin=dp(12);
        row.addView(pill(status,Color.rgb(231,238,248),NAVY),wrapWrap());
        return row;
    }

    private View statusPerson(String name,String sub,int color){
        LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot=tv("●",16,color,false);row.addView(dot,wrapWrapMargin(0,0,10,0));
        LinearLayout tx=column();tx.addView(label(name));tx.addView(muted(sub),matchWrapMargin(0,3,0,0));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1f));
        row.addView(tv("›",28,MUTED,false),wrapWrap());
        return row;
    }

    private View recordRow(String title,String a,String b,String action){
        LinearLayout row=card();
        row.addView(rowTitle(title,action));
        row.addView(muted(a),matchWrapMargin(0,8,0,0));
        row.addView(muted(b),matchWrapMargin(0,3,0,0));
        row.setOnClickListener(v->previewToast("Detail screen wiring will be added after the native design is approved."));
        return row;
    }

    private View structureCard(String title,String sub){
        LinearLayout row=card();
        row.addView(rowTitle(title,"›"));
        row.addView(muted(sub),matchWrapMargin(0,5,0,0));
        return row;
    }

    private View settingRow(String title,String sub,String value){
        LinearLayout row=card();
        row.addView(rowTitle(title,value));
        row.addView(muted(sub),matchWrapMargin(0,5,0,0));
        return row;
    }

    private View attentionRow(String title,String value,String subtitle,int color){
        LinearLayout row=card();row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        View marker=new View(this);marker.setBackground(round(color,8));
        row.addView(marker,new LinearLayout.LayoutParams(dp(5),dp(48)));
        LinearLayout text=column();text.addView(label(title));text.addView(muted(subtitle),matchWrapMargin(0,3,0,0));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(0,-2,1f);tlp.leftMargin=dp(12);row.addView(text,tlp);
        row.addView(tv(value,22,NAVY,true),wrapWrap());
        return row;
    }

    private void addMetricPair(LinearLayout c,String l1,String v1,String l2,String v2){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(metricCard(l1,v1),new LinearLayout.LayoutParams(0,-2,1f));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f);lp.leftMargin=dp(9);
        r.addView(metricCard(l2,v2),lp);
        c.addView(r,matchWrapMargin(0,0,0,9));
    }

    private View metricCard(String label,String value){
        LinearLayout m=card();
        m.addView(tv(value,25,NAVY,true));
        m.addView(tv(label,12,MUTED,false),matchWrapMargin(0,5,0,0));
        return m;
    }

    private View emptyCard(String title,String body){
        LinearLayout e=softCard();e.setGravity(Gravity.CENTER);
        TextView t=label(title);t.setGravity(Gravity.CENTER);
        TextView b=muted(body);b.setGravity(Gravity.CENTER);
        e.addView(t,matchWrap());
        e.addView(b,matchWrapMargin(8,6,8,0));
        return e;
    }

    private View infoBanner(String title,String body){
        LinearLayout b=softCard();
        b.setBackground(round(Color.rgb(232,241,254),16));
        b.addView(tv(title,11,BLUE,true));
        b.addView(tv(body,12,Color.rgb(58,83,118),false),matchWrapMargin(0,5,0,0));
        return b;
    }

    private View rowTitle(String left,String right){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(label(left),new LinearLayout.LayoutParams(0,-2,1f));
        r.addView(tv(right,12,MUTED,true),wrapWrap());
        return r;
    }

    private View keyValue(String key,String value){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(9),0,0);
        r.addView(tv(key,12,MUTED,false),new LinearLayout.LayoutParams(0,-2,1f));
        r.addView(tv(value,13,TEXT,true),wrapWrap());
        return r;
    }

    private LinearLayout card(){
        LinearLayout v=column();v.setPadding(dp(16),dp(15),dp(16),dp(15));v.setBackground(round(Color.WHITE,18));v.setElevation(dp(1));return v;
    }
    private LinearLayout softCard(){
        LinearLayout v=column();v.setPadding(dp(16),dp(15),dp(16),dp(15));v.setBackground(round(Color.rgb(249,251,254),18));return v;
    }
    private LinearLayout darkCard(){
        LinearLayout v=column();v.setPadding(dp(18),dp(18),dp(18),dp(18));v.setBackground(round(NAVY,20));v.setElevation(dp(2));return v;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}

    private TextView label(String s){return tv(s,15,TEXT,true);}
    private TextView muted(String s){return tv(s,12,MUTED,false);}
    private TextView section(String s){return tv(s,17,NAVY,true);}

    private TextView tv(String s,float size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(0,1.08f);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return v;
    }

    private TextView pill(String s,int bg,int fg){
        TextView v=tv(s,10,fg,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(9),dp(5),dp(9),dp(5));v.setBackground(round(bg,99));return v;
    }

    private EditText input(String hint,int type){
        EditText e=new EditText(this);e.setHint(hint);e.setTextSize(15);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(137,150,168));e.setInputType(type);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(stroke(Color.WHITE,BORDER,12,1));e.setMinHeight(dp(52));return e;
    }

    private Button primaryButton(String s){return button(s,NAVY,Color.WHITE);}
    private Button secondaryButton(String s){return button(s,Color.WHITE,NAVY);}
    private Button textButton(String s){Button b=button(s,Color.TRANSPARENT,BLUE);b.setElevation(0);return b;}

    private Button button(String s,int bg,int fg){
        Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setTextColor(fg);b.setAllCaps(false);b.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(52));
        if(bg==Color.WHITE)b.setBackground(stroke(bg,BORDER,14,1));else if(bg==Color.TRANSPARENT)b.setBackgroundColor(Color.TRANSPARENT);else b.setBackground(round(bg,14));
        return b;
    }

    private GradientDrawable round(int color,float radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;
    }
    private GradientDrawable stroke(int color,int border,float radius,int width){
        GradientDrawable g=round(color,radius);g.setStroke(dp(width),border);return g;
    }
    private GradientDrawable circle(int color){
        GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;
    }

    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-1,1f);}
    private LinearLayout.LayoutParams matchMatch(){return new LinearLayout.LayoutParams(-1,-1);}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams wrapWrap(){return new LinearLayout.LayoutParams(-2,-2);}
    private LinearLayout.LayoutParams matchWrapMargin(int l,int t,int r,int b){LinearLayout.LayoutParams p=matchWrap();p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private LinearLayout.LayoutParams wrapWrapMargin(int l,int t,int r,int b){LinearLayout.LayoutParams p=wrapWrap();p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private String pageTitle(String s){
        switch(s){
            case "dashboard": return "Dashboard";
            case "people": return "Employees";
            case "attendance": return "Attendance";
            case "map": return "Live Map";
            case "leave": return "Leave";
            case "profile": return "Profile";
            case "more": return "More";
            case "timesheets": return "Timesheets & OT";
            case "field": return "Field Visits";
            case "expenses": return "Expenses";
            case "payroll": return "Payroll";
            case "notifications": return "Notifications";
            case "security": return "Security";
            case "settings": return "Settings";
            case "organisation": return "Organisation";
            case "locations": return "Work Locations";
            case "shifts": return "Shifts";
            case "holidays": return "Holidays";
            case "admin_leave": return "Leave Management";
            case "reports": return "Reports";
            default: return "Home";
        }
    }

    private void previewToast(String s){
        Toast.makeText(this,s,Toast.LENGTH_SHORT).show();
        View focus=getCurrentFocus();
        if(focus!=null){InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);imm.hideSoftInputFromWindow(focus.getWindowToken(),0);}
    }

    @Override public void onBackPressed(){
        if(role.isEmpty()){super.onBackPressed();return;}
        if(("employee".equals(role)&&"home".equals(screen))||("admin".equals(role)&&"dashboard".equals(screen))){showRoleSelector();return;}
        if(isMoreScreen(screen)){render(role,"more");return;}
        render(role,"employee".equals(role)?"home":"dashboard");
    }

    public static class MapPreviewView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path route=new Path();
        public MapPreviewView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int w=getWidth(),h=getHeight();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(Color.rgb(211,220,230));
            for(int i=1;i<5;i++)canvas.drawLine(w*i/5f,0,w*i/5f,h,p);
            for(int i=1;i<4;i++)canvas.drawLine(0,h*i/4f,w,h*i/4f,p);

            p.setStrokeWidth(10);p.setColor(Color.WHITE);
            canvas.drawLine(0,h*.72f,w,h*.25f,p);
            canvas.drawLine(w*.18f,0,w*.68f,h,p);

            route.reset();route.moveTo(w*.18f,h*.72f);route.cubicTo(w*.30f,h*.60f,w*.44f,h*.54f,w*.55f,h*.45f);route.cubicTo(w*.65f,h*.37f,w*.72f,h*.31f,w*.82f,h*.28f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(BLUE);canvas.drawPath(route,p);

            p.setStyle(Paint.Style.FILL);p.setColor(NAVY);canvas.drawCircle(w*.82f,h*.28f,16,p);
            p.setColor(Color.WHITE);canvas.drawCircle(w*.82f,h*.28f,7,p);

            p.setColor(Color.rgb(24,139,86));canvas.drawCircle(w*.18f,h*.72f,11,p);
            p.setColor(Color.rgb(199,132,20));canvas.drawCircle(w*.55f,h*.45f,8,p);

            p.setColor(Color.rgb(93,113,137));p.setTextSize(28);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            canvas.drawText("Native map layout preview",24,42,p);
            p.setTextSize(22);p.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Route / markers shown for visual design only",24,72,p);
        }
    }
}
