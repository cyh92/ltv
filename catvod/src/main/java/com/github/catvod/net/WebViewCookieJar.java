package com.github.catvod.net;

import android.webkit.CookieManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

/**
 * 登录态（0.1.106 新增）：把 OkHttp 的 Cookie 存取桥到系统 WebView 的 CookieManager。
 * <p>
 * 之前 OkHttp 客户端没有 CookieJar：爬虫登录拿到 Set-Cookie 后，下一次请求不会自动带上，
 * 登录态跨请求、跨启动全部丢失；JS/Node 爬虫虽然能经 WebViewCookies 读写 Cookie，
 * 但 jar/py 爬虫与播放器走的 OkHttp 请求拿不到。接上这个桥之后，所有 OkHttp 请求
 * （爬虫接口调用、登录、播放地址）自动带上已存的 Cookie，收到的 Set-Cookie 自动存下，
 * 与 WebView 登录（如解析页、网页嗅探里登过录）共用同一份存储，重启后还在
 * （CookieManager 自己持久化到应用数据目录）。
 * <p>
 * 只存 Cookie（会话凭证），不存密码：CookieJar 只看响应里的 Set-Cookie 头，从不碰请求体。
 * 手动带了 Cookie 头的请求不受影响（OkHttp 只在请求没有 Cookie 头时才补 jar 里的）。
 * 所有 CookieManager 调用都包 try/catch：WebView 还没初始化、Cookie 格式不对等情况
 * 只当成“没有 Cookie”，绝不让一次 Cookie 读写炸掉整个请求。
 */
public class WebViewCookieJar implements CookieJar {

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) return;
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(true);
            String urlString = url.toString();
            for (Cookie cookie : cookies) manager.setCookie(urlString, cookie.toString());
            manager.flush();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        try {
            String cookieString = CookieManager.getInstance().getCookie(url.toString());
            if (cookieString == null || cookieString.isEmpty()) return Collections.emptyList();
            List<Cookie> cookies = new ArrayList<>();
            for (String pair : cookieString.split(";")) {
                Cookie cookie = Cookie.parse(url, pair.trim());
                if (cookie != null) cookies.add(cookie);
            }
            return cookies;
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }
}
