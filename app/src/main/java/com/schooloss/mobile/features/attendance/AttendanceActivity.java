package com.schooloss.nativeapp.features.attendance;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AttendanceActivity extends SchoolOSActivity {
    private final List<Integer> studentIds = new ArrayList<>();
    private final List<Spinner> statusInputs = new ArrayList<>();
    private final List<EditText> remarkInputs = new ArrayList<>();

    @Override protected void onCreate(Bundle state){ super.onCreate(state); requireSession(this::loadOverview); }

    private void loadOverview(){
        showLoading("Opening attendance","Loading permitted classes and attendance context…");
        io.execute(() -> {
            try { JSONObject r=api.getModule("attendance");JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Attendance is unavailable.");ui.post(() -> renderOverview(d)); }
            catch(Exception e){ui.post(() -> showError("Could not load attendance",e,this::loadOverview));}
        });
    }

    private void renderOverview(JSONObject d){
        LinearLayout page=page("Home",v -> finish(),"ATTENDANCE",d.optString("title","Attendance"),d.optString("subtitle",""));
        JSONArray rows=rows(d);
        page.addView(UiKit.gap(this,12));
        if(rows.length()==0) page.addView(UiKit.body(this,"No attendance context is available for this account.",11));
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            LinearLayout item=simpleRow(r.optString("title","Attendance"),r.optString("subtitle","")+(r.optString("meta","").isEmpty()?"":" · "+r.optString("meta","")));
            if("attendance_context".equals(r.optString("type",""))){
                int classId=r.optInt("class_id",r.optInt("id"));int sectionId=r.optInt("section_id",0);
                item.setOnClickListener(v -> loadRegister(classId,sectionId,today()));
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);
        }
        setScrollable(page);
    }

    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

    private void loadRegister(int classId,int sectionId,String date){
        showLoading("Opening register","Loading students and attendance state…");
        io.execute(() -> {
            try{JSONObject r=api.get("attendance_register","class_id="+classId+"&section_id="+sectionId+"&date="+date);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Attendance register is unavailable.");ui.post(() -> renderRegister(d));}
            catch(Exception e){ui.post(() -> showError("Could not open attendance",e,() -> loadRegister(classId,sectionId,date)));}
        });
    }

    private void renderRegister(JSONObject d){
        studentIds.clear();statusInputs.clear();remarkInputs.clear();
        LinearLayout page=page("Attendance",v -> loadOverview(),"ATTENDANCE REGISTER",d.optString("class_name","Attendance"),pretty(d.optString("submission_status","open")));

        LinearLayout dateCard=UiKit.card(this);dateCard.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));
        dateCard.addView(UiKit.text(this,"Attendance date",11,UiKit.TEXT,true));
        EditText date=UiKit.input(this,d.optString("date",today()),false);date.setText(d.optString("date",today()));date.setInputType(InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE);
        dateCard.addView(UiKit.gap(this,6));dateCard.addView(date);
        Button reload=UiKit.button(this,"Load Date",Color.WHITE,roleColor);reload.setBackground(UiKit.round(this,Color.WHITE,12,UiKit.tint(roleColor,.22f)));reload.setOnClickListener(v -> loadRegister(d.optInt("class_id"),d.optInt("section_id"),date.getText().toString().trim()));
        dateCard.addView(UiKit.gap(this,7));dateCard.addView(reload);page.addView(UiKit.gap(this,12));page.addView(dateCard);

        JSONArray valid=d.optJSONArray("valid_statuses");List<String> options=new ArrayList<>();
        if(valid!=null)for(int i=0;i<valid.length();i++)options.add(valid.optString(i));
        if(options.isEmpty()){options.add("present");options.add("absent");options.add("late");options.add("half_day");options.add("leave");options.add("holiday");}
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);

        JSONArray rows=d.optJSONArray("rows");if(rows==null)rows=new JSONArray();
        page.addView(UiKit.gap(this,14));page.addView(UiKit.title(this,"Students",16));page.addView(UiKit.body(this,rows.length()+" students",10));
        for(int i=0;i<rows.length();i++){
            JSONObject s=rows.optJSONObject(i);if(s==null)continue;
            LinearLayout item=UiKit.card(this);item.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));
            item.addView(UiKit.text(this,s.optString("name","Student"),13,UiKit.TEXT,true));
            String idLine=s.optString("admission_no","");if(!s.optString("roll_no","").isEmpty())idLine+=(idLine.isEmpty()?"":" · ")+"Roll "+s.optString("roll_no","");
            if(!idLine.isEmpty())item.addView(UiKit.text(this,idLine,10,UiKit.MUTED,false));

            Spinner status=new Spinner(this);status.setAdapter(adapter);int pos=options.indexOf(s.optString("status","present"));status.setSelection(Math.max(0,pos));item.addView(status,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,48)));
            EditText remarks=UiKit.input(this,"Remarks (optional)",false);remarks.setText(s.optString("remarks",""));item.addView(remarks);

            studentIds.add(s.optInt("id"));statusInputs.add(status);remarkInputs.add(remarks);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);
        }

        if(d.optBoolean("can_mark",false)){
            Button save=UiKit.button(this,"Submit Attendance",roleColor,Color.WHITE);save.setOnClickListener(v -> save(d,date.getText().toString().trim()));page.addView(UiKit.gap(this,12));page.addView(save);
        } else {
            page.addView(UiKit.gap(this,12));page.addView(UiKit.body(this,d.optBoolean("locked",false)?"This register is locked.":"Editing is not available for this date or role.",11));
        }

        if(d.optBoolean("can_manage",false)){
            page.addView(UiKit.gap(this,8));
            if(d.optBoolean("locked",false)){
                Button reopen=UiKit.button(this,"Reopen Register",Color.WHITE,Color.rgb(180,55,65));reopen.setBackground(UiKit.round(this,Color.WHITE,14,Color.rgb(245,205,212)));reopen.setOnClickListener(v -> askReopen(d,date.getText().toString().trim()));page.addView(reopen);
            }else{
                Button lock=UiKit.button(this,"Lock Register",Color.WHITE,roleColor);lock.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.tint(roleColor,.22f)));lock.setOnClickListener(v -> askLock(d,date.getText().toString().trim()));page.addView(lock);
            }
        }
        setScrollable(page);
    }

    private void save(JSONObject register,String date){
        showLoading("Submitting attendance","Applying SchoolOS attendance rules…");
        io.execute(() -> {
            try{
                JSONObject b=new JSONObject();b.put("class_id",register.optInt("class_id"));b.put("section_id",register.optInt("section_id"));b.put("attendance_date",date);
                JSONArray statuses=new JSONArray();
                for(int i=0;i<studentIds.size();i++){JSONObject row=new JSONObject();row.put("student_id",studentIds.get(i));row.put("status",String.valueOf(statusInputs.get(i).getSelectedItem()));row.put("remarks",remarkInputs.get(i).getText().toString().trim());statuses.put(row);}
                b.put("statuses",statuses);JSONObject r=api.post("attendance_save",b);JSONObject updated=r.optJSONObject("register");ui.post(() -> {toast(r.optString("message","Attendance saved."));if(updated!=null)renderRegister(updated);else loadRegister(register.optInt("class_id"),register.optInt("section_id"),date);});
            }catch(Exception e){ui.post(() -> showError("Attendance was not saved",e,() -> renderRegister(register)));}
        });
    }

    private void askLock(JSONObject register,String date){
        new AlertDialog.Builder(this).setTitle("Lock attendance register?").setMessage("Normal editing will be blocked until an attendance manager reopens it.").setNegativeButton("Cancel",null).setPositiveButton("Lock",(d,w) -> control("attendance_lock",register,date,null)).show();
    }

    private void askReopen(JSONObject register,String date){
        EditText reason=UiKit.input(this,"Reason to reopen",false);LinearLayout box=UiKit.vertical(this);box.setPadding(UiKit.dp(this,18),UiKit.dp(this,8),UiKit.dp(this,18),0);box.addView(reason);
        new AlertDialog.Builder(this).setTitle("Reopen attendance register").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Reopen",(d,w) -> {String x=reason.getText().toString().trim();if(x.isEmpty()){toast("Reopen reason is required.");return;}control("attendance_reopen",register,date,x);}).show();
    }

    private void control(String action,JSONObject register,String date,String reason){
        showLoading("Updating register","Applying attendance manager controls…");
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("class_id",register.optInt("class_id"));b.put("section_id",register.optInt("section_id"));b.put("attendance_date",date);if(reason!=null)b.put("reason",reason);JSONObject r=api.post(action,b);JSONObject updated=r.optJSONObject("register");ui.post(() -> {toast(r.optString("message","Attendance updated."));if(updated!=null)renderRegister(updated);else loadRegister(register.optInt("class_id"),register.optInt("section_id"),date);});}
            catch(Exception e){ui.post(() -> showError("Register update failed",e,() -> loadRegister(register.optInt("class_id"),register.optInt("section_id"),date)));}
        });
    }
}
