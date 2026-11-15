package com.mipad.geckobrowser;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public final class HomePageBuilder {
    public interface Listener {
        void onOpenUrl(String url);
        void onOpenUrlBackground(String title, String url);
        void onManageBookmarks();
    }

    private static final String[][] SPEED_DIAL_SITES = new String[][]{
            {"Google 搜索", "https://www.google.com", "#2563EB", "G"},
            {"GitHub", "https://github.com", "#1F2937", "GH"},
            {"哔哩哔哩", "https://www.bilibili.com", "#FB7299", "B"},
            {"Pixiv", "https://www.pixiv.net", "#0096FA", "P"},
            {"知乎", "https://www.zhihu.com", "#056DE8", "知"},
            {"YouTube", "https://www.youtube.com", "#DC2626", "YT"},
            {"V2EX", "https://www.v2ex.com", "#334155", "V2"},
            {"Bing 必应", "https://www.bing.com", "#0D9488", "必"}
    };

    private HomePageBuilder() {}

    public static void populate(final Context ctx,
                                LinearLayout homeRootContent,
                                final BrowserStore store,
                                boolean isPrivateTab,
                                final Listener listener) {
        homeRootContent.removeAllViews();

        boolean dark = store.isDarkMode();
        boolean isLandscape = ctx.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int cols = isLandscape ? 4 : 3;
        int maxContentWidth = UiKit.dp(ctx, isLandscape ? 720 : 540);
        int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        int containerWidth = Math.min(screenWidth - UiKit.dp(ctx, 36), maxContentWidth);

        int cardBg = dark ? 0xFF1E293B : 0xFFFFFFFF;
        int cardBorder = dark ? 0xFF334155 : 0xFFE2E8F0;
        int titleCol = dark ? 0xFFF8FAFC : 0xFF0F172A;
        int subCol = dark ? 0xFF94A3B8 : 0xFF64748B;
        int accent = 0xFF2563EB;

        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        inner.setPadding(0, UiKit.dp(ctx, isLandscape ? 32 : 42), 0, UiKit.dp(ctx, 28));

        TextView heroTitle = new TextView(ctx);
        heroTitle.setText(isPrivateTab ? "GeckoPad · 无痕浏览模式" : "GeckoPad 极速浏览器");
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        heroTitle.setTextColor(isPrivateTab ? 0xFFA78BFA : titleCol);
        heroTitle.setGravity(Gravity.CENTER);
        inner.addView(heroTitle);

        TextView heroSub = new TextView(ctx);
        heroSub.setText("Mozilla Firefox Gecko 115.0 ESR 内核 · 独立 NSS TLS 1.3 现代根证书 · 纯净无广告");
        heroSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heroSub.setTextColor(subCol);
        heroSub.setGravity(Gravity.CENTER);
        heroSub.setPadding(0, UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 22));
        inner.addView(heroSub);

        LinearLayout searchCard = new LinearLayout(ctx);
        searchCard.setOrientation(LinearLayout.HORIZONTAL);
        searchCard.setGravity(Gravity.CENTER_VERTICAL);
        searchCard.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 4), UiKit.dp(ctx, 8), UiKit.dp(ctx, 4));
        searchCard.setBackgroundDrawable(UiKit.roundRect(cardBg, UiKit.dp(ctx, 24), UiKit.dp(ctx, 1), cardBorder));

        ImageView sIcon = new ImageView(ctx);
        sIcon.setImageDrawable(UiKit.icon(ctx, UiKit.ICON_SEARCH, accent, 18));
        searchCard.addView(sIcon, new LinearLayout.LayoutParams(UiKit.dp(ctx, 20), UiKit.dp(ctx, 20)));

        final EditText searchInput = new EditText(ctx);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setTextColor(titleCol);
        searchInput.setHintTextColor(subCol);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        searchInput.setHint("搜索或输入网址（当前引擎：" + store.getSearchEngineName() + "）");
        searchInput.setPadding(UiKit.dp(ctx, 10), 0, UiKit.dp(ctx, 8), 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String q = searchInput.getText().toString();
                    if (!q.trim().isEmpty()) {
                        listener.onOpenUrl(store.buildSearchOrNavigationUrl(q));
                    }
                    return true;
                }
                return false;
            }
        });
        searchCard.addView(searchInput, new LinearLayout.LayoutParams(0, UiKit.dp(ctx, 42), 1f));

        TextView btnGo = new TextView(ctx);
        btnGo.setText("前往");
        btnGo.setTextColor(0xFFFFFFFF);
        btnGo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnGo.setTypeface(Typeface.DEFAULT_BOLD);
        btnGo.setGravity(Gravity.CENTER);
        btnGo.setPadding(UiKit.dp(ctx, 14), 0, UiKit.dp(ctx, 14), 0);
        btnGo.setBackgroundDrawable(UiKit.pressableRound(accent, 0xFF1D4ED8, UiKit.dp(ctx, 18)));
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = searchInput.getText().toString();
                if (!q.trim().isEmpty()) {
                    listener.onOpenUrl(store.buildSearchOrNavigationUrl(q));
                }
            }
        });
        searchCard.addView(btnGo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dp(ctx, 34)));

        inner.addView(searchCard, new LinearLayout.LayoutParams(containerWidth, UiKit.dp(ctx, 48)));

        TextView sectionTitle = new TextView(ctx);
        sectionTitle.setText("常用站点直达（长按后台打开）");
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sectionTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sectionTitle.setTextColor(subCol);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(containerWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        secLp.topMargin = UiKit.dp(ctx, 22);
        secLp.bottomMargin = UiKit.dp(ctx, 10);
        inner.addView(sectionTitle, secLp);

        int gap = UiKit.dp(ctx, 10);
        int rows = (SPEED_DIAL_SITES.length + cols - 1) / cols;
        for (int r = 0; r < rows; r++) {
            LinearLayout rowLayout = new LinearLayout(ctx);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(containerWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = gap;

            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(0, UiKit.dp(ctx, 78), 1f);
                if (c > 0) cellLp.leftMargin = gap;

                if (idx < SPEED_DIAL_SITES.length) {
                    final String sName = SPEED_DIAL_SITES[idx][0];
                    final String sUrl = SPEED_DIAL_SITES[idx][1];
                    int badgeColor = Color.parseColor(SPEED_DIAL_SITES[idx][2]);
                    String badgeText = SPEED_DIAL_SITES[idx][3];

                    LinearLayout tile = new LinearLayout(ctx);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setGravity(Gravity.CENTER);
                    tile.setBackgroundDrawable(UiKit.roundRect(cardBg, UiKit.dp(ctx, 12), UiKit.dp(ctx, 1), cardBorder));
                    tile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            listener.onOpenUrl(sUrl);
                        }
                    });
                    tile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            listener.onOpenUrlBackground(sName, sUrl);
                            return true;
                        }
                    });

                    TextView avatar = new TextView(ctx);
                    avatar.setText(badgeText);
                    avatar.setTextColor(0xFFFFFFFF);
                    avatar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    avatar.setTypeface(Typeface.DEFAULT_BOLD);
                    avatar.setGravity(Gravity.CENTER);
                    avatar.setBackgroundDrawable(UiKit.roundRect(badgeColor, UiKit.dp(ctx, 16), 0, 0));
                    tile.addView(avatar, new LinearLayout.LayoutParams(UiKit.dp(ctx, 32), UiKit.dp(ctx, 32)));

                    TextView label = new TextView(ctx);
                    label.setText(sName);
                    label.setTextColor(titleCol);
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    label.setSingleLine(true);
                    label.setEllipsize(TextUtils.TruncateAt.END);
                    label.setPadding(UiKit.dp(ctx, 6), UiKit.dp(ctx, 6), UiKit.dp(ctx, 6), 0);
                    tile.addView(label);

                    rowLayout.addView(tile, cellLp);
                } else {
                    rowLayout.addView(new View(ctx), cellLp);
                }
            }
            inner.addView(rowLayout, rowLp);
        }

        List<BrowserStore.LinkItem> bookmarks = store.getBookmarks();
        if (!bookmarks.isEmpty()) {
            TextView bmHeader = new TextView(ctx);
            bmHeader.setText("我的书签（点击直达 · 长按管理）");
            bmHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bmHeader.setTypeface(Typeface.DEFAULT_BOLD);
            bmHeader.setTextColor(subCol);
            LinearLayout.LayoutParams bmHdrLp = new LinearLayout.LayoutParams(containerWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            bmHdrLp.topMargin = UiKit.dp(ctx, 12);
            bmHdrLp.bottomMargin = UiKit.dp(ctx, 8);
            inner.addView(bmHeader, bmHdrLp);

            LinearLayout bmList = new LinearLayout(ctx);
            bmList.setOrientation(LinearLayout.VERTICAL);
            bmList.setBackgroundDrawable(UiKit.roundRect(cardBg, UiKit.dp(ctx, 12), UiKit.dp(ctx, 1), cardBorder));

            int maxShow = Math.min(bookmarks.size(), 8);
            for (int i = 0; i < maxShow; i++) {
                final BrowserStore.LinkItem item = bookmarks.get(i);
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10), UiKit.dp(ctx, 14), UiKit.dp(ctx, 10));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onOpenUrl(item.url);
                    }
                });
                row.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        listener.onManageBookmarks();
                        return true;
                    }
                });

                TextView tView = new TextView(ctx);
                tView.setText(item.title);
                tView.setTextColor(titleCol);
                tView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tView.setSingleLine(true);
                tView.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(tView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView uView = new TextView(ctx);
                uView.setText(item.url);
                uView.setTextColor(subCol);
                uView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                uView.setSingleLine(true);
                uView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                uView.setMaxWidth(UiKit.dp(ctx, isLandscape ? 280 : 180));
                row.addView(uView);

                bmList.addView(row);
                if (i < maxShow - 1) {
                    View div = new View(ctx);
                    div.setBackgroundColor(cardBorder);
                    bmList.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                }
            }
            inner.addView(bmList, new LinearLayout.LayoutParams(containerWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        homeRootContent.addView(inner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
}
