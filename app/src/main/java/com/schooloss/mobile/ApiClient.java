package com.schooloss.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class ApiClient {
    public static final String BASE = "https://alkeynesprjects.com/schools/mobile-api/v1/index.php";
    private static final String PREFS = "schoolos_native_session";
    private final SharedPreferences prefs;

    public ApiClient(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONObject get(String action) throws Exception {
        return request("GET", action, null, null);
    }

    public JSONObject getModule(String name) throws Exception {
        return request("GET", "module", "name=" + URLEncoder.encode(name, "UTF-8"), null);
    }

    public JSONObject post(String action, JSONObject body) throws Exception {
        return request("POST", action, null, body == null ? new JSONObject() : body);
    }

    private JSONObject request(String method, String action, String extraQuery, JSONObject body) throws Exception {
        StringBuilder u = new StringBuilder(BASE).append("?action=").append(URLEncoder.encode(action, "UTF-8"));
        if (extraQuery != null && !extraQuery.isEmpty()) u.append('&').append(extraQuery);
        HttpURLConnection c = (HttpURLConnection) new URL(u.toString()).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("X-SchoolOS-Native", "android");
        c.setRequestProperty("User-Agent", "SchoolOSNative/4.0.0 Android");

        String cookie = prefs.getString("cookie", "");
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        String csrf = prefs.getString("csrf", "");
        if (!csrf.isEmpty() && "POST".equals(method) && !"login".equals(action)) {
            c.setRequestProperty("X-SchoolOS-CSRF", csrf);
        }

        if (body != null) {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = c.getOutputStream()) { out.write(payload); }
        }

        int status = c.getResponseCode();
        captureCookie(c.getHeaderFields());
        InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = read(stream);
        c.disconnect();

        JSONObject json;
        try { json = text == null || text.trim().isEmpty() ? new JSONObject() : new JSONObject(text); }
        catch (Exception badJson) {
            throw new ApiException(status, "SchoolOS returned an invalid response.");
        }

        String newCsrf = json.optString("csrf", "");
        if (newCsrf.isEmpty()) {
            JSONObject session = json.optJSONObject("session");
            if (session != null) newCsrf = session.optString("csrf", "");
        }
        if (!newCsrf.isEmpty()) prefs.edit().putString("csrf", newCsrf).apply();

        if (status >= 400 || !json.optBoolean("ok", status < 400)) {
            if (status == 401) clearSession();
            throw new ApiException(status, json.optString("error", "SchoolOS request failed."));
        }
        return json;
    }

    private void captureCookie(Map<String, List<String>> headers) {
        if (headers == null) return;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !"set-cookie".equalsIgnoreCase(entry.getKey())) continue;
            for (String raw : entry.getValue()) {
                if (raw == null) continue;
                String pair = raw.split(";", 2)[0].trim();
                if (pair.startsWith("schoolos_session=")) {
                    String value = pair.substring("schoolos_session=".length());
                    if (value.isEmpty()) clearSession();
                    else prefs.edit().putString("cookie", pair).apply();
                }
            }
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line);
        }
        return b.toString();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public static final class ApiException extends Exception {
        public final int status;
        public ApiException(int status, String message) { super(message); this.status = status; }
    }
}
