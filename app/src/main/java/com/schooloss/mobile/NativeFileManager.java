package com.schooloss.mobile;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NativeFileManager {
    private static final Set<String> HOSTS = new HashSet<>();
    static {
        HOSTS.add("alkeynesprjects.com");
        HOSTS.add("www.alkeynesprjects.com");
        HOSTS.add("schooloss.com");
        HOSTS.add("www.schooloss.com");
    }

    private NativeFileManager() {}

    public static void perform(Activity activity, String action, String rawUrl, String suggestedName, String mime,
                               Map<String,String> nativeHeaders, String userAgent) {
        if (activity == null || rawUrl == null) return;
        final String finalAction = action == null ? "open" : action.toLowerCase(Locale.ROOT);
        new Thread(() -> {
            try {
                URL url = new URL(rawUrl);
                validate(url);
                Downloaded downloaded = fetch(activity, url, suggestedName, mime, nativeHeaders, userAgent);
                activity.runOnUiThread(() -> finish(activity, finalAction, downloaded.file, downloaded.mime));
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "File could not be opened securely.", Toast.LENGTH_LONG).show());
            }
        }, "schoolos-native-file").start();
    }

    private static Downloaded fetch(Activity activity, URL start, String suggestedName, String requestedMime,
                                    Map<String,String> nativeHeaders, String userAgent) throws Exception {
        URL current = start;
        HttpURLConnection conn = null;
        for (int redirect=0; redirect<5; redirect++) {
            validate(current);
            conn = (HttpURLConnection) current.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(45000);
            conn.setRequestProperty("Accept", "*/*");
            if (userAgent != null && !userAgent.trim().isEmpty()) conn.setRequestProperty("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(current.toString());
            if (cookie != null && !cookie.trim().isEmpty()) conn.setRequestProperty("Cookie", cookie);
            if (nativeHeaders != null) {
                for (Map.Entry<String,String> e : nativeHeaders.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.trim().isEmpty()) throw new IllegalStateException("redirect");
                current = new URL(current, location);
                continue;
            }
            if (code < 200 || code >= 300) throw new IllegalStateException("http " + code);
            break;
        }
        if (conn == null) throw new IllegalStateException("connection");

        String contentType = conn.getContentType();
        if (contentType != null) {
            int semicolon = contentType.indexOf(';');
            if (semicolon > 0) contentType = contentType.substring(0, semicolon);
        }
        String resolvedMime = contentType == null || contentType.trim().isEmpty() ? requestedMime : contentType.trim();
        String disposition = conn.getHeaderField("Content-Disposition");
        String name = resolveName(current.toString(), disposition, resolvedMime, suggestedName);
        File dir = new File(activity.getFilesDir(), "native-files");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("dir");
        File out = new File(dir, sanitize(name));
        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(out, false)) {
            byte[] buffer = new byte[32768];
            int n; long total = 0;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > 80L * 1024L * 1024L) throw new IllegalStateException("file too large");
                fos.write(buffer, 0, n);
            }
        } finally {
            conn.disconnect();
        }
        if (resolvedMime == null || resolvedMime.trim().isEmpty() || "application/octet-stream".equals(resolvedMime)) {
            String guessed = URLConnection.guessContentTypeFromName(out.getName());
            if (guessed != null) resolvedMime = guessed;
        }
        if (resolvedMime == null || resolvedMime.trim().isEmpty()) resolvedMime = "application/octet-stream";
        return new Downloaded(out, resolvedMime);
    }

    private static void finish(Activity activity, String action, File file, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file);
            if ("share".equals(action)) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType(mime);
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(share, "Share SchoolOS file"));
                return;
            }
            if ("print".equals(action) && ("application/pdf".equalsIgnoreCase(mime) || file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf"))) {
                PrintManager pm = (PrintManager) activity.getSystemService(Activity.PRINT_SERVICE);
                if (pm != null) pm.print(file.getName(), new PdfAdapter(file), new PrintAttributes.Builder().build());
                return;
            }
            Intent open = new Intent(Intent.ACTION_VIEW);
            open.setDataAndType(uri, mime);
            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { activity.startActivity(open); }
            catch (Exception e) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType(mime); share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(Intent.createChooser(share, "Open or share SchoolOS file"));
            }
        } catch (Exception e) {
            Toast.makeText(activity, "No compatible app is available for this file.", Toast.LENGTH_LONG).show();
        }
    }

    private static void validate(URL url) {
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("https only");
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (!HOSTS.contains(host)) throw new IllegalArgumentException("external host");
    }

    private static String resolveName(String url, String disposition, String mime, String suggested) {
        String name = suggested == null ? "" : suggested.trim();
        if (name.isEmpty() || name.length() > 180 || name.contains("/") || name.contains("\\")) {
            name = URLUtil.guessFileName(url, disposition, mime);
        }
        if (name == null || name.trim().isEmpty()) name = "SchoolOS-file";
        return name;
    }

    private static String sanitize(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._() -]", "_").trim();
        if (safe.isEmpty()) safe = "SchoolOS-file";
        if (safe.length() > 180) safe = safe.substring(safe.length() - 180);
        return safe;
    }

    private static final class Downloaded {
        final File file; final String mime;
        Downloaded(File file, String mime) { this.file = file; this.mime = mime; }
    }

    private static final class PdfAdapter extends PrintDocumentAdapter {
        private final File file;
        PdfAdapter(File file) { this.file = file; }

        @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                                       CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
            if (cancellationSignal.isCanceled()) { callback.onLayoutCancelled(); return; }
            callback.onLayoutFinished(new PrintDocumentInfo.Builder(file.getName())
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), false);
        }

        @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                                      CancellationSignal cancellationSignal, WriteResultCallback callback) {
            try (FileInputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = in.read(buffer)) > 0) {
                    if (cancellationSignal.isCanceled()) { callback.onWriteCancelled(); return; }
                    out.write(buffer, 0, n);
                }
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (Exception e) {
                callback.onWriteFailed("Unable to print this PDF.");
            }
        }
    }
}
