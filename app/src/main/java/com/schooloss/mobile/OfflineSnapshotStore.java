package com.schooloss.mobile;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class OfflineSnapshotStore {
    private static final String KEY_ALIAS = "schoolos_offline_v1";
    private static final int MAX_HTML_BYTES = 1572864;
    private static final Set<String> SAFE_MOBILE = new HashSet<>(Arrays.asList(
            "", "index.php", "home.php", "profile.php", "updates.php", "announcement.php",
            "schedule.php", "attendance-history.php", "learn.php", "learning-materials.php",
            "gallery.php", "gallery-album.php", "transport.php", "my-child.php", "student.php",
            "classes.php", "teaching.php", "inbox.php", "more.php"
    ));
    private static final Set<String> SAFE_SFH = new HashSet<>(Arrays.asList(
            "", "index.php", "student-home.php", "parent-home.php", "student-desktop-home.php",
            "parent-desktop-home.php", "lessons.php", "lesson-view.php", "materials.php",
            "assignment-view.php", "tests.php", "test-view.php", "live-classes.php",
            "live-class-view.php", "notifications.php", "participation.php"
    ));

    private OfflineSnapshotStore() {}

    public static boolean isSafeUrl(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        try {
            URI u = URI.create(raw);
            if (!"https".equalsIgnoreCase(u.getScheme())) return false;
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (!host.equals("alkeynesprjects.com") && !host.equals("www.alkeynesprjects.com")
                    && !host.equals("schooloss.com") && !host.equals("www.schooloss.com")) return false;
            String path = u.getPath() == null ? "" : u.getPath();
            if (path.startsWith("/schools/mobile/")) {
                String page = path.substring("/schools/mobile/".length());
                return SAFE_MOBILE.contains(page);
            }
            if (path.startsWith("/schools/study-from-home/")) {
                String page = path.substring("/schools/study-from-home/".length());
                return SAFE_SFH.contains(page);
            }
            return false;
        } catch (Exception ignored) { return false; }
    }

    public static void save(Context context, String url, String html) {
        if (context == null || !isSafeUrl(url) || html == null) return;
        byte[] plain = html.getBytes(StandardCharsets.UTF_8);
        if (plain.length < 64 || plain.length > MAX_HTML_BYTES) return;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(plain);
            File dir = directory(context); if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, hash(url) + ".bin");
            try (FileOutputStream fos = new FileOutputStream(out, false)) {
                fos.write(iv.length);
                fos.write(iv);
                fos.write(encrypted);
            }
            prune(dir, 20);
        } catch (Exception ignored) {}
    }

    public static String read(Context context, String url) {
        if (context == null || !isSafeUrl(url)) return null;
        File file = new File(directory(context), hash(url) + ".bin");
        if (!file.isFile() || file.length() < 32 || file.length() > MAX_HTML_BYTES + 256L) return null;
        try (FileInputStream fis = new FileInputStream(file)) {
            int ivLen = fis.read();
            if (ivLen < 12 || ivLen > 16) return null;
            byte[] iv = new byte[ivLen];
            if (fis.read(iv) != ivLen) return null;
            byte[] encrypted = new byte[(int)(file.length() - 1 - ivLen)];
            int off = 0, n;
            while (off < encrypted.length && (n = fis.read(encrypted, off, encrypted.length - off)) > 0) off += n;
            if (off != encrypted.length) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) { return null; }
    }

    public static void clearAll(Context context) {
        if (context == null) return;
        File dir = directory(context);
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) if (f.isFile()) f.delete();
    }

    private static File directory(Context c) { return new File(c.getFilesDir(), "offline-snapshots"); }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        java.security.Key existing = ks.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : digest) b.append(String.format(Locale.US, "%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        }
    }

    private static void prune(File dir, int keep) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= keep) return;
        Arrays.sort(files, (a,b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i=0; i<files.length-keep; i++) files[i].delete();
    }
}
