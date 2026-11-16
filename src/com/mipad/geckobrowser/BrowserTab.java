package com.mipad.geckobrowser;

import org.mozilla.geckoview.GeckoSession;

public class BrowserTab {
    private static int sNextId = 1;

    public final int id;
    public final boolean isPrivate;
    public String title;
    public String url;
    public boolean isHomePage;
    public boolean isLoading;
    public int progress;
    public boolean isSecure;
    public boolean canGoBack;
    public boolean canGoForward;
    public boolean desktopMode;
    public boolean killedInBackground;
    public boolean lazyPendingLoad;
    public boolean isPopup;
    public BrowserTab openerTab;
    public long lastCrashAtMs;
    public int crashStreak;
    public GeckoSession session;
    public GeckoSession.SessionState savedState;

    public BrowserTab(boolean isPrivate, boolean desktopMode, String initialUrl) {
        this.id = sNextId++;
        this.isPrivate = isPrivate;
        this.desktopMode = desktopMode;
        if (initialUrl == null || initialUrl.trim().isEmpty() || "about:home".equalsIgnoreCase(initialUrl.trim())) {
            this.isHomePage = true;
            this.url = "about:home";
            this.title = isPrivate ? "无痕起始页" : "起始页";
        } else {
            this.isHomePage = false;
            this.url = initialUrl.trim();
            this.title = this.url;
        }
    }

    public String getDisplayTitle() {
        if (isHomePage) {
            return isPrivate ? "无痕起始页" : "起始页";
        }
        if (title != null && !title.trim().isEmpty()) {
            return title.trim();
        }
        if (url != null && !url.isEmpty()) {
            return url;
        }
        return "新标签页";
    }
}
