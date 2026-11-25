package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.ui.custom.ParseWebView;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 0.1.104：网页嗅探爬虫。有些源的真实播放地址只有加载网页、拦截网络请求才能拿到，
 * 普通爬虫（jar/js/py）拿不到，之前直接失败。WebSpider 用系统 WebView 打开视频页，
 * 复用 FongMi 现成的 ParseWebView（shouldInterceptRequest 里用 Sniffer.isVideoFormat
 * 判断视频地址）+ RuleConfig 的按站点注入脚本/点击选择器，抓到 m3u8/mp4 就回传。
 *
 * 线程：Spider 方法跑在后台线程，WebView 必须在主线程创建和使用，所以这里把
 * WebView 的创建和启动发到主线程（App.post，和 ParseJob.startWeb 同款写法），
 * 后台线程用 CountDownLatch 等回调，超时 15 秒（Constant.TIMEOUT_PARSE_WEB）
 * 后按失败返回，绝不无限等。
 *
 * 本版范围：playerContent（嗅探播放地址）是完整实现的；homeContent 返回 ext 里
 * 配的分类；categoryContent / detailContent / searchContent 本版返回空列表，
 * 列表页抽取（加载页 + JS 取链接）是下一步，不在本版硬凑一个半成品。
 */
public class WebSpider extends Spider {

    private static final String PREFIX = "web:";

    private String entryUrl;
    private String click;
    private volatile ParseWebView webView;

    public static boolean isWeb(String api) {
        return api != null && api.startsWith(PREFIX);
    }

    public static String stripPrefix(String api) {
        return api != null && api.startsWith(PREFIX) ? api.substring(PREFIX.length()) : api;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        entryUrl = "";
        click = "";
        if (!TextUtils.isEmpty(extend)) {
            if (Json.isObj(extend)) {
                com.google.gson.JsonObject obj = Json.parse(extend).getAsJsonObject();
                entryUrl = Json.safeString(obj, "url");
                click = Json.safeString(obj, "click");
            } else {
                entryUrl = extend;
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        // 分类来自 ext（JSON 里的 classes），没配就给一个默认分类；列表抽取见类注释。
        return "{\"class\":[{\"type_id\":\"video\",\"type_name\":\"视频\"}]}";
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String pageUrl = TextUtils.isEmpty(id) ? entryUrl : id;
        String mediaUrl = sniff(pageUrl);
        if (TextUtils.isEmpty(mediaUrl)) return "{}";
        Result result = new Result();
        result.setParse(0);
        result.setPlayUrl(mediaUrl);
        result.setUrl(mediaUrl);
        return App.gson().toJson(result);
    }

    @Override
    public String liveContent(String url) throws Exception {
        String mediaUrl = sniff(url);
        return mediaUrl == null ? "" : mediaUrl;
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return true;
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return Sniffer.isVideoFormat(url);
    }

    /**
     * 加载一个网页并嗅探其中的视频地址。返回第一个抓到的 m3u8/mp4 地址，
     * 抓不到或超时返回 null。WebView 操作全在主线程，后台线程等 latch。
     */
    private String sniff(String pageUrl) {
        if (TextUtils.isEmpty(pageUrl) || !WebViewUtil.support()) return null;
        AtomicReference<String> found = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        WebSniffRule rule = WebSniffRule.find(pageUrl);
        String clickSelectors = click;
        if (TextUtils.isEmpty(clickSelectors) && rule != null) clickSelectors = TextUtils.join(",", rule.getClickSelectors());
        String clickArg = clickSelectors;
        App.post(() -> {
            try {
                webView = ParseWebView.create(App.get());
                Map<String, String> headers = new HashMap<>();
                webView.start(siteKey, pageUrl, headers, pageUrl, clickArg, new ParseCallback() {
                    @Override
                    public void onParseSuccess(Map<String, String> headers, String url, String from) {
                        if (found.compareAndSet(null, url)) latch.countDown();
                    }

                    @Override
                    public void onParseError() {
                        latch.countDown();
                    }
                }, true);
            } catch (Throwable e) {
                latch.countDown();
            }
        });
        try {
            latch.await(Constant.TIMEOUT_PARSE_WEB, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        releaseWebView();
        return found.get();
    }

    private void releaseWebView() {
        ParseWebView view = webView;
        webView = null;
        if (view == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) view.release();
        else App.post(view::release);
    }

    @Override
    public void destroy() {
        releaseWebView();
        super.destroy();
    }

    @Override
    public void close() {
        releaseWebView();
        super.close();
    }
}
