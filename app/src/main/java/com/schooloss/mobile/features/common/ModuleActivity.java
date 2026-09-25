package com.schooloss.nativeapp.features.common;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

public final class ModuleActivity extends SchoolOSActivity {
    private String module;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        module = getIntent().getStringExtra("module");
        if (module == null || module.trim().isEmpty()) module = "home";
        final String m = module;
        requireSession(() -> load(m));
    }

    private void load(String name) {
        showLoading("Loading "+pretty(name),"Syncing live SchoolOS data…");
        io.execute(() -> {
            try {
                JSONObject r = api.getModule(name);
                JSONObject d = r.optJSONObject("data");
                if (d == null) throw new Exception("Module data is unavailable.");
                ui.post(() -> render(d));
            } catch (Exception e) {
                ui.post(() -> showError("Could not load "+pretty(name),e,() -> load(name)));
            }
        });
    }

    private void render(JSONObject d) {
        LinearLayout page = page("Home",v -> finish(),pretty(module).toUpperCase(),d.optString("title",pretty(module)),d.optString("subtitle",""));
        JSONArray metrics = d.optJSONArray("metrics");
        if (metrics != null && metrics.length() > 0) {
            page.addView(UiKit.gap(this,12));
            LinearLayout wrap = UiKit.vertical(this);
            for (int i=0;i<metrics.length();i+=2) {
                LinearLayout row = UiKit.horizontal(this);
                for (int j=0;j<2;j++) {
                    int idx=i+j;
                    if (idx>=metrics.length()) { row.addView(new android.view.View(this),new LinearLayout.LayoutParams(0,1,1f)); continue; }
                    JSONObject m=metrics.optJSONObject(idx);
                    LinearLayout card=UiKit.card(this);card.setGravity(android.view.Gravity.CENTER);
                    card.addView(UiKit.center(this,m==null?"—":m.optString("value","—"),17,UiKit.TEXT,true));
                    card.addView(UiKit.center(this,m==null?"":m.optString("label",""),9,UiKit.MUTED,true));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,66),1f);if(j>0)lp.leftMargin=UiKit.dp(this,7);row.addView(card,lp);
                }
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)rp.topMargin=UiKit.dp(this,7);wrap.addView(row,rp);
            }
            page.addView(wrap);
        }

        JSONArray rows = rows(d);
        page.addView(UiKit.gap(this,14));
        page.addView(UiKit.title(this,"Overview",16));
        page.addView(UiKit.body(this,rows.length()==0?"No records are available for this context.":rows.length()+" live records",10));
        for (int i=0;i<rows.length();i++) {
            JSONObject r=rows.optJSONObject(i); if(r==null)continue;
            LinearLayout item=simpleRow(r.optString("title","Record"),r.optString("subtitle","")+(r.optString("meta","").isEmpty()?"":" · "+r.optString("meta","")));
            String target=r.optString("module","");
            if(!target.isEmpty())item.setOnClickListener(v -> openModule(target));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);
        }
        setScrollable(page);
    }
}
