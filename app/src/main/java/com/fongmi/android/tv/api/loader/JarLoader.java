package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.exception.SpiderLoadException;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.Download;
import com.github.catvod.net.Net;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Local;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.RuntimeModule;

import org.json.JSONObject;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class JarLoader extends SpiderLoader {

    private static final String TAG = "JarLoader";

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Object> locks;
    // 0.1.108：本地 jar 源文件的长度+修改时间（key → {length, lastModified}），
    // JarLoader 跨切换保留后，靠它发现用户换了新的 spider.jar 文件。
    private final ConcurrentHashMap<String, long[]> fileStamps;
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
        fileStamps = new ConcurrentHashMap<>();
    }

    @Override
    protected void clearReferences() {
        loaders.clear();
        methods.clear();
        locks.clear();
        fileStamps.clear();
        recent = null;
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    private void load(String key, File file) {
        if (Thread.interrupted()) return;
        if (!Path.exists(file)) {
            // 0.1.108：缺文件不再静默跳过。以前这里只 Log.w 就 return，loader 永远是
            // null，配置照常列出 119 个站，点开才报“爬虫加载失败”，且 logcat 里没有任何
            // 可定位的记录。现在直接抛 SpiderLoadException：配置加载时抛错会走 0.1.103
            // 的快照恢复（留在旧接口上、不留空白首页）；点站时抛错走 SpiderLoader.load
            // 的包装，照常弹“爬虫加载失败”。两种路径都比静默失败可诊断。
            Log.e(TAG, "JAR missing or empty, cannot load: " + file);
            throw new SpiderLoadException(new IllegalStateException("Cannot load spider JAR: " + file));
        }
        // 0.1.105: setReadOnly() is best-effort only. On shared storage it often
        // fails (read-only media / SAF), and DexClassLoader does NOT need the file
        // to be read-only. The old `|| !file.setReadOnly()` silently dropped the
        // whole jar here, so every csp_ site depending on it later failed with
        // "Cannot load spider JAR" ("爬虫加载失败").
        try {
            file.setReadOnly();
        } catch (Exception ignored) {
        }
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, RuntimeModule.getSpiderClassLoader());
        try {
            initDex(loader);
        } catch (Exception e) {
            throw new SpiderLoadException(e);
        }
        cacheLoader(key, loader);
    }

    private synchronized void cacheLoader(String key, DexClassLoader loader) {
        checkOpen();
        invokeProxy(key, loader);
        loaders.put(key, loader);
    }

    static void initDex(DexClassLoader loader) throws Exception {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            clz.getMethod("init", Context.class).invoke(null, App.get());
        } catch (ClassNotFoundException ignored) {
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception error) throw error;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void parseJar(String key, String jar) {
        Object lock;
        synchronized (this) {
            checkOpen();
            if (loaders.containsKey(key)) {
                // 0.1.108：JarLoader 跨切换保留后，本地 jar 文件被用户换掉时缓存会
                // 一直命中旧的 DexClassLoader。命中时校验源文件长度/修改时间，变了
                // 就逐出重载；没变才直接返回。http jar 不校验（URL 缓存 + md5 机制已覆盖）。
                if (!isLocalJarChanged(key, jar)) return;
                loaders.remove(key);
                methods.remove(key);
                fileStamps.remove(key);
            }
            lock = locks.computeIfAbsent(key, k -> new Object());
        }
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        synchronized (lock) {
            checkOpen();
            if (!loaders.containsKey(key)) {
                loadJar(key, jar);
                recordLocalJarStamp(key, jar);
            }
        }
    }

    // 本地 jar 源文件的长度+修改时间（换文件检测用）；不是本地文件返回 null。
    private static long[] localJarStamp(String jar) {
        String plain = jar.split(";md5;")[0];
        if (!plain.startsWith("file") && !new File(plain).isFile()) return null;
        File src = localJarFile(plain);
        if (!Path.exists(src)) return null;
        return new long[]{src.length(), src.lastModified()};
    }

    private boolean isLocalJarChanged(String key, String jar) {
        long[] recorded = fileStamps.get(key);
        if (recorded == null) return false;
        long[] current = localJarStamp(jar);
        if (current == null) return false;
        return current[0] != recorded[0] || current[1] != recorded[1];
    }

    private void recordLocalJarStamp(String key, String jar) {
        long[] stamp = localJarStamp(jar);
        if (stamp != null) fileStamps.put(key, stamp);
    }

    // 0.1.108：本地 jar 路径确定性剥离 file:// scheme（与 Path.exists(String)
    // 同一规则）。不要用 Path.local()：它 replace("file:/","") 后先拼
    // new File(root(), path) 再回退 new File(path)，靠 Linux 把 //storage 解析成
    // /storage，脆弱且不可读；file:/ 单斜杠与 file:// 三斜杠走不同分支，容易在
    // 共享存储路径上悄悄算错。
    private static File localJarFile(String jar) {
        String path = jar;
        if (path.startsWith("file://")) path = path.substring("file://".length());
        else if (path.startsWith("file:")) path = path.substring("file:".length());
        return new File(path);
    }

    private static synchronized File copyLocalJar(String jar) {
        File src = localJarFile(jar);
        if (!Path.exists(src)) {
            // 0.1.108：源文件根本不存在时不再返回一个不存在的 src（load() 收到后会静默
            // 跳过，loader 留空，点站才报“爬虫加载失败”）。直接抛错，配置加载走 0.1.103
            // 快照恢复，点站走 SpiderLoader 的 toast。
            Log.e(TAG, "Local spider JAR not found: " + jar);
            throw new SpiderLoadException(new IllegalStateException("Cannot load spider JAR: " + jar));
        }
        File dst = Path.jar(jar);
        if (Path.exists(dst) && Path.exists(src) && dst.length() == src.length() && dst.lastModified() >= src.lastModified()) return dst;
        // 0.1.105: make sure the cache dir exists before copying, so a missing
        // dir can never be the reason the local jar falls back to shared storage.
        File parent = dst.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(dst.getParent(), dst.getName() + ".tmp");
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            if (!tmp.renameTo(dst)) throw new IOException("Cannot move cached JAR into place");
            return dst;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Cannot cache local JAR, use original: " + jar, e);
            if (tmp.exists()) tmp.delete();
            return src;
        }
    }

    private void loadJar(String key, String jar) {
        String[] texts = jar.split(";md5;");
        String md5 = texts.length > 1 ? texts[1].trim() : "";
        if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
        jar = texts[0];
        if (!md5.isEmpty() && Crypto.equals(Path.jar(jar), md5)) load(key, Path.jar(jar));
        else if (jar.startsWith("http")) load(key, Download.create(jar, Path.jar(jar)).get());
        else if (jar.startsWith("file")) load(key, copyLocalJar(jar));
        // 0.1.108：裸本地路径兜底。以前除 http/file 外任何形式都悄悄落空，loader 留
        // null，点站才报“爬虫加载失败”。现在真实存在的本地文件按本地 jar 加载；其他形式
        // 记 Log.e 而不是静默跳过。
        else if (isLocalFile(jar)) load(key, copyLocalJar(jar));
        else Log.e(TAG, "Unsupported spider JAR location, skip loading: " + jar);
    }

    private static boolean isLocalFile(String jar) {
        return !TextUtils.isEmpty(jar) && new File(jar).isFile();
    }

    public DexClassLoader dex(String jar) {
        if (TextUtils.isEmpty(jar)) return null;
        String jaKey = Crypto.md5(jar);
        parseJar(jaKey, jar);
        DexClassLoader loader = loaders.get(jaKey);
        if (loader == null) throw new SpiderLoadException(new IllegalStateException("Cannot load spider JAR: " + jar));
        return loader;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return load(Crypto.md5(jar) + key, App.get(), ext, () -> newSpider(key, api, jar));
    }

    Spider newSpider(String key, String api, String jar) throws Exception {
        String jaKey = Crypto.md5(jar);
        parseJar(jaKey, jar);
        DexClassLoader loader = loaders.get(jaKey);
        if (loader == null) throw new IllegalStateException("Cannot load spider JAR: " + jar);
        return newSpider(key, api, loader);
    }

    private static DexClassLoader openDex(File file) throws Exception {
        if (!Path.exists(file)) throw new IllegalArgumentException("JAR is missing or empty: " + file);
        // 0.1.105: setReadOnly() is best-effort only, see load() — a failure here
        // must not fail the whole jar (crawler test session hit the same bug).
        try {
            file.setReadOnly();
        } catch (Exception ignored) {
        }
        String cachePath = file.getParent();
        return new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, RuntimeModule.getSpiderClassLoader());
    }

    @SuppressWarnings("deprecation")
    static List<String> spiders(File file) throws Exception {
        DexClassLoader loader = openDex(file);
        TreeSet<String> names = new TreeSet<>();
        DexFile dex = new DexFile(file);
        try (Closeable close = dex::close) {
            readSpiders(dex, loader, names);
        }
        return new ArrayList<>(names);
    }

    private static void readSpiders(DexFile dex, DexClassLoader loader, TreeSet<String> names) throws Exception {
        Enumeration<String> entries = dex.entries();
        while (entries.hasMoreElements()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Crawler test session has ended");
            addSpider(entries.nextElement().replace('/', '.'), loader, names);
        }
    }

    private static void addSpider(String name, DexClassLoader loader, TreeSet<String> names) throws Exception {
        String prefix = "com.github.catvod.spider.";
        if (!name.startsWith(prefix)) return;
        Class<?> clz = Class.forName(name, false, loader);
        if (clz.getClassLoader() != loader || !Spider.class.isAssignableFrom(clz) || !Modifier.isPublic(clz.getModifiers()) || Modifier.isAbstract(clz.getModifiers())) return;
        if (Arrays.stream(clz.getConstructors()).anyMatch(constructor -> constructor.getParameterTypes().length == 0)) names.add("csp_" + name.substring(prefix.length()));
    }

    static DexClassLoader newDex(File file) throws Exception {
        DexClassLoader loader = openDex(file);
        initDex(loader);
        return loader;
    }

    static Spider newSpider(String key, String api, DexClassLoader loader) throws Exception {
        Spider spider;
        try {
            spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.substring(4)).getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception error) throw error;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
        spider.siteKey = key;
        spider.net = new Net(JsHost.INSTANCE, key);
        spider.local = new Local(key);
        return spider;
    }

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            RuntimeModule.MissingException missing = RuntimeModule.MissingException.find(e);
            if (missing != null) throw missing;
            e.printStackTrace();
            return null;
        }
    }
}
