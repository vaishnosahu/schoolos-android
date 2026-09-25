package com.schooloss.nativeapp.features.transport;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

public final class TransportActivity extends SchoolOSActivity {
    @Override protected void onCreate(Bundle state){ super.onCreate(state); requireSession(this::load); }

    private void load(){
        showLoading("Opening transport","Loading routes and today’s trips…");
        io.execute(() -> {
            try { JSONObject r=api.getModule("transport"); JSONObject d=r.optJSONObject("data"); if(d==null)throw new Exception("Transport is unavailable."); ui.post(() -> render(d)); }
            catch(Exception e){ ui.post(() -> showError("Could not load transport",e,this::load)); }
        });
    }

    private void render(JSONObject d){
        LinearLayout page=page("Home",v -> finish(),"TRANSPORT",d.optString("title","Transport"),d.optString("subtitle",""));
        JSONArray rows=rows(d);
        page.addView(UiKit.gap(this,12));
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            LinearLayout card=simpleRow(r.optString("title","Trip"),r.optString("subtitle","")+" · "+r.optString("meta",""));
            JSONArray actions=r.optJSONArray("actions");
            if("transport_trip".equals(r.optString("type",""))&&actions!=null&&actions.length()>0){
                LinearLayout buttons=UiKit.horizontal(this);buttons.setPadding(0,UiKit.dp(this,8),0,0);
                for(int a=0;a<actions.length();a++){
                    JSONObject x=actions.optJSONObject(a);if(x==null)continue;
                    String key=x.optString("key",""), label=x.optString("label",pretty(key)); int tripId=r.optInt("id");
                    boolean cancel="trip_cancel".equals(key);
                    Button b=UiKit.button(this,label,cancel?Color.WHITE:roleColor,cancel?Color.rgb(180,55,65):Color.WHITE);
                    if(cancel)b.setBackground(UiKit.round(this,Color.WHITE,12,Color.rgb(245,205,212)));
                    b.setOnClickListener(v -> confirm(tripId,key,label));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,42),1f);if(a>0)lp.leftMargin=UiKit.dp(this,6);buttons.addView(b,lp);
                }
                card.addView(buttons);
            }
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(card,lp);
        }
        setScrollable(page);
    }

    private void confirm(int tripId,String action,String label){
        String status="trip_start".equals(action)?"running":("trip_complete".equals(action)?"completed":"cancelled");
        new AlertDialog.Builder(this).setTitle(label+"?").setMessage("This updates the live SchoolOS trip status.").setNegativeButton("Cancel",null).setPositiveButton(label,(d,w) -> update(tripId,status)).show();
    }

    private void update(int tripId,String status){
        showLoading("Updating trip","Applying SchoolOS transport controls…");
        io.execute(() -> {
            try { JSONObject b=new JSONObject();b.put("trip_id",tripId);b.put("status",status);JSONObject r=api.post("transport_trip_status",b);ui.post(() -> {toast(r.optString("message","Trip updated."));load();}); }
            catch(Exception e){ui.post(() -> showError("Trip update failed",e,this::load));}
        });
    }
}
