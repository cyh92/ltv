package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.ui.custom.WebViewCookies;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 登录态（0.1.106 新增）：按源（Site）管理 Cookie。
 * <p>
 * 存放在哪：系统 WebView 的 CookieManager（与 {@code WebViewCookieJar} 共用同一份存储，
 * 它自己持久化到应用数据目录，重启后还在）。Cookie 本来就是按域名分的（HTTP 语义），
 * 所以“按源”体现为：一个源对应它自己的那几个域名（接口地址 api、扩展 ext 里的 host），
 * 清登录态时只清这几个域名的 Cookie，不动别的源。
 * <p>
 * 存/取的时机：不用显式存取。OkHttp 的 {@code WebViewCookieJar} 在每次请求时自动带上
 * 该域名的 Cookie、每次响应时自动存下 Set-Cookie（登录、接口调用、播放地址都走它）；
 * WebView 里登过录（如解析页、网页嗅探）也进同一份存储，爬虫下次请求自动带上。
 * <p>
 * 安全：只存 Cookie（会话凭证），不存密码——CookieJar 只看响应里的 Set-Cookie 头，
 * 从不碰请求体；登录时提交的账号密码不会进 Cookie 存储。
 * <p>
 * 局限：爬虫若在 api/ext 之外的第三方域名上登录（如接口走 CDN、登录走主站），那个域名的
 * Cookie 不在清登录态的范围内（不知道它属于哪个源）；那时只能等以后加“清除全部登录态”。
 */
public final class SiteCookieStore {

    /** 这个源的域名：接口地址与扩展地址里的 host（只取 http/https，file:// 与相对路径跳过）。 */
    public static Set<String> hostsOf(Site site) {
        Set<String> hosts = new LinkedHashSet<>();
        addHost(hosts, site.getApi());
        addHost(hosts, site.getExt());
        return hosts;
    }

    private static void addHost(Set<String> hosts, String url) {
        if (TextUtils.isEmpty(url)) return;
        try {
            String host = Uri.parse(url).getHost();
            if (!TextUtils.isEmpty(host)) hosts.add(host);
        } catch (Exception ignored) {
        }
    }

    /** 这个源有没有存着的登录态（任一域名有 Cookie 即算）。 */
    public static boolean hasLogin(Site site) {
        for (String host : hostsOf(site)) {
            if (WebViewCookies.hasCookies("https://" + host + "/")) return true;
        }
        return false;
    }

    /**
     * 清掉这个源的登录态（只清它自己的域名）。
     *
     * @return 是否清掉了至少一条 Cookie
     */
    public static boolean clearSite(Site site) {
        boolean cleared = false;
        for (String host : hostsOf(site)) {
            if (WebViewCookies.clearHost("https://" + host + "/")) cleared = true;
            if (WebViewCookies.clearHost("http://" + host + "/")) cleared = true;
        }
        return cleared;
    }

    private SiteCookieStore() {
    }
}
