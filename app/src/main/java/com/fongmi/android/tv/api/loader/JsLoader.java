package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.quickjs.RuntimeEntry;
import com.github.catvod.crawler.Spider;
import com.github.catvod.runtime.QuickJsRuntime;

import java.util.Map;

import dalvik.system.DexClassLoader;

public class JsLoader extends SpiderLoader {

    private final QuickJsRuntime loader;
    private final QuickJsRuntime.Session module;
    private volatile String recent;

    public JsLoader() {
        this(new RuntimeEntry());
    }

    JsLoader(JsLoader previous) {
        this(previous.loader);
    }

    private JsLoader(QuickJsRuntime loader) {
        module = loader.session();
        this.loader = loader;
    }

    @Override
    protected void clearReferences() {
        recent = null;
        module.clear();
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return load(key, App.get(), resolveDrpyExt(api, ext), () -> newSpider(key, resolveJsApi(api), BaseLoader.get().dex(jar)));
    }

    // 0.1.110 A.2.2(a)：独立 JS 脚本的 api 是解析后的 file://（如 file://…/豆瓣.js），
    // Module.load 只认 http/assets，file:// 直接抛“Unsupported module URL”，
    // 点站即“爬虫加载失败”。这里把 file:// api 转成本地服务器 http 地址
    // （Server /file/，与 UrlUtil.convert 已有的 file://→/file/ 映射一致），
    // Module 走已有的 http 分支经 OkHttp 取到脚本内容。drpy2 引擎的 api 不受影响：
    // Spider.resolveDrpyApi 仍按文件名把它换成内置 assets 引擎。
    // 注意：只转 api，不动 Module.java（quickjs/ 不在 apply 工作流同步范围，
    // 见 NOTES.md），也不改任何爬虫源文件。
    private static String resolveJsApi(String api) {
        if (api == null || !api.startsWith("file://")) return api;
        return UrlUtil.convert(api);
    }

    // 0.1.110 A.2.3 断点一：drpy2 引擎用 request(ext) 取规则文件，走 OkHttp，
    // file:// 取不到（OkHttp 只认 http/https），规则读不到就空白首页。
    // api 是 drpy2 引擎且 ext 是解析后的 file:// 时，把 ext 转成本地服务器
    // http 地址（Server /file/），引擎就能用同一条 request 取到规则文件。
    // 只转 drpy2（按文件名判断，与 Spider.resolveDrpyApi 同法），独立 js 脚本
    // 的 ext 不动（它们的 ext 不是规则文件路径）。
    private static String resolveDrpyExt(String api, String ext) {
        if (ext == null || !ext.startsWith("file://")) return ext;
        if (!isDrpy2Api(api)) return ext;
        return UrlUtil.convert(ext);
    }

    private static boolean isDrpy2Api(String api) {
        if (api == null) return false;
        String name = api;
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        int hash = name.indexOf('#');
        if (hash >= 0) name = name.substring(0, hash);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return "drpy2.min.js".equalsIgnoreCase(name);
    }

    Spider newSpider(String key, String api, DexClassLoader dex) {
        Spider spider = module.spider(api, dex, JsHost.INSTANCE);
        spider.siteKey = key;
        return spider;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Spider spider = findSpider(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
