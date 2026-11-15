package com.mipad.geckobrowser;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.mozilla.geckoview.WebResponse;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadHelper {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static void downloadWebResponse(final Context context, final WebResponse response) {
        if (response == null) return;
        final Context appCtx = context.getApplicationContext();
        final String fileName = guessFileName(response.uri, response.headers);

        if (response.body == null) {
            downloadDirectUrl(appCtx, response.uri, fileName);
            return;
        }

        toast(appCtx, "开始下载：" + fileName);
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                File outFile = prepareUniqueFile(fileName);
                try {
                    InputStream in = new BufferedInputStream(response.body);
                    FileOutputStream out = new FileOutputStream(outFile);
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                    out.close();
                    in.close();
                    MediaScannerConnection.scanFile(appCtx, new String[]{outFile.getAbsolutePath()}, null, null);
                    toast(appCtx, "已保存至 Download/" + outFile.getName());
                } catch (final Exception e) {
                    toast(appCtx, "下载失败：" + e.getMessage());
                }
            }
        });
    }

    public static void downloadDirectUrl(final Context context, final String rawUrl, final String suggestedName) {
        if (rawUrl == null || rawUrl.isEmpty()) return;
        final Context appCtx = context.getApplicationContext();
        final String initialName = suggestedName != null && !suggestedName.isEmpty()
                ? suggestedName
                : guessFileName(rawUrl, null);

        toast(appCtx, "开始保存：" + initialName);
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(rawUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 10; Tablet; rv:115.0) Gecko/115.0 Firefox/115.0");
                    if (rawUrl.contains("pximg.net")) {
                        conn.setRequestProperty("Referer", "https://www.pixiv.net/");
                    }
                    int code = conn.getResponseCode();
                    if (code >= 400) {
                        toast(appCtx, "保存失败：HTTP " + code);
                        return;
                    }
                    String contentType = conn.getContentType();
                    String finalName = ensureExtension(initialName, contentType);
                    File outFile = prepareUniqueFile(finalName);

                    InputStream in = new BufferedInputStream(conn.getInputStream());
                    FileOutputStream out = new FileOutputStream(outFile);
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                    }
                    out.flush();
                    out.close();
                    in.close();
                    MediaScannerConnection.scanFile(appCtx, new String[]{outFile.getAbsolutePath()}, null, null);
                    toast(appCtx, "已保存至 Download/" + outFile.getName());
                } catch (Exception e) {
                    toast(appCtx, "保存失败：" + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private static File prepareUniqueFile(String rawName) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File("/sdcard/Download");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String clean = sanitizeFileName(rawName);
        File candidate = new File(dir, clean);
        if (!candidate.exists()) return candidate;

        int dot = clean.lastIndexOf('.');
        String base = dot > 0 ? clean.substring(0, dot) : clean;
        String ext = dot > 0 ? clean.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            File f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
        return new File(dir, System.currentTimeMillis() + "_" + clean);
    }

    private static String guessFileName(String uri, Map<String, String> headers) {
        String contentDisposition = null;
        String contentType = null;
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() == null) continue;
                String k = e.getKey().toLowerCase();
                if ("content-disposition".equals(k)) {
                    contentDisposition = e.getValue();
                } else if ("content-type".equals(k)) {
                    contentType = e.getValue();
                }
            }
        }
        if (contentDisposition != null) {
            String lower = contentDisposition.toLowerCase();
            int utfIdx = lower.indexOf("filename*=utf-8''");
            if (utfIdx >= 0) {
                String part = contentDisposition.substring(utfIdx + 17).split(";")[0].trim();
                try {
                    return sanitizeFileName(URLDecoder.decode(part, "UTF-8"));
                } catch (Exception ignored) {
                }
            }
            int fnIdx = lower.indexOf("filename=");
            if (fnIdx >= 0) {
                String part = contentDisposition.substring(fnIdx + 9).split(";")[0].trim();
                if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2) {
                    part = part.substring(1, part.length() - 1);
                }
                if (!part.isEmpty()) {
                    return ensureExtension(sanitizeFileName(part), contentType);
                }
            }
        }
        String name = null;
        if (uri != null) {
            try {
                String path = Uri.parse(uri).getLastPathSegment();
                if (path != null && !path.trim().isEmpty()) {
                    name = URLDecoder.decode(path, "UTF-8");
                }
            } catch (Exception ignored) {
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = "download_" + System.currentTimeMillis();
        }
        return ensureExtension(sanitizeFileName(name), contentType);
    }

    private static String ensureExtension(String name, String contentType) {
        if (name.contains(".") || contentType == null) return name;
        String mime = contentType.split(";")[0].trim().toLowerCase();
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (ext != null && !ext.isEmpty()) {
            return name + "." + ext;
        }
        if ("application/vnd.android.package-archive".equals(mime)) {
            return name + ".apk";
        }
        return name;
    }

    private static String sanitizeFileName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (s.isEmpty()) return "download_" + System.currentTimeMillis();
        if (s.length() > 120) {
            int dot = s.lastIndexOf('.');
            if (dot > 0 && s.length() - dot <= 10) {
                s = s.substring(0, 105) + s.substring(dot);
            } else {
                s = s.substring(0, 120);
            }
        }
        return s;
    }

    private static void toast(final Context ctx, final String msg) {
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
