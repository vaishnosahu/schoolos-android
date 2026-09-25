package com.schooloss.nativeapp.core;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.schooloss.nativeapp.ApiClient;

import java.io.File;
import java.util.concurrent.ExecutorService;

public final class FileBridge {
    private FileBridge() {}

    public static void downloadAndOpen(
            SchoolOSActivity activity,
            ApiClient api,
            ExecutorService io,
            String action,
            String query,
            String openingLabel) {
        Toast.makeText(activity,openingLabel+"…",Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) dir = activity.getCacheDir();
                ApiClient.DownloadResult result = api.download(action,query,dir);
                activity.runOnUiThread(() -> open(activity,result));
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,e.getMessage()==null?"File could not be opened.":e.getMessage(),Toast.LENGTH_LONG).show());
            }
        });
    }

    public static void open(SchoolOSActivity activity, ApiClient.DownloadResult result) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,activity.getPackageName()+".files",result.file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,result.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent,"Open "+result.filename));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity,"File downloaded, but no compatible viewer is installed.",Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity,"Downloaded file could not be opened.",Toast.LENGTH_LONG).show();
        }
    }

    public static void openExternal(SchoolOSActivity activity,String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(activity,"No app is available to open this link.",Toast.LENGTH_SHORT).show();
        }
    }
}
