package com.mipad.geckobrowser;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.RuntimeSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BrowserApp {
    private static final String TAG = "GeckoPadRuntime";
    private static GeckoRuntime sRuntime;

    private BrowserApp() {}

    public static synchronized GeckoRuntime getRuntime(Context context) {
        if (sRuntime == null) {
            Context appCtx = context.getApplicationContext();
            BrowserStore store = new BrowserStore(appCtx);

            int antiTracking = store.isTrackingProtectionEnabled()
                    ? ContentBlocking.AntiTracking.DEFAULT
                    : ContentBlocking.AntiTracking.NONE;

            ContentBlocking.Settings cbSettings = new ContentBlocking.Settings.Builder()
                    .antiTracking(antiTracking)
                    .build();

            int colorScheme = store.isDarkMode()
                    ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
                    : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;

            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .webFontsEnabled(true)
                    .aboutConfigEnabled(true)
                    .consoleOutput(false)
                    .doubleTapZoomingEnabled(true)
                    .inputAutoZoomEnabled(true)
                    .forceUserScalableEnabled(true)
                    .enterpriseRootsEnabled(true)
                    .locales(new String[]{"zh-CN", "zh", "en-US", "en"})
                    .preferredColorScheme(colorScheme)
                    .contentBlocking(cbSettings)
                    .build();

            // Critical Android 4.4 KitKat compositor & zero-bloat fixes:
            // 1. Disable out-of-process :gpu service (Android 4.4 Surface IPC fails across processes & wastes ~55MB RAM)
            injectInitialPref(settings, "layers.gpu-process.enabled", Boolean.FALSE);
            injectInitialPref(settings, "gfx.webrender.fallback.software.requires-gpu-process", Boolean.FALSE);
            injectInitialPref(settings, "gfx.webrender.software.opengl", Boolean.FALSE);

            // 2. Gecko 115 blocklists HW WebRender on Android < 5.0 (FEATURE_BLOCKED_OS_VERSION).
            // Default to verified NEON SWGL direct ANativeWindow_lock compositor on KitKat, with optional Tegra K1 HW override.
            boolean isEmulator = Build.FINGERPRINT != null
                    && (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"));
            if (!isEmulator && store.isForceHardwareWebRender()) {
                injectInitialPref(settings, "gfx.webrender.all", Boolean.TRUE);
                injectInitialPref(settings, "gfx.webrender.software", Boolean.FALSE);
            } else {
                injectInitialPref(settings, "gfx.webrender.software", Boolean.TRUE);
            }

            // 3. Silence background telemetry, add-on checks, and RemoteSettings sync spikes on 2GB RAM
            injectInitialPref(settings, "datareporting.policy.dataSubmissionEnabled", Boolean.FALSE);
            injectInitialPref(settings, "extensions.systemAddon.update.enabled", Boolean.FALSE);
            injectInitialPref(settings, "browser.safebrowsing.malware.enabled", Boolean.FALSE);
            injectInitialPref(settings, "browser.safebrowsing.phishing.enabled", Boolean.FALSE);
            injectInitialPref(settings, "services.settings.server", "");
            injectInitialPref(settings, "services.settings.loglevel", "off");
            injectInitialPref(settings, "services.settings.poll_interval", Integer.valueOf(31536000));

            sRuntime = GeckoRuntime.create(appCtx, settings);
        }
        return sRuntime;
    }

    private static void injectInitialPref(GeckoRuntimeSettings settings, String name, Object value) {
        try {
            Class<?> prefCls = Class.forName("org.mozilla.geckoview.RuntimeSettings$Pref");
            Constructor<?> ctor = prefCls.getDeclaredConstructor(
                    RuntimeSettings.class, String.class, Object.class);
            ctor.setAccessible(true);
            Object pref = ctor.newInstance(settings, name, value);
            Method setMethod = prefCls.getDeclaredMethod("set", Object.class);
            setMethod.setAccessible(true);
            setMethod.invoke(pref, value);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to inject initial pref " + name, t);
        }
    }
}
