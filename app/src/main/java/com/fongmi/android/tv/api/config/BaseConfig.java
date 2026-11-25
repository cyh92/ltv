package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Task;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

abstract class BaseConfig {

    public static final int VOD = 0;
    public static final int LIVE = 1;
    public static final int WALL = 2;

    private final AtomicInteger taskId = new AtomicInteger(0);
    private final Object loadLock = new Object();

    protected boolean sync;
    protected volatile Config config;
    private volatile Future<?> future;

    protected abstract String getTag();

    protected abstract Config defaultConfig();

    protected abstract void load(Config config) throws Throwable;

    protected abstract boolean isLoaded();

    protected void cancelLoad() {
    }

    public synchronized void ensureLoaded() {
        try {
            if (isLoaded()) return;
            if (config == null) config = defaultConfig();
            Server.get().start();
            load(config);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void postEvent() {
        ConfigEvent.common();
    }

    public boolean needSync(String url) {
        Config selected = getConfig();
        return sync || selected.isEmpty() || url.equals(selected.getUrl());
    }

    public Config getConfig() {
        return config == null ? defaultConfig() : config;
    }

    protected void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    protected void setProxy(List<Proxy> proxy) {
        OkHttp.selector().addAll(proxy);
    }

    protected void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public void load(Callback callback) {
        synchronized (loadLock) {
            int id = taskId.incrementAndGet();
            if (future != null && !future.isDone()) {
                cancelLoad();
                future.cancel(true);
            }
            future = Task.submit(() -> loadConfig(id, config, callback));
            // 0.1.108：配置加载 30 秒看门狗。load(config) 里的 parseJar（DexClassLoader
            // + Init.init + Go 启动）没有任何超时，Go 卡住时配置加载永远挂着：回调不触发、
            // 事件不发、首页不拉取，转圈一直转，必须再点一次才好。30 秒后若本次加载仍是
            // 当前那次且没完成，就判它超时：taskId++ 让它失效、取消 future，并在主线程
            // 调 error（“连接超时”会被 0.1.104 自动换源认出来，走自动换源/回退同一条路），
            // 界面必走到终态。已有更新的点击时看门狗什么都不做。
            App.post(() -> {
                synchronized (loadLock) {
                    if (taskId.get() != id || future == null || future.isDone()) return;
                    taskId.incrementAndGet();
                    cancelLoad();
                    future.cancel(true);
                }
                App.post(() -> callback.error("连接超时，请重试"));
            }, 30000);
        }
        callback.start();
    }

    public void cancel() {
        synchronized (loadLock) {
            taskId.incrementAndGet();
            cancelLoad();
            OkHttp.cancel(getTag());
            if (future != null && !future.isDone()) future.cancel(true);
        }
    }

    protected void loadConfig(int id, Config config, Callback callback) {
        try {
            Server.get().start();
            OkHttp.cancel(getTag());
            load(config);
            if (taskId.get() != id) return;
            completeLoad(id, config, callback);
        } catch (Throwable e) {
            if (isCanceled(e)) return;
            e.printStackTrace();
            if (taskId.get() != id) return;
            App.post(() -> {
                if (taskId.get() == id) callback.error(TextUtils.isEmpty(config.getUrl()) ? "" : Notify.getError(R.string.error_config_get, e));
            });
        }
        if (taskId.get() == id) postEvent();
    }

    private void completeLoad(int id, Config config, Callback callback) {
        if (config.equals(this.config)) config.update();
        String notice = config.getNotice();
        App.post(() -> {
            if (taskId.get() != id) return;
            Notify.show(notice);
            callback.success();
        });
    }

    protected boolean isCanceled(Throwable e) {
        if (Thread.currentThread().isInterrupted()) return true;
        boolean canceled = false;
        boolean timedOut = false;
        for (Throwable current = e; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException || current instanceof CancellationException) return true;
            canceled |= current instanceof InterruptedIOException || "Canceled".equals(current.getMessage());
            timedOut |= current instanceof SocketTimeoutException;
        }
        return canceled && !timedOut;
    }

    protected static boolean isLocalFile(String url) {
        return !TextUtils.isEmpty(url) && url.startsWith("file");
    }

    protected static String readLocalText(String url) throws IOException {
        File file = Path.local(url);
        try (InputStream is = new FileInputStream(file); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) os.write(buffer, 0, len);
            return new String(os.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    protected JsonArray fetchArray(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        if (element.isJsonObject()) return new JsonArray();
        if (element.isJsonPrimitive()) element = fetch(element.getAsString());
        JsonArray result = new JsonArray();
        for (JsonElement item : element.getAsJsonArray()) {
            if (item.isJsonPrimitive()) result.addAll(fetch(item.getAsString()));
            else if (item.isJsonObject()) result.add(item);
        }
        return result;
    }

    private JsonArray fetch(String url) {
        try {
            String text = isLocalFile(url) ? readLocalText(url) : OkHttp.string(UrlUtil.convert(url));
            JsonElement parsed = Json.parse(text);
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}
