package com.schooloss.nativeapp;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
    private final Context context;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public JSONObject get(String action) throws Exception {
        return request("GET", action, null, null);
    }

    public JSONObject get(String action, String extraQuery) throws Exception {
        return request("GET", action, extraQuery, null);
    }

    public JSONObject getModule(String name) throws Exception {
        return request("GET", "module", "name=" + URLEncoder.encode(name, "UTF-8"), null);
    }

    public JSONObject post(String action, JSONObject body) throws Exception {
        return request("POST", action, null, body == null ? new JSONObject() : body);
    }

    public JSONObject postMultipart(String action, Map<String,String> fields, Uri fileUri, String fileField) throws Exception {
        String boundary = "----SchoolOSNative" + System.currentTimeMillis();
        HttpURLConnection c = open(action, null, "POST", "application/json");
        c.setDoOutput(true);
        c.setChunkedStreamingMode(64 * 1024);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(c.getOutputStream())) {
            for (Map.Entry<String,String> entry : fields.entrySet()) {
                writeTextPart(out, boundary, entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
            if (fileUri != null && fileField != null && !fileField.isEmpty()) {
                ContentResolver resolver = context.getContentResolver();
                String filename = displayName(resolver, fileUri);
                String mime = resolver.getType(fileUri);
                if (mime == null || mime.trim().isEmpty()) mime = "application/octet-stream";
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"" + escapeHeader(fileField) + "\"; filename=\"" + escapeHeader(filename) + "\"\r\n");
                out.writeBytes("Content-Type: " + mime + "\r\n\r\n");
                try (InputStream in = resolver.openInputStream(fileUri)) {
                    if (in == null) throw new IllegalStateException("Selected file is unavailable.");
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        if (n > 0) out.write(buffer, 0, n);
                    }
                }
                out.writeBytes("\r\n");
            }
            out.writeBytes("--" + boundary + "--\r\n");
            out.flush();
        }

        return readJsonResponse(c);
    }

    public DownloadResult download(String action, String extraQuery, File directory) throws Exception {
        HttpURLConnection c = open(action, extraQuery, "GET", "*/*");
        int status = c.getResponseCode();
        captureCookie(c.getHeaderFields());
        if (status >= 400) {
            String errorText = read(c.getErrorStream());
            c.disconnect();
            String message = "SchoolOS file request failed.";
            try {
                JSONObject json = new JSONObject(errorText);
                message = json.optString("error", message);
            } catch (Exception ignored) {
                if (errorText != null && !errorText.trim().isEmpty() && errorText.length() < 300) message = errorText.trim();
            }
            if (status == 401) clearSession();
            throw new ApiException(status, message);
        }

        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            c.disconnect();
            throw new IllegalStateException("Could not prepare app download storage.");
        }

        String mime = c.getContentType();
        if (mime == null || mime.trim().isEmpty()) mime = "application/octet-stream";
        String filename = filenameFromDisposition(c.getHeaderField("Content-Disposition"));
        if (filename == null || filename.trim().isEmpty()) filename = "schoolos-file";
        filename = safeFilename(filename);

        File target = uniqueFile(directory, filename);
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n > 0) out.write(buffer, 0, n);
            }
            out.flush();
        } finally {
            c.disconnect();
        }
        return new DownloadResult(target, mime, target.getName());
    }

    private JSONObject request(String method, String action, String extraQuery, JSONObject body) throws Exception {
        HttpURLConnection c = open(action, extraQuery, method, "application/json");

        if (body != null) {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = c.getOutputStream()) { out.write(payload); }
        }

        return readJsonResponse(c);
    }

    private HttpURLConnection open(String action, String extraQuery, String method, String accept) throws Exception {
        StringBuilder u = new StringBuilder(BASE).append("?action=").append(URLEncoder.encode(action, "UTF-8"));
        if (extraQuery != null && !extraQuery.isEmpty()) u.append('&').append(extraQuery);

        HttpURLConnection c = (HttpURLConnection) new URL(u.toString()).openConnection();
        c.setConnectTimeout(18000);
        c.setReadTimeout(60000);
        c.setUseCaches(false);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("X-SchoolOS-Native", "android");
        c.setRequestProperty("User-Agent", "SchoolOSNative/4.0.0 Android");

        String cookie = prefs.getString("cookie", "");
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);

        String csrf = prefs.getString("csrf", "");
        if (!csrf.isEmpty() && "POST".equals(method) && !"login".equals(action)) {
            c.setRequestProperty("X-SchoolOS-CSRF", csrf);
        }
        return c;
    }

    private JSONObject readJsonResponse(HttpURLConnection c) throws Exception {
        int status = c.getResponseCode();
        captureCookie(c.getHeaderFields());
        InputStream stream = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = read(stream);
        c.disconnect();

        JSONObject json;
        try {
            json = text == null || text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (Exception badJson) {
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

    private static void writeTextPart(DataOutputStream out, String boundary, String name, String value) throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + escapeHeader(name) + "\"\r\n");
        out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = cursor.getString(i);
            }
        } catch (Exception ignored) {}
        if (name == null || name.trim().isEmpty()) {
            String p = uri.getLastPathSegment();
            name = p == null || p.trim().isEmpty() ? "upload-file" : p;
        }
        return safeFilename(name);
    }

    private static String filenameFromDisposition(String raw) {
        if (raw == null) return null;
        String lower = raw.toLowerCase();
        int star = lower.indexOf("filename*=utf-8''");
        if (star >= 0) {
            String value = raw.substring(star + "filename*=UTF-8''".length()).trim();
            int semi = value.indexOf(';');
            if (semi >= 0) value = value.substring(0, semi);
            try { return java.net.URLDecoder.decode(value.replace("\"", ""), "UTF-8"); } catch (Exception ignored) {}
        }
        int idx = lower.indexOf("filename=");
        if (idx >= 0) {
            String value = raw.substring(idx + 9).trim();
            int semi = value.indexOf(';');
            if (semi >= 0) value = value.substring(0, semi);
            return value.replace("\"", "").trim();
        }
        return null;
    }

    private static String safeFilename(String name) {
        String n = name == null ? "schoolos-file" : name.replaceAll("[\\\\/:*?\\"<>|\\r\\n]+", "_").trim();
        if (n.isEmpty()) n = "schoolos-file";
        if (n.length() > 180) n = n.substring(n.length() - 180);
        return n;
    }

    private static File uniqueFile(File dir, String filename) {
        File f = new File(dir, filename);
        if (!f.exists()) return f;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String ext = dot > 0 ? filename.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            f = new File(dir, base + "-" + i + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, System.currentTimeMillis() + "-" + filename);
    }

    private static String escapeHeader(String value) {
        return value == null ? "" : value.replace("\"", "").replace("\r", "").replace("\n", "");
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

    public static final class DownloadResult {
        public final File file;
        public final String mime;
        public final String filename;
        public DownloadResult(File file, String mime, String filename) {
            this.file = file;
            this.mime = mime;
            this.filename = filename;
        }
    }

    public static final class ApiException extends Exception {
        public final int status;
        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
