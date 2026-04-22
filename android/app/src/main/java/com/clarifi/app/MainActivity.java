package com.clarifi.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import com.getcapacitor.BridgeActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(new BackupExportBridge(), "ClariFiAndroid");
        }
    }

    private class BackupExportBridge {
        @JavascriptInterface
        public String saveBackup(String filename, String jsonContent) {
            try {
                String safeFilename = sanitizeFilename(filename);
                String path = writeBackupFile(safeFilename, jsonContent == null ? "" : jsonContent);
                return new JSONObject()
                    .put("ok", true)
                    .put("filename", safeFilename)
                    .put("path", path)
                    .toString();
            } catch (Exception e) {
                try {
                    return new JSONObject()
                        .put("ok", false)
                        .put("error", e.getMessage() == null ? "Export failed" : e.getMessage())
                        .toString();
                } catch (Exception ignored) {
                    return "{\"ok\":false,\"error\":\"Export failed\"}";
                }
            }
        }
    }

    private String sanitizeFilename(String filename) {
        String fallback = "clarifi-backup.json";
        String cleaned = filename == null ? fallback : filename.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private String writeBackupFile(String filename, String content) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IllegalStateException("Could not create file in Downloads");
            }

            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("Could not open Downloads file");
                }
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return "Downloads/" + filename;
        }

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            throw new IllegalStateException("Downloads folder is unavailable");
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create Downloads folder");
        }
        File file = new File(dir, filename);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file.getAbsolutePath();
    }
}
