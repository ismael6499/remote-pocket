package com.remotepocket.network;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.remotepocket.R;
import com.remotepocket.model.Device;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private final Context context;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public UpdateManager(Context context) {
        this.context = context;
    }

    public void checkForUpdates(Device device, boolean silentIfUpToDate) {
        if (device == null) return;
        String versionUrl = device.getBaseHttpUrl() + "/api/version";

        executor.execute(() -> {
            try {
                Request request = new Request.Builder().url(versionUrl).build();
                Response response = httpClient.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject obj = gson.fromJson(json, JsonObject.class);

                    int remoteVersionCode = obj.has("versionCode") ? obj.get("versionCode").getAsInt() : 1;
                    String remoteVersionName = obj.has("versionName") ? obj.get("versionName").getAsString() : "1.0.0";
                    boolean hasFile = obj.has("hasUpdateFile") && obj.get("hasUpdateFile").getAsBoolean();
                    String apkPath = obj.has("apkUrl") && !obj.get("apkUrl").isJsonNull() ? obj.get("apkUrl").getAsString() : null;

                    int currentVersionCode = 1;
                    try {
                        currentVersionCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
                    } catch (Exception ignored) {}

                    if (remoteVersionCode > currentVersionCode && hasFile && apkPath != null) {
                        String fullDownloadUrl = device.getBaseHttpUrl() + (apkPath.startsWith("/") ? apkPath : "/" + apkPath);
                        mainHandler.post(() -> showUpdateDialog(remoteVersionName, fullDownloadUrl));
                    } else if (!silentIfUpToDate) {
                        mainHandler.post(() -> Toast.makeText(context, "App is up to date", Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
            }
        });
    }

    private void showUpdateDialog(String versionName, String downloadUrl) {
        String msg = context.getString(R.string.update_available_msg, versionName);
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_available_title)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_update_now, (dialog, which) -> downloadAndInstallApk(downloadUrl))
                .setNegativeButton(R.string.btn_update_later, null)
                .show();
    }

    public void downloadAndInstallApk(String downloadUrl) {
        Toast.makeText(context, R.string.toast_downloading_update, Toast.LENGTH_LONG).show();

        executor.execute(() -> {
            try {
                Request request = new Request.Builder().url(downloadUrl).build();
                Response response = httpClient.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new Exception("HTTP download failed: " + response.code());
                }

                File cacheDir = context.getExternalCacheDir() != null ? context.getExternalCacheDir() : context.getCacheDir();
                File apkFile = new File(cacheDir, "remote_pocket_update.apk");
                if (apkFile.exists()) apkFile.delete();

                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                    fos.flush();
                }

                mainHandler.post(() -> {
                    Toast.makeText(context, R.string.toast_update_downloaded, Toast.LENGTH_SHORT).show();
                    installApk(apkFile);
                });
            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                mainHandler.post(() -> Toast.makeText(context, "Failed to download update", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void installApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Install intent failed", e);
            Toast.makeText(context, "Unable to launch installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
