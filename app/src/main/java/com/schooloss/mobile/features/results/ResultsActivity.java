package com.schooloss.nativeapp.features.results;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.schooloss.nativeapp.core.FileBridge;
import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class ResultsActivity extends SchoolOSActivity {
    private String mode;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        mode=getIntent().getStringExtra("mode");
        if(mode==null)mode="results";
        requireSession(this::loadList);
    }

    private void loadList(){
        showLoading("Opening results","Loading published SchoolOS results…");
        io.execute(() -> {
            try{
                JSONObject r=api.getModule("results");
                JSONObject d=r.optJSONObject("data");
                if(d==null)throw new Exception("Results are unavailable.");
                ui.post(() -> renderList(d));
            }catch(Exception e){ui.post(() -> showError("Could not load results",e,this::loadList));}
        });
    }

    private void renderList(JSONObject d){
        LinearLayout page=page("Home",v -> finish(),"RESULTS",d.optString("title","Results"),d.optString("subtitle","Published SchoolOS results"));
        JSONArray rows=rows(d);page.addView(UiKit.gap(this,12));
        if(rows.length()==0)page.addView(UiKit.body(this,"No published results are available for this context.",11));
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            LinearLayout item=simpleRow(r.optString("title","Result"),r.optString("subtitle","")+(r.optString("meta","").isEmpty()?"":" · "+r.optString("meta","")));
            String type=r.optString("type","");int id=r.optInt("id");
            if(("result_term".equals(type)||"result_exam".equals(type))&&id>0){
                String kind="result_exam".equals(type)?"exam":"term";
                item.setOnClickListener(v -> loadDetail(kind,id));
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);
        }
        setScrollable(page);
    }

    private void loadDetail(String kind,int id){
        showLoading("Opening result","Loading published result detail…");
        io.execute(() -> {
            try{JSONObject r=api.get("result_detail","kind="+kind+"&id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Result is unavailable.");ui.post(() -> renderDetail(d));}
            catch(Exception e){ui.post(() -> showError("Could not open result",e,() -> loadDetail(kind,id)));}
        });
    }

    private void renderDetail(JSONObject d){
        String kind=d.optString("kind","term");
        LinearLayout page=page("Results",v -> loadList(),kind.equals("exam")?"PUBLISHED EXAM RESULT":"PUBLISHED TERM RESULT",d.optString("title","Result"),d.optString("subtitle",""));

        LinearLayout metrics=UiKit.horizontal(this);
        metrics.addView(metric("RESULT",String.format(Locale.US,"%.1f%%",d.optDouble("percentage",0))),new LinearLayout.LayoutParams(0,UiKit.dp(this,70),1f));
        if(d.has("grade")){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,70),1f);lp.leftMargin=UiKit.dp(this,7);
            metrics.addView(metric("GRADE",d.optString("grade","—")),lp);
        }
        page.addView(UiKit.gap(this,12));page.addView(metrics);

        JSONArray subjects=d.optJSONArray("subjects");page.addView(UiKit.gap(this,14));page.addView(UiKit.title(this,"Subject Performance",16));
        if(subjects!=null)for(int i=0;i<subjects.length();i++){
            JSONObject s=subjects.optJSONObject(i);if(s==null)continue;
            String meta;
            if("exam".equals(kind)){
                meta=(s.isNull("total_marks")?"—":s.optString("total_marks"))+" / "+s.optString("max_marks")+" · "+s.optString("result","");
            }else{
                meta=String.format(Locale.US,"%.1f%%",s.optDouble("percentage",0))+" · Failed "+s.optInt("failed")+" · Absent "+s.optInt("absent");
            }
            LinearLayout row=simpleRow(s.optString("subject","Subject"),meta);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,7);page.addView(row,lp);
        }

        if(!d.optString("publication_notes","").isEmpty()){
            LinearLayout note=UiKit.card(this);note.setPadding(UiKit.dp(this,13),UiKit.dp(this,12),UiKit.dp(this,13),UiKit.dp(this,12));note.addView(UiKit.label(this,"SCHOOL NOTE",9,roleColor));note.addView(UiKit.body(this,d.optString("publication_notes",""),11));page.addView(UiKit.gap(this,12));page.addView(note);
        }

        if(d.optBoolean("official_document",false)){
            int id=d.optInt("id");
            Button report=UiKit.button(this,"Open Official Report Document",roleColor,Color.WHITE);
            report.setOnClickListener(v -> FileBridge.downloadAndOpen(this,api,io,"report_document","kind="+kind+"&id="+id,"Opening official report"));
            page.addView(UiKit.gap(this,14));page.addView(report);
        }
        setScrollable(page);
    }

    private LinearLayout metric(String label,String value){
        LinearLayout c=UiKit.vertical(this);c.setGravity(android.view.Gravity.CENTER);c.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.LINE));c.addView(UiKit.center(this,value,18,UiKit.TEXT,true));c.addView(UiKit.center(this,label,9,UiKit.MUTED,true));return c;
    }
}
