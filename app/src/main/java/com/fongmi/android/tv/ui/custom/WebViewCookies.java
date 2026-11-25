package com.fongmi.android.tv.ui.custom;

import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebView;

import com.github.catvod.utils.UriUtil;

public final class WebViewCookies {

    public static String get(String url) {
        if (!isHttp(url)) return "";
        String value = CookieManager.getInstance().getCookie(url);
        return value == null || hasLineBreak(value) ? "" : value;
    }

    public static boolean set(String url, String cookie) {
        if (!isHttp(url) || cookie == null || cookie.isEmpty() || hasLineBreak(cookie)) return false;
        CookieManager manager = CookieManager.getInstance();
        manager.setAcceptCookie(true);
        manager.setCookie(url, cookie);
        manager.flush();
        return true;
    }

    /** 该网址有没有存着的 Cookie（登录态判断用）。 */
    public static boolean hasCookies(String url) {
        return !get(url).isEmpty();
    }

    /**
     * 清掉某个 host 下的全部 Cookie（按名逐个设过期时间删除；CookieManager 没有按 host 删除的接口）。
     * 只清 Path=/ 的常规 Cookie，带特殊 Path 的 Cookie 不在此列（登录 Cookie 基本都是 Path=/）。
     *
     * @return 是否清掉了至少一条
     */
    public static boolean clearHost(String url) {
        if (!isHttp(url)) return false;
        String value = get(url);
        if (value.isEmpty()) return false;
        CookieManager manager = CookieManager.getInstance();
        boolean cleared = false;
        for (String pair : value.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String name = pair.substring(0, eq).trim();
            if (name.isEmpty()) continue;
            manager.setCookie(url, name + "=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/");
            cleared = true;
        }
        if (cleared) manager.flush();
        return cleared;
    }

    private static boolean isHttp(String url) {
        String scheme = UriUtil.scheme(url);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    static void set(WebView view, String url, String cookies) {
        if (TextUtils.isEmpty(cookies)) return;
        CookieManager manager = CookieManager.getInstance();
        manager.setAcceptCookie(true);
        manager.setAcceptThirdPartyCookies(view, true);
        for (String cookie : cookies.split(";")) {
            cookie = cookie.trim();
            if (!cookie.isEmpty()) manager.setCookie(url, cookie);
        }
        manager.flush();
    }

    private WebViewCookies() {
    }
}
