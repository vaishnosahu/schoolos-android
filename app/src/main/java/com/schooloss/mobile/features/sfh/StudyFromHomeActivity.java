package com.schooloss.nativeapp.features.sfh;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.schooloss.nativeapp.core.FileBridge;
import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public final class StudyFromHomeActivity extends SchoolOSActivity {
    private static final int PICK_FILE = 4401;
    private int pendingAssignmentId;
    private String pendingToken = "";
    private EditText pendingAnswer;
    private TextView pendingFileLabel;
    private Uri pendingFile;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        requireSession(this::loadOverview);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_FILE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            pendingFile=data.getData();
            try{getContentResolver().takePersistableUriPermission(pendingFile,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            if(pendingFileLabel!=null){pendingFileLabel.setText("Selected: "+String.valueOf(pendingFile.getLastPathSegment()));pendingFileLabel.setTextColor(roleColor);}
        }
    }

    private void loadOverview(){
        showLoading("Opening Study From Home","Loading lessons, assignments and learning material…");
        io.execute(() -> {
            try{
                JSONObject r=api.get("sfh_overview");
                JSONObject d=r.optJSONObject("data");
                if(d==null){
                    JSONObject fallback=api.getModule("study_from_home");
                    d=fallback.optJSONObject("data");
                }
                JSONObject finalD=d;
                if(finalD==null)throw new Exception("Study From Home is unavailable.");
                ui.post(() -> renderOverview(finalD));
            }catch(Exception e){ui.post(() -> showError("Could not load Study From Home",e,this::loadOverview));}
        });
    }

    private void renderOverview(JSONObject d){
        LinearLayout page=page("Home",v -> finish(),"STUDY FROM HOME",d.optString("title","Study From Home"),d.optString("subtitle",""));
        JSONArray lessons=d.optJSONArray("lessons");
        JSONArray assignments=d.optJSONArray("assignments");
        JSONArray materials=d.optJSONArray("materials");
        JSONArray rows=d.optJSONArray("rows");

        if(lessons!=null){
            page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Lessons",16));
            for(int i=0;i<lessons.length();i++){JSONObject x=lessons.optJSONObject(i);if(x==null)continue;LinearLayout item=simpleRow(x.optString("title","Lesson"),x.optString("subject","")+" · "+x.optString("status",""));int id=x.optInt("id");item.setOnClickListener(v -> loadLesson(id));page.addView(withMargin(item,i>0?8:0));}
        }
        if(assignments!=null){
            page.addView(UiKit.gap(this,14));page.addView(UiKit.title(this,"Assignments",16));
            for(int i=0;i<assignments.length();i++){JSONObject x=assignments.optJSONObject(i);if(x==null)continue;LinearLayout item=simpleRow(x.optString("title","Assignment"),x.optString("subject","")+" · "+pretty(x.optString("status","")));int id=x.optInt("id");item.setOnClickListener(v -> loadAssignment(id));page.addView(withMargin(item,i>0?8:0));}
        }
        if(materials!=null){
            page.addView(UiKit.gap(this,14));page.addView(UiKit.title(this,"Learning Material",16));
            for(int i=0;i<materials.length();i++){JSONObject x=materials.optJSONObject(i);if(x==null)continue;LinearLayout item=simpleRow(x.optString("title","Material"),x.optString("label","Resource"));int id=x.optInt("id");item.setOnClickListener(v -> loadMaterial(id));page.addView(withMargin(item,i>0?8:0));}
        }
        if(rows!=null&&lessons==null&&assignments==null&&materials==null){
            page.addView(UiKit.gap(this,12));
            for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;LinearLayout item=simpleRow(r.optString("title","Item"),r.optString("subtitle","")+" · "+r.optString("meta",""));String type=r.optString("type","");int id=r.optInt("id");if("sfh_lesson".equals(type))item.setOnClickListener(v -> loadLesson(id));else if("sfh_assignment".equals(type))item.setOnClickListener(v -> loadAssignment(id));else if("sfh_material".equals(type))item.setOnClickListener(v -> loadMaterial(id));page.addView(withMargin(item,i>0?8:0));}
        }
        setScrollable(page);
    }

    private void loadLesson(int id){
        showLoading("Opening lesson","Loading Study From Home lesson…");
        io.execute(() -> {try{JSONObject r=api.get("sfh_lesson","id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Lesson is unavailable.");ui.post(() -> renderLesson(d));}catch(Exception e){ui.post(() -> showError("Could not open lesson",e,() -> loadLesson(id)));}});
    }

    private void renderLesson(JSONObject d){
        LinearLayout page=page("Study From Home",v -> loadOverview(),"LESSON",d.optString("title","Lesson"),d.optString("subject","")+" · "+d.optString("class_name",""));
        if(!d.optString("topic","").isEmpty()){page.addView(UiKit.gap(this,10));page.addView(UiKit.text(this,d.optString("topic",""),15,UiKit.TEXT,true));}
        if(!d.optString("description","").isEmpty()){page.addView(UiKit.gap(this,10));LinearLayout c=UiKit.card(this);c.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));c.addView(UiKit.body(this,d.optString("description",""),12));page.addView(c);}
        if(!d.optString("teacher_instructions","").isEmpty()){page.addView(UiKit.gap(this,10));LinearLayout c=UiKit.card(this);c.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));c.addView(UiKit.label(this,"TEACHER INSTRUCTIONS",9,roleColor));c.addView(UiKit.body(this,d.optString("teacher_instructions",""),12));page.addView(c);}

        if(d.optBoolean("completed",false)){TextView done=UiKit.text(this,"✓ Lesson completed "+d.optString("completed_at",""),11,Color.rgb(20,135,90),true);done.setPadding(UiKit.dp(this,12),UiKit.dp(this,10),UiKit.dp(this,12),UiKit.dp(this,10));done.setBackground(UiKit.round(this,Color.rgb(238,250,244),12,Color.rgb(190,232,211)));page.addView(UiKit.gap(this,12));page.addView(done);}
        else if(d.optBoolean("can_complete",false)){Button done=UiKit.button(this,"Mark Lesson Complete",roleColor,Color.WHITE);done.setOnClickListener(v -> completeLesson(d.optInt("id"),d.optString("complete_token","")));page.addView(UiKit.gap(this,12));page.addView(done);}

        JSONArray materials=d.optJSONArray("materials");page.addView(UiKit.gap(this,14));page.addView(UiKit.title(this,"Study Material",16));
        if(materials!=null)for(int i=0;i<materials.length();i++){JSONObject m=materials.optJSONObject(i);if(m==null)continue;LinearLayout item=simpleRow(m.optString("title","Material"),m.optString("label","Resource")+" · "+formatBytes(m.optLong("file_size",0)));int id=m.optInt("id");item.setOnClickListener(v -> loadMaterial(id));page.addView(withMargin(item,i>0?8:0));}

        JSONArray assignments=d.optJSONArray("assignments");page.addView(UiKit.gap(this,14));page.addView(UiKit.title(this,"Assignments",16));
        if(assignments!=null)for(int i=0;i<assignments.length();i++){JSONObject a=assignments.optJSONObject(i);if(a==null)continue;LinearLayout item=simpleRow(a.optString("title","Assignment"),pretty(a.optString("status",""))+" · Due "+a.optString("due_at",""));int id=a.optInt("id");item.setOnClickListener(v -> loadAssignment(id));page.addView(withMargin(item,i>0?8:0));}
        setScrollable(page);
    }

    private void completeLesson(int id,String token){
        showLoading("Saving progress","Marking lesson complete…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("lesson_id",id);b.put("action_token",token);JSONObject r=api.post("sfh_lesson_complete",b);JSONObject lesson=r.optJSONObject("lesson");ui.post(() -> {toast(r.optString("message","Lesson completed."));if(lesson!=null)renderLesson(lesson);else loadLesson(id);});}catch(Exception e){ui.post(() -> showError("Lesson progress was not saved",e,() -> loadLesson(id)));}});
    }

    private void loadMaterial(int id){
        showLoading("Opening material","Checking protected resource…");
        io.execute(() -> {try{JSONObject r=api.get("sfh_material","id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Material is unavailable.");ui.post(() -> renderMaterial(d));}catch(Exception e){ui.post(() -> showError("Could not open material",e,() -> loadMaterial(id)));}});
    }

    private void renderMaterial(JSONObject d){
        LinearLayout page=page("Study From Home",v -> loadOverview(),d.optString("type_label","MATERIAL").toUpperCase(),d.optString("title","Study Material"),d.optString("subject","")+" · "+d.optString("lesson_title",""));
        String type=d.optString("type","");
        page.addView(UiKit.gap(this,12));
        if("note".equals(type)){LinearLayout c=UiKit.card(this);c.setPadding(UiKit.dp(this,14),UiKit.dp(this,13),UiKit.dp(this,14),UiKit.dp(this,13));c.addView(UiKit.body(this,d.optString("note_body",""),12));page.addView(c);}
        else if("file".equals(type)){LinearLayout c=simpleRow(d.optString("original_filename","Study material"),formatBytes(d.optLong("file_size",0))+" · "+d.optString("mime_type",""));Button open=UiKit.button(this,"Download & Open",roleColor,Color.WHITE);int id=d.optInt("id");open.setOnClickListener(v -> FileBridge.downloadAndOpen(this,api,io,"file","kind=sfh_material&id="+id,"Opening material"));c.addView(UiKit.gap(this,8));c.addView(open);page.addView(c);}
        else {String url=d.optString("external_url","");LinearLayout c=simpleRow("External Resource",url);if(!url.isEmpty()){Button open=UiKit.button(this,"Open External Resource",roleColor,Color.WHITE);open.setOnClickListener(v -> FileBridge.openExternal(this,url));c.addView(UiKit.gap(this,8));c.addView(open);}page.addView(c);}
        setScrollable(page);
    }

    private void loadAssignment(int id){
        showLoading("Opening assignment","Loading assignment and submission state…");
        io.execute(() -> {try{JSONObject r=api.get("sfh_assignment","id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Assignment is unavailable.");ui.post(() -> renderAssignment(d));}catch(Exception e){ui.post(() -> showError("Could not open assignment",e,() -> loadAssignment(id)));}});
    }

    private void renderAssignment(JSONObject d){
        LinearLayout page=page("Study From Home",v -> loadOverview(),"ASSIGNMENT",d.optString("title","Assignment"),d.optString("subject","")+" · Due "+d.optString("due_at",""));
        if(!d.optString("instructions","").isEmpty()){page.addView(UiKit.gap(this,12));LinearLayout c=UiKit.card(this);c.setPadding(UiKit.dp(this,14),UiKit.dp(this,13),UiKit.dp(this,14),UiKit.dp(this,13));c.addView(UiKit.body(this,d.optString("instructions",""),12));page.addView(c);}

        String role=d.optString("role","");
        JSONObject submission=d.optJSONObject("submission");

        if("student".equals(role)){
            page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Your Work",16));
            if(submission!=null){LinearLayout row=simpleRow("Submission #"+submission.optInt("id"),pretty(submission.optString("status","started")));int sid=submission.optInt("id");row.setOnClickListener(v -> loadSubmission(sid));page.addView(row);}
            if(d.optBoolean("can_start",false)){Button start=UiKit.button(this,"Start Assignment",roleColor,Color.WHITE);start.setOnClickListener(v -> startAssignment(d.optInt("id"),d.optString("start_token","")));page.addView(UiKit.gap(this,10));page.addView(start);}
            if(d.optBoolean("can_submit",false)) addSubmitForm(page,d);
        } else if("parent".equals(role)){
            page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Child Submission",16));if(submission!=null){LinearLayout row=simpleRow("Submission #"+submission.optInt("id"),pretty(submission.optString("status","")));int sid=submission.optInt("id");row.setOnClickListener(v -> loadSubmission(sid));page.addView(row);}
        } else {
            JSONObject counts=d.optJSONObject("submission_counts");if(counts!=null){page.addView(UiKit.gap(this,12));page.addView(UiKit.body(this,"Started "+counts.optInt("started")+" · Submitted "+counts.optInt("submitted")+" · Late "+counts.optInt("late")+" · Reviewed "+counts.optInt("reviewed"),11));}
            JSONArray students=d.optJSONArray("students");if(students!=null){page.addView(UiKit.gap(this,12));for(int i=0;i<students.length();i++){JSONObject s=students.optJSONObject(i);if(s==null)continue;LinearLayout row=simpleRow(s.optString("name","Student"),pretty(s.optString("status","assigned")));int sid=s.optInt("submission_id",0);if(sid>0)row.setOnClickListener(v -> loadSubmission(sid));page.addView(withMargin(row,i>0?8:0));}}
        }
        setScrollable(page);
    }

    private void addSubmitForm(LinearLayout page,JSONObject d){
        LinearLayout form=UiKit.card(this);form.setPadding(UiKit.dp(this,14),UiKit.dp(this,13),UiKit.dp(this,14),UiKit.dp(this,13));
        form.addView(UiKit.title(this,"Submit Assignment",16));
        EditText answer=new EditText(this);answer.setHint(d.optBoolean("allow_text",true)?"Write your answer…":"Typed answers are disabled.");answer.setTextSize(13);answer.setTextColor(UiKit.TEXT);answer.setHintTextColor(UiKit.MUTED);answer.setMinLines(4);answer.setGravity(Gravity.TOP);answer.setEnabled(d.optBoolean("allow_text",true));answer.setPadding(UiKit.dp(this,12),UiKit.dp(this,10),UiKit.dp(this,12),UiKit.dp(this,10));answer.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.LINE));form.addView(answer);
        TextView fileLabel=UiKit.text(this,"No file selected",10,UiKit.MUTED,false);form.addView(UiKit.gap(this,8));form.addView(fileLabel);
        if(d.optBoolean("allow_file",false)){Button choose=UiKit.button(this,"Choose Answer File",Color.WHITE,roleColor);choose.setBackground(UiKit.round(this,Color.WHITE,12,UiKit.tint(roleColor,.22f)));choose.setOnClickListener(v -> chooseFile(d.optInt("id"),d.optString("submit_token",""),answer,fileLabel));form.addView(UiKit.gap(this,7));form.addView(choose);}
        Button submit=UiKit.button(this,"Submit Assignment",roleColor,Color.WHITE);submit.setOnClickListener(v -> submitAssignment(d.optInt("id"),d.optString("submit_token",""),answer,fileLabel));form.addView(UiKit.gap(this,9));form.addView(submit);
        form.addView(UiKit.gap(this,7));form.addView(UiKit.text(this,"Allowed: "+joinJson(d.optJSONArray("allowed_extensions"))+" · Max "+formatBytes(d.optLong("max_upload_bytes",0)),9,UiKit.MUTED,false));
        page.addView(UiKit.gap(this,12));page.addView(form);
    }

    private void startAssignment(int id,String token){
        showLoading("Starting assignment","Preparing submission workspace…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("assignment_id",id);b.put("action_token",token);JSONObject r=api.post("sfh_assignment_start",b);JSONObject a=r.optJSONObject("assignment");ui.post(() -> {toast(r.optString("message","Assignment started."));if(a!=null)renderAssignment(a);else loadAssignment(id);});}catch(Exception e){ui.post(() -> showError("Assignment was not started",e,() -> loadAssignment(id)));}});
    }

    private void chooseFile(int assignmentId,String token,EditText answer,TextView label){
        pendingAssignmentId=assignmentId;pendingToken=token;pendingAnswer=answer;pendingFileLabel=label;pendingFile=null;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_FILE);
    }

    private void submitAssignment(int assignmentId,String token,EditText answer,TextView label){
        pendingAssignmentId=assignmentId;pendingToken=token;pendingAnswer=answer;pendingFileLabel=label;
        showLoading("Submitting assignment","Uploading through protected SchoolOS storage…");
        Uri file=pendingFile;String textValue=answer==null?"":answer.getText().toString();
        io.execute(() -> {
            try{Map<String,String> fields=new HashMap<>();fields.put("assignment_id",String.valueOf(assignmentId));fields.put("action_token",token);fields.put("answer_text",textValue);JSONObject r=api.postMultipart("sfh_assignment_submit",fields,file,"answer_file");JSONObject a=r.optJSONObject("assignment");ui.post(() -> {pendingFile=null;toast(r.optString("message","Assignment submitted."));if(a!=null)renderAssignment(a);else loadAssignment(assignmentId);});}
            catch(Exception e){ui.post(() -> showError("Assignment was not submitted",e,() -> loadAssignment(assignmentId)));}
        });
    }

    private void loadSubmission(int id){
        showLoading("Opening submission","Loading versions and feedback…");
        io.execute(() -> {try{JSONObject r=api.get("sfh_submission","id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Submission is unavailable.");ui.post(() -> renderSubmission(d));}catch(Exception e){ui.post(() -> showError("Could not open submission",e,() -> loadSubmission(id)));}});
    }

    private void renderSubmission(JSONObject d){
        LinearLayout page=page("Assignment",v -> loadAssignment(d.optInt("assignment_id")),"SUBMISSION",d.optString("student_name","Your Work"),d.optString("assignment_title","")+" · "+pretty(d.optString("status","")));
        if(d.has("score_awarded")&&!d.isNull("score_awarded")){page.addView(UiKit.gap(this,10));page.addView(UiKit.text(this,"Score: "+d.optString("score_awarded")+" / "+d.optString("score_out_of"),15,UiKit.TEXT,true));}
        if(!d.optString("feedback_summary","").isEmpty()){page.addView(UiKit.gap(this,10));LinearLayout c=UiKit.card(this);c.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));c.addView(UiKit.label(this,"TEACHER FEEDBACK",9,roleColor));c.addView(UiKit.body(this,d.optString("feedback_summary",""),12));if(!d.optString("strengths","").isEmpty())c.addView(UiKit.body(this,"Strengths: "+d.optString("strengths"),11));if(!d.optString("improvement_areas","").isEmpty())c.addView(UiKit.body(this,"Improve: "+d.optString("improvement_areas"),11));page.addView(c);}
        JSONArray versions=d.optJSONArray("versions");page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Submission Versions",16));
        if(versions!=null)for(int i=0;i<versions.length();i++){JSONObject v=versions.optJSONObject(i);if(v==null)continue;LinearLayout c=UiKit.card(this);c.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));c.addView(UiKit.text(this,"Version "+v.optInt("version_no"),12,UiKit.TEXT,true));c.addView(UiKit.text(this,v.optString("submitted_at","")+(v.optBoolean("is_late",false)?" · Late":""),9,UiKit.MUTED,false));if(!v.optString("answer_text","").isEmpty())c.addView(UiKit.body(this,v.optString("answer_text",""),11));if(v.optBoolean("has_file",false)){Button open=UiKit.button(this,"Open "+v.optString("original_filename","Answer File"),Color.WHITE,roleColor);open.setBackground(UiKit.round(this,Color.WHITE,12,UiKit.tint(roleColor,.22f)));int vid=v.optInt("id");open.setOnClickListener(x -> FileBridge.downloadAndOpen(this,api,io,"file","kind=sfh_submission&id="+vid,"Opening answer file"));c.addView(UiKit.gap(this,7));c.addView(open);}page.addView(withMargin(c,i>0?8:0));}
        if(d.optBoolean("can_review",false))addReviewForm(page,d);
        setScrollable(page);
    }

    private void addReviewForm(LinearLayout page,JSONObject d){
        LinearLayout form=UiKit.card(this);form.setPadding(UiKit.dp(this,14),UiKit.dp(this,13),UiKit.dp(this,14),UiKit.dp(this,13));form.addView(UiKit.title(this,"Review Submission",16));
        EditText feedback=UiKit.input(this,"Feedback summary",false);EditText strengths=UiKit.input(this,"Strengths",false);EditText improve=UiKit.input(this,"Improvement areas",false);EditText corrections=UiKit.input(this,"Correction instructions",false);
        form.addView(feedback);form.addView(UiKit.gap(this,7));form.addView(strengths);form.addView(UiKit.gap(this,7));form.addView(improve);form.addView(UiKit.gap(this,7));form.addView(corrections);
        LinearLayout scores=UiKit.horizontal(this);EditText score=UiKit.input(this,"Score",false);score.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);EditText outOf=UiKit.input(this,"Out of",false);outOf.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);scores.addView(score,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f);op.leftMargin=UiKit.dp(this,7);scores.addView(outOf,op);form.addView(UiKit.gap(this,7));form.addView(scores);
        Button review=UiKit.button(this,"Save Review",roleColor,Color.WHITE);review.setOnClickListener(v -> reviewSubmission(d,"reviewed",feedback,strengths,improve,corrections,score,outOf));Button returned=UiKit.button(this,"Return for Correction",Color.WHITE,Color.rgb(180,55,65));returned.setBackground(UiKit.round(this,Color.WHITE,14,Color.rgb(245,205,212)));returned.setOnClickListener(v -> reviewSubmission(d,"returned",feedback,strengths,improve,corrections,score,outOf));form.addView(UiKit.gap(this,9));form.addView(review);form.addView(UiKit.gap(this,7));form.addView(returned);page.addView(UiKit.gap(this,14));page.addView(form);
    }

    private void reviewSubmission(JSONObject d,String decision,EditText feedback,EditText strengths,EditText improve,EditText corrections,EditText score,EditText outOf){
        showLoading("Saving review","Applying Study From Home review authority…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("submission_id",d.optInt("id"));b.put("action_token",d.optString("review_token",""));b.put("decision",decision);b.put("feedback_summary",feedback.getText().toString());b.put("strengths",strengths.getText().toString());b.put("improvement_areas",improve.getText().toString());b.put("correction_instructions",corrections.getText().toString());b.put("score_awarded",score.getText().toString());b.put("score_out_of",outOf.getText().toString());JSONObject r=api.post("sfh_submission_review",b);JSONObject sub=r.optJSONObject("submission");ui.post(() -> {toast(r.optString("message","Review saved."));if(sub!=null)renderSubmission(sub);else loadSubmission(d.optInt("id"));});}catch(Exception e){ui.post(() -> showError("Review was not saved",e,() -> loadSubmission(d.optInt("id"))));}});
    }

    private android.view.View withMargin(android.view.View v,int top){LinearLayout holder=UiKit.vertical(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=UiKit.dp(this,top);holder.addView(v,lp);return holder;}
    private String joinJson(JSONArray arr){if(arr==null||arr.length()==0)return "School-approved files";StringBuilder b=new StringBuilder();for(int i=0;i<arr.length();i++){if(i>0)b.append(", ");b.append(arr.optString(i).toUpperCase());}return b.toString();}
    private String formatBytes(long bytes){if(bytes<=0)return "—";String[] u={"B","KB","MB","GB"};double v=bytes;int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return (i==0?String.valueOf((long)v):String.format(java.util.Locale.US,"%.1f",v))+" "+u[i];}
}
