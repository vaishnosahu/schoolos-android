package com.schooloss.nativeapp.features.messages;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class MessagesActivity extends SchoolOSActivity {
    private String mode;

    @Override protected void onCreate(Bundle state){super.onCreate(state);mode=getIntent().getStringExtra("mode");if(mode==null)mode="messages";requireSession(this::load);}

    private void load(){
        showLoading("Opening "+pretty(mode),"Loading communication and updates…");
        io.execute(() -> {
            try{JSONObject r=api.getModule(mode);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("This communication view is unavailable.");ui.post(() -> renderList(d));}
            catch(Exception e){ui.post(() -> showError("Could not load "+pretty(mode),e,this::load));}
        });
    }

    private void renderList(JSONObject d){
        LinearLayout page=page("Home",v -> finish(),pretty(mode).toUpperCase(),d.optString("title",pretty(mode)),d.optString("subtitle",""));
        if("messages".equals(mode)&&d.optBoolean("can_compose",false)){Button compose=UiKit.button(this,"New Conversation",roleColor,Color.WHITE);compose.setOnClickListener(v -> loadCompose());page.addView(UiKit.gap(this,12));page.addView(compose);}
        if("updates".equals(mode)||"alerts".equals(mode)){Button all=UiKit.button(this,"Mark All Notifications Read",Color.WHITE,roleColor);all.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.tint(roleColor,.22f)));all.setOnClickListener(v -> markAll());page.addView(UiKit.gap(this,12));page.addView(all);}

        JSONArray rows=rows(d);page.addView(UiKit.gap(this,14));
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.optJSONObject(i);if(r==null)continue;
            LinearLayout card=simpleRow(r.optString("title","Item"),r.optString("subtitle","")+(r.optString("meta","").isEmpty()?"":" · "+r.optString("meta","")));
            String type=r.optString("type","");int id=r.optInt("id");
            if("conversation".equals(type)&&id>0){int finalId=id;card.setOnClickListener(v -> loadThread(finalId));}
            if("notification".equals(type)&&!r.optBoolean("read",false)&&id>0){Button read=UiKit.button(this,"Mark Read",Color.WHITE,roleColor);read.setBackground(UiKit.round(this,Color.WHITE,12,UiKit.tint(roleColor,.22f)));int finalId=id;read.setOnClickListener(v -> markRead(finalId));card.addView(UiKit.gap(this,7));card.addView(read);}
            if("announcement".equals(type)&&r.optBoolean("requires_ack",false)&&!r.optBoolean("acknowledged",false)&&id>0){Button ack=UiKit.button(this,"Acknowledge",Color.WHITE,roleColor);ack.setBackground(UiKit.round(this,Color.WHITE,12,UiKit.tint(roleColor,.22f)));int finalId=id;ack.setOnClickListener(v -> acknowledge(finalId));card.addView(UiKit.gap(this,7));card.addView(ack);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(card,lp);
        }
        setScrollable(page);
    }

    private void loadThread(int id){
        showLoading("Opening conversation","Loading secure message thread…");
        io.execute(() -> {
            try{JSONObject r=api.get("message_thread","id="+id);JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("Conversation is unavailable.");ui.post(() -> renderThread(d));}
            catch(Exception e){ui.post(() -> showError("Could not open conversation",e,() -> loadThread(id)));}
        });
    }

    private void renderThread(JSONObject d){
        LinearLayout page=page("Messages",v -> {mode="messages";load();},d.optString("category","MESSAGE").toUpperCase(),d.optString("subject","Conversation"),pretty(d.optString("status","open"))+(d.optString("student","").isEmpty()?"":" · "+d.optString("student","")));
        JSONArray messages=d.optJSONArray("messages");if(messages==null)messages=new JSONArray();page.addView(UiKit.gap(this,12));
        for(int i=0;i<messages.length();i++){JSONObject m=messages.optJSONObject(i);if(m==null)continue;LinearLayout bubble=UiKit.card(this);bubble.setPadding(UiKit.dp(this,13),UiKit.dp(this,11),UiKit.dp(this,13),UiKit.dp(this,11));bubble.addView(UiKit.text(this,(m.optBoolean("mine",false)?"You":m.optString("sender","User"))+" · "+m.optString("created_at",""),9,m.optBoolean("mine",false)?roleColor:UiKit.MUTED,true));android.widget.TextView body=UiKit.text(this,m.optString("body",""),12,UiKit.TEXT,false);body.setPadding(0,UiKit.dp(this,5),0,0);bubble.addView(body);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=UiKit.dp(this,7);page.addView(bubble,lp);}

        if(d.optBoolean("can_reply",false)){page.addView(UiKit.gap(this,12));EditText reply=new EditText(this);reply.setHint("Write a reply…");reply.setTextSize(13);reply.setTextColor(UiKit.TEXT);reply.setHintTextColor(UiKit.MUTED);reply.setMinLines(3);reply.setGravity(Gravity.TOP);reply.setPadding(UiKit.dp(this,12),UiKit.dp(this,10),UiKit.dp(this,12),UiKit.dp(this,10));reply.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.LINE));page.addView(reply);Button send=UiKit.button(this,"Send Reply",roleColor,Color.WHITE);send.setOnClickListener(v -> sendReply(d.optInt("id"),reply.getText().toString()));page.addView(UiKit.gap(this,8));page.addView(send);}
        if(d.optBoolean("can_resolve",false)){Button resolve=UiKit.button(this,"Resolve Conversation",Color.WHITE,roleColor);resolve.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.tint(roleColor,.22f)));resolve.setOnClickListener(v -> resolve(d.optInt("id")));page.addView(UiKit.gap(this,8));page.addView(resolve);}
        setScrollable(page);
    }

    private void loadCompose(){
        showLoading("New conversation","Loading permitted SchoolOS contacts…");
        io.execute(() -> {try{JSONObject r=api.get("message_compose");JSONObject d=r.optJSONObject("data");if(d==null)throw new Exception("New conversations are unavailable.");ui.post(() -> renderCompose(d));}catch(Exception e){ui.post(() -> showError("Could not start a conversation",e,this::load));}});
    }

    private void renderCompose(JSONObject d){
        LinearLayout page=page("Messages",v -> load(),"NEW CONVERSATION","Start a SchoolOS message","Recipients are limited by your communication scope.");
        EditText subject=UiKit.input(this,"Conversation subject",false);page.addView(UiKit.gap(this,12));page.addView(subject);

        JSONArray cats=d.optJSONArray("categories");List<String> catLabels=new ArrayList<>();if(cats!=null)for(int i=0;i<cats.length();i++)catLabels.add(cats.optString(i));if(catLabels.isEmpty())catLabels.add("general");
        Spinner category=new Spinner(this);category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,catLabels));page.addView(UiKit.gap(this,8));page.addView(category);

        JSONArray participants=d.optJSONArray("participants");List<String> participantLabels=new ArrayList<>();List<Integer> participantIds=new ArrayList<>();if(participants!=null)for(int i=0;i<participants.length();i++){JSONObject u=participants.optJSONObject(i);if(u==null)continue;participantIds.add(u.optInt("id"));participantLabels.add(u.optString("name","User")+" · "+u.optString("role","User"));}
        Spinner participant=new Spinner(this);participant.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,participantLabels));page.addView(UiKit.gap(this,8));page.addView(participant);

        JSONArray students=d.optJSONArray("students");List<String> studentLabels=new ArrayList<>();List<Integer> studentIds=new ArrayList<>();studentLabels.add("No student context");studentIds.add(0);if(students!=null)for(int i=0;i<students.length();i++){JSONObject s=students.optJSONObject(i);if(s==null)continue;studentIds.add(s.optInt("id"));studentLabels.add(s.optString("admission_no","")+" · "+s.optString("name","Student"));}
        Spinner student=new Spinner(this);student.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,studentLabels));page.addView(UiKit.gap(this,8));page.addView(student);

        EditText body=new EditText(this);body.setHint("Write your first message…");body.setTextSize(13);body.setTextColor(UiKit.TEXT);body.setHintTextColor(UiKit.MUTED);body.setMinLines(4);body.setGravity(Gravity.TOP);body.setPadding(UiKit.dp(this,12),UiKit.dp(this,10),UiKit.dp(this,12),UiKit.dp(this,10));body.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.LINE));page.addView(UiKit.gap(this,8));page.addView(body);

        Button send=UiKit.button(this,"Open Conversation",roleColor,Color.WHITE);send.setOnClickListener(v -> {if(participantIds.isEmpty()){toast("No permitted participant is available.");return;}create(subject.getText().toString(),String.valueOf(category.getSelectedItem()),participantIds.get(participant.getSelectedItemPosition()),studentIds.get(student.getSelectedItemPosition()),body.getText().toString());});page.addView(UiKit.gap(this,12));page.addView(send);
        setScrollable(page);
    }

    private void create(String subject,String category,int participantId,int studentId,String body){
        if(subject.trim().isEmpty()){toast("Conversation subject is required.");return;}
        showLoading("Opening conversation","Applying SchoolOS communication scope…");
        io.execute(() -> {try{JSONObject b=new JSONObject();b.put("subject",subject.trim());b.put("category",category);b.put("participant_user_id",participantId);b.put("student_id",studentId);b.put("body",body.trim());JSONObject r=api.post("message_create",b);JSONObject thread=r.optJSONObject("thread");ui.post(() -> {toast(r.optString("message","Conversation opened."));if(thread!=null)renderThread(thread);else load();});}catch(Exception e){ui.post(() -> showError("Conversation was not opened",e,this::load));}});
    }

    private void sendReply(int id,String text){String value=text.trim();if(value.isEmpty()){toast("Message cannot be blank.");return;}showLoading("Sending reply","Delivering through SchoolOS…");io.execute(() -> {try{JSONObject b=new JSONObject();b.put("conversation_id",id);b.put("body",value);JSONObject r=api.post("message_reply",b);JSONObject thread=r.optJSONObject("thread");ui.post(() -> {toast(r.optString("message","Reply sent."));if(thread!=null)renderThread(thread);else loadThread(id);});}catch(Exception e){ui.post(() -> showError("Reply was not sent",e,() -> loadThread(id)));}});}
    private void resolve(int id){showLoading("Resolving conversation","Updating SchoolOS message status…");io.execute(() -> {try{JSONObject b=new JSONObject();b.put("conversation_id",id);JSONObject r=api.post("message_resolve",b);JSONObject thread=r.optJSONObject("thread");ui.post(() -> {toast(r.optString("message","Conversation resolved."));if(thread!=null)renderThread(thread);else load();});}catch(Exception e){ui.post(() -> showError("Conversation was not resolved",e,() -> loadThread(id)));}});}
    private void markRead(int id){io.execute(() -> {try{JSONObject b=new JSONObject();b.put("id",id);api.post("notification_read",b);ui.post(this::load);}catch(Exception e){ui.post(() -> toast(message(e)));}});}
    private void acknowledge(int id){io.execute(() -> {try{JSONObject b=new JSONObject();b.put("id",id);api.post("ack_announcement",b);ui.post(this::load);}catch(Exception e){ui.post(() -> toast(message(e)));}});}
    private void markAll(){io.execute(() -> {try{api.post("notification_read_all",new JSONObject());ui.post(this::load);}catch(Exception e){ui.post(() -> toast(message(e)));}});}
}
