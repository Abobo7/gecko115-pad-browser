package com.mipad.geckobrowser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class BrowserStore {
    private static final String PREF_NAME = "gecko_pad_prefs";
    private static final String KEY_BOOKMARKS = "bookmarks_v1";
    private static final String KEY_HISTORY = "history_v1";
    private static final String KEY_TABS = "saved_tabs_v1";
    private static final String KEY_ACTIVE_TAB = "active_tab_idx_v1";
    private static final String KEY_SEARCH_ENGINE = "search_engine";
    private static final String KEY_DEFAULT_DESKTOP = "default_desktop_ua";
    private static final String KEY_TRACKING_PROT = "tracking_protection";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_TEXTURE_BACKEND = "texture_backend";
    private static final String KEY_FORCE_HW_WR = "force_hw_webrender";
    private static final String KEY_SEEDED = "bookmarks_seeded";

    public static final int ENGINE_GOOGLE = 0;
    public static final int ENGINE_BING = 1;
    public static final int ENGINE_BAIDU = 2;
    public static final int ENGINE_DUCKDUCKGO = 3;

    public static class LinkItem {
        public final String title;
        public final String url;
        public final long timestamp;

        public LinkItem(String title, String url, long timestamp) {
            this.title = title != null && !title.trim().isEmpty() ? title.trim() : url;
            this.url = url;
            this.timestamp = timestamp;
        }
    }

    private final SharedPreferences prefs;

    public BrowserStore(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        ensureSeeded();
    }

    private void ensureSeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            return;
        }
        JSONArray arr = new JSONArray();
        seedItem(arr, "Google 搜索", "https://www.google.com");
        seedItem(arr, "GitHub", "https://github.com");
        seedItem(arr, "哔哩哔哩", "https://www.bilibili.com");
        seedItem(arr, "Pixiv", "https://www.pixiv.net");
        seedItem(arr, "知乎", "https://www.zhihu.com");
        seedItem(arr, "V2EX", "https://www.v2ex.com");
        prefs.edit()
                .putString(KEY_BOOKMARKS, arr.toString())
                .putBoolean(KEY_SEEDED, true)
                .apply();
    }

    private void seedItem(JSONArray arr, String title, String url) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("url", url);
            obj.put("ts", System.currentTimeMillis());
            arr.put(obj);
        } catch (JSONException ignored) {
        }
    }

    public int getSearchEngine() {
        return prefs.getInt(KEY_SEARCH_ENGINE, ENGINE_GOOGLE);
    }

    public void setSearchEngine(int engine) {
        prefs.edit().putInt(KEY_SEARCH_ENGINE, engine).apply();
    }

    public String getSearchEngineName() {
        switch (getSearchEngine()) {
            case ENGINE_BING:
                return "Bing 必应";
            case ENGINE_BAIDU:
                return "百度搜索";
            case ENGINE_DUCKDUCKGO:
                return "DuckDuckGo";
            default:
                return "Google 搜索";
        }
    }

    public String buildSearchOrNavigationUrl(String rawInput) {
        if (rawInput == null) return "about:home";
        String input = rawInput.trim();
        if (input.isEmpty() || "about:home".equalsIgnoreCase(input)) {
            return "about:home";
        }
        if (input.startsWith("about:")
                || input.startsWith("http://")
                || input.startsWith("https://")
                || input.startsWith("data:")
                || input.startsWith("file:")
                || input.startsWith("javascript:")) {
            return input;
        }
        // Check if input looks like a domain / IP / localhost
        if (!input.contains(" ") && (input.contains(".") || input.startsWith("localhost") || input.contains(":"))) {
            String hostCandidate = input.split("/")[0];
            if (hostCandidate.contains(".") || hostCandidate.startsWith("localhost") || hostCandidate.matches("^[0-9.:]+$")) {
                return "https://" + input;
            }
        }
        String encoded;
        try {
            encoded = URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            encoded = input;
        }
        switch (getSearchEngine()) {
            case ENGINE_BING:
                return "https://www.bing.com/search?q=" + encoded;
            case ENGINE_BAIDU:
                return "https://www.baidu.com/s?wd=" + encoded;
            case ENGINE_DUCKDUCKGO:
                return "https://duckduckgo.com/?q=" + encoded;
            default:
                return "https://www.google.com/search?q=" + encoded;
        }
    }

    public boolean isDefaultDesktopMode() {
        return prefs.getBoolean(KEY_DEFAULT_DESKTOP, false);
    }

    public void setDefaultDesktopMode(boolean desktop) {
        prefs.edit().putBoolean(KEY_DEFAULT_DESKTOP, desktop).apply();
    }

    public boolean isTrackingProtectionEnabled() {
        return prefs.getBoolean(KEY_TRACKING_PROT, true);
    }

    public void setTrackingProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TRACKING_PROT, enabled).apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false);
    }

    public void setDarkMode(boolean dark) {
        prefs.edit().putBoolean(KEY_DARK_MODE, dark).apply();
    }

    public boolean useTextureBackend() {
        return prefs.getBoolean(KEY_TEXTURE_BACKEND, false);
    }

    public void setUseTextureBackend(boolean texture) {
        prefs.edit().putBoolean(KEY_TEXTURE_BACKEND, texture).apply();
    }

    public boolean isForceHardwareWebRender() {
        // Verified on real Xiaomi Mi Pad 1 (NVIDIA Tegra K1, OpenGL ES 3.1 334.00):
        // Compositing: WebRender + WebGL 1/2 hardware acceleration works natively!
        return prefs.getBoolean(KEY_FORCE_HW_WR, true);
    }

    public void setForceHardwareWebRender(boolean hw) {
        prefs.edit().putBoolean(KEY_FORCE_HW_WR, hw).commit();
    }

    public List<LinkItem> getBookmarks() {
        return readList(KEY_BOOKMARKS);
    }

    public boolean isBookmarked(String url) {
        if (url == null || url.isEmpty() || "about:home".equals(url)) return false;
        for (LinkItem item : getBookmarks()) {
            if (url.equals(item.url)) return true;
        }
        return false;
    }

    public boolean toggleBookmark(String title, String url) {
        if (url == null || url.isEmpty() || "about:home".equals(url)) return false;
        List<LinkItem> list = getBookmarks();
        for (int i = 0; i < list.size(); i++) {
            if (url.equals(list.get(i).url)) {
                list.remove(i);
                writeList(KEY_BOOKMARKS, list);
                return false;
            }
        }
        list.add(0, new LinkItem(title, url, System.currentTimeMillis()));
        writeList(KEY_BOOKMARKS, list);
        return true;
    }

    public void removeBookmarkAt(int index) {
        List<LinkItem> list = getBookmarks();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            writeList(KEY_BOOKMARKS, list);
        }
    }

    public List<LinkItem> getHistory() {
        return readList(KEY_HISTORY);
    }

    public void addHistory(String title, String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) {
            return;
        }
        List<LinkItem> list = getHistory();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (url.equals(list.get(i).url)) {
                list.remove(i);
            }
        }
        list.add(0, new LinkItem(title, url, System.currentTimeMillis()));
        if (list.size() > 200) {
            list = new ArrayList<LinkItem>(list.subList(0, 200));
        }
        writeList(KEY_HISTORY, list);
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }

    public void saveTabs(List<BrowserTab> tabs, int activeIndex) {
        JSONArray arr = new JSONArray();
        int savedActive = 0;
        int count = 0;
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab t = tabs.get(i);
            if (t.isPrivate) continue;
            try {
                JSONObject obj = new JSONObject();
                obj.put("title", t.title != null ? t.title : "新标签页");
                obj.put("url", t.isHomePage ? "about:home" : (t.url != null ? t.url : "about:home"));
                obj.put("desktop", t.desktopMode);
                if (i == activeIndex) {
                    savedActive = count;
                }
                arr.put(obj);
                count++;
            } catch (JSONException ignored) {
            }
        }
        prefs.edit()
                .putString(KEY_TABS, arr.toString())
                .putInt(KEY_ACTIVE_TAB, savedActive)
                .commit();
    }

    public JSONArray getSavedTabsJson() {
        String raw = prefs.getString(KEY_TABS, null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    public int getSavedActiveTabIndex() {
        return prefs.getInt(KEY_ACTIVE_TAB, 0);
    }

    private List<LinkItem> readList(String key) {
        List<LinkItem> out = new ArrayList<LinkItem>();
        String raw = prefs.getString(key, null);
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    String url = obj.optString("url", "");
                    if (!url.isEmpty()) {
                        out.add(new LinkItem(
                                obj.optString("title", url),
                                url,
                                obj.optLong("ts", 0L)));
                    }
                }
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private void writeList(String key, List<LinkItem> items) {
        JSONArray arr = new JSONArray();
        for (LinkItem item : items) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("title", item.title);
                obj.put("url", item.url);
                obj.put("ts", item.timestamp);
                arr.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(key, arr.toString()).apply();
    }
}
