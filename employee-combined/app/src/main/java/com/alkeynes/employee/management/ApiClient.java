package com.alkeynes.employee.management;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ApiClient {
    public static final String ROOT = "https://alkeynesprjects.com/employee-management";
    public static final String NATIVE_API = ROOT + "/api/native-v1.php";
    public static final String GEO_API = ROOT + "/api/mobile-geo-ping.php";
    private final SecureStore store;

    public ApiClient(SecureStore store){ this.store=store; }

    public JSONObject get(String action) throws Exception { return request("GET", action, null, null); }
    public JSONObject get(String action, Map<String,String> query) throws Exception { return request("GET", action, query, null); }
    public JSONObject post(String action, JSONObject body) throws Exception { return request("POST", action, null, body); }

    public JSONObject login(String email,String password,String deviceId,String deviceLabel) throws Exception {
        JSONObject b=new JSONObject(); b.put("email",email); b.put("password",password); b.put("device_id",deviceId); b.put("device_label",deviceLabel);
        return requestRaw("POST", NATIVE_API+"?action=login", b, null);
    }

    private JSONObject request(String method,String action,Map<String,String> query,JSONObject body) throws Exception {
        StringBuilder url=new StringBuilder(NATIVE_API).append("?action=").append(URLEncoder.encode(action,"UTF-8"));
        if(query!=null) for(Map.Entry<String,String> e:query.entrySet()) if(e.getValue()!=null) url.append("&").append(URLEncoder.encode(e.getKey(),"UTF-8")).append("=").append(URLEncoder.encode(e.getValue(),"UTF-8"));
        return requestRaw(method,url.toString(),body,store.get("access_token"));
    }

    public static JSONObject postBearer(String url,JSONObject body,String token) throws Exception {
        return requestRaw("POST",url,body,token);
    }

    private static JSONObject requestRaw(String method,String urlString,JSONObject body,String bearer) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(urlString).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","EmployeeManagementNative/3.1 Android");
        if(bearer!=null&&!bearer.isEmpty()) c.setRequestProperty("Authorization","Bearer "+bearer);
        if(body!=null){
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code=c.getResponseCode();
        InputStream is=(code>=200&&code<400)?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();
        if(is!=null) try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){ String line; while((line=br.readLine())!=null) sb.append(line); }
        JSONObject out;
        try{ out=new JSONObject(sb.length()==0?"{}":sb.toString()); }catch(Exception ex){ out=new JSONObject(); out.put("ok",false); out.put("message","Unexpected server response."); }
        out.put("http_status",code); c.disconnect();
        if(code==401) throw new ApiException(out.optString("message","Session expired."),code,"unauthorized",out);
        if(code>=400) throw new ApiException(out.optString("message","Request failed."),code,out.optString("code",""),out);
        return out;
    }

    public static final class ApiException extends Exception {
        public final int status; public final String code; public final JSONObject payload;
        public ApiException(String message,int status,String code,JSONObject payload){ super(message);this.status=status;this.code=code;this.payload=payload; }
    }
}
