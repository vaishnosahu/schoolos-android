package com.schooloss.fullnative.core;
import android.content.*;import org.json.JSONObject;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class ApiClient {
 private final SessionStore store;
 public ApiClient(Context c){store=new SessionStore(c.getApplicationContext());}
 public SessionStore store(){return store;}
 public JSONObject get(String a)throws Exception{return request("GET",a,null,null);}
 public JSONObject get(String a,String q)throws Exception{return request("GET",a,q,null);}
 public JSONObject module(String key)throws Exception{return get("module","name="+URLEncoder.encode(key,"UTF-8"));}
 public JSONObject post(String a,JSONObject body)throws Exception{return request("POST",a,null,body==null?new JSONObject():body);}
 private JSONObject request(String method,String action,String query,JSONObject body)throws Exception{
  StringBuilder u=new StringBuilder(Constants.API).append("?action=").append(URLEncoder.encode(action,"UTF-8"));if(query!=null&&!query.isEmpty())u.append('&').append(query);
  HttpURLConnection c=(HttpURLConnection)new URL(u.toString()).openConnection();c.setConnectTimeout(18000);c.setReadTimeout(60000);c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-SchoolOS-Native","android-full");c.setRequestProperty("User-Agent","SchoolOSFullNative/5.0.0 Android");
  if(!store.cookie().isEmpty())c.setRequestProperty("Cookie",store.cookie());if("POST".equals(method)&&!"login".equals(action)&&!store.csrf().isEmpty())c.setRequestProperty("X-SchoolOS-CSRF",store.csrf());
  if(body!=null){byte[] p=body.toString().getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setFixedLengthStreamingMode(p.length);try(OutputStream o=c.getOutputStream()){o.write(p);}}
  int status=c.getResponseCode();capture(c.getHeaderFields());String text=read(status>=400?c.getErrorStream():c.getInputStream());c.disconnect();JSONObject j;try{j=text==null||text.trim().isEmpty()?new JSONObject():new JSONObject(text);}catch(Exception e){throw new ApiException(status,"SchoolOS returned an invalid response.");}
  String csrf=j.optString("csrf","");JSONObject s=j.optJSONObject("session");if(csrf.isEmpty()&&s!=null)csrf=s.optString("csrf","");if(!csrf.isEmpty())store.csrf(csrf);if(s!=null)store.session(s);
  if(status>=400||!j.optBoolean("ok",status<400)){if(status==401)store.clear();throw new ApiException(status,j.optString("error","SchoolOS request failed."));}return j;
 }
 private void capture(Map<String,List<String>> h){if(h==null)return;for(Map.Entry<String,List<String>> e:h.entrySet()){if(e.getKey()==null||!"set-cookie".equalsIgnoreCase(e.getKey()))continue;for(String raw:e.getValue()){if(raw==null)continue;String pair=raw.split(";",2)[0].trim();if(pair.startsWith("schoolos_session=")){String v=pair.substring("schoolos_session=".length());if(v.isEmpty())store.clear();else store.cookie(pair);}}}}
 private static String read(InputStream in)throws Exception{if(in==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
 public static final class ApiException extends Exception{public final int status;public ApiException(int s,String m){super(m);status=s;}}
}