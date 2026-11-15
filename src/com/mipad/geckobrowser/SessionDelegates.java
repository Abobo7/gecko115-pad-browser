package com.mipad.geckobrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.SlowScriptResponse;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.util.ArrayList;
import java.util.List;

public final class SessionDelegates {
    public interface Host {
        Activity getActivity();
        BrowserStore getStore();
        BrowserTab getActiveTab();
        void onTabUpdated(BrowserTab tab, boolean rebuildStrip);
        void onProgressUpdated(BrowserTab tab);
        BrowserTab createPopupTab(String uri, boolean isPrivate, boolean desktopMode);
        void loadInActiveTab(String url);
        void closeTabObject(BrowserTab tab);
        void setFullScreenMode(boolean fullScreen);
        void showContextMenu(BrowserTab tab, GeckoSession.ContentDelegate.ContextElement el);
        void handleTabProcessDied(BrowserTab tab);
        GeckoResult<GeckoSession.PromptDelegate.PromptResponse> startFilePicker(
                GeckoSession.PromptDelegate.FilePrompt prompt);
    }

    private static class ChoiceItemFlat {
        final String id;
        final String label;
        final boolean selected;
        final boolean disabled;

        ChoiceItemFlat(String id, String label, boolean selected, boolean disabled) {
            this.id = id;
            this.label = label;
            this.selected = selected;
            this.disabled = disabled;
        }
    }

    private SessionDelegates() {}

    public static void attach(final Host host, final BrowserTab tab, GeckoSession session) {
        final Activity act = host.getActivity();
        final BrowserStore store = host.getStore();

        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession s, String url,
                                         List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
                if (url == null || ("about:blank".equals(url) && tab.url != null && !"about:blank".equals(tab.url))) return;
                tab.url = url;
                if (tab.title == null || tab.title.isEmpty() || tab.title.startsWith("http")) {
                    tab.title = url;
                }
                host.onTabUpdated(tab, tab == host.getActiveTab());
            }

            @Override
            public void onCanGoBack(GeckoSession s, boolean canGoBack) {
                tab.canGoBack = canGoBack;
                host.onTabUpdated(tab, false);
            }

