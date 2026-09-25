package com.schooloss.nativeapp.features.gallery;

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

public final class GalleryActivity extends SchoolOSActivity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);requireSession(this::loadAlbums);}

    private void loadAlbums(){
        showLoading("Opening gallery","Loading school albums…");
        io.execute(() -> {
            try{JSONObject r=api.getModule("gallery");JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Gallery is unavailable.");ui.post(() -> renderAlbums(d));}
            catch(Exception e){ui.post(() -> showError("Could not load gallery",e,this::loadAlbums));}
        });
    }

    private void renderAlbums(JSONObject d){
        LinearLayout page=page("Home",v -> finish(),"SCHOOL GALLERY",d.optString("title","Gallery"),d.optString("subtitle",""));
        JSONArray rows=rows(d);page.addView(UiKit.gap(this,12));
        if(rows.length()==0)page.addView(UiKit.body(this,"No gallery albums are visible for this account.",11));
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            LinearLayout item=simpleRow(r.optString("title","Album"),r.optString("subtitle","")+(r.optString("meta","").isEmpty()?"":" · "+r.optString("meta","")));
            int id=r.optInt("id");if("gallery_album".equals(r.optString("type",""))&&id>0)item.setOnClickListener(v -> loadAlbum(id));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);
        }
        setScrollable(page);
    }

    private void loadAlbum(int id){
        showLoading("Opening album","Loading private school media…");
        io.execute(() -> {
            try{JSONObject r=api.get("gallery_album","id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Album is unavailable.");ui.post(() -> renderAlbum(d));}
            catch(Exception e){ui.post(() -> showError("Could not open album",e,() -> loadAlbum(id)));}
        });
    }

    private void renderAlbum(JSONObject d){
        LinearLayout page=page("Gallery",v -> loadAlbums(),"SCHOOL GALLERY",d.optString("title","Album"),d.optString("event_date",""));
        if(!d.optString("description","").isEmpty())page.addView(UiKit.body(this,d.optString("description",""),12));
        JSONArray media=d.optJSONArray("media");page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Media",16));
        if(media!=null)for(int i=0;i<media.length();i++){
            JSONObject m=media.optJSONObject(i);if(m==null)continue;
            LinearLayout card=simpleRow(pretty(m.optString("media_type","Media"))+" #"+m.optInt("id"),formatBytes(m.optLong("file_size",0))+(m.optInt("duration_seconds",0)>0?" · "+m.optInt("duration_seconds")+" sec":""));
            int mediaId=m.optInt("id");
            Button open=UiKit.button(this,"Open Media",roleColor,Color.WHITE);open.setOnClickListener(v -> FileBridge.downloadAndOpen(this,api,io,"file","kind=gallery&id="+mediaId+"&size=full","Opening gallery media"));card.addView(UiKit.gap(this,8));card.addView(open);
            if(m.optBoolean("can_download",false)){Button save=UiKit.button(this,"Save Copy",Color.WHITE,roleColor);save.setBackground(UiKit.round(this,Color.WHITE,12,UiKit.tint(roleColor,.22f)));save.setOnClickListener(v -> FileBridge.downloadAndOpen(this,api,io,"file","kind=gallery&id="+mediaId+"&size=full&download=1","Saving gallery media"));card.addView(UiKit.gap(this,7));card.addView(save);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(card,lp);
        }
        setScrollable(page);
    }

    private String formatBytes(long bytes){if(bytes<=0)return "—";String[] u={"B","KB","MB","GB"};double v=bytes;int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return (i==0?String.valueOf((long)v):String.format(java.util.Locale.US,"%.1f",v))+" "+u[i];}
}
