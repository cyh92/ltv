package com.fongmi.android.tv.player.exo;

import android.content.Context;

import androidx.annotation.Nullable;

import org.chromium.net.CronetEngine;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 0.1.107：Cronet 内嵌引擎单例。
 * <p>
 * 用 cronet-embedded（不依赖 Google Play Services），引擎只建一次；创建失败
 * （如 native 库加载失败）返回 null，调用方回落到 OkHttp，不影响播放。
 */
public final class CronetEngineHolder {

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile CronetEngine engine;
    private static volatile boolean tried;

    private CronetEngineHolder() {
    }

    /** 返回共享的 CronetEngine，建不出来返回 null。 */
    @Nullable
    public static synchronized CronetEngine getEngine(Context context) {
        if (engine != null) return engine;
        if (tried) return null;
        tried = true;
        try {
            engine = new CronetEngine.Builder(context.getApplicationContext()).build();
        } catch (Throwable t) {
            engine = null;
        }
        return engine;
    }

    public static boolean isAvailable(Context context) {
        return getEngine(context) != null;
    }

    /** CronetDataSource 的回调执行器。 */
    public static Executor getExecutor() {
        return EXECUTOR;
    }
}
