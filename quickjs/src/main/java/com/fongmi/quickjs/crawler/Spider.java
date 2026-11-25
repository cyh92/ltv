package com.fongmi.quickjs.crawler;

import android.content.Context;

import com.fongmi.quickjs.Console;
import com.fongmi.quickjs.QuickJS;
import com.fongmi.quickjs.QuickJSContext;
import com.fongmi.quickjs.QuickJSLoader;
import com.fongmi.quickjs.host.Host;
import com.fongmi.quickjs.method.Global;
import com.fongmi.quickjs.method.Invocation;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.Runtime;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import dalvik.system.DexClassLoader;

public class Spider extends com.github.catvod.crawler.Spider {

    private static final long DESTROY_TIMEOUT = 5000;
    private final DexClassLoader dex;
    private final String api;
    private final Host host;
    private final Module module;

    private QuickJS ctx;
    private volatile Global global;
    private volatile boolean closed;
    private volatile boolean destroyed;

    public Spider(String api, DexClassLoader dex) {
        this(api, dex, Host.NONE);
    }

    public Spider(String api, DexClassLoader dex, Host host) {
        this(api, dex, host, new Module());
    }

    public Spider(String api, DexClassLoader dex, Host host, Module module) {
        this.api = api;
        this.dex = dex;
        this.host = host;
        this.module = module;
    }

    private void await(CompletableFuture<Object> future) throws Exception {
        if (closed) future.cancel(false);
        try {
            future.get();
        } catch (InterruptedException error) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private Object call(String func, Object... args) throws Exception {
        try (Invocation current = new Invocation()) {
            return call(current, func, args);
        }
    }

    private Object call(Invocation current, String func, Object... args) throws Exception {
        current.timeout("destroy".equals(func) || "stopDanmaku".equals(func) ? DESTROY_TIMEOUT : host.getCallTimeout());
        CompletableFuture<Object> result = global.call(func, future -> beginCall(current, func), global::endCall, args);
        current.bind(result);
        try {
            return "destroy".equals(func) ? result.join() : result.get();
        } catch (InterruptedException error) {
            result.cancel(false);
            current.cancel();
            Thread.currentThread().interrupt();
            throw error;
        } finally {
            logMetrics(current, func);
        }
    }

    private void beginCall(Invocation current, String func) {
        if (closed && !"destroy".equals(func)) throw new IllegalStateException("Spider closed");
        global.beginCall(current);
    }

    private void logMetrics(Invocation current, String func) {
        Invocation.Metrics metrics = current.metrics();
        SpiderDebug.log("quickjs", "%s total=%.2fms queue=%.2fms owner=%.2fms net=%.2fms html=%.2fms netOps=%d pending=%d streams=%d", func, metrics.elapsedNanos() / 1e6, metrics.queuedNanos() / 1e6, metrics.ownerNanos() / 1e6, metrics.networkNanos() / 1e6, metrics.htmlNanos() / 1e6, metrics.networkOperations(), metrics.pending(), metrics.streams());
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        try {
            initializeJS();
            call("init", extend);
        } catch (Exception | Error error) {
            closeContext(closeResources(error));
            throw error;
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return (String) call("homeContent", filter);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return (String) call("homeVideoContent");
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return (String) call("categoryContent", tid, pg, filter, new QuickJS.Json(new JSONObject(extend).toString()));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        return (String) call("detailContent", (Object) ids.toArray());
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return (String) call("searchContent", key, quick, pg);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return (String) call("playerContent", flag, id, vipFlags.toArray());
    }

    @Override
    public String liveContent(String url) throws Exception {
        return (String) call("liveContent", url);
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return (Boolean) call("manualVideoCheck");
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return (Boolean) call("isVideoFormat", url);
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        try (Invocation current = new Invocation(true)) {
            Object[] array = (Object[]) call(current, "proxy", new QuickJS.Json(new JSONObject(params).toString()));
            return proxyResponse(current, array);
        }
    }

    @Override
    public String action(String action) throws Exception {
        return (String) call("action", action);
    }

    @Override
    public AutoCloseable danmaku(String id, Consumer<JSONObject> emit) throws Exception {
        int token = global.addDanmaku(emit);
        try {
            call("startDanmaku", id, token);
        } catch (Exception | Error error) {
            global.removeDanmaku(token);
            throw error;
        }
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) return;
            global.removeDanmaku(token);
            if (!this.closed) call("stopDanmaku", token);
        };
    }

    @Override
    public synchronized void close() {
        throwFailure(closeResources(null));
    }

