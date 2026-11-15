package com.mipad.geckobrowser;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.lang.reflect.Field;

import dalvik.system.PathClassLoader;

/**
 * Minimal isolated bootstrap Application that references ONLY Android bootclasspath types.
 * Installs a slash-normalizing PathClassLoader into LoadedApk.mClassLoader before any
 * Activity, Service (:gpu, :tabN), or GeckoView class is loaded, avoiding Dalvik
 * CLASS_ISPREVERIFIED cross-loader conflicts while fixing JNI '/' loadClass calls on KitKat.
 */
public final class BootstrapApp extends Application {
    private static final class SlashCompatLoader extends PathClassLoader {
        SlashCompatLoader(String dexPath, String libraryPath, ClassLoader parent) {
            super(dexPath, libraryPath, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name != null && name.indexOf('/') >= 0) {
                name = name.replace('/', '.');
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name != null && name.indexOf('/') >= 0) {
                name = name.replace('/', '.');
            }
            return super.findClass(name);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        installCompatClassLoader(base);
        super.attachBaseContext(base);
    }

    private static void installCompatClassLoader(Context base) {
        try {
            ClassLoader orig = base.getClassLoader();
            ApplicationInfo ai = base.getApplicationInfo();
            ClassLoader compat = new SlashCompatLoader(
                    ai.sourceDir,
                    ai.nativeLibraryDir,
                    orig.getParent());

            Field pkgField = findField(base.getClass(), "mPackageInfo");
            if (pkgField != null) {
                Object loadedApk = pkgField.get(base);
                if (loadedApk != null) {
                    Field clField = findField(loadedApk.getClass(), "mClassLoader");
                    if (clField != null) {
                        clField.set(loadedApk, compat);
                    }
                }
            }
            Thread.currentThread().setContextClassLoader(compat);
        } catch (Throwable t) {
            Log.w("GeckoBootstrap", "Failed to install SlashCompatLoader", t);
        }
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> cur = cls;
        while (cur != null && cur != Object.class) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
