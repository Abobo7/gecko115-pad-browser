package com.mipad.geckobrowser;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.RuntimeSettings;
import org.mozilla.geckoview.WebExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BrowserApp {
    private static final String TAG = "GeckoPadRuntime";
    private static GeckoRuntime sRuntime;
    private static boolean sWebCompatReady = false;
    private static final List<Runnable> sPendingReadyCallbacks = new ArrayList<Runnable>();

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

            // 3. Disable Android FIDO2 WebAuthn backend, enable CSS :has(), & support OAuth popup window.close()
            injectInitialPref(settings, "security.webauth.webauthn_enable_android_fido2", Boolean.FALSE);
            injectInitialPref(settings, "layout.css.has-selector.enabled", Boolean.TRUE);
            injectInitialPref(settings, "dom.allow_scripts_to_close_windows", Boolean.TRUE);
            injectInitialPref(settings, "dom.ipc.processPrelaunch.enabled", Boolean.FALSE);
            try {
                Method setPc = GeckoRuntimeSettings.class.getDeclaredMethod("setProcessCount", int.class);
                setPc.setAccessible(true);
                setPc.invoke(settings, Integer.valueOf(2));
            } catch (Throwable ignored) {
            }

            // 4. Native Dual-Stack IPv6 + CDN HTTP/2 concurrency tuning (X / pbs.twimg.com / Fastly):
            //    - Preserve full native IPv6 support (network.dns.disableIPv6 = false) while v2rayNG handles
            //      dual-stack IPv4+IPv6 TUN routing at the system layer.
            //    - Disable HTTP/3 QUIC over UDP:443 (which TUN rulesets commonly block) so HTTP/2 connects immediately.
            injectInitialPref(settings, "network.dns.disableIPv6", Boolean.FALSE);
            injectInitialPref(settings, "network.connectivity-service.enabled", Boolean.FALSE);
            injectInitialPref(settings, "network.http.http3.enable", Boolean.FALSE);
            injectInitialPref(settings, "network.http.max-persistent-connections-per-server", Integer.valueOf(12));
            injectInitialPref(settings, "network.http.max-persistent-connections-per-proxy", Integer.valueOf(32));
            injectInitialPref(settings, "network.http.pacing.requests.enabled", Boolean.FALSE);

            // 5. Image cache & SurfaceCache tuning for timeline-heavy sites (stock GeckoView defaults to 1 MB!):
            injectInitialPref(settings, "image.cache.size", Integer.valueOf(33554432)); // 32 MB compressed image cache
            injectInitialPref(settings, "image.mem.surfacecache.max_size_kb", Integer.valueOf(131072)); // 128 MB decoded surface cap
            injectInitialPref(settings, "image.mem.surfacecache.min_expiration_ms", Integer.valueOf(120000)); // retain 120s across scrolls
            injectInitialPref(settings, "image.mem.decode_bytes_at_a_time", Integer.valueOf(65536));
            injectInitialPref(settings, "browser.cache.memory.enable", Boolean.TRUE);
            injectInitialPref(settings, "browser.cache.memory.capacity", Integer.valueOf(32768)); // 32 MB HTTP memory cache
            injectInitialPref(settings, "browser.cache.memory.max_entry_size", Integer.valueOf(8192)); // 8 MB max single asset
            injectInitialPref(settings, "browser.cache.disk.enable", Boolean.TRUE);
            injectInitialPref(settings, "browser.cache.disk_cache_ssl", Boolean.TRUE);
            injectInitialPref(settings, "browser.cache.disk.capacity", Integer.valueOf(102400)); // 100 MB persistent HTTPS disk cache

            // 6. Silence background telemetry, add-on checks, and RemoteSettings sync spikes on 2GB RAM
            injectInitialPref(settings, "datareporting.policy.dataSubmissionEnabled", Boolean.FALSE);
            injectInitialPref(settings, "extensions.systemAddon.update.enabled", Boolean.FALSE);
            injectInitialPref(settings, "browser.safebrowsing.malware.enabled", Boolean.FALSE);
            injectInitialPref(settings, "browser.safebrowsing.phishing.enabled", Boolean.FALSE);
            injectInitialPref(settings, "services.settings.server", "");
            injectInitialPref(settings, "services.settings.loglevel", "off");
            injectInitialPref(settings, "services.settings.poll_interval", Integer.valueOf(31536000));

            sRuntime = GeckoRuntime.create(appCtx, settings);
            installBuiltInWebCompat(sRuntime);
        }
        return sRuntime;
    }

    private static void installBuiltInWebCompat(final GeckoRuntime runtime) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        // Safety fallback so UI tab init never blocks > 700ms even on first extension install
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                markWebCompatReady();
            }
        }, 700L);

        runtime.getWebExtensionController()
                .ensureBuiltIn("resource://android/assets/webcompat/", "webcompat@mipad.geckobrowser")
                .accept(new GeckoResult.Consumer<WebExtension>() {
                    @Override
                    public void accept(WebExtension ext) {
                        if (ext != null && ext.metaData != null && !ext.metaData.allowedInPrivateBrowsing) {
                            runtime.getWebExtensionController()
                                    .setAllowedInPrivateBrowsing(ext, true)
                                    .accept(new GeckoResult.Consumer<WebExtension>() {
                                        @Override
                                        public void accept(WebExtension updated) {
                                            Log.i(TAG, "Built-in WebCompat extension ready (PB enabled)");
                                            markWebCompatReady();
                                        }
                                    }, new GeckoResult.Consumer<Throwable>() {
                                        @Override
                                        public void accept(Throwable e) {
                                            markWebCompatReady();
                                        }
                                    });
                        } else {
                            Log.i(TAG, "Built-in WebCompat polyfill extension active");
                            markWebCompatReady();
                        }
                    }
                }, new GeckoResult.Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable err) {
                        Log.w(TAG, "Failed to install built-in WebCompat extension", err);
                        markWebCompatReady();
                    }
                });
    }

    private static void markWebCompatReady() {
        final List<Runnable> toRun;
        synchronized (BrowserApp.class) {
            if (sWebCompatReady) return;
            sWebCompatReady = true;
            toRun = new ArrayList<Runnable>(sPendingReadyCallbacks);
            sPendingReadyCallbacks.clear();
        }
        Handler mainHandler = new Handler(Looper.getMainLooper());
        for (final Runnable r : toRun) {
            mainHandler.post(r);
        }
    }

    public static void runWhenWebCompatReady(Runnable action) {
        synchronized (BrowserApp.class) {
            if (sWebCompatReady) {
                new Handler(Looper.getMainLooper()).post(action);
                return;
            }
            sPendingReadyCallbacks.add(action);
        }
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