    private synchronized Throwable closeResources(Throwable failure) {
        if (closed) return failure;
        closed = true;
        failure = cancelRequests(failure);
        failure = cancelPending(failure);
        return closeHost(failure);
    }

    private Throwable closeHost(Throwable failure) {
        try {
            super.close();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    @Override
    public synchronized void destroy() {
        if (destroyed) return;
        destroyed = true;
        Throwable failure = closeResources(null);
        failure = destroyScript(failure);
        failure = closeContext(failure);
        throwFailure(failure);
    }

    private void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Cannot destroy JavaScript spider", failure);
    }

    private Throwable cancelRequests(Throwable failure) {
        try {
            if (global != null) global.cancelRequests();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    private Throwable cancelPending(Throwable failure) {
        try {
            if (ctx != null) ctx.cancelPending();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    private Throwable destroyScript(Throwable failure) {
        try {
            if (global != null) call("destroy");
        } catch (Exception | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    private Throwable closeContext(Throwable failure) {
        try {
            if (ctx != null) ctx.close();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    private void initializeJS() throws Exception {
        ctx = new QuickJS(QuickJSLoader::init);
        ctx.submit(this::bindGlobals).get();
        ctx.setModuleLoader(module).get();
        ctx.submit(() -> {
            createFun();
            return null;
        }).get();
        createObj();
    }

    private Object bindGlobals() {
        ctx.setUnhandledRejectionListener(SpiderDebug::log).join();
        Console.install(ctx).join();
        global = Global.create(ctx, host, siteKey, api);
        return null;
    }

    private void createFun() {
        if (dex == null) return;
        try {
            Class<?> clz = dex.loadClass("com.github.catvod.js.Function");
            clz.getDeclaredConstructor(QuickJSContext.class).newInstance(new QuickJSContext(ctx));
        } catch (ClassNotFoundException | ReflectiveOperationException ignored) {
            // 0.1.112：JAR 里的 Function 类可能按旧版 QuickJS（com.whl.quickjs）编，
            // 构造器是 <init>(com.whl.quickjs.wrapper.QuickJSContext)，跟新版
            // com.fongmi.quickjs.QuickJSContext 对不上（vox.jar 就是这样）。
            // drpy2 引擎自己用 JS 实现了 pd/pdfl，不需要这个 Java 类，跳过即可，
            // 不要让整个爬虫死掉。原来这里抛 IllegalStateException，果果/酷酷/
            // 爱奇艺[秒播] 全报“爬虫加载失败”。
        }
    }

    private void createObj() throws Exception {
        String effectiveApi = resolveDrpyApi(api);
        String content = module.load(effectiveApi);
        content = transformDrpySource(content);
        evaluateModule(content, effectiveApi);
        evaluateModule(String.format(readHostAsset("js/lib/spider.js"), JSONObject.quote(effectiveApi)), effectiveApi + "#spider");
    }

    // drpy support (0.1.94): when a site's api is a drpy2 engine file, use the engine
    // bundled in the app instead of the user's copy (local or remote). The bundled
    // engine's relative import (./drpy-core-lite.min.js) then resolves inside the APK,
    // so the source works on any phone with no local files and no plugin install.
    // Any other api is returned unchanged.
    private static String resolveDrpyApi(String api) {
        String name = api;
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        int hash = name.indexOf('#');
        if (hash >= 0) name = name.substring(0, hash);
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        return "drpy2.min.js".equalsIgnoreCase(name) ? "assets://js/lib/drpy2/drpy2.min.js" : api;
    }

    // drpy support (0.1.94): make drpy v1 rules and cat rules reachable from the host.
    // A v1 rule is top-level functions (init/home/category/detail/play/search) with no
    // export; a cat rule is `var rule = {...}` with no export. Both are module-scoped,
    // so the host cannot see them. Append a default export (with typeof guards, so a
    // rule that omits a function does not break). Spider classes and files that already
    // export a default are returned unchanged.
    private static String transformDrpySource(String content) {
        if (content == null) return content;
        if (content.contains("class Spider") || content.contains("extends Spider")) return content;
        if (content.contains("export default")) return content;
        if (content.contains("var rule") || content.contains("let rule") || content.contains("const rule")) {
            return content + "\nexport default rule;\n";
        }
        if (isDrpyV1Source(content)) {
            return content + "\nexport default {"
                    + "init: typeof init !== 'undefined' ? init : undefined,"
                    + "home: typeof home !== 'undefined' ? home : undefined,"
                    + "homeVod: typeof homeVod !== 'undefined' ? homeVod : undefined,"
                    + "category: typeof category !== 'undefined' ? category : undefined,"
                    + "detail: typeof detail !== 'undefined' ? detail : undefined,"
                    + "play: typeof play !== 'undefined' ? play : undefined,"
                    + "search: typeof search !== 'undefined' ? search : undefined,"
                    + "proxy: typeof proxy !== 'undefined' ? proxy : undefined,"
                    + "action: typeof action !== 'undefined' ? action : undefined};\n";
        }
        return content;
    }

    private static boolean isDrpyV1Source(String content) {
        return content.contains("function home") || content.contains("function category")
                || content.contains("function detail") || content.contains("function play")
                || content.contains("function search") || content.contains("async function init");
    }

    private static String readHostAsset(String path) {
        try (InputStream input = Runtime.context().getAssets().open(path)) {
            return Path.readOrThrow(input);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read QuickJS asset: " + path, e);
        }
    }

    private void evaluateModule(String source, String name) throws Exception {
        try (Invocation current = new Invocation()) {
            current.timeout(host.getCallTimeout());
            CompletableFuture<Object> result = ctx.evaluateModule(source, name, future -> global.beginCall(current), global::endCall);
            current.bind(result);
            await(result);
        }
    }

    private Object[] proxyResponse(Invocation current, Object[] array) throws Exception {
        if (array == null || array.length < 3) throw new IllegalArgumentException("Proxy response requires status, MIME and body");
        int code = proxyCode(array[0]);
        if (!(array[1] instanceof String mime)) throw new IllegalArgumentException("Proxy response MIME requires a string");
        Map<String, String> headers = proxyHeaders(array.length > 3 ? array[3] : null);
        InputStream body = proxyBody(current, array[2]);
        return new Object[]{code, mime, body, headers};
    }

    private int proxyCode(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Proxy response status requires an integer from 100 to 599");
        int code = number.intValue();
        if (number.doubleValue() != code || code < 100 || code > 599) throw new IllegalArgumentException("Proxy response status requires an integer from 100 to 599");
        return code;
    }

    private Map<String, String> proxyHeaders(Object header) throws JSONException {
        if (header == null) return null;
        if (!(header instanceof QuickJS.Json(String text))) throw new IllegalArgumentException("Proxy response headers require an object");
        JSONObject object = new JSONObject(text);
        Map<String, String> headers = new LinkedHashMap<>();
        for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String key = keys.next();
            headers.put(key, headerValue(object.get(key)));
        }
        return headers;
    }

    private String headerValue(Object value) throws JSONException {
        if (value instanceof String text) return text;
        if (!(value instanceof JSONArray values)) throw new IllegalArgumentException("Invalid proxy response header");
        StringJoiner joined = new StringJoiner(", ");
        for (int i = 0; i < values.length(); i++) {
            if (!(values.get(i) instanceof String text)) throw new IllegalArgumentException("Invalid proxy response header");
            joined.add(text);
        }
        return joined.toString();
    }

    private InputStream proxyBody(Invocation current, Object value) throws JSONException {
        if (value instanceof QuickJS.Json(String text)) value = new JSONTokener(text).nextValue();
        if (value instanceof JSONObject stream && stream.has("$tvStream")) return current.takeStream(stream.getString("$tvStream"));
        if (value instanceof byte[] bytes) return new ByteArrayInputStream(bytes);
        if (value instanceof String text) return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        if (value instanceof Object[] array) return new ByteArrayInputStream(proxyBytes(array));
        if (value instanceof JSONArray array) return new ByteArrayInputStream(proxyBytes(array));
        throw new IllegalArgumentException("Proxy response body requires text or bytes");
    }

    private byte[] proxyBytes(Object[] array) {
        byte[] bytes = new byte[array.length];
        for (int i = 0; i < bytes.length; i++) bytes[i] = proxyByte(array[i]);
        return bytes;
    }

    private byte[] proxyBytes(JSONArray array) {
        byte[] bytes = new byte[array.length()];
        for (int i = 0; i < bytes.length; i++) bytes[i] = proxyByte(array.opt(i));
        return bytes;
    }

    private byte proxyByte(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Proxy bytes must be integers from 0 to 255");
        int byteValue = number.intValue();
        if (number.doubleValue() != byteValue || byteValue < 0 || byteValue > 255) throw new IllegalArgumentException("Proxy bytes must be integers from 0 to 255");
        return (byte) byteValue;
    }
}