            @Override
            public void onCanGoForward(GeckoSession s, boolean canGoForward) {
                tab.canGoForward = canGoForward;
                host.onTabUpdated(tab, false);
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, LoadRequest request) {
                if (request == null || request.uri == null) {
                    return GeckoResult.allow();
                }
                String uri = request.uri;
                String lower = uri.toLowerCase();
                if (lower.startsWith("http://") || lower.startsWith("https://")
                        || lower.startsWith("about:") || lower.startsWith("data:")
                        || lower.startsWith("blob:") || lower.startsWith("javascript:")
                        || lower.startsWith("file:") || lower.startsWith("resource:")) {
                    return GeckoResult.allow();
                }
                handleExternalScheme(host, uri);
                return GeckoResult.deny();
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                BrowserTab popup = host.createPopupTab(uri, tab.isPrivate, tab.desktopMode);
                return GeckoResult.fromValue(popup.session);
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession s, String uri, WebRequestError error) {
                String reason = describeWebError(error);
                String safeUri = TextUtils.htmlEncode(uri != null ? uri : "");
                String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                        + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                        + "<title>页面加载失败</title>"
                        + "<style>body{font-family:sans-serif;background:#F8FAFC;color:#1E293B;padding:48px 24px;text-align:center;}"
                        + ".card{max-width:520px;margin:0 auto;background:#FFF;border:1px solid #E2E8F0;border-radius:14px;padding:28px;}"
                        + "h2{margin:0 0 12px;font-size:20px;}p{color:#64748B;font-size:14px;word-break:break-all;line-height:1.6;}"
                        + "button{margin-top:18px;background:#2563EB;color:#FFF;border:0;border-radius:8px;padding:10px 24px;font-size:14px;}"
                        + "</style></head><body><div class='card'>"
                        + "<h2>无法打开此网页</h2>"
                        + "<p>" + TextUtils.htmlEncode(reason) + "</p>"
                        + "<p>" + safeUri + "</p>"
                        + "<button onclick='location.reload()'>重新加载</button>"
                        + "</div></body></html>";
                return GeckoResult.fromValue("data:text/html;charset=utf-8," + Uri.encode(html));
            }
        });

        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession s, String url) {
                tab.isLoading = true;
                tab.progress = 10;
                host.onTabUpdated(tab, false);
            }

            @Override
            public void onPageStop(GeckoSession s, boolean success) {
                tab.isLoading = false;
                tab.progress = 100;
                if (success && !tab.isPrivate && tab.url != null) {
                    store.addHistory(tab.getDisplayTitle(), tab.url);
                }
                host.onTabUpdated(tab, false);
            }

            @Override
            public void onProgressChange(GeckoSession s, int progress) {
                tab.progress = progress;
                host.onProgressUpdated(tab);
            }

            @Override
            public void onSecurityChange(GeckoSession s, SecurityInformation securityInfo) {
                tab.isSecure = securityInfo != null && securityInfo.isSecure;
                host.onTabUpdated(tab, false);
            }

            @Override
            public void onSessionStateChange(GeckoSession s, GeckoSession.SessionState state) {
                tab.savedState = state;
            }
        });

        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession s, String title) {
                if (title != null && !title.trim().isEmpty()) {
                    tab.title = title.trim();
                    if (!tab.isPrivate && tab.url != null) {
                        store.addHistory(tab.title, tab.url);
                    }
                    host.onTabUpdated(tab, true);
                }
            }

            @Override
            public void onCloseRequest(GeckoSession s) {
                host.closeTabObject(tab);
            }

            @Override
            public void onFullScreen(GeckoSession s, boolean fullScreen) {
                host.setFullScreenMode(fullScreen);
            }

            @Override
            public void onExternalResponse(GeckoSession s, WebResponse response) {
                DownloadHelper.downloadWebResponse(act, response);
            }

            @Override
            public void onContextMenu(GeckoSession s, int screenX, int screenY, ContextElement element) {
                if (element != null) host.showContextMenu(tab, element);
            }

            @Override
            public GeckoResult<SlowScriptResponse> onSlowScript(GeckoSession s, String scriptFileName) {
                return GeckoResult.fromValue(SlowScriptResponse.CONTINUE);
            }

            @Override
            public void onCrash(GeckoSession s) {
                host.handleTabProcessDied(tab);
            }

            @Override
            public void onKill(GeckoSession s) {
                host.handleTabProcessDied(tab);
            }
        });

        session.setHistoryDelegate(new GeckoSession.HistoryDelegate() {
            @Override
            public GeckoResult<Boolean> onVisited(GeckoSession s, String url, String reloadUrl, int flags) {
                if (!tab.isPrivate && (flags & VISIT_TOP_LEVEL) != 0 && url != null) {
                    store.addHistory(tab.getDisplayTitle(), url);
                }
                return GeckoResult.fromValue(Boolean.TRUE);
            }
        });

        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override
            public void onAndroidPermissionsRequest(GeckoSession s, String[] permissions, Callback callback) {
                callback.grant();
            }

            @Override
            public GeckoResult<Integer> onContentPermissionRequest(GeckoSession s, ContentPermission perm) {
                if (perm == null) return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
                if (perm.permission == PERMISSION_AUTOPLAY_INAUDIBLE
                        || perm.permission == PERMISSION_AUTOPLAY_AUDIBLE
                        || perm.permission == PERMISSION_STORAGE_ACCESS
                        || perm.permission == PERMISSION_PERSISTENT_STORAGE
                        || perm.permission == PERMISSION_MEDIA_KEY_SYSTEM_ACCESS) {
                    return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
                }
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
        });

        session.setPromptDelegate(new GeckoSession.PromptDelegate() {
            @Override
            public GeckoResult<PromptResponse> onPopupPrompt(GeckoSession s, PopupPrompt prompt) {
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
            }

            @Override
            public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, final AlertPrompt prompt) {
                final GeckoResult<PromptResponse> res = new GeckoResult<PromptResponse>();
                if (act.isFinishing()) {
                    res.complete(prompt.dismiss());
                    return res;
                }
                new AlertDialog.Builder(act)
                        .setTitle(prompt.title != null && !prompt.title.isEmpty() ? prompt.title : "网页提示")
                        .setMessage(prompt.message != null ? prompt.message : "")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                res.complete(prompt.dismiss());
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                if (!prompt.isComplete()) res.complete(prompt.dismiss());
                            }
                        })
                        .show();
                return res;
            }

            @Override
            public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, final ButtonPrompt prompt) {
                final GeckoResult<PromptResponse> res = new GeckoResult<PromptResponse>();
                if (act.isFinishing()) {
                    res.complete(prompt.dismiss());
                    return res;
                }
                new AlertDialog.Builder(act)
                        .setTitle(prompt.title != null && !prompt.title.isEmpty() ? prompt.title : "网页确认")
                        .setMessage(prompt.message != null ? prompt.message : "")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                res.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE));
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                res.complete(prompt.confirm(ButtonPrompt.Type.NEGATIVE));
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                if (!prompt.isComplete()) res.complete(prompt.dismiss());
                            }
                        })
                        .show();
                return res;
            }

            @Override
            public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, final TextPrompt prompt) {
                final GeckoResult<PromptResponse> res = new GeckoResult<PromptResponse>();
                if (act.isFinishing()) {
                    res.complete(prompt.dismiss());
                    return res;
                }
                final EditText input = new EditText(act);
                input.setText(prompt.defaultValue != null ? prompt.defaultValue : "");
                input.setSelectAllOnFocus(true);
                int pad = UiKit.dp(act, 14);
                LinearLayout wrap = new LinearLayout(act);
                wrap.setPadding(pad, pad / 2, pad, 0);
                wrap.addView(input, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                new AlertDialog.Builder(act)
                        .setTitle(prompt.title != null && !prompt.title.isEmpty() ? prompt.title : "网页输入")
                        .setMessage(prompt.message != null ? prompt.message : "")
                        .setView(wrap)
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                res.complete(prompt.confirm(input.getText().toString()));
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                res.complete(prompt.dismiss());
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                if (!prompt.isComplete()) res.complete(prompt.dismiss());
                            }
                        })
                        .show();
                return res;
            }

            @Override
            public GeckoResult<PromptResponse> onBeforeUnloadPrompt(GeckoSession s, BeforeUnloadPrompt prompt) {
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
            }

            @Override
            public GeckoResult<PromptResponse> onRepostConfirmPrompt(GeckoSession s, RepostConfirmPrompt prompt) {
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW));
            }

            @Override
            public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession s, final ChoicePrompt prompt) {
                final GeckoResult<PromptResponse> res = new GeckoResult<PromptResponse>();
                if (act.isFinishing() || prompt.choices == null) {
                    res.complete(prompt.dismiss());
                    return res;
                }
                final List<ChoiceItemFlat> flat = new ArrayList<ChoiceItemFlat>();
                flattenChoices(prompt.choices, "", flat);
                if (flat.isEmpty()) {
                    res.complete(prompt.dismiss());
                    return res;
                }

                String[] labels = new String[flat.size()];
                final boolean[] checked = new boolean[flat.size()];
                int singleSelected = -1;
                for (int i = 0; i < flat.size(); i++) {
                    ChoiceItemFlat c = flat.get(i);
                    labels[i] = c.disabled ? (c.label + " (不可用)") : c.label;
                    checked[i] = c.selected;
                    if (c.selected && singleSelected == -1) singleSelected = i;
                }

                AlertDialog.Builder b = new AlertDialog.Builder(act);
                if (prompt.title != null && !prompt.title.isEmpty()) b.setTitle(prompt.title);
                if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
                    b.setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                            if (!flat.get(which).disabled) checked[which] = isChecked;
                        }
                    });
                    b.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            List<String> ids = new ArrayList<String>();
                            for (int i = 0; i < flat.size(); i++) {
                                if (checked[i] && flat.get(i).id != null) ids.add(flat.get(i).id);
                            }
                            res.complete(prompt.confirm(ids.toArray(new String[0])));
                        }
                    });
                    b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            res.complete(prompt.dismiss());
                        }
                    });
                } else {
                    b.setSingleChoiceItems(labels, singleSelected, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ChoiceItemFlat picked = flat.get(which);
                            if (!picked.disabled && picked.id != null) {
                                res.complete(prompt.confirm(picked.id));
                                dialog.dismiss();
                            }
                        }
                    });
                    b.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            res.complete(prompt.dismiss());
                        }
                    });
                }
                b.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (!prompt.isComplete()) res.complete(prompt.dismiss());
                    }
                });
                b.show();
                return res;
            }

            @Override
            public GeckoResult<PromptResponse> onFilePrompt(GeckoSession s, FilePrompt prompt) {
                return host.startFilePicker(prompt);
            }
        });
    }

    private static void flattenChoices(GeckoSession.PromptDelegate.ChoicePrompt.Choice[] choices,
                                       String prefix, List<ChoiceItemFlat> out) {
        if (choices == null) return;
        for (GeckoSession.PromptDelegate.ChoicePrompt.Choice ch : choices) {
            if (ch == null || ch.separator) continue;
            if (ch.items != null && ch.items.length > 0) {
                String group = (ch.label != null && !ch.label.isEmpty()) ? (ch.label + " - ") : "";
                flattenChoices(ch.items, prefix + group, out);
            } else {
                out.add(new ChoiceItemFlat(ch.id,
                        prefix + (ch.label != null ? ch.label : ""),
                        ch.selected,
                        ch.disabled));
            }
        }
    }

    private static void handleExternalScheme(final Host host, final String uri) {
        final Activity act = host.getActivity();
        try {
            final Intent intent;
            if (uri.startsWith("intent://")) {
                intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (act.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null
                        && fallback != null && !fallback.isEmpty()) {
                    host.loadInActiveTab(fallback);
                    return;
                }
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (act.getPackageManager().resolveActivity(intent, 0) != null) {
                new AlertDialog.Builder(act)
                        .setTitle("打开外部应用")
                        .setMessage("网页请求唤起外部应用处理链接：\n" + uri)
                        .setPositiveButton("允许打开", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                try {
                                    act.startActivity(intent);
                                } catch (Exception e) {
                                    Toast.makeText(act, "无法启动外部应用", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        } catch (Exception ignored) {
        }
    }

    private static String describeWebError(WebRequestError err) {
        if (err == null) return "发生未知网络异常，请检查网络连接或代理状态。";
        switch (err.code) {
            case WebRequestError.ERROR_SECURITY_SSL:
            case WebRequestError.ERROR_SECURITY_BAD_CERT:
            case WebRequestError.ERROR_BAD_HSTS_CERT:
                return "TLS / HTTPS 安全证书连接异常（错误码 " + err.code + "）。请检查系统时间是否准确。";
            case WebRequestError.ERROR_NET_TIMEOUT:
                return "连接服务器超时，请检查 Wi-Fi 或代理节点状态。";
            case WebRequestError.ERROR_CONNECTION_REFUSED:
            case WebRequestError.ERROR_PROXY_CONNECTION_REFUSED:
                return "目标服务器或本地代理拒绝了连接请求。";
            case WebRequestError.ERROR_UNKNOWN_HOST:
            case WebRequestError.ERROR_UNKNOWN_PROXY_HOST:
                return "DNS 域名解析失败，无法找到该站点服务器地址。";
            case WebRequestError.ERROR_OFFLINE:
                return "当前平板处于离线断网状态。";
            default:
                return "网页加载中断（类别 " + err.category + " / 代码 " + err.code + "）。";
        }
    }
}
