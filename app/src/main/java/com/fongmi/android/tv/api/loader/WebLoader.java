package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;

import java.util.Map;

/**
 * 0.1.104：网页嗅探爬虫的加载器，跟 PyLoader / JsLoader / JarLoader 平级。
 * WebSpider 不需要外部运行时（不用 Chaquopy/QuickJS），所以构造器不初始化
 * 任何 runtime；蜘蛛的创建和缓存走父类 SpiderLoader.load。
 */
public class WebLoader extends SpiderLoader {

    private volatile String recent;

    public WebLoader() {
    }

    WebLoader(WebLoader previous) {
    }

    @Override
    protected void clearReferences() {
        recent = null;
    }

    public Spider getSpider(String key, String api, String ext) {
        return load(key, App.get(), ext, () -> newSpider(key, api, ext));
    }

    Spider newSpider(String key, String api, String ext) {
        WebSpider spider = new WebSpider();
        spider.siteKey = key;
        return spider;
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Spider spider = findSpider(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
