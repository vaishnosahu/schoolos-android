package com.alkeynes.employee.management;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.location.Location;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

final class AdminOperations {
    interface Host {
        void adminPost(String action, JSONObject body, Consumer<JSONObject> ok);
        void adminRender(String screen);
        void adminBusy(String text);
        void adminHideBusy();
        void adminToast(String text);
        void adminFreshLocation(Consumer<Location> callback);
    }

    private final Activity a;
    private final Ui ui;
    private final LinearLayout content;
    private final JSONObject user;
    private final Host host;

    AdminOperations(Activity a, Ui ui, LinearLayout content, JSONObject user, Host host) {
        this.a=a; this.ui=ui; this.content=content; this.user=user; this.host=host;
    }

    void people(JSONObject d){
        boolean manage=d.optBoolean("can_manage_all"),roles=d.optBoolean("can_manage_roles");
        if(manage){Button add=ui.primary("Add Employee");add.setOnClickListener(v->employeeCreate(d));content.addView(add,ui.match(0,8,0,12));}
        JSONArray rows=d.optJSONArray("employees");
        content.addView(ui.section("Employees"),ui.match(0,8,0,10));
        if(rows==null||rows.length()==0){content.addView(empty("No employees","Employee directory is empty."));return;}
        for(int i=0;i<rows.length();i++){
            JSONObject x=rows.optJSONObject(i);if(x==null)continue;
            LinearLayout c=ui.card();
            c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),title(x.optString("status"))));
            c.addView(ui.muted(x.optString("employee_code")+" · "+blank(x.optString("designation_name"))),ui.match(0,4,0,0));
            c.addView(ui.muted(blank(x.optString("branch_name"))+" · "+blank(x.optString("department_name"))+" · "+title(x.optString("access_role"))),ui.match(0,3,0,0));
            if(manage){Button b=ui.secondary("Manage Employee");b.setOnClickListener(v->employeeMenu(d,x,roles));c.addView(b,ui.match(0,10,0,0));}
            content.addView(c,ui.match(0,0,0,8));
        }
    }

    void attendance(JSONObject d){
        JSONArray rows=d.optJSONArray("sessions");
        content.addView(ui.section("Attendance records"),ui.match(0,8,0,10));
        if(rows==null||rows.length()==0){content.addView(empty("No attendance in selected period","Recorded employee sessions will appear here."));return;}
        for(int i=0;i<rows.length();i++){
            JSONObject x=rows.optJSONObject(i);if(x==null)continue;
            LinearLayout c=ui.card();
            c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),title(x.optString("status"))));
            c.addView(ui.muted(x.optString("work_date")+" · "+shortTime(x.optString("effective_clock_in"))+" → "+(x.isNull("effective_clock_out")?"Open":shortTime(x.optString("effective_clock_out")))),ui.match(0,4,0,0));
            c.addView(kv("Worked",minutes(x.optInt("effective_work_minutes"))));
            c.addView(kv("Geofence",title(x.optString("geo_status"))));
            c.addView(kv("Source",x.optString("source","—").toUpperCase(Locale.US)));
            if(x.optBoolean("has_correction"))c.addView(ui.muted("Effective values include approved audit corrections."),ui.match(0,5,0,0));
            Button correction=ui.secondary(x.optBoolean("has_correction")?"Add Another Correction":"Record Correction");
            correction.setOnClickListener(v->attendanceCorrection(x));
            c.addView(correction,ui.match(0,10,0,0));
            content.addView(c,ui.match(0,0,0,8));
        }
    }

    void locations(JSONObject d){
        boolean manage=d.optBoolean("can_manage");
        if(manage){
            LinearLayout actions=new LinearLayout(a);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button add=ui.primary("Add Geofence"),assign=ui.secondary("Assign Location");
            add.setOnClickListener(v->locationCreate(d));assign.setOnClickListener(v->locationAssign(d));
            actions.addView(add,new LinearLayout.LayoutParams(0,ui.dp(50),1f));
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,ui.dp(50),1f);ap.leftMargin=ui.dp(8);actions.addView(assign,ap);
            content.addView(actions,ui.match(0,8,0,12));
        }
        JSONArray rows=d.optJSONArray("locations");
        content.addView(ui.section("Work locations"),ui.match(0,8,0,10));
        if(rows==null||rows.length()==0){content.addView(empty("No work locations","Create an authorised GPS area before geofence-required attendance."));return;}
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),x.optInt("radius_meters")+" m"));c.addView(ui.muted(blank(x.optString("branch_name"))+" · "+blank(x.optString("address"))),ui.match(0,4,0,0));c.addView(ui.muted(x.optString("latitude")+", "+x.optString("longitude")),ui.match(0,3,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    void shifts(JSONObject d){
        if(d.optBoolean("can_manage")){
            LinearLayout actions=new LinearLayout(a);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button add=ui.primary("Create Shift"),assign=ui.secondary("Assign Shift");
            add.setOnClickListener(v->shiftCreate());assign.setOnClickListener(v->shiftAssign(d));
            actions.addView(add,new LinearLayout.LayoutParams(0,ui.dp(50),1f));
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,ui.dp(50),1f);ap.leftMargin=ui.dp(8);actions.addView(assign,ap);
            content.addView(actions,ui.match(0,8,0,12));
        }
        JSONArray shifts=d.optJSONArray("shifts");
        content.addView(ui.section("Shift templates"),ui.match(0,8,0,10));
        if(shifts==null||shifts.length()==0)content.addView(empty("No shifts","Create the first reusable work schedule."));
        else for(int i=0;i<shifts.length();i++){JSONObject x=shifts.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),shortTime(x.optString("start_time"))+"–"+shortTime(x.optString("end_time"))));c.addView(ui.muted(x.optInt("break_minutes")+"m break · "+x.optInt("grace_minutes")+"m grace"),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
        content.addView(ui.section("Recent assignments"),ui.match(0,20,0,10));
        JSONArray as=d.optJSONArray("assignments");if(as==null||as.length()==0)content.addView(empty("No shift assignments","Assignments appear here after a shift is applied."));
        else for(int i=0;i<as.length();i++){JSONObject x=as.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),x.optString("shift_name")));c.addView(ui.muted(x.optString("start_date")+" → "+(x.isNull("end_date")||x.optString("end_date").isEmpty()?"Open":x.optString("end_date"))),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    void holidays(JSONObject d){
        if(d.optBoolean("can_manage")){Button add=ui.primary("Add Holiday");add.setOnClickListener(v->holidayCreate(d));content.addView(add,ui.match(0,8,0,12));}
        JSONArray rows=d.optJSONArray("holidays");content.addView(ui.section("Holiday calendar"),ui.match(0,8,0,10));
        if(rows==null||rows.length()==0){content.addView(empty("No holidays","No company or branch holiday is configured."));return;}
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),x.optString("holiday_date")));c.addView(ui.muted(blank(x.optString("branch_name")).equals("—")?"All branches":x.optString("branch_name")),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    void field(JSONObject d){
        if(d.optBoolean("can_manage_all")){
            LinearLayout actions=new LinearLayout(a);actions.setOrientation(LinearLayout.HORIZONTAL);
            Button client=ui.primary("Add Client / Site"),visit=ui.secondary("Assign Visit");
            client.setOnClickListener(v->clientCreate());visit.setOnClickListener(v->visitCreate(d));
            actions.addView(client,new LinearLayout.LayoutParams(0,ui.dp(50),1f));
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,ui.dp(50),1f);ap.leftMargin=ui.dp(8);actions.addView(visit,ap);
            content.addView(actions,ui.match(0,8,0,12));
        }
        JSONArray rows=d.optJSONArray("visits");content.addView(ui.section("Field visits"),ui.match(0,8,0,10));
        if(rows==null||rows.length()==0){content.addView(empty("No field visits","Assigned field work will appear here."));return;}
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("title"),title(x.optString("status"))));c.addView(ui.muted(x.optString("first_name")+" "+x.optString("last_name")+" · "+x.optString("client_name")),ui.match(0,4,0,0));c.addView(ui.muted("Scheduled "+blank(x.optString("scheduled_at"))),ui.match(0,3,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    void payroll(JSONObject d){
        Button create=ui.primary("Create Payroll Period");create.setOnClickListener(v->payrollPeriodCreate());content.addView(create,ui.match(0,8,0,12));
        JSONArray periods=d.optJSONArray("periods"),rows=d.optJSONArray("records");
        content.addView(ui.section("Payroll periods"),ui.match(0,8,0,10));
        if(periods==null||periods.length()==0)content.addView(empty("No payroll periods","Create a period after attendance, leave and overtime are reviewed."));
        else for(int i=0;i<periods.length();i++){
            JSONObject x=periods.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("name"),title(x.optString("status"))));c.addView(ui.muted(x.optString("start_date")+" → "+x.optString("end_date")),ui.match(0,4,0,0));
            if("draft".equals(x.optString("status"))){
                LinearLayout r=new LinearLayout(a);r.setOrientation(LinearLayout.HORIZONTAL);
                Button gen=ui.secondary("Generate / Refresh"),lock=ui.danger("Lock Period");final int id=x.optInt("id");
                gen.setOnClickListener(v->confirm("Generate payroll?","This refreshes the draft using the existing server calculation authority.",()->simplePost("admin_payroll_generate",obj("period_id",id),"payroll","Generating payroll…")));
                lock.setOnClickListener(v->confirm("Lock payroll period?","Locked periods cannot be regenerated by the current workflow.",()->simplePost("admin_payroll_lock",obj("period_id",id),"payroll","Locking payroll…")));
                r.addView(gen,new LinearLayout.LayoutParams(0,ui.dp(48),1f));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ui.dp(48),1f);lp.leftMargin=ui.dp(8);r.addView(lock,lp);c.addView(r,ui.match(0,10,0,0));
            }
            content.addView(c,ui.match(0,0,0,8));
        }
        content.addView(ui.section("Selected period records"),ui.match(0,20,0,10));
        if(rows==null||rows.length()==0)content.addView(empty("No generated records","Generate a draft payroll period to populate employee records."));
        else for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);LinearLayout c=ui.card();c.addView(rowTitle(x.optString("first_name")+" "+x.optString("last_name"),"₹"+money(x.optDouble("net_salary"))));c.addView(ui.muted("Base ₹"+money(x.optDouble("base_salary"))+" · OT ₹"+money(x.optDouble("overtime_amount"))+" · deduction ₹"+money(x.optDouble("unpaid_leave_deduction"))),ui.match(0,4,0,0));content.addView(c,ui.match(0,0,0,8));}
    }

    void settings(JSONObject d){
        JSONObject s=d.optJSONObject("settings");if(s==null)s=new JSONObject();
        Button edit=ui.primary("Edit Policy Settings");JSONObject settings=s;edit.setOnClickListener(v->settingsEdit(settings));content.addView(edit,ui.match(0,8,0,12));
        content.addView(ui.section("Attendance"),ui.match(0,8,0,10));setting("Require geofence",yesNo(s.optString("attendance.require_geofence")));setting("Maximum GPS accuracy",s.optString("attendance.max_accuracy_m")+" m");setting("Outside geofence","1".equals(s.optString("attendance.allow_outside_geofence"))?"Allow for review":"Block");setting("Offline punch",yesNo(s.optString("attendance.offline_punch")));setting("Weekly off days",blank(s.optString("attendance.weekly_off_days")));
        content.addView(ui.section("Live tracking"),ui.match(0,20,0,10));setting("Tracking enabled",yesNo(s.optString("tracking.enabled")));setting("Shift only",yesNo(s.optString("tracking.shift_only")));setting("Ping interval",s.optString("attendance.live_ping_seconds")+" sec");
        content.addView(ui.section("Payroll"),ui.match(0,20,0,10));setting("Monthly divisor",s.optString("payroll.monthly_divisor"));
    }

    private void attendanceCorrection(JSONObject x){
        LinearLayout f=form();
        StringChoice type=stringChoice(new String[]{"Clock In","Clock Out","Work Minutes","Note"},new String[]{"clock_in","clock_out","work_minutes","note"},"clock_in");
        f.addView(type.spinner,ui.match());
        EditText value=input("Corrected value",InputType.TYPE_CLASS_TEXT);EditText reason=input("Reason",InputType.TYPE_CLASS_TEXT);
        f.addView(value,ui.match(0,10,0,0));f.addView(reason,ui.match(0,10,0,0));
        TextView help=ui.muted("Clock fields accept a valid date/time such as 2026-09-25 09:10:00. Original punches are never overwritten.");f.addView(help,ui.match(0,8,0,0));
        formDialog("Record Attendance Correction",f,"Save Correction",dlg->{
            if(value.getText().toString().trim().isEmpty()||reason.getText().toString().trim().isEmpty()){host.adminToast("Corrected value and reason are required.");return;}
            JSONObject b=obj("session_id",x.optInt("id"));put(b,"correction_type",type.value());put(b,"requested_value",value.getText().toString().trim());put(b,"reason",reason.getText().toString().trim());
            host.adminBusy("Recording correction…");host.adminPost("admin_attendance_correction",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("attendance");});
        });
    }

    private void employeeCreate(JSONObject d){
        LinearLayout f=form();EditText code=input("Employee code",InputType.TYPE_CLASS_TEXT),first=input("First name",InputType.TYPE_CLASS_TEXT),last=input("Last name",InputType.TYPE_CLASS_TEXT),email=input("Email",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),phone=input("Phone",InputType.TYPE_CLASS_PHONE),password=input("Login password (8+ characters)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(f,code,first,last,email,phone,password);
        IntChoice branch=namedChoice(d.optJSONArray("branches"),"No branch",0),dep=namedChoice(d.optJSONArray("departments"),"No department",0),des=namedChoice(d.optJSONArray("designations"),"No designation",0),manager=employeeChoice(d.optJSONArray("managers"),"No manager",0);
        add(f,branch.spinner,dep.spinner,des.spinner,manager.spinner);
        EditText joining=dateInput("Joining date"),salary=input("Monthly salary",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL),ot=input("Overtime hourly rate",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);salary.setText("0");ot.setText("0");StringChoice employment=stringChoice(new String[]{"Full time","Part time","Contract","Intern"},new String[]{"full_time","part_time","contract","intern"},"full_time");add(f,joining,employment.spinner,salary,ot);
        formDialog("Add Employee",f,"Create Employee",dlg->{
            JSONObject b=new JSONObject();put(b,"employee_code",text(code));put(b,"first_name",text(first));put(b,"last_name",text(last));put(b,"email",text(email));put(b,"phone",text(phone));put(b,"password",text(password));put(b,"branch_id",branch.value());put(b,"department_id",dep.value());put(b,"designation_id",des.value());put(b,"manager_id",manager.value());put(b,"joining_date",text(joining));put(b,"employment_type",employment.value());put(b,"monthly_salary",numberText(salary));put(b,"overtime_hourly_rate",numberText(ot));
            host.adminBusy("Creating employee…");host.adminPost("admin_employee_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("people");});
        });
    }

    private void employeeMenu(JSONObject d,JSONObject e,boolean roles){
        ArrayList<String> items=new ArrayList<>();items.add("Edit profile");items.add("Change status");if(roles)items.add("Change access role");items.add("Reset login password");
        new AlertDialog.Builder(a).setTitle(e.optString("first_name")+" "+e.optString("last_name")).setItems(items.toArray(new String[0]),(dlg,which)->{
            String choice=items.get(which);if(choice.equals("Edit profile"))employeeEdit(d,e);else if(choice.equals("Change status"))employeeStatus(e);else if(choice.equals("Change access role"))employeeRole(d,e);else employeePassword(e);
        }).setNegativeButton("Close",null).show();
    }

    private void employeeEdit(JSONObject d,JSONObject e){
        LinearLayout f=form();EditText first=input("First name",InputType.TYPE_CLASS_TEXT),last=input("Last name",InputType.TYPE_CLASS_TEXT),email=input("Email",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),phone=input("Phone",InputType.TYPE_CLASS_PHONE);first.setText(e.optString("first_name"));last.setText(e.optString("last_name"));email.setText(e.optString("email"));phone.setText(e.optString("phone"));add(f,first,last,email,phone);
        IntChoice branch=namedChoice(d.optJSONArray("branches"),"No branch",e.optInt("branch_id")),dep=namedChoice(d.optJSONArray("departments"),"No department",e.optInt("department_id")),des=namedChoice(d.optJSONArray("designations"),"No designation",e.optInt("designation_id")),manager=employeeChoice(d.optJSONArray("managers"),"No manager",e.optInt("manager_id"));add(f,branch.spinner,dep.spinner,des.spinner,manager.spinner);
        EditText joining=dateInput("Joining date"),salary=input("Monthly salary",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL),ot=input("Overtime hourly rate",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);joining.setText(e.optString("joining_date"));salary.setText(e.optString("monthly_salary","0"));ot.setText(e.optString("overtime_hourly_rate","0"));StringChoice employment=stringChoice(new String[]{"Full time","Part time","Contract","Intern"},new String[]{"full_time","part_time","contract","intern"},e.optString("employment_type","full_time"));add(f,joining,employment.spinner,salary,ot);
        formDialog("Edit Employee",f,"Save Changes",dlg->{JSONObject b=obj("employee_id",e.optInt("id"));put(b,"first_name",text(first));put(b,"last_name",text(last));put(b,"email",text(email));put(b,"phone",text(phone));put(b,"branch_id",branch.value());put(b,"department_id",dep.value());put(b,"designation_id",des.value());put(b,"manager_id",manager.value());put(b,"joining_date",text(joining));put(b,"employment_type",employment.value());put(b,"monthly_salary",numberText(salary));put(b,"overtime_hourly_rate",numberText(ot));host.adminBusy("Saving employee…");host.adminPost("admin_employee_update",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("people");});});
    }

    private void employeeStatus(JSONObject e){
        StringChoice sc=stringChoice(new String[]{"Active","Inactive","Terminated"},new String[]{"active","inactive","terminated"},e.optString("status","active"));
        new AlertDialog.Builder(a).setTitle("Employee Status").setView(sc.spinner).setPositiveButton("Apply",(d,w)->confirm("Change employee status?","Inactive/terminated status disables login. Existing native sessions are revoked.",()->simplePost("admin_employee_status",merge(obj("employee_id",e.optInt("id")),obj("status",sc.value())),"people","Updating status…"))).setNegativeButton("Cancel",null).show();
    }

    private void employeeRole(JSONObject d,JSONObject e){
        JSONArray rs=d.optJSONArray("access_roles");ArrayList<String> names=new ArrayList<>(),vals=new ArrayList<>();if(rs!=null)for(int i=0;i<rs.length();i++){JSONObject r=rs.optJSONObject(i);names.add(r.optString("name"));vals.add(r.optString("role_key"));}
        if(names.isEmpty()){host.adminToast("No assignable access roles are configured.");return;}StringChoice sc=stringChoice(names.toArray(new String[0]),vals.toArray(new String[0]),e.optString("access_role","employee"));
        new AlertDialog.Builder(a).setTitle("Access Role").setView(sc.spinner).setPositiveButton("Apply",(d1,w)->simplePost("admin_employee_role",merge(obj("employee_id",e.optInt("id")),obj("role_key",sc.value())),"people","Updating access role…")).setNegativeButton("Cancel",null).show();
    }

    private void employeePassword(JSONObject e){
        EditText p=input("New password (8+ characters)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog d=new AlertDialog.Builder(a).setTitle("Reset Employee Password").setMessage("Existing browser and native sessions for this employee will be signed out.").setView(p).setPositiveButton("Reset",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(text(p).length()<8){host.adminToast("Password must be at least 8 characters.");return;}JSONObject b=obj("employee_id",e.optInt("id"));put(b,"new_password",text(p));host.adminBusy("Resetting password…");host.adminPost("admin_employee_password",b,r->{host.adminHideBusy();d.dismiss();host.adminToast(r.optString("message"));host.adminRender("people");});}));d.show();
    }

    private void locationCreate(JSONObject d){
        LinearLayout f=form();EditText name=input("Location name",InputType.TYPE_CLASS_TEXT),address=input("Address",InputType.TYPE_CLASS_TEXT),lat=input("Latitude",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED),lng=input("Longitude",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED),radius=input("Radius metres",InputType.TYPE_CLASS_NUMBER);radius.setText("150");IntChoice branch=namedChoice(d.optJSONArray("branches"),"No branch / shared site",0);add(f,name,branch.spinner,address,lat,lng,radius);
        Button gps=ui.secondary("Use My Current GPS");gps.setOnClickListener(v->host.adminFreshLocation(loc->{lat.setText(String.format(Locale.US,"%.7f",loc.getLatitude()));lng.setText(String.format(Locale.US,"%.7f",loc.getLongitude()));if(loc.hasAccuracy()&&loc.getAccuracy()>50)radius.setText(String.valueOf(Math.max(150,(int)Math.ceil(loc.getAccuracy()*2))));host.adminToast("GPS captured. Review the radius before saving.");}));f.addView(gps,ui.match(0,10,0,0));
        formDialog("Add Geofence",f,"Create Geofence",dlg->{JSONObject b=new JSONObject();put(b,"name",text(name));put(b,"branch_id",branch.value());put(b,"address",text(address));put(b,"latitude",text(lat));put(b,"longitude",text(lng));put(b,"radius_meters",intText(radius,150));host.adminBusy("Creating geofence…");host.adminPost("admin_location_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("locations");});});
    }

    private void locationAssign(JSONObject d){
        IntChoice emp=employeeChoice(d.optJSONArray("employees"),null,0),loc=namedChoice(d.optJSONArray("locations"),null,0);if(emp.ids.isEmpty()||loc.ids.isEmpty()){host.adminToast("An active employee and work location are required.");return;}
        LinearLayout f=form();add(f,emp.spinner,loc.spinner);formDialog("Assign Extra Location",f,"Assign",dlg->{JSONObject b=obj("employee_id",emp.value());put(b,"work_location_id",loc.value());host.adminBusy("Assigning location…");host.adminPost("admin_location_assign",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("locations");});});
    }

    private void shiftCreate(){
        LinearLayout f=form();EditText name=input("Shift name",InputType.TYPE_CLASS_TEXT),start=timeInput("Start time"),end=timeInput("End time"),br=input("Break minutes",InputType.TYPE_CLASS_NUMBER),grace=input("Grace minutes",InputType.TYPE_CLASS_NUMBER);br.setText("30");grace.setText("10");add(f,name,start,end,br,grace);
        formDialog("Create Shift",f,"Create Shift",dlg->{JSONObject b=new JSONObject();put(b,"name",text(name));put(b,"start_time",text(start));put(b,"end_time",text(end));put(b,"break_minutes",intText(br,30));put(b,"grace_minutes",intText(grace,10));host.adminBusy("Creating shift…");host.adminPost("admin_shift_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("shifts");});});
    }

    private void shiftAssign(JSONObject d){
        IntChoice emp=employeeChoice(d.optJSONArray("employees"),null,0),shift=namedChoice(d.optJSONArray("shifts"),null,0);if(emp.ids.isEmpty()||shift.ids.isEmpty()){host.adminToast("An active employee and shift are required.");return;}
        LinearLayout f=form();EditText from=dateInput("Effective from"),until=dateInput("Until (optional)");from.setText(new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));add(f,emp.spinner,shift.spinner,from,until);
        formDialog("Assign Shift",f,"Assign Shift",dlg->{JSONObject b=obj("employee_id",emp.value());put(b,"shift_id",shift.value());put(b,"start_date",text(from));put(b,"end_date",text(until));host.adminBusy("Assigning shift…");host.adminPost("admin_shift_assign",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("shifts");});});
    }

    private void clientCreate(){
        LinearLayout f=form();EditText name=input("Client / site name",InputType.TYPE_CLASS_TEXT),contact=input("Contact name",InputType.TYPE_CLASS_TEXT),phone=input("Phone",InputType.TYPE_CLASS_PHONE),address=input("Address",InputType.TYPE_CLASS_TEXT),lat=input("Latitude (optional)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED),lng=input("Longitude (optional)",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED),radius=input("Radius metres",InputType.TYPE_CLASS_NUMBER);radius.setText("200");add(f,name,contact,phone,address,lat,lng,radius);
        Button gps=ui.secondary("Use My Current GPS");gps.setOnClickListener(v->host.adminFreshLocation(loc->{lat.setText(String.format(Locale.US,"%.7f",loc.getLatitude()));lng.setText(String.format(Locale.US,"%.7f",loc.getLongitude()));host.adminToast("Client/site GPS captured.");}));f.addView(gps,ui.match(0,10,0,0));
        formDialog("Add Client / Site",f,"Create Site",dlg->{JSONObject b=new JSONObject();put(b,"name",text(name));put(b,"contact_name",text(contact));put(b,"phone",text(phone));put(b,"address",text(address));put(b,"latitude",text(lat));put(b,"longitude",text(lng));put(b,"radius_meters",intText(radius,200));host.adminBusy("Creating client/site…");host.adminPost("admin_client_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("field");});});
    }

    private void visitCreate(JSONObject d){
        IntChoice emp=employeeChoice(d.optJSONArray("employees"),null,0),client=namedChoice(d.optJSONArray("clients"),null,0);if(emp.ids.isEmpty()||client.ids.isEmpty()){host.adminToast("An active employee and client/site are required.");return;}
        LinearLayout f=form();EditText title=input("Visit title",InputType.TYPE_CLASS_TEXT),when=dateTimeInput("Scheduled at (optional)"),notes=input("Notes",InputType.TYPE_CLASS_TEXT);add(f,emp.spinner,client.spinner,title,when,notes);
        formDialog("Assign Field Visit",f,"Assign Visit",dlg->{JSONObject b=obj("employee_id",emp.value());put(b,"client_id",client.value());put(b,"title",text(title));put(b,"scheduled_at",text(when));put(b,"notes",text(notes));host.adminBusy("Assigning field visit…");host.adminPost("admin_visit_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("field");});});
    }

    private void holidayCreate(JSONObject d){
        LinearLayout f=form();EditText date=dateInput("Holiday date"),name=input("Holiday name",InputType.TYPE_CLASS_TEXT);IntChoice branch=namedChoice(d.optJSONArray("branches"),"All branches",0);add(f,date,name,branch.spinner);
        formDialog("Add Holiday",f,"Add Holiday",dlg->{JSONObject b=new JSONObject();put(b,"holiday_date",text(date));put(b,"name",text(name));put(b,"branch_id",branch.value());host.adminBusy("Adding holiday…");host.adminPost("admin_holiday_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("holidays");});});
    }

    private void payrollPeriodCreate(){
        LinearLayout f=form();EditText name=input("Period name",InputType.TYPE_CLASS_TEXT),from=dateInput("Start date"),to=dateInput("End date");add(f,name,from,to);
        formDialog("Create Payroll Period",f,"Create Period",dlg->{JSONObject b=new JSONObject();put(b,"name",text(name));put(b,"start_date",text(from));put(b,"end_date",text(to));host.adminBusy("Creating payroll period…");host.adminPost("admin_payroll_period_create",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("payroll");});});
    }

    private void settingsEdit(JSONObject s){
        LinearLayout f=form();
        StringChoice geofence=yesNoChoice(s.optString("attendance.require_geofence","1")),outside=stringChoice(new String[]{"Block","Allow for review"},new String[]{"0","1"},s.optString("attendance.allow_outside_geofence","0")),offline=yesNoChoice(s.optString("attendance.offline_punch","0")),tracking=yesNoChoice(s.optString("tracking.enabled","1")),shiftOnly=yesNoChoice(s.optString("tracking.shift_only","1"));
        EditText accuracy=input("Maximum GPS accuracy (m)",InputType.TYPE_CLASS_NUMBER),ping=input("Live ping interval (sec)",InputType.TYPE_CLASS_NUMBER),offDays=input("Weekly off days (0=Sun … 6=Sat)",InputType.TYPE_CLASS_TEXT),divisor=input("Payroll monthly divisor",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        accuracy.setText(s.optString("attendance.max_accuracy_m","200"));ping.setText(s.optString("attendance.live_ping_seconds","60"));offDays.setText(s.optString("attendance.weekly_off_days","0"));divisor.setText(s.optString("payroll.monthly_divisor","30"));
        f.addView(ui.label("Require geofence"));f.addView(geofence.spinner,ui.match());f.addView(ui.label("Outside geofence"),ui.match(0,10,0,0));f.addView(outside.spinner,ui.match());add(f,accuracy);f.addView(ui.label("Offline queued punches"),ui.match(0,10,0,0));f.addView(offline.spinner,ui.match());add(f,offDays);f.addView(ui.label("Live tracking"),ui.match(0,10,0,0));f.addView(tracking.spinner,ui.match());f.addView(ui.label("Limit tracking to active shift"),ui.match(0,10,0,0));f.addView(shiftOnly.spinner,ui.match());add(f,ping,divisor);
        formDialog("Policy Settings",f,"Save Settings",dlg->{JSONObject b=new JSONObject();put(b,"attendance.require_geofence",geofence.value());put(b,"attendance.max_accuracy_m",text(accuracy));put(b,"attendance.allow_outside_geofence",outside.value());put(b,"attendance.offline_punch",offline.value());put(b,"attendance.weekly_off_days",text(offDays));put(b,"tracking.enabled",tracking.value());put(b,"tracking.shift_only",shiftOnly.value());put(b,"attendance.live_ping_seconds",text(ping));put(b,"payroll.monthly_divisor",text(divisor));host.adminBusy("Saving settings…");host.adminPost("admin_settings_update",b,r->{host.adminHideBusy();dlg.dismiss();host.adminToast(r.optString("message"));host.adminRender("settings");});});
    }

    private void simplePost(String action,JSONObject body,String screen,String busy){host.adminBusy(busy);host.adminPost(action,body,r->{host.adminHideBusy();host.adminToast(r.optString("message"));host.adminRender(screen);});}
    private void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("Continue",(d,w)->yes.run()).setNegativeButton("Cancel",null).show();}
    private LinearLayout form(){LinearLayout f=ui.column();f.setPadding(ui.dp(8),ui.dp(4),ui.dp(8),ui.dp(4));return f;}
    private EditText input(String hint,int type){return ui.input(hint,type);}
    private void add(LinearLayout f,View... views){for(View v:views)f.addView(v,ui.match(0,10,0,0));}
    private EditText dateInput(String hint){EditText e=input(hint,InputType.TYPE_CLASS_DATETIME);e.setFocusable(false);e.setOnClickListener(v->{Calendar c=Calendar.getInstance();new DatePickerDialog(a,(d,y,m,day)->e.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,day)),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});return e;}
    private EditText timeInput(String hint){EditText e=input(hint,InputType.TYPE_CLASS_DATETIME);e.setFocusable(false);e.setOnClickListener(v->{Calendar c=Calendar.getInstance();new TimePickerDialog(a,(d,h,m)->e.setText(String.format(Locale.US,"%02d:%02d",h,m)),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();});return e;}
    private EditText dateTimeInput(String hint){EditText e=input(hint,InputType.TYPE_CLASS_DATETIME);e.setFocusable(false);e.setOnClickListener(v->{Calendar c=Calendar.getInstance();new DatePickerDialog(a,(d,y,m,day)->new TimePickerDialog(a,(td,h,min)->e.setText(String.format(Locale.US,"%04d-%02d-%02d %02d:%02d:00",y,m+1,day,h,min)),c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show(),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();});return e;}

    private void formDialog(String title,LinearLayout form,String positive,Consumer<AlertDialog> submit){
        ScrollView sv=new ScrollView(a);sv.addView(form,new ScrollView.LayoutParams(-1,-2));
        AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(sv).setPositiveButton(positive,null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->submit.accept(d)));d.show();
    }

    private IntChoice namedChoice(JSONArray rows,String empty,int selected){return intChoice(rows,empty,selected,x->x.optString("name","Record"));}
    private IntChoice employeeChoice(JSONArray rows,String empty,int selected){return intChoice(rows,empty,selected,x->x.optString("employee_code")+" · "+x.optString("first_name")+" "+x.optString("last_name"));}
    private IntChoice intChoice(JSONArray rows,String empty,int selected,java.util.function.Function<JSONObject,String> label){
        ArrayList<String> names=new ArrayList<>();ArrayList<Integer> ids=new ArrayList<>();int pos=0;
        if(empty!=null){names.add(empty);ids.add(0);}
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x==null)continue;ids.add(x.optInt("id"));names.add(label.apply(x));if(x.optInt("id")==selected)pos=names.size()-1;}
        Spinner sp=new Spinner(a);sp.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,names));if(!names.isEmpty())sp.setSelection(Math.min(pos,names.size()-1));return new IntChoice(sp,ids);
    }
    private StringChoice yesNoChoice(String selected){return stringChoice(new String[]{"Yes","No"},new String[]{"1","0"},selected);}
    private StringChoice stringChoice(String[] names,String[] values,String selected){Spinner sp=new Spinner(a);sp.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,names));int pos=0;for(int i=0;i<values.length;i++)if(values[i].equals(selected)){pos=i;break;}sp.setSelection(pos);return new StringChoice(sp,new ArrayList<>(Arrays.asList(values)));}
    private static final class IntChoice{final Spinner spinner;final ArrayList<Integer> ids;IntChoice(Spinner s,ArrayList<Integer> i){spinner=s;ids=i;}int value(){int p=spinner.getSelectedItemPosition();return p>=0&&p<ids.size()?ids.get(p):0;}}
    private static final class StringChoice{final Spinner spinner;final ArrayList<String> values;StringChoice(Spinner s,ArrayList<String> v){spinner=s;values=v;}String value(){int p=spinner.getSelectedItemPosition();return p>=0&&p<values.size()?values.get(p):"";}}

    private JSONObject obj(String k,Object v){JSONObject o=new JSONObject();put(o,k,v);return o;}
    private JSONObject merge(JSONObject a1,JSONObject b){Iterator<String> it=b.keys();while(it.hasNext()){String k=it.next();put(a1,k,b.opt(k));}return a1;}
    private void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(Exception ignored){}}
    private String text(EditText e){return e.getText().toString().trim();}
    private double numberText(EditText e){try{return Double.parseDouble(text(e));}catch(Exception x){return 0;}}
    private int intText(EditText e,int d){try{return Integer.parseInt(text(e));}catch(Exception x){return d;}}
    private View rowTitle(String left,String right){LinearLayout r=new LinearLayout(a);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(ui.label(left),new LinearLayout.LayoutParams(0,-2,1f));r.addView(ui.text(right,12,Ui.MUTED,true),ui.wrap());return r;}
    private View kv(String key,String value){LinearLayout r=new LinearLayout(a);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,ui.dp(9),0,0);r.addView(ui.text(key,12,Ui.MUTED,false),new LinearLayout.LayoutParams(0,-2,1f));r.addView(ui.text(value,13,Ui.TEXT,true),ui.wrap());return r;}
    private View empty(String title,String body){LinearLayout c=ui.softCard();TextView t=ui.label(title);t.setGravity(Gravity.CENTER);TextView b=ui.muted(body);b.setGravity(Gravity.CENTER);c.addView(t,ui.match());c.addView(b,ui.match(8,6,8,0));return c;}
    private void setting(String k,String v){LinearLayout c=ui.card();c.addView(rowTitle(k,v));content.addView(c,ui.match(0,0,0,8));}
    private String shortTime(String s){if(s==null||s.isEmpty())return"—";try{if(s.length()>=16&&s.charAt(10)==' ')return s.substring(11,16);if(s.length()>=5)return s.substring(0,5);}catch(Exception ignored){}return s;}
    private String minutes(int m){m=Math.max(0,m);return(m/60)+"h "+(m%60)+"m";}
    private String money(double d){return String.format(Locale.US,"%,.2f",d);}
    private String blank(String s){return s==null||s.trim().isEmpty()?"—":s;}
    private String yesNo(String s){return"1".equals(s)?"Yes":"No";}
    private String title(String s){if(s==null||s.trim().isEmpty())return"—";s=s.replace('_',' ').replace('-',' ');StringBuilder b=new StringBuilder();for(String x:s.split(" ")){if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(Character.toUpperCase(x.charAt(0))).append(x.substring(1));}return b.toString();}
}
