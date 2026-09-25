package com.schooloss.nativeapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.schooloss.nativeapp.core.SchoolOSActivity;
import com.schooloss.nativeapp.core.UiKit;

import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends SchoolOSActivity {
    private JSONObject homeData;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        restore();
    }

    @Override protected void onResume() {
        super.onResume();
    }

    private void restore() {
        showLoading("Opening SchoolOS","Checking your secure school session…");
        io.execute(() -> {
            try {
                JSONObject s = api.get("session");
                ui.post(() -> acceptSession(s));
            } catch (Exception e) {
                ui.post(() -> renderLogin(null));
            }
        });
    }

    private void renderLogin(String initialError) {
        session=null;homeData=null;roleColor=UiKit.BLUE;
        LinearLayout page=UiKit.vertical(this);
        page.setPadding(UiKit.dp(this,18),UiKit.dp(this,16),UiKit.dp(this,18),UiKit.dp(this,24));
        page.setBackgroundColor(UiKit.BG);

        LinearLayout brand=UiKit.horizontal(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=UiKit.center(this,"S",22,Color.WHITE,true);mark.setBackground(UiKit.round(this,UiKit.BLUE,15,UiKit.BLUE));brand.addView(mark,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));
        LinearLayout copy=UiKit.vertical(this);copy.setPadding(UiKit.dp(this,12),0,0,0);copy.addView(UiKit.text(this,"SchoolOS",20,UiKit.TEXT,true));copy.addView(UiKit.label(this,"NATIVE ANDROID",9,UiKit.MUTED));brand.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));page.addView(brand);

        page.addView(UiKit.gap(this,14));
        LinearLayout hero=UiKit.vertical(this);hero.setPadding(UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20));hero.setBackground(UiKit.gradient(this,new int[]{Color.rgb(47,88,218),Color.rgb(90,74,229),Color.rgb(162,72,198)},25));hero.setElevation(UiKit.dp(this,5));
        hero.addView(UiKit.label(this,"ONE APP · EVERY SCHOOL ROLE",10,Color.argb(220,255,255,255)));
        TextView big=UiKit.text(this,"SchoolOS for Android",24,Color.WHITE,true);big.setPadding(0,UiKit.dp(this,7),0,0);hero.addView(big);
        TextView small=UiKit.text(this,"Admin, teacher, parent and student workspaces with native screens and existing SchoolOS authority.",13,Color.argb(225,255,255,255),false);small.setPadding(0,UiKit.dp(this,8),0,0);hero.addView(small);
        page.addView(hero);

        page.addView(UiKit.gap(this,16));
        LinearLayout form=UiKit.card(this);form.setPadding(UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,18));
        form.addView(UiKit.label(this,"WELCOME BACK",10,UiKit.BLUE));form.addView(UiKit.title(this,"Sign in to SchoolOS",26));form.addView(UiKit.body(this,"Use your existing SchoolOS account. School, role and permissions remain server-authoritative.",13));

        TextView error=UiKit.text(this,"",11,Color.rgb(190,45,60),true);error.setPadding(UiKit.dp(this,12),UiKit.dp(this,10),UiKit.dp(this,12),UiKit.dp(this,10));error.setBackground(UiKit.round(this,Color.rgb(255,244,245),12,Color.rgb(250,210,216)));error.setVisibility(android.view.View.GONE);
        if(initialError!=null&&!initialError.isEmpty()){error.setText(initialError);error.setVisibility(android.view.View.VISIBLE);}
        form.addView(UiKit.gap(this,12));form.addView(error);

        EditText email=UiKit.input(this,"Email address",false);email.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=UiKit.input(this,"Password",true);
        form.addView(UiKit.gap(this,10));form.addView(email);form.addView(UiKit.gap(this,10));form.addView(password);
        Button sign=UiKit.button(this,"Sign In",UiKit.BLUE,Color.WHITE);sign.setOnClickListener(v -> doLogin(email,password,sign,error));form.addView(UiKit.gap(this,14));form.addView(sign);
        TextView note=UiKit.center(this,"Secure session · existing SchoolOS database · native UI",10,UiKit.MUTED,false);note.setPadding(0,UiKit.dp(this,10),0,0);form.addView(note);
        page.addView(form);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);setContentView(scroll);
    }

    private void doLogin(EditText email,EditText password,Button button,TextView error) {
        String e=email.getText().toString().trim(),p=password.getText().toString();
        if(e.isEmpty()||p.isEmpty()){error.setText("Enter your SchoolOS email and password.");error.setVisibility(android.view.View.VISIBLE);return;}
        button.setEnabled(false);button.setText("Signing in…");error.setVisibility(android.view.View.GONE);
        io.execute(() -> {
            try{JSONObject b=new JSONObject();b.put("email",e);b.put("password",p);JSONObject s=api.post("login",b);ui.post(() -> acceptSession(s));}
            catch(Exception ex){ui.post(() -> {button.setEnabled(true);button.setText("Sign In");error.setText(message(ex));error.setVisibility(android.view.View.VISIBLE);});}
        });
    }

    private void acceptSession(JSONObject s) {
        session=s;applyRole();
        if(s.optBoolean("school_required",false)){renderSchoolChooser(s.optJSONArray("memberships"));return;}
        loadHome();
    }

    private void loadHome() {
        showLoading("Loading your workspace","Syncing live SchoolOS dashboard…");
        io.execute(() -> {
            try{JSONObject r=api.get("home");JSONObject d=r.optJSONObject("data");JSONObject s=r.optJSONObject("session");if(d==null)throw new Exception("Dashboard data is unavailable.");if(s!=null)session=s;homeData=d;ui.post(() -> {applyRole();renderHome();});}
            catch(Exception e){ui.post(() -> showError("Could not load SchoolOS",e,this::loadHome));}
        });
    }

    private void renderHome() {
        LinearLayout root=UiKit.vertical(this);root.setBackgroundColor(UiKit.BG);root.addView(topBar());
        LinearLayout page=UiKit.vertical(this);page.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,22));

        JSONObject heroData=homeData==null?null:homeData.optJSONObject("hero");
        LinearLayout hero=UiKit.vertical(this);hero.setPadding(UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,18));hero.setBackground(UiKit.gradient(this,new int[]{roleColor,UiKit.tint(roleColor,-.18f)},23));
        hero.addView(UiKit.label(this,heroData==null?"SCHOOL WORKSPACE":heroData.optString("eyebrow","SCHOOL WORKSPACE"),9,Color.argb(220,255,255,255)));
        hero.addView(UiKit.text(this,heroData==null?"Your SchoolOS workspace":heroData.optString("title","Your SchoolOS workspace"),23,Color.WHITE,true));
        hero.addView(UiKit.text(this,heroData==null?"":heroData.optString("subtitle",""),12,Color.argb(225,255,255,255),false));
        JSONArray metrics=homeData==null?null:homeData.optJSONArray("metrics");
        if(metrics!=null&&metrics.length()>0)hero.addView(metricGrid(metrics,true));
        page.addView(hero);

        addContextSwitchers(page);

        JSONArray modules=homeData==null?null:homeData.optJSONArray("modules");
        page.addView(UiKit.gap(this,15));page.addView(UiKit.title(this,"Quick Access",16));page.addView(UiKit.body(this,"Modules are shown according to your current role and permissions.",10));
        if(modules!=null)page.addView(moduleGrid(modules));

        JSONArray rows=homeData==null?null:homeData.optJSONArray("rows");
        if(rows!=null&&rows.length()>0){page.addView(UiKit.gap(this,15));page.addView(UiKit.title(this,"Today",16));for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;LinearLayout item=simpleRow(r.optString("title","Update"),r.optString("subtitle","")+(r.optString("meta","").isEmpty()?"":" · "+r.optString("meta","")));String target=r.optString("module","");if(!target.isEmpty())item.setOnClickListener(v -> openModule(target));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);if(i>0)lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);}}

        JSONArray nav=session==null?null:session.optJSONArray("nav");
        if(nav!=null&&nav.length()>0){page.addView(UiKit.gap(this,15));page.addView(UiKit.title(this,"Workspace Navigation",16));LinearLayout navWrap=UiKit.horizontal(this);for(int i=0;i<nav.length();i++){JSONObject n=nav.optJSONObject(i);if(n==null)continue;String key=n.optString("key","home");if("home".equals(key))continue;TextView chip=UiKit.center(this,n.optString("label",pretty(key)),10,roleColor,true);chip.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,8));chip.setBackground(UiKit.round(this,UiKit.tint(roleColor,.08f),99,UiKit.tint(roleColor,.18f)));chip.setOnClickListener(v -> openModule(key));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);if(i>0)lp.leftMargin=UiKit.dp(this,5);navWrap.addView(chip,lp);}page.addView(navWrap);}

        Button logout=UiKit.button(this,"Sign Out",Color.WHITE,Color.rgb(180,55,65));logout.setBackground(UiKit.round(this,Color.WHITE,14,Color.rgb(245,210,215)));logout.setOnClickListener(v -> logout());page.addView(UiKit.gap(this,18));page.addView(logout);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);
    }

    private LinearLayout metricGrid(JSONArray metrics,boolean heroStyle) {
        LinearLayout wrap=UiKit.vertical(this);wrap.setPadding(0,UiKit.dp(this,12),0,0);
        for(int i=0;i<metrics.length();i+=2){LinearLayout row=UiKit.horizontal(this);for(int j=0;j<2;j++){int idx=i+j;if(idx>=metrics.length()){row.addView(new android.view.View(this),new LinearLayout.LayoutParams(0,1,1f));continue;}JSONObject m=metrics.optJSONObject(idx);LinearLayout c=UiKit.vertical(this);c.setGravity(Gravity.CENTER);c.setBackground(UiKit.round(this,heroStyle?Color.argb(25,255,255,255):Color.WHITE,13,heroStyle?Color.argb(38,255,255,255):UiKit.LINE));c.addView(UiKit.center(this,m==null?"—":m.optString("value","—"),17,heroStyle?Color.WHITE:UiKit.TEXT,true));c.addView(UiKit.center(this,m==null?"":m.optString("label",""),9,heroStyle?Color.argb(220,255,255,255):UiKit.MUTED,true));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,60),1f);if(j>0)lp.leftMargin=UiKit.dp(this,7);if(i>0)lp.topMargin=UiKit.dp(this,7);row.addView(c,lp);}wrap.addView(row);}return wrap;
    }

    private LinearLayout moduleGrid(JSONArray items) {
        LinearLayout wrap=UiKit.vertical(this);
        for(int i=0;i<items.length();i+=2){LinearLayout row=UiKit.horizontal(this);for(int c=0;c<2;c++){int idx=i+c;if(idx>=items.length()){row.addView(new android.view.View(this),new LinearLayout.LayoutParams(0,1,1f));continue;}JSONObject m=items.optJSONObject(idx);if(m==null)continue;String key=m.optString("key",""),name=m.optString("label",pretty(key));LinearLayout card=UiKit.card(this);card.setPadding(UiKit.dp(this,13),UiKit.dp(this,13),UiKit.dp(this,13),UiKit.dp(this,13));TextView icon=UiKit.center(this,initials(name),12,roleColor,true);icon.setBackground(UiKit.round(this,UiKit.tint(roleColor,.10f),12,UiKit.tint(roleColor,.16f)));card.addView(icon,new LinearLayout.LayoutParams(UiKit.dp(this,34),UiKit.dp(this,34)));card.addView(UiKit.text(this,name,12,UiKit.TEXT,true));card.addView(UiKit.text(this,m.optString("subtitle","Open"),9,UiKit.MUTED,false));card.setOnClickListener(v -> openModule(key));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,UiKit.dp(this,112),1f);if(c>0)lp.leftMargin=UiKit.dp(this,8);if(i>0)lp.topMargin=UiKit.dp(this,8);row.addView(card,lp);}wrap.addView(row);}return wrap;
    }

    private String initials(String name){if(name==null||name.trim().isEmpty())return "•";String[] p=name.trim().split("\\s+");String s="";for(int i=0;i<Math.min(2,p.length);i++)if(!p[i].isEmpty())s+=p[i].substring(0,1).toUpperCase();return s;}

    private void addContextSwitchers(LinearLayout page) {
        if(session==null)return;
        String role=session.optString("role","admin");
        if("parent".equals(role)){JSONArray kids=session.optJSONArray("children");if(kids!=null&&kids.length()>1){page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Child Context",15));for(int i=0;i<kids.length();i++){JSONObject k=kids.optJSONObject(i);if(k==null)continue;Button b=UiKit.button(this,(k.optBoolean("selected",false)?"✓  ":"")+k.optString("name","Child"),k.optBoolean("selected",false)?roleColor:Color.WHITE,k.optBoolean("selected",false)?Color.WHITE:UiKit.TEXT);if(!k.optBoolean("selected",false))b.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.LINE));int id=k.optInt("id");b.setEnabled(!k.optBoolean("selected",false));b.setOnClickListener(v -> selectChild(id));page.addView(UiKit.gap(this,6));page.addView(b);}}}
        else if("teacher".equals(role)){JSONArray contexts=session.optJSONArray("teaching_contexts");if(contexts!=null&&contexts.length()>1){page.addView(UiKit.gap(this,12));page.addView(UiKit.title(this,"Teaching Context",15));for(int i=0;i<contexts.length();i++){JSONObject x=contexts.optJSONObject(i);if(x==null)continue;Button b=UiKit.button(this,x.optString("label","Teaching context"),Color.WHITE,UiKit.TEXT);b.setBackground(UiKit.round(this,Color.WHITE,14,UiKit.LINE));String key=x.optString("key","");b.setOnClickListener(v -> selectTeaching(key));page.addView(UiKit.gap(this,6));page.addView(b);}}}
    }

    private void selectChild(int id){showLoading("Switching child","Loading selected student context…");io.execute(() -> {try{JSONObject b=new JSONObject();b.put("child_id",id);session=api.post("select_child",b);ui.post(this::loadHome);}catch(Exception e){ui.post(() -> showError("Could not switch child",e,this::loadHome));}});}
    private void selectTeaching(String key){showLoading("Switching class","Applying teaching allocation…");io.execute(() -> {try{JSONObject b=new JSONObject();b.put("key",key);session=api.post("select_teaching_context",b);ui.post(this::loadHome);}catch(Exception e){ui.post(() -> showError("Could not switch class",e,this::loadHome));}});}

    private void renderSchoolChooser(JSONArray memberships) {
        LinearLayout page=UiKit.vertical(this);page.setPadding(UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,24));page.setBackgroundColor(UiKit.BG);page.addView(UiKit.title(this,"Select SchoolOS Workspace",25));page.addView(UiKit.body(this,"Choose the school you want to open.",12));
        if(memberships!=null)for(int i=0;i<memberships.length();i++){JSONObject m=memberships.optJSONObject(i);if(m==null)continue;LinearLayout item=UiKit.card(this);item.setPadding(UiKit.dp(this,15),UiKit.dp(this,14),UiKit.dp(this,15),UiKit.dp(this,14));item.addView(UiKit.text(this,m.optString("name","School"),15,UiKit.TEXT,true));item.addView(UiKit.text(this,m.optString("role_name","School member"),10,UiKit.MUTED,false));int id=m.optInt("school_id");item.setOnClickListener(v -> switchSchool(id));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.topMargin=UiKit.dp(this,8);page.addView(item,lp);}
        ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
    }

    private void switchSchool(int id){showLoading("Switching school","Applying your workspace…");io.execute(() -> {try{JSONObject b=new JSONObject();b.put("school_id",id);JSONObject s=api.post("switch_school",b);ui.post(() -> acceptSession(s));}catch(Exception e){ui.post(() -> showError("School switch failed",e,this::restore));}});}
    private void logout(){showLoading("Signing out","Closing secure SchoolOS session…");io.execute(() -> {try{api.post("logout",new JSONObject());}catch(Exception ignored){}api.clearSession();ui.post(() -> renderLogin(null));});}
}
