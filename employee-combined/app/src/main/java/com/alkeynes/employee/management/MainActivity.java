package com.alkeynes.employee.management;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.*;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MainActivity extends Activity implements AdminOperations.Host {
    private FrameLayout root;
    private LinearLayout content;
    private Ui ui;
    private SecureStore store;
    private ApiClient api;
    private final ExecutorService net=Executors.newFixedThreadPool(3);
    private final Handler main=new Handler(Looper.getMainLooper());
    private JSONObject user;
    private String screen="";
    private MapView mapView;
    private Consumer<Location> pendingLocationAction;
    private static final int REQ_LOCATION=501, REQ_NOTIFICATION=502;
    private boolean notificationPermissionAsked=false;
    private boolean backgroundGuideShown=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        MapLibre.getInstance(this);
        store=new SecureStore(this); api=new ApiClient(store); ui=new Ui(this);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=new FrameLayout(this); root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());top=i.top;bottom=i.bottom;}
            else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(0,top,0,bottom);return insets;
        });
        setContentView(root);root.requestApplyInsets();
        boot();
    }

    private void boot(){
        if(store.get("access_token")==null){showLogin();return;}
        showBusy("Restoring secure session…");
        callGet("bootstrap",null,r->{
            hideBusy();
            user=r.optJSONObject("user");
            JSONObject a=r.optJSONObject("attendance");
            if(a!=null)syncTracking(a);
            String requested=getIntent()!=null?getIntent().getStringExtra("open_screen"):null;
            if(requested!=null)getIntent().removeExtra("open_screen");
            render(requested!=null?requested:(isAdmin()?"dashboard":"home"));
        });
    }

    private boolean isAdmin(){return user!=null&&user.optBoolean("is_admin",false);}
    private boolean isEmployeeRole(){return user!=null&&"employee".equals(user.optString("role_key"));}

    private void showLogin(){
        cleanupMap();root.removeAllViews();
        ScrollView sv=new ScrollView(this);
        LinearLayout page=ui.column();page.setPadding(ui.dp(22),ui.dp(34),ui.dp(22),ui.dp(34));sv.addView(page,new ScrollView.LayoutParams(-1,-2));

        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_employee_management);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(72),ui.dp(72));lp.gravity=Gravity.CENTER_HORIZONTAL;page.addView(logo,lp);
        TextView title=ui.text("Employee Management",27,Ui.NAVY,true);title.setGravity(Gravity.CENTER);page.addView(title,ui.match(0,14,0,0));
        TextView sub=ui.text("Native Android",14,Ui.MUTED,false);sub.setGravity(Gravity.CENTER);page.addView(sub,ui.match(0,4,0,22));

        LinearLayout card=ui.card();card.addView(ui.label("Sign in"));
        card.addView(ui.muted("Use your existing Employee Management account."),ui.match(0,4,0,0));
        EditText email=ui.input("Work email",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pass=ui.input("Password",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(email,ui.match(0,14,0,0));card.addView(pass,ui.match(0,10,0,0));
        Button login=ui.primary("Sign in");
        card.addView(login,ui.match(0,14,0,0));
        TextView status=ui.muted("");status.setGravity(Gravity.CENTER);card.addView(status,ui.match(0,10,0,0));
        page.addView(card,ui.match());

        LinearLayout security=ui.softCard();
        security.addView(ui.label("Secure native session"));
        security.addView(ui.muted("The app connects only to the Employee Management HTTPS API. Database credentials are never stored in the APK."),ui.match(0,5,0,0));
        page.addView(security,ui.match(0,14,0,0));

        login.setOnClickListener(v->{
            String e=email.getText().toString().trim(),p=pass.getText().toString();
            if(e.isEmpty()||p.isEmpty()){status.setText("Enter your email and password.");return;}
            status.setText("Signing in…");login.setEnabled(false);
            final String deviceId=Settings.Secure.getString(getContentResolver(),Settings.Secure.ANDROID_ID);
            final String label=Build.MANUFACTURER+" "+Build.MODEL;
            net.submit(()->{
                try{
                    JSONObject r=api.login(e,p,deviceId,label);
                    store.put("access_token",r.optString("access_token"));
                    String tt=r.optString("tracking_token",null);if(tt!=null&&!tt.isEmpty())store.put("tracking_token",tt);
                    user=r.getJSONObject("user");
                    runOnUiThread(()->{hideKeyboard();render(isAdmin()?"dashboard":"home");syncBootstrap();});
                }catch(Exception ex){runOnUiThread(()->{login.setEnabled(true);status.setText(message(ex));});}
            });
        });
        root.addView(sv,new FrameLayout.LayoutParams(-1,-1));
    }

    private void syncBootstrap(){
        callGet("bootstrap",null,r->{user=r.optJSONObject("user");JSONObject a=r.optJSONObject("attendance");if(a!=null)syncTracking(a);});
    }

    private void render(String target){
        if(target==null||target.isEmpty())target=isAdmin()?"dashboard":"home";
        if(!allowedTarget(target))target=isAdmin()?"dashboard":"home";
        screen=target; cleanupMap(); root.removeAllViews();

        LinearLayout shell=ui.column();shell.setBackgroundColor(Ui.BG);root.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        shell.addView(topBar(),new LinearLayout.LayoutParams(-1,ui.dp(72)));
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);
        content=ui.column();content.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(24));sv.addView(content,new ScrollView.LayoutParams(-1,-2));
        shell.addView(sv,new LinearLayout.LayoutParams(-1,0,1f));
        shell.addView(bottomNav(),new LinearLayout.LayoutParams(-1,ui.dp(68)));

        if("more".equals(screen)){if(isAdmin())adminMore();else employeeMore();return;}
        content.addView(loadingCard(),ui.match());
        Map<String,String> q=null;
        String action=actionForScreen(screen);
        callGet(action,q,r->{content.removeAllViews();JSONObject d=r.optJSONObject("data");if(d==null)d=new JSONObject();if(isAdmin())buildAdmin(d);else buildEmployee(d);});
    }

    private boolean allowedTarget(String s){
        if(isAdmin())return Arrays.asList("dashboard","people","attendance","map","more","organisation","locations","shifts","holidays","admin_leave","timesheets","field","expenses","payroll","reports","notifications","security","settings").contains(s);
        return Arrays.asList("home","attendance","leave","more","profile","timesheets","field","expenses","payroll","notifications","security").contains(s);
    }

    private String actionForScreen(String s){
        if(!isAdmin()){
            switch(s){case"home":return"employee_home";case"attendance":return"employee_attendance";case"leave":return"employee_leave";case"profile":return"profile";case"timesheets":return"employee_timesheets";case"field":return"employee_field";case"expenses":return"employee_expenses";case"payroll":return"employee_payroll";case"notifications":return"notifications";case"security":return"security";default:return"employee_home";}
        }
        switch(s){case"dashboard":return"admin_dashboard";case"people":return"admin_employees";case"attendance":return"admin_attendance";case"map":return"admin_live_map";case"organisation":return"admin_organisation";case"locations":return"admin_locations";case"shifts":return"admin_shifts";case"holidays":return"admin_holidays";case"admin_leave":return"admin_leave";case"timesheets":return"admin_timesheets";case"field":return"admin_field";case"expenses":return"admin_expenses";case"payroll":return"admin_payroll";case"reports":return"admin_reports";case"notifications":return"notifications";case"security":return"security";case"settings":return"admin_settings";default:return"admin_dashboard";}
    }

    private View topBar(){
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(ui.dp(16),ui.dp(8),ui.dp(12),ui.dp(8));bar.setBackgroundColor(Color.WHITE);bar.setElevation(ui.dp(2));
        LinearLayout tx=ui.column();tx.addView(ui.text("Employee Management",12,Ui.MUTED,true));tx.addView(ui.text(pageTitle(screen),20,Ui.NAVY,true));bar.addView(tx,new LinearLayout.LayoutParams(0,-2,1f));
        TextView role=ui.pill(isAdmin()?"ADMIN":"EMPLOYEE",Ui.NAVY,Color.WHITE);bar.addView(role,ui.wrap());
        return bar;
    }

    private View bottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);nav.setPadding(ui.dp(4),ui.dp(5),ui.dp(4),ui.dp(5));nav.setBackgroundColor(Color.WHITE);nav.setElevation(ui.dp(8));
        if(isAdmin()){
            nav.addView(navItem("Home","dashboard","⌂"),ui.weight());nav.addView(navItem("People","people","♙"),ui.weight());nav.addView(navItem("Attendance","attendance","◎"),ui.weight());nav.addView(navItem("Live Map","map","⌖"),ui.weight());nav.addView(navItem("More","more","☷"),ui.weight());
        }else{
            nav.addView(navItem("Home","home","⌂"),ui.weight());nav.addView(navItem("Attendance","attendance","◎"),ui.weight());nav.addView(navItem("Leave","leave","◫"),ui.weight());nav.addView(navItem("More","more","☷"),ui.weight());nav.addView(navItem("Profile","profile","○"),ui.weight());
        }
        return nav;
    }

    private View navItem(String label,String target,String icon){
        boolean active=target.equals(screen)||("more".equals(target)&&isMoreScreen(screen));
        LinearLayout item=ui.column();item.setGravity(Gravity.CENTER);item.setPadding(ui.dp(2),ui.dp(3),ui.dp(2),ui.dp(2));
        TextView i=ui.text(icon,20,active?Ui.BLUE:Ui.MUTED,false);i.setGravity(Gravity.CENTER);TextView l=ui.text(label,10,active?Ui.NAVY:Ui.MUTED,active);l.setGravity(Gravity.CENTER);
        item.addView(i,ui.match());item.addView(l,ui.match());item.setOnClickListener(v->render(target));return item;
    }

    private boolean isMoreScreen(String s){return Arrays.asList("timesheets","field","expenses","payroll","notifications","security","settings","reports","organisation","locations","shifts","holidays","admin_leave").contains(s);}

    private View loadingCard(){LinearLayout c=ui.softCard();ProgressBar p=new ProgressBar(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(34),ui.dp(34));lp.gravity=Gravity.CENTER_HORIZONTAL;c.addView(p,lp);TextView t=ui.muted("Loading secure data…");t.setGravity(Gravity.CENTER);c.addView(t,ui.match(0,8,0,0));return c;}

    private void buildEmployee(JSONObject d){
        switch(screen){case"home":employeeHome(d);break;case"attendance":employeeAttendance(d);break;case"leave":employeeLeave(d);break;case"profile":employeeProfile(d);break;case"timesheets":employeeTimesheets(d);break;case"field":employeeField(d);break;case"expenses":employeeExpenses(d);break;case"payroll":employeePayroll(d);break;case"notifications":notifications(d);break;case"security":security(d);break;default:employeeHome(d);}
    }

    private void buildAdmin(JSONObject d){
        AdminOperations ops=new AdminOperations(this,ui,content,user,this);
        switch(screen){
            case"dashboard":adminDashboard(d);break;
            case"people":ops.people(d);break;
            case"attendance":ops.attendance(d);break;
            case"map":adminMap(d);break;
            case"organisation":adminOrganisation(d);break;
            case"locations":ops.locations(d);break;
            case"shifts":ops.shifts(d);break;
            case"holidays":ops.holidays(d);break;
            case"admin_leave":adminLeave(d);break;
            case"timesheets":adminTimesheets(d);break;
            case"field":ops.field(d);break;
            case"expenses":adminExpenses(d);break;
            case"payroll":ops.payroll(d);break;
            case"reports":adminReports(d);break;
            case"notifications":notifications(d);break;
            case"security":security(d);break;
            case"settings":ops.settings(d);break;
            default:adminDashboard(d);
        }
    }

    private void employeeHome(JSONObject d){
        JSONObject u=d.optJSONObject("user"),a=d.optJSONObject("attendance"),month=d.optJSONObject("month");
        LinearLayout hero=ui.darkCard();hero.addView(ui.text(greeting(),14,Color.rgb(206,218,235),false));hero.addView(ui.text(u!=null?u.optString("name","Employee"):"Employee",25,Color.WHITE,true),ui.match(0,4,0,0));hero.addView(ui.pill(a!=null&&a.optBoolean("active")?"WORKING":"NOT CLOCKED IN",Color.rgb(38,82,130),Color.WHITE),ui.wrap());content.addView(hero,ui.match(0,12,0,0));
        content.addView(ui.section("Today"),ui.match(0,20,0,10));
        LinearLayout today=ui.card();JSONObject shift=a!=null?a.optJSONObject("shift"):null;JSONObject session=a!=null?a.optJSONObject("session"):null;
        today.addView(rowTitle("Today's shift",shift!=null?shift.optString("name","Assigned"):"Not assigned"));
        if(shift!=null)today.addView(ui.kv("Hours",shortTime(shift.optString("start_time"))+" – "+shortTime(shift.optString("end_time"))),ui.match());
        today.addView(ui.kv("Attendance",a!=null&&a.optBoolean("active")?"Clocked in "+shortTime(session!=null?session.optString("clock_in_at"):""):"No active session"),ui.match());
        Button att=ui.primary(a!=null&&a.optBoolean("active")?"Open Attendance":"Clock In");att.setOnClickListener(v->render("attendance"));today.addView(att,ui.match(0,14,0,0));content.addView(today,ui.match());
        content.addView(ui.section("Quick snapshot"),ui.match(0,20,0,10));
        addMetricPair("Worked today",session!=null?minutes(session.optInt("work_minutes")):"0h 0m","Month present",month!=null?String.valueOf(month.optInt("sessions")):"0");
        addMetricPair("Late days",month!=null?String.valueOf(month.optInt("late_days")):"0","Leave used",fmt(d.optDouble("used_leave",0))+" d");
        addMetricPair("Active visits",String.valueOf(d.optInt("active_visits",0)),"Role",user.optString("role_name","Employee"));
    }

    private void employeeAttendance(JSONObject d){
        JSONObject st=d.optJSONObject("state");if(st==null)st=new JSONObject();syncTracking(st);
        boolean active=st.optBoolean("active"),br=st.optBoolean("break_active");JSONObject s=st.optJSONObject("session");
        LinearLayout state=ui.darkCard();TextView clock=ui.text(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),42,Color.WHITE,true);clock.setGravity(Gravity.CENTER);state.addView(clock,ui.match());
        TextView status=ui.text(active?(br?"ON BREAK":"CLOCKED IN"):"NOT CLOCKED IN",13,Color.rgb(204,219,237),true);status.setGravity(Gravity.CENTER);state.addView(status,ui.match(0,5,0,0));
        if(active&&s!=null){TextView since=ui.text("Since "+shortTime(s.optString("clock_in_at")),13,Color.WHITE,false);since.setGravity(Gravity.CENTER);state.addView(since,ui.match(0,4,0,0));}
        content.addView(state,ui.match(0,12,0,0));

        LinearLayout gps=ui.card();gps.addView(rowTitle("Location authority","Server validated"));gps.addView(ui.kv("Maximum accuracy",Math.round(st.optDouble("max_accuracy_m",200))+" m"),ui.match());gps.addView(ui.kv("Live tracking",st.optBoolean("tracking_enabled")?"Enabled":"Disabled"),ui.match());content.addView(gps,ui.match(0,12,0,0));

        if(!active){Button b=ui.primary("Clock In");b.setOnClickListener(v->attendanceAction("clock_in"));content.addView(b,ui.match(0,12,0,0));}
        else{
            Button b=ui.secondary(br?"End Break":"Start Break");b.setOnClickListener(v->attendanceAction(br?"break_end":"break_start"));content.addView(b,ui.match(0,12,0,0));
            Button out=ui.danger("Clock Out");out.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clock out?").setMessage("This will end your active work session.").setPositiveButton("Clock Out",(x,w)->attendanceAction("clock_out")).setNegativeButton("Cancel",null).show());content.addView(out,ui.match(0,8,0,0));
        }

        content.addView(ui.section("Authorised work locations"),ui.match(0,22,0,10));
        JSONArray locs=st.optJSONArray("locations");if(locs==null||locs.length()==0)content.addView(empty("No work location assigned","Clock-in may be blocked when geofencing is required."));
        else for(int i=0;i<locs.length();i++){JSONObject x=locs.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),x.optInt("radius_meters")+" m"));c.addView(ui.muted(x.optString("address","Assigned GPS location")),ui.match(0,5,0,0));content.addView(c,ui.match(0,0,0,8));}

        content.addView(ui.section("Today's events"),ui.match(0,20,0,10));JSONArray ev=d.optJSONArray("events");if(ev==null||ev.length()==0)content.addView(empty("No attendance events yet","Clock-in, breaks and clock-out will appear here."));
        else for(int i=0;i<ev.length();i++){JSONObject x=ev.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(titleCase(x.optString("event_type")),shortTime(x.optString("event_at"))));String sub=x.optString("location_name","GPS captured");if(x.has("accuracy")&&!x.isNull("accuracy"))sub+=" · "+Math.round(x.optDouble("accuracy"))+" m accuracy";c.addView(ui.muted(sub),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    private void attendanceAction(String action){
        getFreshLocation(loc->{
            showBusy("Recording "+titleCase(action)+"…");
            JSONObject b=new JSONObject();
            try{b.put("attendance_action",action);b.put("lat",loc.getLatitude());b.put("lng",loc.getLongitude());b.put("accuracy",loc.hasAccuracy()?loc.getAccuracy():0);b.put("client_event_id",UUID.randomUUID().toString().replace("-",""));}catch(Exception ignored){}
            callPost("attendance_action",b,r->{hideBusy();JSONObject st=r.optJSONObject("state");if(st!=null)syncTracking(st);toast(r.optString("message","Attendance saved."));render("attendance");});
        });
    }

    private void employeeLeave(JSONObject d){
        JSONArray balances=d.optJSONArray("balances");content.addView(ui.section("Leave balance"),ui.match(0,14,0,10));
        if(balances!=null)for(int i=0;i<balances.length();i++){JSONObject b=balances.optJSONObject(i);LinearLayout c=ui.card();String v=b.optDouble("annual_days")>0?fmt(b.optDouble("available"))+" days":"No annual cap";c.addView(rowTitle(b.optString("name"),v));c.addView(ui.muted(b.optInt("is_paid")==1?"Paid leave":"Unpaid leave"),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
        Button req=ui.primary("Request Leave");req.setOnClickListener(v->leaveDialog(d.optJSONArray("types")));content.addView(req,ui.match(0,12,0,0));
        content.addView(ui.section("Request history"),ui.match(0,22,0,10));JSONArray rows=d.optJSONArray("requests");if(rows==null||rows.length()==0)content.addView(empty("No leave requests","Your submitted requests will appear here."));
        else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("type_name"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("start_date")+" → "+x.optString("end_date")+" · "+fmt(x.optDouble("days"))+" days"),ui.match(0,4,0,0));if(!x.optString("reason").isEmpty())c.addView(ui.muted(x.optString("reason")),ui.match(0,3,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    private void leaveDialog(JSONArray types){
        if(types==null||types.length()==0){toast("No active leave types are available.");return;}
        LinearLayout form=dialogForm();List<String> names=new ArrayList<>();List<Integer> ids=new ArrayList<>();for(int i=0;i<types.length();i++){JSONObject t=types.optJSONObject(i);names.add(t.optString("name")+(t.optInt("is_paid")==1?" · Paid":" · Unpaid"));ids.add(t.optInt("id"));}
        Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));form.addView(sp,ui.match());
        EditText from=dateInput("From date"),to=dateInput("To date"),reason=ui.input("Reason",InputType.TYPE_CLASS_TEXT);
        form.addView(from,ui.match(0,10,0,0));form.addView(to,ui.match(0,10,0,0));form.addView(reason,ui.match(0,10,0,0));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Request Leave").setView(form).setPositiveButton("Submit",null).setNegativeButton("Cancel",null).create();
        dlg.setOnShowListener(x->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(from.getText().length()==0||to.getText().length()==0){toast("Select both dates.");return;}JSONObject b=new JSONObject();try{b.put("leave_type_id",ids.get(sp.getSelectedItemPosition()));b.put("start_date",from.getText().toString());b.put("end_date",to.getText().toString());b.put("reason",reason.getText().toString());}catch(Exception ignored){}showBusy("Submitting leave…");callPost("leave_request",b,r->{hideBusy();dlg.dismiss();toast(r.optString("message"));render("leave");});}));dlg.show();
    }

    private void employeeTimesheets(JSONObject d){
        JSONArray sessions=d.optJSONArray("sessions"),ots=d.optJSONArray("overtime_requests");int work=0,br=0,ot=0;if(sessions!=null)for(int i=0;i<sessions.length();i++){JSONObject x=sessions.optJSONObject(i);work+=x.optInt("effective_work_minutes");br+=x.optInt("break_minutes");ot+=x.optInt("overtime_minutes");}
        addMetricPair("Worked",minutes(work),"Break",minutes(br));addMetricPair("Auto OT",minutes(ot),"Days",String.valueOf(sessions==null?0:sessions.length()));
        content.addView(ui.section("Daily timesheet"),ui.match(0,20,0,10));if(sessions==null||sessions.length()==0)content.addView(empty("No attendance in this period","Recorded attendance sessions will appear here."));
        else for(int i=0;i<sessions.length();i++){JSONObject x=sessions.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("work_date"),minutes(x.optInt("effective_work_minutes"))));c.addView(ui.muted(shortTime(x.optString("effective_clock_in"))+" → "+(x.isNull("effective_clock_out")?"Open":shortTime(x.optString("effective_clock_out")))+" · break "+minutes(x.optInt("break_minutes"))),ui.match(0,4,0,0));if(!x.isNull("effective_clock_out")){Button r=ui.secondary("Request OT");final int sid=x.optInt("id"),suggest=Math.max(1,x.optInt("overtime_minutes"));r.setOnClickListener(v->overtimeDialog(sid,suggest));c.addView(r,ui.match(0,10,0,0));}content.addView(c,ui.match(0,0,0,8));}
        content.addView(ui.section("Overtime requests"),ui.match(0,20,0,10));if(ots==null||ots.length()==0)content.addView(empty("No overtime requests","Requests submitted from closed attendance days will appear here."));else for(int i=0;i<ots.length();i++){JSONObject x=ots.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("work_date"),titleCase(x.optString("status"))));c.addView(ui.muted(minutes(x.optInt("minutes"))+" · "+x.optString("reason","No reason")),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    private void overtimeDialog(int sid,int suggested){
        LinearLayout f=dialogForm();EditText mins=ui.input("Minutes",InputType.TYPE_CLASS_NUMBER);mins.setText(String.valueOf(suggested));EditText reason=ui.input("Reason",InputType.TYPE_CLASS_TEXT);f.addView(mins,ui.match());f.addView(reason,ui.match(0,10,0,0));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Request Overtime Approval").setView(f).setPositiveButton("Submit",null).setNegativeButton("Cancel",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{JSONObject b=new JSONObject();try{b.put("attendance_session_id",sid);b.put("minutes",Integer.parseInt(mins.getText().toString()));b.put("reason",reason.getText().toString());}catch(Exception e){toast("Enter valid minutes.");return;}showBusy("Submitting overtime…");callPost("overtime_request",b,r->{hideBusy();d.dismiss();toast(r.optString("message"));render("timesheets");});}));d.show();
    }

    private void employeeField(JSONObject d){
        JSONArray rows=d.optJSONArray("visits");content.addView(ui.section("Field assignments"),ui.match(0,14,0,10));if(rows==null||rows.length()==0){content.addView(empty("No field assignments","Assigned client/site visits will appear here."));return;}
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("title"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("client_name")+" · "+x.optString("address","")),ui.match(0,4,0,0));if(x.optString("status").equals("assigned")||x.optString("status").equals("in_progress")){Button b=ui.primary(x.optString("status").equals("assigned")?"Check In":"Check Out");final int id=x.optInt("id");final String a=x.optString("status").equals("assigned")?"check_in":"check_out";b.setOnClickListener(v->fieldAction(id,a));c.addView(b,ui.match(0,10,0,0));}content.addView(c,ui.match(0,0,0,8));}
    }

    private void fieldAction(int id,String a){
        getFreshLocation(loc->{JSONObject b=new JSONObject();try{b.put("visit_id",id);b.put("visit_action",a);b.put("lat",loc.getLatitude());b.put("lng",loc.getLongitude());b.put("accuracy",loc.hasAccuracy()?loc.getAccuracy():0);}catch(Exception ignored){}showBusy("Validating site location…");callPost("field_action",b,r->{hideBusy();toast(r.optString("message"));render("field");});});
    }

    private void employeeExpenses(JSONObject d){
        JSONArray rows=d.optJSONArray("expenses");double pending=0,approved=0;if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if("pending".equals(x.optString("status")))pending+=x.optDouble("amount");if("approved".equals(x.optString("status")))approved+=x.optDouble("amount");}
        addMetricPair("Pending","₹"+money(pending),"Approved","₹"+money(approved));Button add=ui.primary("Submit Expense");add.setOnClickListener(v->expenseDialog(d.optJSONArray("visits")));content.addView(add,ui.match(0,12,0,0));
        content.addView(ui.section("Claims"),ui.match(0,20,0,10));if(rows==null||rows.length()==0)content.addView(empty("No expense claims","Submitted claims will appear here."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("category"),"₹"+money(x.optDouble("amount"))));c.addView(ui.muted(x.optString("expense_date")+" · "+titleCase(x.optString("status"))+(x.optString("visit_title").isEmpty()?"":" · "+x.optString("visit_title"))),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    private void expenseDialog(JSONArray visits){
        LinearLayout f=dialogForm();EditText date=dateInput("Expense date");date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));EditText cat=ui.input("Category",InputType.TYPE_CLASS_TEXT);EditText amount=ui.input("Amount",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText notes=ui.input("Notes",InputType.TYPE_CLASS_TEXT);f.addView(date,ui.match());f.addView(cat,ui.match(0,10,0,0));f.addView(amount,ui.match(0,10,0,0));
        Spinner sp=new Spinner(this);List<String> names=new ArrayList<>();List<Integer> ids=new ArrayList<>();names.add("General expense");ids.add(0);if(visits!=null)for(int i=0;i<visits.length();i++){JSONObject x=visits.optJSONObject(i);names.add(x.optString("title"));ids.add(x.optInt("id"));}sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));f.addView(sp,ui.match(0,10,0,0));f.addView(notes,ui.match(0,10,0,0));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Submit Expense").setView(f).setPositiveButton("Submit",null).setNegativeButton("Cancel",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{JSONObject b=new JSONObject();try{b.put("expense_date",date.getText().toString());b.put("category",cat.getText().toString());b.put("amount",Double.parseDouble(amount.getText().toString()));b.put("field_visit_id",ids.get(sp.getSelectedItemPosition()));b.put("notes",notes.getText().toString());}catch(Exception e){toast("Enter a valid amount.");return;}showBusy("Submitting expense…");callPost("expense_submit",b,r->{hideBusy();d.dismiss();toast(r.optString("message"));render("expenses");});}));d.show();
    }

    private void employeePayroll(JSONObject d){
        JSONArray rows=d.optJSONArray("records");if(rows==null||rows.length()==0){content.addView(empty("No payroll record yet","Generated payroll will appear here after the payroll period is processed."));return;}
        JSONObject latest=rows.optJSONObject(0);LinearLayout hero=ui.darkCard();hero.addView(ui.text("Latest net pay",13,Color.rgb(204,219,237),false));hero.addView(ui.text("₹"+money(latest.optDouble("net_salary")),34,Color.WHITE,true),ui.match(0,5,0,0));hero.addView(ui.text(latest.optString("period_name"),12,Color.rgb(204,219,237),false),ui.match(0,5,0,0));content.addView(hero,ui.match(0,12,0,0));
        content.addView(ui.section("Payroll history"),ui.match(0,20,0,10));for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("period_name"),"₹"+money(x.optDouble("net_salary"))));c.addView(ui.muted(x.optString("start_date")+" → "+x.optString("end_date")+" · "+titleCase(x.optString("period_status"))),ui.match(0,4,0,0));c.addView(ui.kv("Base","₹"+money(x.optDouble("base_salary"))),ui.match());c.addView(ui.kv("Overtime","₹"+money(x.optDouble("overtime_amount"))),ui.match());c.addView(ui.kv("Deduction","₹"+money(x.optDouble("unpaid_leave_deduction"))),ui.match());content.addView(c,ui.match(0,0,0,8));}
    }

    private void employeeProfile(JSONObject d){
        JSONObject e=d.optJSONObject("employee");LinearLayout hero=ui.darkCard();String name=user.optString("name");hero.addView(ui.text(name,25,Color.WHITE,true));hero.addView(ui.text(user.optString("employee_code")+" · "+user.optString("role_name"),13,Color.rgb(203,216,234),false),ui.match(0,5,0,0));content.addView(hero,ui.match(0,12,0,0));if(e!=null){LinearLayout c=ui.card();c.addView(ui.kv("Email",e.optString("email")),ui.match());c.addView(ui.kv("Phone",blank(e.optString("phone"))),ui.match());c.addView(ui.kv("Branch",blank(e.optString("branch_name"))),ui.match());c.addView(ui.kv("Department",blank(e.optString("department_name"))),ui.match());c.addView(ui.kv("Designation",blank(e.optString("designation_name"))),ui.match());c.addView(ui.kv("Manager",blank((e.optString("manager_first")+" "+e.optString("manager_last")).trim())),ui.match());c.addView(ui.kv("Joining date",blank(e.optString("joining_date"))),ui.match());content.addView(c,ui.match(0,12,0,0));}Button logout=ui.danger("Sign Out");logout.setOnClickListener(v->logout());content.addView(logout,ui.match(0,14,0,0));
    }

    private void employeeMore(){
        content.addView(ui.section("Work"),ui.match(0,14,0,10));menu("Timesheets & OT","Recorded work and overtime","timesheets");menu("Field Visits","Client/site assignments","field");menu("Expenses","Submit and review claims","expenses");menu("Payroll","Generated pay records","payroll");content.addView(ui.section("Account"),ui.match(0,22,0,10));menu("Notifications","Updates that need attention","notifications");menu("Security","Native sessions and tracking devices","security");
    }

    private void adminDashboard(JSONObject d){
        JSONObject c=d.optJSONObject("counts");if(c==null)c=new JSONObject();LinearLayout hero=ui.darkCard();hero.addView(ui.text("Operations overview",13,Color.rgb(203,216,234),false));hero.addView(ui.text("Today",28,Color.WHITE,true),ui.match(0,3,0,0));content.addView(hero,ui.match(0,12,0,0));content.addView(ui.section("Workforce"),ui.match(0,20,0,10));addMetricPair("Present",String.valueOf(c.optInt("present")),"Absent",String.valueOf(c.optInt("absent")));addMetricPair("Late",String.valueOf(c.optInt("late")),"On Leave",String.valueOf(c.optInt("on_leave")));addMetricPair("Working",String.valueOf(c.optInt("working")),"GPS Stale",String.valueOf(c.optInt("stale")));content.addView(ui.section("Needs attention"),ui.match(0,20,0,10));attention("Attendance corrections",c.optInt("pending_corrections"),"Review correction requests");attention("Pending leave",c.optInt("pending_leave"),"Review employee requests");attention("Pending expenses",c.optInt("pending_expenses"),"Review submitted claims");
    }

    private void adminPeople(JSONObject d){
        JSONArray rows=d.optJSONArray("employees");content.addView(ui.section("Employees"),ui.match(0,14,0,10));if(rows==null||rows.length()==0){content.addView(empty("No employees","Employee directory is empty."));return;}for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("employee_code")+" · "+blank(x.optString("designation_name"))),ui.match(0,4,0,0));c.addView(ui.muted(blank(x.optString("branch_name"))+" · "+blank(x.optString("department_name"))),ui.match(0,3,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    private void adminAttendance(JSONObject d){
        JSONArray rows=d.optJSONArray("sessions");content.addView(ui.section("Attendance records"),ui.match(0,14,0,10));if(rows==null||rows.length()==0){content.addView(empty("No attendance in selected period","Recorded employee sessions will appear here."));return;}for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("work_date")+" · "+shortTime(x.optString("effective_clock_in"))+" → "+(x.isNull("effective_clock_out")?"Open":shortTime(x.optString("effective_clock_out")))),ui.match(0,4,0,0));c.addView(ui.kv("Worked",minutes(x.optInt("effective_work_minutes"))),ui.match());c.addView(ui.kv("Geofence",titleCase(x.optString("geo_status"))),ui.match());content.addView(c,ui.match(0,0,0,8));}
    }

    private void adminMap(JSONObject d){
        JSONArray people=d.optJSONArray("people");int live=0,stale=0,waiting=0;if(people!=null)for(int i=0;i<people.length();i++){String s=people.optJSONObject(i).optString("tracking_state");if("live".equals(s))live++;else if("stale".equals(s))stale++;else waiting++;}
        addMetricPair("Live",String.valueOf(live),"Stale",String.valueOf(stale));addMetricPair("Waiting",String.valueOf(waiting),"Clocked In",String.valueOf(people==null?0:people.length()));
        mapView=new MapView(this);mapView.onCreate(null);mapView.onStart();mapView.onResume();content.addView(mapView,new LinearLayout.LayoutParams(-1,ui.dp(330)));setupMap(people);
        content.addView(ui.section("People on map"),ui.match(0,20,0,10));if(people==null||people.length()==0){content.addView(empty("No one is clocked in","Employees appear here after clock-in and accepted GPS evidence."));return;}for(int i=0;i<people.length();i++){JSONObject x=people.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),titleCase(x.optString("tracking_state"))));c.addView(ui.muted(x.optString("employee_code")+" · last seen "+blank(x.optString("last_seen_at"))+(x.isNull("accuracy")?"":" · "+Math.round(x.optDouble("accuracy"))+" m")),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    private void setupMap(JSONArray people){
        if(mapView==null)return;mapView.getMapAsync(map->{
            String style="{\"version\":8,\"sources\":{\"osm\":{\"type\":\"raster\",\"tiles\":[\"https://tile.openstreetmap.org/{z}/{x}/{y}.png\"],\"tileSize\":256,\"attribution\":\"© OpenStreetMap contributors\"}},\"layers\":[{\"id\":\"osm\",\"type\":\"raster\",\"source\":\"osm\"}]}";
            map.setStyle(new Style.Builder().fromJson(style),loaded->{
                if(people==null||people.length()==0)return;boolean centered=false;
                for(int i=0;i<people.length();i++){JSONObject p=people.optJSONObject(i);if(p==null||p.isNull("latitude")||p.isNull("longitude"))continue;double lat=p.optDouble("latitude"),lng=p.optDouble("longitude");map.addMarker(new MarkerOptions().position(new LatLng(lat,lng)).title(p.optString("first_name")+" "+p.optString("last_name")).snippet(titleCase(p.optString("tracking_state"))+" · "+blank(p.optString("last_seen_at"))));JSONArray path=p.optJSONArray("path_points");if(path!=null&&path.length()>1){List<LatLng> pts=new ArrayList<>();for(int k=0;k<path.length();k++){JSONObject x=path.optJSONObject(k);if(x!=null&&!x.isNull("latitude")&&!x.isNull("longitude"))pts.add(new LatLng(x.optDouble("latitude"),x.optDouble("longitude")));}if(pts.size()>1)map.addPolyline(new PolylineOptions().addAll(pts).color(Ui.BLUE).width(4f));}if(!centered){map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat,lng),15));centered=true;}}
            });
        });
    }

    private void adminOrganisation(JSONObject d){listSimple("Branches",d.optJSONArray("branches"),"name","code");listSimple("Departments",d.optJSONArray("departments"),"name",null);listSimple("Designations",d.optJSONArray("designations"),"name",null);}
    private void adminLocations(JSONObject d){JSONArray a=d.optJSONArray("locations");content.addView(ui.section("Work locations"),ui.match(0,14,0,10));if(a==null||a.length()==0)content.addView(empty("No work locations","Create locations from the web admin until native setup-write controls are enabled."));else for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),x.optInt("radius_meters")+" m"));c.addView(ui.muted(blank(x.optString("branch_name"))+" · "+blank(x.optString("address"))),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}}
    private void adminShifts(JSONObject d){listSimple("Shifts",d.optJSONArray("shifts"),"name","start_time");listSimple("Recent assignments",d.optJSONArray("assignments"),"first_name","shift_name");}
    private void adminHolidays(JSONObject d){listSimple("Holiday calendar",d.optJSONArray("holidays"),"name","holiday_date");}

    private void adminLeave(JSONObject d){
        JSONArray rows=d.optJSONArray("requests");content.addView(ui.section("Leave requests"),ui.match(0,14,0,10));if(rows==null||rows.length()==0){content.addView(empty("No leave requests","Requests will appear here."));return;}for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("type_name")+" · "+x.optString("start_date")+" → "+x.optString("end_date")+" · "+fmt(x.optDouble("days"))+" days"),ui.match(0,4,0,0));if("pending".equals(x.optString("status")))reviewButtons(c,"admin_leave_review",x.optInt("id"),"admin_leave");content.addView(c,ui.match(0,0,0,8));}
    }

    private void adminTimesheets(JSONObject d){
        JSONArray em=d.optJSONArray("employees");int work=0,ot=0;if(em!=null)for(int i=0;i<em.length();i++){work+=em.optJSONObject(i).optInt("work_minutes");ot+=em.optJSONObject(i).optInt("overtime_minutes");}addMetricPair("Recorded work",minutes(work),"Auto OT",minutes(ot));
        content.addView(ui.section("Overtime approvals"),ui.match(0,20,0,10));JSONArray rows=d.optJSONArray("overtime_requests");if(rows==null||rows.length()==0)content.addView(empty("No overtime requests","Employee requests will appear here."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("work_date")+" · "+minutes(x.optInt("minutes"))+" · "+x.optString("reason","")),ui.match(0,4,0,0));if("pending".equals(x.optString("status")))reviewButtons(c,"admin_overtime_review",x.optInt("id"),"timesheets");content.addView(c,ui.match(0,0,0,8));}
    }

    private void adminField(JSONObject d){JSONArray rows=d.optJSONArray("visits");content.addView(ui.section("Field visits"),ui.match(0,14,0,10));if(rows==null||rows.length()==0)content.addView(empty("No field visits","Assigned field work will appear here."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("title"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("first_name")+" "+x.optString("last_name")+" · "+x.optString("client_name")),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}}
    private void adminExpenses(JSONObject d){JSONArray rows=d.optJSONArray("expenses");content.addView(ui.section("Expense claims"),ui.match(0,14,0,10));if(rows==null||rows.length()==0)content.addView(empty("No expense claims","Employee claims will appear here."));else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),"₹"+money(x.optDouble("amount"))));c.addView(ui.muted(x.optString("expense_date")+" · "+x.optString("category")+" · "+titleCase(x.optString("status"))),ui.match(0,4,0,0));if("pending".equals(x.optString("status")))reviewButtons(c,"admin_expense_review",x.optInt("id"),"expenses");content.addView(c,ui.match(0,0,0,8));}}
    private void adminPayroll(JSONObject d){JSONArray periods=d.optJSONArray("periods"),rows=d.optJSONArray("records");content.addView(ui.section("Payroll periods"),ui.match(0,14,0,10));if(periods==null||periods.length()==0)content.addView(empty("No payroll periods","Create/generate payroll from the current admin workflow."));else for(int i=0;i<periods.length();i++){JSONObject x=periods.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),titleCase(x.optString("status"))));c.addView(ui.muted(x.optString("start_date")+" → "+x.optString("end_date")),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}content.addView(ui.section("Selected period records"),ui.match(0,20,0,10));if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),"₹"+money(x.optDouble("net_salary"))));c.addView(ui.muted("Base ₹"+money(x.optDouble("base_salary"))+" · OT ₹"+money(x.optDouble("overtime_amount"))+" · deduction ₹"+money(x.optDouble("unpaid_leave_deduction"))),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}}
    private void adminReports(JSONObject d){JSONObject a=d.optJSONObject("attendance");content.addView(ui.section("Last 30 days"),ui.match(0,14,0,10));addMetricPair("Attendance sessions",a!=null?String.valueOf(a.optInt("c")):"0","Worked",a!=null?minutes(a.optInt("work_minutes")):"0h 0m");addMetricPair("Overtime",a!=null?minutes(a.optInt("overtime_minutes")):"0h 0m","Period",d.optString("from")+" → "+d.optString("to"));listStatusSummary("Leave",d.optJSONArray("leave"));listStatusSummary("Expenses",d.optJSONArray("expenses"));}
    private void adminSettings(JSONObject d){JSONObject s=d.optJSONObject("settings");if(s==null)s=new JSONObject();content.addView(ui.section("Attendance"),ui.match(0,14,0,10));setting("Require geofence",yesNo(s.optString("attendance.require_geofence")));setting("Maximum GPS accuracy",s.optString("attendance.max_accuracy_m")+" m");setting("Outside geofence",s.optString("attendance.allow_outside_geofence").equals("1")?"Allow for review":"Block");setting("Offline punch",yesNo(s.optString("attendance.offline_punch")));content.addView(ui.section("Live tracking"),ui.match(0,22,0,10));setting("Tracking enabled",yesNo(s.optString("tracking.enabled")));setting("Shift only",yesNo(s.optString("tracking.shift_only")));setting("Ping interval",s.optString("attendance.live_ping_seconds")+" sec");content.addView(ui.section("Payroll"),ui.match(0,22,0,10));setting("Monthly divisor",s.optString("payroll.monthly_divisor"));}

    private void notifications(JSONObject d){
        JSONArray rows=d.optJSONArray("notifications");content.addView(ui.section("Notifications"),ui.match(0,14,0,10));if(rows==null||rows.length()==0){content.addView(empty("No notifications","Updates will appear here."));return;}for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("title"),x.isNull("read_at")?"Unread":"Read"));c.addView(ui.muted(x.optString("message")),ui.match(0,4,0,0));c.addView(ui.muted(x.optString("created_at")),ui.match(0,3,0,0));if(x.isNull("read_at")){Button b=ui.secondary("Mark Read");final int id=x.optInt("id");b.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("id",id);}catch(Exception ignored){}callPost("notification_read",p,r->render("notifications"));});c.addView(b,ui.match(0,10,0,0));}content.addView(c,ui.match(0,0,0,8));}
    }

    private void security(JSONObject d){
        int current=d.optInt("current_native_token_id");
        content.addView(ui.section("Native app sessions"),ui.match(0,14,0,10));JSONArray a=d.optJSONArray("native_sessions");int otherActive=0;
        if(a==null||a.length()==0)content.addView(empty("No native sessions","This device session will appear after successful sign-in."));
        else for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);boolean isCurrent=x.optInt("id")==current;boolean active=x.isNull("revoked_at");if(active&&!isCurrent)otherActive++;LinearLayout card=ui.card();card.addView(rowTitle(blank(x.optString("device_label")),isCurrent?"Current":(active?"Active":"Revoked")));card.addView(ui.muted("Last used "+blank(x.optString("last_used_at"))+" · expires "+blank(x.optString("expires_at"))),ui.match(0,4,0,0));content.addView(card,ui.match(0,0,0,8));}
        JSONArray web=d.optJSONArray("web_sessions");if(web!=null)for(int i=0;i<web.length();i++)if(web.optJSONObject(i).isNull("revoked_at"))otherActive++;
        if(otherActive>0){Button revoke=ui.danger("Sign Out Other Sessions");revoke.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Sign out other sessions?").setMessage("Other browser and native app sessions will be revoked. This app stays signed in.").setPositiveButton("Sign Out",(x,w)->{showBusy("Signing out other sessions…");callPost("security_revoke_others",new JSONObject(),r->{hideBusy();toast(r.optString("message"));render("security");});}).setNegativeButton("Cancel",null).show());content.addView(revoke,ui.match(0,10,0,0));}
        content.addView(ui.section("Tracking authorisations"),ui.match(0,20,0,10));JSONArray t=d.optJSONArray("tracking_tokens");if(t!=null)for(int i=0;i<t.length();i++){JSONObject x=t.optJSONObject(i);LinearLayout card=ui.card();card.addView(rowTitle(blank(x.optString("device_label")),x.isNull("revoked_at")?"Authorised":"Revoked"));card.addView(ui.muted("Last used "+blank(x.optString("last_used_at"))),ui.match(0,4,0,0));content.addView(card,ui.match(0,0,0,8));}
        Button logout=ui.danger("Sign Out This App");logout.setOnClickListener(v->logout());content.addView(logout,ui.match(0,16,0,0));
    }

    private void adminMore(){
        content.addView(ui.section("Organisation"),ui.match(0,14,0,10));menu("Organisation","Branches, departments, designations","organisation");menu("Work Locations","Geofence sites","locations");menu("Shifts","Schedules and assignments","shifts");menu("Holidays","Holiday calendar","holidays");
        content.addView(ui.section("Operations"),ui.match(0,22,0,10));menu("Leave","Requests and approvals","admin_leave");menu("Timesheets & OT","Time and overtime review","timesheets");menu("Field Visits","Field assignments","field");menu("Expenses","Claims and approvals","expenses");menu("Payroll","Generated payroll","payroll");menu("Reports","Operational summaries","reports");
        content.addView(ui.section("Control"),ui.match(0,22,0,10));menu("Notifications","Operational updates","notifications");menu("Security","Sessions and devices","security");menu("Settings","Attendance/tracking policy","settings");
    }

    private void reviewButtons(LinearLayout c,String action,int id,String returnScreen){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);Button ok=ui.primary("Approve"),no=ui.danger("Reject");r.addView(ok,new LinearLayout.LayoutParams(0,ui.dp(48),1f));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(48),1f);lp.leftMargin=ui.dp(8);r.addView(no,lp);c.addView(r,ui.match(0,10,0,0));ok.setOnClickListener(v->review(action,id,"approved",returnScreen));no.setOnClickListener(v->review(action,id,"rejected",returnScreen));
    }
    private void review(String action,int id,String status,String returnScreen){JSONObject b=new JSONObject();try{b.put("id",id);b.put("status",status);}catch(Exception ignored){}showBusy(titleCase(status)+"…");callPost(action,b,r->{hideBusy();toast(r.optString("message"));render(returnScreen);});}

    private void listSimple(String title,JSONArray rows,String mainKey,String subKey){content.addView(ui.section(title),ui.match(0,14,0,10));if(rows==null||rows.length()==0){content.addView(empty("No records","Nothing is configured yet."));return;}for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(blank(x.optString(mainKey)),subKey==null?"":blank(x.optString(subKey))));content.addView(c,ui.match(0,0,0,8));}}
    private void listStatusSummary(String title,JSONArray rows){content.addView(ui.section(title),ui.match(0,20,0,10));if(rows==null||rows.length()==0){content.addView(empty("No "+title.toLowerCase()+" activity","No matching records in this reporting period."));return;}for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(titleCase(x.optString("status")),String.valueOf(x.optInt("c"))));if(x.has("amount"))c.addView(ui.muted("₹"+money(x.optDouble("amount"))),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}}
    private void setting(String k,String v){LinearLayout c=ui.card();c.addView(rowTitle(k,v));content.addView(c,ui.match(0,0,0,8));}
    private void attention(String title,int count,String sub){LinearLayout c=ui.card();c.addView(rowTitle(title,String.valueOf(count)));c.addView(ui.muted(sub),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    private void menu(String title,String sub,String target){LinearLayout c=ui.card();c.setOrientation(LinearLayout.HORIZONTAL);c.setGravity(Gravity.CENTER_VERTICAL);LinearLayout tx=ui.column();tx.addView(ui.label(title));tx.addView(ui.muted(sub),ui.match(0,3,0,0));c.addView(tx,new LinearLayout.LayoutParams(0,-2,1f));c.addView(ui.text("›",30,Ui.MUTED,false),ui.wrap());c.setOnClickListener(v->render(target));content.addView(c,ui.match(0,0,0,9));}

    private View rowTitle(String left,String right){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(ui.label(left),new LinearLayout.LayoutParams(0,-2,1f));r.addView(ui.text(right,12,Ui.MUTED,true),ui.wrap());return r;}
    private View empty(String title,String body){LinearLayout c=ui.softCard();TextView t=ui.label(title);t.setGravity(Gravity.CENTER);TextView b=ui.muted(body);b.setGravity(Gravity.CENTER);c.addView(t,ui.match());c.addView(b,ui.match(8,6,8,0));return c;}
    private void addMetricPair(String l1,String v1,String l2,String v2){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.addView(metric(l1,v1),new LinearLayout.LayoutParams(0,-2,1f));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);p.leftMargin=ui.dp(9);r.addView(metric(l2,v2),p);content.addView(r,ui.match(0,0,0,9));}
    private View metric(String label,String value){LinearLayout c=ui.card();c.addView(ui.text(value,24,Ui.NAVY,true));c.addView(ui.text(label,12,Ui.MUTED,false),ui.match(0,5,0,0));return c;}

    private LinearLayout dialogForm(){LinearLayout f=ui.column();f.setPadding(ui.dp(8),ui.dp(4),ui.dp(8),0);return f;}
    private EditText dateInput(String hint){EditText e=ui.input(hint,InputType.TYPE_CLASS_DATETIME);e.setFocusable(false);e.setOnClickListener(v->{Calendar c=Calendar.getInstance();new DatePickerDialog(this,(d,y,m,day)->e.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,day)),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});return e;}

    private void getFreshLocation(Consumer<Location> callback){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){pendingLocationAction=callback;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location best=null;
        try{for(String p:lm.getProviders(true)){Location l=lm.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}}catch(Exception ignored){}
        if(best!=null&&System.currentTimeMillis()-best.getTime()<30000){callback.accept(best);return;}
        showBusy("Getting a precise GPS fix…");
        final boolean[] done={false};LocationListener listener=new LocationListener(){@Override public void onLocationChanged(Location l){if(done[0])return;done[0]=true;try{lm.removeUpdates(this);}catch(Exception ignored){}hideBusy();callback.accept(l);}};
        try{lm.requestSingleUpdate(LocationManager.GPS_PROVIDER,listener,Looper.getMainLooper());}
        catch(Exception e){try{lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper());}catch(Exception x){hideBusy();toast("Location is unavailable. Turn on GPS and try again.");return;}}
        main.postDelayed(()->{if(done[0])return;done[0]=true;try{lm.removeUpdates(listener);}catch(Exception ignored){}hideBusy();toast("Could not get a fresh GPS fix. Move to an open area and try again.");},15000);
    }

    private void syncTracking(JSONObject st){
        if(!isEmployeeRole()){stopService(new Intent(this,TrackingService.class));return;}
        JSONObject s=st.optJSONObject("session");if(s!=null&&!s.isNull("clock_in_at"))store.put("clock_in_at",s.optString("clock_in_at"));else store.remove("clock_in_at");
        if(!st.optBoolean("should_track")){stopService(new Intent(this,TrackingService.class));return;}
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!notificationPermissionAsked){notificationPermissionAsked=true;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATION);}
        Intent i=new Intent(this,TrackingService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        if(Build.VERSION.SDK_INT>=29&&checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=PackageManager.PERMISSION_GRANTED&&!backgroundGuideShown){backgroundGuideShown=true;new AlertDialog.Builder(this).setTitle("Keep tracking active with the screen off").setMessage("For reliable screen-off tracking during an active work session, allow background location for Employee Management Native in Android app settings.").setPositiveButton("Open Settings",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).setNegativeButton("Later",null).show();}
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&pendingLocationAction!=null){Consumer<Location> c=pendingLocationAction;pendingLocationAction=null;getFreshLocation(c);}
        if(requestCode==REQ_NOTIFICATION)syncBootstrap();
    }

    private void callGet(String action,Map<String,String> q,Consumer<JSONObject> ok){net.submit(()->{try{JSONObject r=q==null?api.get(action):api.get(action,q);runOnUiThread(()->ok.accept(r));}catch(Exception e){runOnUiThread(()->handleError(e));}});}
    private void callPost(String action,JSONObject b,Consumer<JSONObject> ok){net.submit(()->{try{JSONObject r=api.post(action,b);runOnUiThread(()->ok.accept(r));}catch(Exception e){runOnUiThread(()->{hideBusy();handleError(e);});}});}

    @Override public void adminPost(String action,JSONObject body,Consumer<JSONObject> ok){callPost(action,body,ok);}
    @Override public void adminRender(String target){render(target);}
    @Override public void adminBusy(String text){showBusy(text);}
    @Override public void adminHideBusy(){hideBusy();}
    @Override public void adminToast(String text){toast(text);}
    @Override public void adminFreshLocation(Consumer<Location> callback){getFreshLocation(callback);}
    private void handleError(Exception e){if(e instanceof ApiClient.ApiException&&((ApiClient.ApiException)e).status==401){store.clear();user=null;stopService(new Intent(this,TrackingService.class));toast("Session expired. Sign in again.");showLogin();return;}toast(message(e));}
    private String message(Exception e){String m=e.getMessage();return m==null||m.isEmpty()?"Request failed.":m;}

    private void logout(){showBusy("Signing out…");callPost("logout",new JSONObject(),r->{hideBusy();store.clear();stopService(new Intent(this,TrackingService.class));user=null;showLogin();});}

    private void showBusy(String text){hideBusy();LinearLayout box=ui.softCard();box.setGravity(Gravity.CENTER);ProgressBar p=new ProgressBar(this);box.addView(p,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));TextView t=ui.muted(text);t.setGravity(Gravity.CENTER);box.addView(t,ui.match(0,8,0,0));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2);lp.gravity=Gravity.CENTER;lp.setMargins(ui.dp(28),0,ui.dp(28),0);box.setTag("busy");root.addView(box,lp);}
    private void hideBusy(){for(int i=root.getChildCount()-1;i>=0;i--){View v=root.getChildAt(i);if("busy".equals(v.getTag()))root.removeView(v);}}

    private void cleanupMap(){if(mapView!=null){try{mapView.onPause();mapView.onStop();mapView.onDestroy();}catch(Exception ignored){}mapView=null;}}
    @Override protected void onResume(){super.onResume();if(mapView!=null)try{mapView.onResume();}catch(Exception ignored){}if(store.get("access_token")!=null&&user!=null)syncBootstrap();}
    @Override protected void onPause(){if(mapView!=null)try{mapView.onPause();}catch(Exception ignored){}super.onPause();}
    @Override protected void onStart(){super.onStart();if(mapView!=null)try{mapView.onStart();}catch(Exception ignored){}}
    @Override protected void onStop(){if(mapView!=null)try{mapView.onStop();}catch(Exception ignored){}super.onStop();}
    @Override public void onLowMemory(){super.onLowMemory();if(mapView!=null)mapView.onLowMemory();}
    @Override protected void onDestroy(){cleanupMap();net.shutdownNow();super.onDestroy();}

    @Override public void onBackPressed(){if(user==null){super.onBackPressed();return;}String home=isAdmin()?"dashboard":"home";if(screen.equals(home)){new AlertDialog.Builder(this).setTitle("Exit Employee Management?").setPositiveButton("Exit",(d,w)->finish()).setNegativeButton("Cancel",null).show();return;}if(isMoreScreen(screen)){render("more");return;}render(home);}

    private String pageTitle(String s){switch(s){case"dashboard":return"Dashboard";case"home":return"Home";case"people":return"Employees";case"attendance":return"Attendance";case"map":return"Live Map";case"leave":return"Leave";case"profile":return"Profile";case"more":return"More";case"timesheets":return"Timesheets & OT";case"field":return"Field Visits";case"expenses":return"Expenses";case"payroll":return"Payroll";case"notifications":return"Notifications";case"security":return"Security";case"settings":return"Settings";case"organisation":return"Organisation";case"locations":return"Work Locations";case"shifts":return"Shifts";case"holidays":return"Holidays";case"admin_leave":return"Leave Management";case"reports":return"Reports";default:return"Employee Management";}}
    private String greeting(){int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return h<12?"Good morning":h<17?"Good afternoon":"Good evening";}
    private String minutes(int m){m=Math.max(0,m);return(m/60)+"h "+(m%60)+"m";}
    private String shortTime(String s){if(s==null||s.isEmpty())return"—";try{if(s.length()>=16&&s.charAt(10)==' ')return s.substring(11,16);if(s.length()>=5)return s.substring(0,5);}catch(Exception ignored){}return s;}
    private String titleCase(String s){if(s==null)return"—";s=s.replace('_',' ').replace('-',' ');StringBuilder b=new StringBuilder();for(String x:s.split(" ")){if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));}return b.length()==0?"—":b.toString();}
    private String blank(String s){return s==null||s.trim().isEmpty()?"—":s;}
    private String money(double d){return String.format(Locale.US,"%,.2f",d);}
    private String fmt(double d){return d==(long)d?String.valueOf((long)d):String.format(Locale.US,"%.1f",d);}
    private String yesNo(String v){return"1".equals(v)?"Yes":"No";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void hideKeyboard(){View v=getCurrentFocus();if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
}
