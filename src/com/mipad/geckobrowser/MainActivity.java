package com.mipad.geckobrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.StorageController;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SessionDelegates.Host {
    private static final int REQ_FILE_PICKER = 1001;

    private static final int COL_TAB_STRIP_BG = 0xFF191E28;
    private static final int COL_TAB_INACTIVE = 0xFF262E3D;
    private static final int COL_TAB_PRIVATE_ACTIVE = 0xFF4C1D95;
    private static final int COL_TAB_PRIVATE_INACTIVE = 0xFF31185E;
    private static final int COL_ACCENT = 0xFF2563EB;
    private static final int COL_GOLD = 0xFFF59E0B;
    private static final int COL_SECURE_GREEN = 0xFF16A34A;

    private GeckoRuntime runtime;
    private BrowserStore store;

    private final List<BrowserTab> tabs = new ArrayList<BrowserTab>();
    private int activeTabIndex = -1;

    private LinearLayout topChromeContainer;
    private HorizontalScrollView tabScrollView;
    private LinearLayout tabStripLinear;
    private TextView tabCountBadge;

    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnReloadStop;
    private ImageView securityIcon;
    private EditText urlInput;
    private ImageButton btnClearUrl;
    private TextView btnDesktopToggle;
    private ImageButton btnBookmark;

    private FrameLayout progressTrack;
    private View progressFill;

    private GeckoView geckoView;
    private ScrollView homeScrollView;
    private LinearLayout homeRootContent;

    private LinearLayout findBar;
    private EditText findInput;
    private TextView findStatusText;

    private boolean isFullScreenVideo = false;
    private GeckoSession.PromptDelegate.FilePrompt pendingFilePrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> pendingFileResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new BrowserStore(this);
        runtime = BrowserApp.getRuntime(this);
        runtime.attachTo(this);

        buildUi();
        restoreOrInitTabs(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String incomingUrl = extractIntentUrl(intent);
        if (incomingUrl != null && !incomingUrl.isEmpty()) {
            createNewTab(incomingUrl, false, true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (store != null) {
            store.saveTabs(tabs, activeTabIndex);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (runtime != null) {
            runtime.configurationChanged(newConfig);
        }
        rebuildTabStrip();
        if (getActiveTab() != null && getActiveTab().isHomePage) {
            rebuildHomeContent();
        }
    }

    @Override
    public void onBackPressed() {
        if (isFullScreenVideo && getActiveTab() != null && getActiveTab().session != null) {
            getActiveTab().session.exitFullScreen();
            return;
        }
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            closeFindBar();
            return;
        }
        BrowserTab active = getActiveTab();
        if (active != null && !active.isHomePage && active.canGoBack && active.session != null) {
            active.session.goBack();
            return;
        }
        if (active != null && !active.isHomePage) {
            navigateToHome(active);
            return;
        }
        if (tabs.size() > 1 && activeTabIndex >= 0) {
            closeTab(activeTabIndex);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_PICKER) {
            if (pendingFilePrompt != null && pendingFileResult != null) {
                try {
                    if (resultCode == RESULT_OK && data != null) {
                        if (Build.VERSION.SDK_INT >= 16 && data.getClipData() != null) {
                            ClipData cd = data.getClipData();
                            Uri[] uris = new Uri[cd.getItemCount()];
                            for (int i = 0; i < cd.getItemCount(); i++) {
                                uris[i] = cd.getItemAt(i).getUri();
                            }
                            pendingFileResult.complete(pendingFilePrompt.confirm(this, uris));
                        } else if (data.getData() != null) {
                            pendingFileResult.complete(pendingFilePrompt.confirm(this, data.getData()));
                        } else {
                            pendingFileResult.complete(pendingFilePrompt.dismiss());
                        }
                    } else {
                        pendingFileResult.complete(pendingFilePrompt.dismiss());
                    }
                } catch (Exception e) {
                    pendingFileResult.complete(pendingFilePrompt.dismiss());
                }
            }
            pendingFilePrompt = null;
            pendingFileResult = null;
        }
    }

    // =========================================================================
    // SessionDelegates.Host Implementation
    // =========================================================================

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public BrowserStore getStore() {
        return store;
    }

    @Override
    public BrowserTab getActiveTab() {
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            return tabs.get(activeTabIndex);
        }
        return null;
    }

    @Override
    public void onTabUpdated(BrowserTab tab, boolean rebuildStrip) {
        if (rebuildStrip) rebuildTabStrip();
        if (tab == getActiveTab()) syncToolbarWithActiveTab();
    }

    @Override
    public void onProgressUpdated(BrowserTab tab) {
        if (tab == getActiveTab()) updateProgressBar(tab);
    }

    @Override
    public BrowserTab createPopupTab(String uri, boolean isPrivate, boolean desktopMode) {
        BrowserTab tab = new BrowserTab(isPrivate, desktopMode,
                (uri == null || uri.isEmpty()) ? "about:blank" : uri);
        tab.isHomePage = false;
        ensureSessionReady(tab);
        tabs.add(tab);
        switchToTab(tabs.size() - 1);
        store.saveTabs(tabs, activeTabIndex);
        return tab;
    }

    @Override
    public void closeTabObject(BrowserTab tab) {
        int idx = tabs.indexOf(tab);
        if (idx >= 0 && tabs.size() > 1) {
            closeTab(idx);
        }
    }

    @Override
    public void showContextMenu(BrowserTab tab, GeckoSession.ContentDelegate.ContextElement el) {
        showElementContextMenu(tab, el);
    }

    @Override
    public void handleTabProcessDied(final BrowserTab tab) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (tab == getActiveTab() && !tab.isHomePage) {
                    ensureSessionReady(tab);
                    geckoView.setSession(tab.session);
                    if (tab.savedState != null) {
                        tab.session.restoreState(tab.savedState);
                    } else if (tab.url != null) {
                        tab.session.loadUri(tab.url);
                    }
                } else {
                    tab.killedInBackground = true;
                }
            }
        });
    }

    @Override
    public GeckoResult<GeckoSession.PromptDelegate.PromptResponse> startFilePicker(
            GeckoSession.PromptDelegate.FilePrompt prompt) {
        if (pendingFileResult != null && pendingFilePrompt != null && !pendingFilePrompt.isComplete()) {
            pendingFileResult.complete(pendingFilePrompt.dismiss());
        }
        pendingFilePrompt = prompt;
        pendingFileResult = new GeckoResult<GeckoSession.PromptDelegate.PromptResponse>();
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            if (prompt.type == 2) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            startActivityForResult(Intent.createChooser(intent, "选择要上传的文件"), REQ_FILE_PICKER);
        } catch (Exception e) {
            GeckoResult<GeckoSession.PromptDelegate.PromptResponse> r = pendingFileResult;
            pendingFilePrompt = null;
            pendingFileResult = null;
            r.complete(prompt.dismiss());
            return r;
        }
        return pendingFileResult;
    }

    // =========================================================================
    // UI Layout & Controls
    // =========================================================================

    private void buildUi() {
        boolean dark = store.isDarkMode();
        int toolbarBg = dark ? 0xFF1F2430 : 0xFFF8FAFC;
        int toolbarBorder = dark ? 0xFF334155 : 0xFFE2E8F0;
        int urlBoxBg = dark ? 0xFF111827 : 0xFFFFFFFF;
        int urlBoxBorder = dark ? 0xFF374151 : 0xFFCBD5E1;
        int iconTint = dark ? 0xFFE2E8F0 : 0xFF334155;
        int textPrimary = dark ? 0xFFF1F5F9 : 0xFF0F172A;
        int textHint = dark ? 0xFF94A3B8 : 0xFF64748B;
        int hoverColor = dark ? 0xFF334155 : 0xFFE2E8F0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark ? 0xFF111827 : 0xFFFFFFFF);

        topChromeContainer = new LinearLayout(this);
        topChromeContainer.setOrientation(LinearLayout.VERTICAL);

        // Row 1: Tab Strip
        LinearLayout tabBarRow = new LinearLayout(this);
        tabBarRow.setOrientation(LinearLayout.HORIZONTAL);
        tabBarRow.setGravity(Gravity.CENTER_VERTICAL);
        tabBarRow.setBackgroundColor(COL_TAB_STRIP_BG);
        tabBarRow.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 5), UiKit.dp(this, 6), 0);

        tabScrollView = new HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);
        tabStripLinear = new LinearLayout(this);
        tabStripLinear.setOrientation(LinearLayout.HORIZONTAL);
        tabStripLinear.setGravity(Gravity.BOTTOM);
        tabScrollView.addView(tabStripLinear, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tabBarRow.addView(tabScrollView, new LinearLayout.LayoutParams(0, UiKit.dp(this, 35), 1f));

        ImageButton btnNewTab = makeIconBtn(UiKit.ICON_PLUS, 0xFFF8FAFC, 0xFF2D3648, 0xFF3B475E);
        btnNewTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewTab("about:home", false, true);
            }
        });
        LinearLayout.LayoutParams newTabLp = new LinearLayout.LayoutParams(UiKit.dp(this, 34), UiKit.dp(this, 30));
        newTabLp.leftMargin = UiKit.dp(this, 6);
        newTabLp.bottomMargin = UiKit.dp(this, 3);
        tabBarRow.addView(btnNewTab, newTabLp);

        TextView btnPrivateTab = new TextView(this);
        btnPrivateTab.setText("无痕");
        btnPrivateTab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnPrivateTab.setTextColor(0xFFE9D5FF);
        btnPrivateTab.setGravity(Gravity.CENTER);
        btnPrivateTab.setPadding(UiKit.dp(this, 8), 0, UiKit.dp(this, 8), 0);
        btnPrivateTab.setBackgroundDrawable(UiKit.pressableRound(0xFF3B1F6E, 0xFF5B21B6, UiKit.dp(this, 6)));
        btnPrivateTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNewTab("about:home", true, true);
                Toast.makeText(MainActivity.this, "已开启无痕标签页", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams privLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 30));
        privLp.leftMargin = UiKit.dp(this, 5);
        privLp.bottomMargin = UiKit.dp(this, 3);
        tabBarRow.addView(btnPrivateTab, privLp);

        tabCountBadge = new TextView(this);
        tabCountBadge.setText("1");
        tabCountBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tabCountBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tabCountBadge.setTextColor(0xFFF8FAFC);
        tabCountBadge.setGravity(Gravity.CENTER);
        tabCountBadge.setMinimumWidth(UiKit.dp(this, 30));
        tabCountBadge.setPadding(UiKit.dp(this, 8), 0, UiKit.dp(this, 8), 0);
        tabCountBadge.setBackgroundDrawable(UiKit.roundRect(0xFF334155, UiKit.dp(this, 6), UiKit.dp(this, 1), 0xFF64748B));
        tabCountBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabsOverviewDialog();
            }
        });
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 30));
        badgeLp.leftMargin = UiKit.dp(this, 5);
        badgeLp.bottomMargin = UiKit.dp(this, 3);
        tabBarRow.addView(tabCountBadge, badgeLp);

        topChromeContainer.addView(tabBarRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 40)));

        // Row 2: Address & Control Bar
        LinearLayout addressToolbar = new LinearLayout(this);
        addressToolbar.setOrientation(LinearLayout.HORIZONTAL);
        addressToolbar.setGravity(Gravity.CENTER_VERTICAL);
        addressToolbar.setBackgroundColor(toolbarBg);
        addressToolbar.setPadding(UiKit.dp(this, 6), UiKit.dp(this, 5), UiKit.dp(this, 6), UiKit.dp(this, 5));

        btnBack = makeIconBtn(UiKit.ICON_BACK, iconTint, Color.TRANSPARENT, hoverColor);
        btnForward = makeIconBtn(UiKit.ICON_FORWARD, iconTint, Color.TRANSPARENT, hoverColor);
        btnReloadStop = makeIconBtn(UiKit.ICON_RELOAD, iconTint, Color.TRANSPARENT, hoverColor);
        ImageButton btnHome = makeIconBtn(UiKit.ICON_HOME, iconTint, Color.TRANSPARENT, hoverColor);

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BrowserTab t = getActiveTab();
                if (t != null && !t.isHomePage && t.canGoBack && t.session != null) {
                    t.session.goBack();
                } else if (t != null && !t.isHomePage) {
                    navigateToHome(t);
                }
            }
        });
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BrowserTab t = getActiveTab();
                if (t != null && !t.isHomePage && t.canGoForward && t.session != null) {
                    t.session.goForward();
                }
            }
        });
        btnReloadStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BrowserTab t = getActiveTab();
                if (t == null) return;
                if (t.isHomePage) {
                    rebuildHomeContent();
                } else if (t.session != null) {
                    if (t.isLoading) t.session.stop();
                    else t.session.reload();
                }
            }
        });
        btnReloadStop.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                BrowserTab t = getActiveTab();
                if (t != null && !t.isHomePage && t.session != null) {
                    t.session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
                    Toast.makeText(MainActivity.this, "已强制跳过缓存刷新", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BrowserTab t = getActiveTab();
                if (t != null) navigateToHome(t);
            }
        });

        addressToolbar.addView(btnBack, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        addressToolbar.addView(btnForward, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        addressToolbar.addView(btnReloadStop, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        addressToolbar.addView(btnHome, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));

        LinearLayout urlBoxContainer = new LinearLayout(this);
        urlBoxContainer.setOrientation(LinearLayout.HORIZONTAL);
        urlBoxContainer.setGravity(Gravity.CENTER_VERTICAL);
        urlBoxContainer.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 6), 0);
        urlBoxContainer.setBackgroundDrawable(UiKit.roundRect(urlBoxBg, UiKit.dp(this, 18), UiKit.dp(this, 1), urlBoxBorder));

        securityIcon = new ImageView(this);
        securityIcon.setImageDrawable(UiKit.icon(this, UiKit.ICON_SEARCH, textHint, 16));
        urlBoxContainer.addView(securityIcon, new LinearLayout.LayoutParams(UiKit.dp(this, 18), UiKit.dp(this, 18)));

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setBackgroundColor(Color.TRANSPARENT);
        urlInput.setTextColor(textPrimary);
        urlInput.setHintTextColor(textHint);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setSelectAllOnFocus(true);
        urlInput.setPadding(UiKit.dp(this, 8), 0, UiKit.dp(this, 4), 0);

        btnClearUrl = makeIconBtn(UiKit.ICON_CLOSE, textHint, Color.TRANSPARENT, hoverColor);
        btnClearUrl.setVisibility(View.GONE);
        btnClearUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                urlInput.setText("");
                urlInput.requestFocus();
                showKeyboard(urlInput);
            }
        });
        urlInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                btnClearUrl.setVisibility(hasFocus && urlInput.getText().length() > 0 ? View.VISIBLE : View.GONE);
                if (!hasFocus) syncToolbarWithActiveTab();
            }
        });
        urlInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearUrl.setVisibility(urlInput.hasFocus() && s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        urlInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String target = store.buildSearchOrNavigationUrl(urlInput.getText().toString());
                    hideKeyboard(urlInput);
                    urlInput.clearFocus();
                    loadInActiveTab(target);
                    return true;
                }
                return false;
            }
        });

        urlBoxContainer.addView(urlInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        urlBoxContainer.addView(btnClearUrl, new LinearLayout.LayoutParams(UiKit.dp(this, 26), UiKit.dp(this, 26)));

        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(0, UiKit.dp(this, 36), 1f);
        urlLp.leftMargin = UiKit.dp(this, 4);
        urlLp.rightMargin = UiKit.dp(this, 6);
        addressToolbar.addView(urlBoxContainer, urlLp);

        btnDesktopToggle = new TextView(this);
        btnDesktopToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnDesktopToggle.setTypeface(Typeface.DEFAULT_BOLD);
        btnDesktopToggle.setGravity(Gravity.CENTER);
        btnDesktopToggle.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 10), 0);
        btnDesktopToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleActiveTabDesktopMode();
            }
        });
        LinearLayout.LayoutParams deskLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 32));
        deskLp.rightMargin = UiKit.dp(this, 4);
        addressToolbar.addView(btnDesktopToggle, deskLp);

        btnBookmark = makeIconBtn(UiKit.ICON_STAR_OUTLINE, iconTint, Color.TRANSPARENT, hoverColor);
        btnBookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BrowserTab t = getActiveTab();
                if (t == null || t.isHomePage || t.url == null) {
                    showBookmarksDialog();
                    return;
                }
                boolean added = store.toggleBookmark(t.getDisplayTitle(), t.url);
                updateBookmarkStar(t);
                Toast.makeText(MainActivity.this, added ? "已加入书签" : "已取消书签", Toast.LENGTH_SHORT).show();
            }
        });
        btnBookmark.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showBookmarksDialog();
                return true;
            }
        });

        ImageButton btnMenu = makeIconBtn(UiKit.ICON_MENU, iconTint, Color.TRANSPARENT, hoverColor);
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainMenuDialog();
            }
        });

        addressToolbar.addView(btnBookmark, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));
        addressToolbar.addView(btnMenu, new LinearLayout.LayoutParams(UiKit.dp(this, 36), UiKit.dp(this, 36)));

        topChromeContainer.addView(addressToolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));

        progressTrack = new FrameLayout(this);
        progressTrack.setBackgroundColor(toolbarBorder);
        progressFill = new View(this);
        progressFill.setBackgroundColor(COL_ACCENT);
        progressFill.setVisibility(View.GONE);
        progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));

        topChromeContainer.addView(progressTrack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 2)));

        root.addView(topChromeContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout contentFrame = new FrameLayout(this);
        geckoView = new GeckoView(this);
        if (store.useTextureBackend()) {
            geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW);
        }
        geckoView.coverUntilFirstPaint(dark ? 0xFF111827 : Color.WHITE);
        contentFrame.addView(geckoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homeScrollView = new ScrollView(this);
        homeScrollView.setFillViewport(true);
        homeScrollView.setBackgroundColor(dark ? 0xFF0F172A : 0xFFF1F5F9);
        homeRootContent = new LinearLayout(this);
        homeRootContent.setOrientation(LinearLayout.VERTICAL);
        homeRootContent.setGravity(Gravity.CENTER_HORIZONTAL);
        homeScrollView.addView(homeRootContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentFrame.addView(homeScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildFindBar(root, dark, toolbarBg, toolbarBorder, textPrimary, textHint, iconTint, hoverColor);
        setContentView(root);
        rebuildHomeContent();
    }

    private void buildFindBar(LinearLayout root, boolean dark, int toolbarBg, int borderCol,
                              int textPrimary, int textHint, int iconTint, int hoverCol) {
        findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.HORIZONTAL);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setBackgroundColor(toolbarBg);
        findBar.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 4), UiKit.dp(this, 8), UiKit.dp(this, 4));
        findBar.setVisibility(View.GONE);

        findInput = new EditText(this);
        findInput.setSingleLine(true);
        findInput.setHint("在页面中查找关键字...");
        findInput.setTextColor(textPrimary);
        findInput.setHintTextColor(textHint);
        findInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        findInput.setBackgroundDrawable(UiKit.roundRect(dark ? 0xFF111827 : 0xFFFFFFFF,
                UiKit.dp(this, 8), UiKit.dp(this, 1), borderCol));
        findInput.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 4), UiKit.dp(this, 10), UiKit.dp(this, 4));
        findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        findInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerFindInPage(false);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        findStatusText = new TextView(this);
        findStatusText.setText("0/0");
        findStatusText.setTextColor(textHint);
        findStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        findStatusText.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 6), 0);

        ImageButton btnPrev = makeIconBtn(UiKit.ICON_UP, iconTint, Color.TRANSPARENT, hoverCol);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerFindInPage(true);
            }
        });
        ImageButton btnNext = makeIconBtn(UiKit.ICON_DOWN, iconTint, Color.TRANSPARENT, hoverCol);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerFindInPage(false);
            }
        });
        ImageButton btnCloseFind = makeIconBtn(UiKit.ICON_CLOSE, iconTint, Color.TRANSPARENT, hoverCol);
        btnCloseFind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFindBar();
            }
        });

        findBar.addView(findInput, new LinearLayout.LayoutParams(0, UiKit.dp(this, 34), 1f));
        findBar.addView(findStatusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        findBar.addView(btnPrev, new LinearLayout.LayoutParams(UiKit.dp(this, 34), UiKit.dp(this, 34)));
        findBar.addView(btnNext, new LinearLayout.LayoutParams(UiKit.dp(this, 34), UiKit.dp(this, 34)));
        findBar.addView(btnCloseFind, new LinearLayout.LayoutParams(UiKit.dp(this, 34), UiKit.dp(this, 34)));

        root.addView(findBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 44)));
    }

    private void rebuildHomeContent() {
        if (homeRootContent == null) return;
        BrowserTab active = getActiveTab();
        HomePageBuilder.populate(this, homeRootContent, store,
                active != null && active.isPrivate,
                new HomePageBuilder.Listener() {
                    @Override
                    public void onOpenUrl(String url) {
                        hideKeyboard(homeRootContent);
                        loadInActiveTab(url);
                    }

                    @Override
                    public void onOpenUrlBackground(String title, String url) {
                        createNewTab(url, false, false);
                        Toast.makeText(MainActivity.this, "已在后台新标签打开 " + title, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onManageBookmarks() {
                        showBookmarksDialog();
                    }
                });
    }

    private ImageButton makeIconBtn(int iconType, int iconColor, int normalBg, int pressedBg) {
        ImageButton btn = new ImageButton(this);
        btn.setImageDrawable(UiKit.icon(this, iconType, iconColor, 18));
        btn.setScaleType(ImageView.ScaleType.CENTER);
        btn.setBackgroundDrawable(UiKit.pressableRound(normalBg, pressedBg, UiKit.dp(this, 8)));
        int pad = UiKit.dp(this, 4);
        btn.setPadding(pad, pad, pad, pad);
        return btn;
    }

    // =========================================================================
    // Tab Management & Navigation
    // =========================================================================

    private String extractIntentUrl(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            return intent.getDataString();
        }
        if (Intent.ACTION_WEB_SEARCH.equals(action)) {
            String q = intent.getStringExtra("query");
            if (q != null && !q.trim().isEmpty()) {
                return store.buildSearchOrNavigationUrl(q);
            }
        }
        return null;
    }

    private void restoreOrInitTabs(Intent launchIntent) {
        String launchUrl = extractIntentUrl(launchIntent);
        JSONArray saved = store.getSavedTabsJson();
        if (saved != null && saved.length() > 0) {
            int targetActive = store.getSavedActiveTabIndex();
            if (targetActive < 0 || targetActive >= saved.length()) targetActive = 0;
            for (int i = 0; i < saved.length(); i++) {
                JSONObject obj = saved.optJSONObject(i);
                if (obj == null) continue;
                String url = obj.optString("url", "about:home");
                String title = obj.optString("title", "起始页");
                boolean desktop = obj.optBoolean("desktop", store.isDefaultDesktopMode());
                BrowserTab tab = new BrowserTab(false, desktop, url);
                tab.title = title;
                if (!tab.isHomePage && i != targetActive && launchUrl == null) {
                    tab.lazyPendingLoad = true;
                }
                tabs.add(tab);
            }
            if (!tabs.isEmpty()) {
                if (launchUrl != null && !launchUrl.isEmpty()) {
                    createNewTab(launchUrl, false, true);
                } else {
                    switchToTab(Math.min(targetActive, tabs.size() - 1));
                }
                return;
            }
        }
        if (launchUrl != null && !launchUrl.isEmpty()) {
            createNewTab(launchUrl, false, true);
        } else {
            createNewTab("about:home", false, true);
        }
    }

    private BrowserTab createNewTab(String initialUrl, boolean isPrivate, boolean switchToIt) {
        BrowserTab tab = new BrowserTab(isPrivate, store.isDefaultDesktopMode(), initialUrl);
        tabs.add(tab);
        int idx = tabs.size() - 1;
        if (!tab.isHomePage) {
            ensureSessionReady(tab);
            tab.session.loadUri(tab.url);
            if (!switchToIt) tab.session.setActive(false);
        }
        if (switchToIt) switchToTab(idx);
        else rebuildTabStrip();
        store.saveTabs(tabs, activeTabIndex);
        return tab;
    }

    private void ensureSessionReady(BrowserTab tab) {
        if (tab.session == null) {
            GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                    .usePrivateMode(tab.isPrivate)
                    .useTrackingProtection(store.isTrackingProtectionEnabled())
                    .suspendMediaWhenInactive(true)
                    .allowJavascript(true)
                    .userAgentMode(tab.desktopMode
                            ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                            : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                    .viewportMode(tab.desktopMode
                            ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                            : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                    .displayMode(GeckoSessionSettings.DISPLAY_MODE_BROWSER)
                    .build();
            tab.session = new GeckoSession(settings);
            SessionDelegates.attach(this, tab, tab.session);
        }
        if (!tab.session.isOpen()) {
            tab.session.open(runtime);
        }
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab prev = getActiveTab();
        if (prev != null && prev != tabs.get(index) && prev.session != null && prev.session.isOpen()) {
            prev.session.setFocused(false);
            prev.session.setActive(false);
            prev.session.setPriorityHint(GeckoSession.PRIORITY_DEFAULT);
        }

        activeTabIndex = index;
        BrowserTab current = tabs.get(index);

        if (current.isHomePage) {
            geckoView.releaseSession();
            homeScrollView.setVisibility(View.VISIBLE);
            rebuildHomeContent();
        } else {
            homeScrollView.setVisibility(View.GONE);
            ensureSessionReady(current);
            if (current.killedInBackground) {
                current.killedInBackground = false;
                if (current.savedState != null) current.session.restoreState(current.savedState);
                else if (current.url != null) current.session.loadUri(current.url);
            } else if (current.lazyPendingLoad) {
                current.lazyPendingLoad = false;
                current.session.loadUri(current.url);
            }
            current.session.setActive(true);
            current.session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
            geckoView.coverUntilFirstPaint(store.isDarkMode() ? 0xFF111827 : Color.WHITE);
            geckoView.setSession(current.session);
            current.session.setFocused(true);
        }

        rebuildTabStrip();
        syncToolbarWithActiveTab();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab removed = tabs.remove(index);
        if (geckoView.getSession() == removed.session) {
            geckoView.releaseSession();
        }
        if (removed.session != null && removed.session.isOpen()) {
            removed.session.close();
        }
        if (removed.isPrivate && removed.session != null) {
            String ctxId = removed.session.getSettings().getContextId();
            if (ctxId != null) runtime.getStorageController().clearDataForSessionContext(ctxId);
        }

        if (tabs.isEmpty()) {
            activeTabIndex = -1;
            createNewTab("about:home", false, true);
            return;
        }
        if (activeTabIndex >= tabs.size()) activeTabIndex = tabs.size() - 1;
        else if (index < activeTabIndex) activeTabIndex--;
        switchToTab(activeTabIndex);
        store.saveTabs(tabs, activeTabIndex);
    }

    private void navigateToHome(BrowserTab tab) {
        tab.isHomePage = true;
        tab.url = "about:home";
        tab.title = tab.isPrivate ? "无痕起始页" : "起始页";
        tab.isLoading = false;
        tab.progress = 0;
        if (tab == getActiveTab()) {
            geckoView.releaseSession();
            homeScrollView.setVisibility(View.VISIBLE);
            rebuildHomeContent();
            rebuildTabStrip();
            syncToolbarWithActiveTab();
        }
        store.saveTabs(tabs, activeTabIndex);
    }

    @Override
    public void loadInActiveTab(String url) {
        BrowserTab tab = getActiveTab();
        if (tab == null) {
            createNewTab(url, false, true);
            return;
        }
        if ("about:home".equalsIgnoreCase(url)) {
            navigateToHome(tab);
            return;
        }
        tab.isHomePage = false;
        tab.lazyPendingLoad = false;
        tab.killedInBackground = false;
        tab.url = url;
        if (tab.title == null || tab.title.contains("起始页")) tab.title = url;
        homeScrollView.setVisibility(View.GONE);
        ensureSessionReady(tab);
        tab.session.setActive(true);
        tab.session.setPriorityHint(GeckoSession.PRIORITY_HIGH);
        if (geckoView.getSession() != tab.session) {
            geckoView.coverUntilFirstPaint(store.isDarkMode() ? 0xFF111827 : Color.WHITE);
            geckoView.setSession(tab.session);
        }
        tab.session.setFocused(true);
        tab.session.loadUri(url);
        rebuildTabStrip();
        syncToolbarWithActiveTab();
        store.saveTabs(tabs, activeTabIndex);
    }

    private void toggleActiveTabDesktopMode() {
        BrowserTab tab = getActiveTab();
        if (tab == null) return;
        tab.desktopMode = !tab.desktopMode;
        store.setDefaultDesktopMode(tab.desktopMode);
        if (tab.session != null) {
            tab.session.getSettings().setUserAgentMode(tab.desktopMode
                    ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                    : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
            tab.session.getSettings().setViewportMode(tab.desktopMode
                    ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                    : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
            if (!tab.isHomePage) tab.session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE);
        }
        syncToolbarWithActiveTab();
        Toast.makeText(this, tab.desktopMode ? "已切换为桌面版网页" : "已切换为移动版网页", Toast.LENGTH_SHORT).show();
    }

    private void rebuildTabStrip() {
        if (tabStripLinear == null) return;
        tabStripLinear.removeAllViews();
        tabCountBadge.setText(String.valueOf(tabs.size()));

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int maxTabWidth = UiKit.dp(this, isLandscape ? 210 : 152);
        int minTabWidth = UiKit.dp(this, isLandscape ? 115 : 96);
        boolean dark = store.isDarkMode();

        View activeChipView = null;
        for (int i = 0; i < tabs.size(); i++) {
            final int tabIdx = i;
            final BrowserTab tab = tabs.get(i);
            boolean isActive = (i == activeTabIndex);

            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setMinimumWidth(minTabWidth);
            chip.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 6), 0);

            int bgCol;
            int textCol;
            if (tab.isPrivate) {
                bgCol = isActive ? COL_TAB_PRIVATE_ACTIVE : COL_TAB_PRIVATE_INACTIVE;
                textCol = isActive ? 0xFFFFFFFF : 0xFFDDD6FE;
            } else if (isActive) {
                bgCol = dark ? 0xFF1F2430 : 0xFFF8FAFC;
                textCol = dark ? 0xFFF8FAFC : 0xFF0F172A;
            } else {
                bgCol = COL_TAB_INACTIVE;
                textCol = 0xFFCBD5E1;
            }
            chip.setBackgroundDrawable(UiKit.topRoundTab(bgCol, UiKit.dp(this, 8), 0, 0));

            TextView titleTv = new TextView(this);
            titleTv.setText((tab.isPrivate ? "[隐] " : "") + tab.getDisplayTitle());
            titleTv.setTextColor(textCol);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            if (isActive) titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            titleTv.setMaxWidth(maxTabWidth - UiKit.dp(this, 36));

            ImageButton closeBtn = new ImageButton(this);
            closeBtn.setImageDrawable(UiKit.icon(this, UiKit.ICON_CLOSE, textCol, 13));
            closeBtn.setBackgroundDrawable(UiKit.pressableRound(Color.TRANSPARENT, 0x3394A3B8, UiKit.dp(this, 10)));
            int cPad = UiKit.dp(this, 3);
            closeBtn.setPadding(cPad, cPad, cPad, cPad);
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeTab(tabIdx);
                }
            });

            chip.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(UiKit.dp(this, 22), UiKit.dp(this, 22));
            cLp.leftMargin = UiKit.dp(this, 4);
            chip.addView(closeBtn, cLp);

            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tabIdx != activeTabIndex) switchToTab(tabIdx);
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showTabContextDialog(tabIdx);
                    return true;
                }
            });

            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(this, 34));
            chipLp.rightMargin = UiKit.dp(this, 4);
            tabStripLinear.addView(chip, chipLp);
            if (isActive) activeChipView = chip;
        }

        if (activeChipView != null) {
            final View target = activeChipView;
            tabScrollView.post(new Runnable() {
                @Override
                public void run() {
                    int scrollX = target.getLeft() - (tabScrollView.getWidth() - target.getWidth()) / 2;
                    tabScrollView.smoothScrollTo(Math.max(0, scrollX), 0);
                }
            });
        }
    }

    private void syncToolbarWithActiveTab() {
        BrowserTab t = getActiveTab();
        if (t == null) return;
        boolean dark = store.isDarkMode();
        int iconTint = dark ? 0xFFE2E8F0 : 0xFF334155;
        int disabledTint = dark ? 0xFF475569 : 0xFFCBD5E1;

        boolean canBack = !t.isHomePage;
        boolean canFwd = !t.isHomePage && t.canGoForward;
        btnBack.setEnabled(canBack);
        btnBack.setImageDrawable(UiKit.icon(this, UiKit.ICON_BACK, canBack ? iconTint : disabledTint, 18));
        btnForward.setEnabled(canFwd);
        btnForward.setImageDrawable(UiKit.icon(this, UiKit.ICON_FORWARD, canFwd ? iconTint : disabledTint, 18));
        btnReloadStop.setImageDrawable(UiKit.icon(this,
                (t.isLoading && !t.isHomePage) ? UiKit.ICON_STOP : UiKit.ICON_RELOAD, iconTint, 18));

        if (!urlInput.hasFocus()) {
            urlInput.setText(t.isHomePage ? "" : (t.url != null ? t.url : ""));
            urlInput.setHint("输入网址或搜索内容（" + store.getSearchEngineName() + "）");
        }

        if (t.isHomePage) {
            securityIcon.setImageDrawable(UiKit.icon(this, UiKit.ICON_SEARCH, COL_ACCENT, 16));
        } else if (t.isSecure) {
            securityIcon.setImageDrawable(UiKit.icon(this, UiKit.ICON_LOCK, COL_SECURE_GREEN, 16));
        } else {
            securityIcon.setImageDrawable(UiKit.icon(this, UiKit.ICON_GLOBE, dark ? 0xFF94A3B8 : 0xFF64748B, 16));
        }

        if (t.desktopMode) {
            btnDesktopToggle.setText("桌面版");
            btnDesktopToggle.setTextColor(0xFFFFFFFF);
            btnDesktopToggle.setBackgroundDrawable(UiKit.roundRect(COL_ACCENT, UiKit.dp(this, 8), 0, 0));
        } else {
            btnDesktopToggle.setText("移动版");
            btnDesktopToggle.setTextColor(dark ? 0xFFCBD5E1 : 0xFF475569);
            btnDesktopToggle.setBackgroundDrawable(UiKit.roundRect(
                    dark ? 0xFF334155 : 0xFFE2E8F0, UiKit.dp(this, 8), 0, 0));
        }

        updateBookmarkStar(t);
        updateProgressBar(t);
    }

    private void updateBookmarkStar(BrowserTab t) {
        boolean dark = store.isDarkMode();
        int iconTint = dark ? 0xFFE2E8F0 : 0xFF334155;
        if (t != null && !t.isHomePage && store.isBookmarked(t.url)) {
            btnBookmark.setImageDrawable(UiKit.icon(this, UiKit.ICON_STAR_FILLED, COL_GOLD, 18));
        } else {
            btnBookmark.setImageDrawable(UiKit.icon(this, UiKit.ICON_STAR_OUTLINE, iconTint, 18));
        }
    }

    private void updateProgressBar(BrowserTab t) {
        if (t == null || t.isHomePage || !t.isLoading || t.progress >= 100) {
            progressFill.setVisibility(View.GONE);
            return;
        }
        progressFill.setVisibility(View.VISIBLE);
        int totalW = progressTrack.getWidth();
        if (totalW <= 0) totalW = getResources().getDisplayMetrics().widthPixels;
        int fillW = Math.max(UiKit.dp(this, 18), (int) (totalW * (Math.max(5, t.progress) / 100f)));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
        lp.width = fillW;
        progressFill.setLayoutParams(lp);
    }

    @Override
    public void setFullScreenMode(boolean fullScreen) {
        isFullScreenVideo = fullScreen;
        if (fullScreen) {
            topChromeContainer.setVisibility(View.GONE);
            if (findBar != null) findBar.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= 19) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } else {
            topChromeContainer.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (Build.VERSION.SDK_INT >= 19) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        }
    }

    // =========================================================================
    // Dialogs & Menus
    // =========================================================================

    private void showElementContextMenu(final BrowserTab tab, final GeckoSession.ContentDelegate.ContextElement el) {
        final List<String> actions = new ArrayList<String>();
        final List<Runnable> handlers = new ArrayList<Runnable>();

        if (el.linkUri != null && !el.linkUri.isEmpty()) {
            actions.add("在新标签页打开链接");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    createNewTab(el.linkUri, tab.isPrivate, true);
                }
            });
            actions.add("在后台标签页打开链接");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    createNewTab(el.linkUri, tab.isPrivate, false);
                    Toast.makeText(MainActivity.this, "已在后台打开新标签页", Toast.LENGTH_SHORT).show();
                }
            });
            actions.add("复制链接地址");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    copyToClipboard(el.linkUri);
                }
            });
            actions.add("下载链接文件");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    DownloadHelper.downloadDirectUrl(MainActivity.this, el.linkUri, null);
                }
            });
        }

        if (el.srcUri != null && !el.srcUri.isEmpty()
                && (el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
                || el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO)) {
            actions.add("新标签页查看原图/媒体");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    createNewTab(el.srcUri, tab.isPrivate, true);
                }
            });
            actions.add("保存图片/媒体到 Download");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    DownloadHelper.downloadDirectUrl(MainActivity.this, el.srcUri, null);
                }
            });
            actions.add("复制媒体直链");
            handlers.add(new Runnable() {
                @Override
                public void run() {
                    copyToClipboard(el.srcUri);
                }
            });
        }

        if (actions.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(el.linkUri != null ? el.linkUri : el.srcUri)
                .setItems(actions.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handlers.get(which).run();
                    }
                })
                .show();
    }

    private void showTabContextDialog(final int tabIdx) {
        if (tabIdx < 0 || tabIdx >= tabs.size()) return;
        final BrowserTab t = tabs.get(tabIdx);
        String[] opts = new String[]{"复制当前标签网址", "克隆此标签页", "关闭其他标签页", "关闭此标签页"};
        new AlertDialog.Builder(this)
                .setTitle(t.getDisplayTitle())
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                copyToClipboard(t.url);
                                break;
                            case 1:
                                createNewTab(t.url, t.isPrivate, true);
                                break;
                            case 2:
                                for (int i = tabs.size() - 1; i >= 0; i--) {
                                    if (i != tabIdx) {
                                        BrowserTab r = tabs.remove(i);
                                        if (r.session != null && r.session.isOpen()) r.session.close();
                                    }
                                }
                                activeTabIndex = 0;
                                switchToTab(0);
                                store.saveTabs(tabs, activeTabIndex);
                                break;
                            case 3:
                                closeTab(tabIdx);
                                break;
                        }
                    }
                })
                .show();
    }

    private void showTabsOverviewDialog() {
        String[] titles = new String[tabs.size() + 2];
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab t = tabs.get(i);
            titles[i] = ((i == activeTabIndex) ? "● " : "   ")
                    + (t.isPrivate ? "[无痕] " : "") + t.getDisplayTitle();
        }
        titles[tabs.size()] = "＋ 新建普通标签页";
        titles[tabs.size() + 1] = "✕ 关闭全部标签页并回到起始页";

        new AlertDialog.Builder(this)
                .setTitle("已打开的标签页 (" + tabs.size() + ")")
                .setItems(titles, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < tabs.size()) switchToTab(which);
                        else if (which == tabs.size()) createNewTab("about:home", false, true);
                        else {
                            geckoView.releaseSession();
                            for (BrowserTab r : tabs) {
                                if (r.session != null && r.session.isOpen()) r.session.close();
                            }
                            tabs.clear();
                            activeTabIndex = -1;
                            createNewTab("about:home", false, true);
                        }
                    }
                })
                .show();
    }

    private void showMainMenuDialog() {
        final BrowserTab active = getActiveTab();
        final boolean isDesk = active != null && active.desktopMode;
        final boolean trackOn = store.isTrackingProtectionEnabled();
        final boolean darkOn = store.isDarkMode();
        final boolean texBackend = store.useTextureBackend();
        final boolean forceHw = store.isForceHardwareWebRender();

        String[] items = new String[]{
                "新建普通标签页",
                "新建无痕标签页",
                isDesk ? "切换为移动版 UA（当前：桌面版）" : "切换为桌面版 UA（当前：移动版）",
                "书签收藏夹",
                "浏览历史记录",
                "页内查找关键字",
                "复制当前网页链接",
                "默认搜索引擎（当前：" + store.getSearchEngineName() + "）",
                trackOn ? "追踪与广告脚本拦截：已开启" : "追踪与广告脚本拦截：已关闭",
                darkOn ? "界面与网页深色模式：已开启" : "界面与网页深色模式：已关闭",
                texBackend ? "渲染后端：TextureView（点击切回 SurfaceView）" : "渲染后端：SurfaceView 硬件直出（推荐）",
                forceHw ? "光栅引擎：Tegra K1 硬件 WebRender（已启用，点击切软件）" : "光栅引擎：SWGL 软件光栅（点击开启 Tegra K1 硬件渲染）",
                "清理浏览器缓存与 Cookie 数据",
                "打开高级内核页 about:config",
                "关于 GeckoPad 115"
        };

        new AlertDialog.Builder(this)
                .setTitle("GeckoPad 菜单")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                createNewTab("about:home", false, true);
                                break;
                            case 1:
                                createNewTab("about:home", true, true);
                                break;
                            case 2:
                                toggleActiveTabDesktopMode();
                                break;
                            case 3:
                                showBookmarksDialog();
                                break;
                            case 4:
                                showHistoryDialog();
                                break;
                            case 5:
                                openFindBar();
                                break;
                            case 6:
                                if (active != null && active.url != null) copyToClipboard(active.url);
                                break;
                            case 7:
                                showSearchEngineDialog();
                                break;
                            case 8: {
                                boolean next = !trackOn;
                                store.setTrackingProtectionEnabled(next);
                                for (BrowserTab t : tabs) {
                                    if (t.session != null) t.session.getSettings().setUseTrackingProtection(next);
                                }
                                if (active != null && !active.isHomePage && active.session != null) {
                                    active.session.reload();
                                }
                                Toast.makeText(MainActivity.this,
                                        next ? "已开启追踪与广告脚本拦截" : "已关闭追踪拦截（兼容严格站点）",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 9: {
                                boolean nextDark = !darkOn;
                                store.setDarkMode(nextDark);
                                runtime.getSettings().setPreferredColorScheme(
                                        nextDark ? GeckoRuntimeSettings.COLOR_SCHEME_DARK
                                                : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT);
                                recreateUiPreservingTabs();
                                break;
                            }
                            case 10: {
                                boolean nextTex = !texBackend;
                                store.setUseTextureBackend(nextTex);
                                recreateUiPreservingTabs();
                                Toast.makeText(MainActivity.this,
                                        nextTex ? "已切换至 TextureView 纹理后端" : "已切换至 SurfaceView 硬件直出后端",
                                        Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case 11: {
                                boolean nextHw = !forceHw;
                                store.setForceHardwareWebRender(nextHw);
                                store.saveTabs(tabs, activeTabIndex);
                                Toast.makeText(MainActivity.this,
                                        nextHw ? "正在重启并开启 Tegra K1 硬件 WebRender..."
                                                : "正在重启并切换为 SWGL 软件光栅...",
                                        Toast.LENGTH_SHORT).show();
                                restartBrowserProcess();
                                break;
                            }
                            case 12:
                                showClearDataDialog();
                                break;
                            case 13:
                                createNewTab("about:config", false, true);
                                break;
                            case 14:
                                showAboutDialog();
                                break;
                        }
                    }
                })
                .show();
    }

    private void restartBrowserProcess() {
        geckoView.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent launch = new Intent(MainActivity.this, MainActivity.class);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                            MainActivity.this, 7788, launch,
                            android.app.PendingIntent.FLAG_CANCEL_CURRENT);
                    android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    if (am != null) {
                        am.set(android.app.AlarmManager.ELAPSED_REALTIME,
                                android.os.SystemClock.elapsedRealtime() + 350L, pi);
                    }
                    android.app.ActivityManager actMgr =
                            (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    int myPid = android.os.Process.myPid();
                    int myUid = android.os.Process.myUid();
                    if (actMgr != null && actMgr.getRunningAppProcesses() != null) {
                        for (android.app.ActivityManager.RunningAppProcessInfo p : actMgr.getRunningAppProcesses()) {
                            if (p.uid == myUid && p.pid != myPid) {
                                android.os.Process.killProcess(p.pid);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
                finish();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }
        }, 250L);
    }

    private void recreateUiPreservingTabs() {
        geckoView.releaseSession();
        buildUi();
        if (activeTabIndex >= 0 && activeTabIndex < tabs.size()) {
            switchToTab(activeTabIndex);
        }
    }

    private void showBookmarksDialog() {
        final List<BrowserStore.LinkItem> list = store.getBookmarks();
        if (list.isEmpty()) {
            Toast.makeText(this, "暂无保存的书签，点击地址栏右侧星标即可收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            labels[i] = list.get(i).title + "\n" + list.get(i).url;
        }
        new AlertDialog.Builder(this)
                .setTitle("书签收藏夹（点击打开）")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadInActiveTab(list.get(which).url);
                    }
                })
                .setNeutralButton("删除书签...", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDeleteBookmarkDialog(list);
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showDeleteBookmarkDialog(final List<BrowserStore.LinkItem> list) {
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = "✕ 删除：" + list.get(i).title;
        new AlertDialog.Builder(this)
                .setTitle("选择要移除的书签")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store.removeBookmarkAt(which);
                        if (getActiveTab() != null && getActiveTab().isHomePage) rebuildHomeContent();
                        syncToolbarWithActiveTab();
                        Toast.makeText(MainActivity.this, "已删除书签", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHistoryDialog() {
        final List<BrowserStore.LinkItem> list = store.getHistory();
        if (list.isEmpty()) {
            Toast.makeText(this, "暂无历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = list.get(i).title + "\n" + list.get(i).url;
        new AlertDialog.Builder(this)
                .setTitle("最近浏览历史 (" + list.size() + ")")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadInActiveTab(list.get(which).url);
                    }
                })
                .setNeutralButton("清空历史", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store.clearHistory();
                        Toast.makeText(MainActivity.this, "已清空全部浏览历史", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showSearchEngineDialog() {
        String[] engines = new String[]{
                "Google 搜索 (google.com)",
                "Bing 必应 (bing.com)",
                "百度搜索 (baidu.com)",
                "DuckDuckGo (duckduckgo.com)"
        };
        new AlertDialog.Builder(this)
                .setTitle("选择默认搜索引擎")
                .setSingleChoiceItems(engines, store.getSearchEngine(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store.setSearchEngine(which);
                        syncToolbarWithActiveTab();
                        if (getActiveTab() != null && getActiveTab().isHomePage) rebuildHomeContent();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showClearDataDialog() {
        String[] opts = new String[]{"仅清理图片与网络缓存（保留登录态 Cookie）", "彻底清理全部缓存、站点存储与登录 Cookie"};
        new AlertDialog.Builder(this)
                .setTitle("清理浏览数据")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        long flags = (which == 0)
                                ? StorageController.ClearFlags.ALL_CACHES
                                : StorageController.ClearFlags.ALL;
                        runtime.getStorageController().clearData(flags).accept(new GeckoResult.Consumer<Void>() {
                            @Override
                            public void accept(Void value) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "清理完成", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAboutDialog() {
        boolean hw = store.isForceHardwareWebRender();
        new AlertDialog.Builder(this)
                .setTitle("GeckoPad 极速平板浏览器 v1.1")
                .setMessage("内核版本：Mozilla GeckoView 115.0.20230726201356 (Firefox 115 ESR)\n"
                        + "当前光栅：" + (hw ? "Tegra K1 硬件 WebRender (OpenGL ES 3.1 + WebGL 2)" : "SWGL NEON 软件光栅") + "\n"
                        + "证书加密：内置 Mozilla NSS TLS 1.3 + 独立现代根证书库\n"
                        + "硬件平台：专为小米平板 1 (Tegra K1 ARMv7 / Android 4.4.4 API 19) 定制编译\n"
                        + "特性：零广告 · 零后台常驻 · 横竖屏无缝自适应 · 后台标签自动休眠省内存")
                .setPositiveButton("确定", null)
                .setNeutralButton("查看 about:support", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        createNewTab("about:support", false, true);
                    }
                })
                .show();
    }

    private void openFindBar() {
        BrowserTab t = getActiveTab();
        if (t == null || t.isHomePage || t.session == null) {
            Toast.makeText(this, "请先打开一个网页再使用页内查找", Toast.LENGTH_SHORT).show();
            return;
        }
        findBar.setVisibility(View.VISIBLE);
        findInput.requestFocus();
        showKeyboard(findInput);
    }

    private void closeFindBar() {
        findBar.setVisibility(View.GONE);
        hideKeyboard(findInput);
        BrowserTab t = getActiveTab();
        if (t != null && t.session != null) t.session.getFinder().clear();
    }

    private void triggerFindInPage(boolean backwards) {
        BrowserTab t = getActiveTab();
        if (t == null || t.isHomePage || t.session == null) return;
        String q = findInput.getText().toString();
        if (q.isEmpty()) {
            t.session.getFinder().clear();
            findStatusText.setText("0/0");
            return;
        }
        t.session.getFinder().setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
        t.session.getFinder().find(q, backwards ? GeckoSession.FINDER_FIND_BACKWARDS : 0)
                .accept(new GeckoResult.Consumer<GeckoSession.FinderResult>() {
                    @Override
                    public void accept(final GeckoSession.FinderResult result) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (result != null && result.found) {
                                    findStatusText.setText(result.current + "/" + result.total);
                                } else {
                                    findStatusText.setText("0/0");
                                }
                            }
                        });
                    }
                });
    }

    private void copyToClipboard(String text) {
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("url", text));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void showKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
