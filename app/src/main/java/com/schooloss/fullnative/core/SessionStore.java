package com.schooloss.fullnative.core;
import android.content.Context;import android.content.SharedPreferences;import org.json.JSONObject;
public final class SessionStore {
 private final SharedPreferences p;
 public SessionStore(Context c){p=c.getSharedPreferences("schoolos_full_native_session",Context.MODE_PRIVATE);}
 public String cookie(){return p.getString("cookie","");} public void cookie(String v){p.edit().putString("cookie",v==null?"":v).apply();}
 public String csrf(){return p.getString("csrf","");} public void csrf(String v){p.edit().putString("csrf",v==null?"":v).apply();}
 public void session(JSONObject j){p.edit().putString("session",j==null?"":j.toString()).apply();}
 public JSONObject session(){try{return new JSONObject(p.getString("session","{}"));}catch(Exception e){return new JSONObject();}}
 public void clear(){p.edit().clear().apply();}
}