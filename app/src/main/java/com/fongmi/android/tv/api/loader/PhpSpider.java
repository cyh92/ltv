package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.util.Base64;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.exception.SpiderLoadException;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.UriUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PHP 爬虫（0.1.106 新增）。
 * <p>
 * 做法：每次爬虫调用都起一个 php 进程跑 runner.php（CLI 模式，JSON 走 stdin/stdout），
 * 与 TV+ 的 PhpSpider 同款式（runner + bin/php）。爬虫 .php 文件里按 TVBox 约定
 * 定义与 Spider 同名的全局函数（homeContent / categoryContent / detailContent /
 * playerContent / searchContent / liveContent / proxy / action / homeVideoContent），
 * 每个函数返回 JSON 字符串或数组。
 * <p>
 * 注意：解释器不在 APK 里（见 {@link PhpLoader#isAvailable()}）。解释器缺失时，
 * 加载在 {@link #init(Context, String)} 里直接失败（原因写进异常，不静默当空源）。
 * 进程调用是同步阻塞的——爬虫本来就在后台线程调，这里不另起线程（与 PyLoader 同款）。
 */
public class PhpSpider extends Spider {

    /** 单次 php 调用的超时（爬虫接口大多是网络请求，30 秒够一次往返）。 */
    private static final long CALL_TIMEOUT_MS = 30000;

    private final String api;
    private File spiderFile;

    public PhpSpider(String key, String api) {
        this.siteKey = key;
        this.api = api;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (!PhpLoader.isAvailable()) {
            throw new SpiderLoadException(new IllegalStateException("PHP interpreter is missing: " + PhpLoader.binary().getAbsolutePath() + " (PHP 解释器未安装，.php 爬虫源无法运行；见 0.1.106 说明)"));
        }
        ensureRunner();
        spiderFile = resolveSpiderFile(api);
        if (spiderFile == null || !Path.exists(spiderFile)) {
            throw new SpiderLoadException(new IllegalStateException("Cannot load PHP spider: " + api));
        }
        super.init(context, extend);
    }

    /** runner.php 首次使用时从 assets 复制到私有目录（之后直接用，不重复复制）。 */
    private static void ensureRunner() throws Exception {
        File runner = PhpLoader.runnerFile();
        if (Path.exists(runner)) return;
        File parent = runner.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream in = App.get().getAssets().open("php/runner.php");
             OutputStream out = new FileOutputStream(runner)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        }
    }

    /**
     * 爬虫 .php 文件落盘：file:// 直接用本地路径；http(s) 先下载到缓存（与 py/js 的
     * 远程源同款处理）。相对路径在 VodConfig 里已经 resolveRelative 成绝对路径了。
     */
    private static File resolveSpiderFile(String api) throws Exception {
        String scheme = UriUtil.scheme(api);
        if ("http".equals(scheme) || "https".equals(scheme)) {
            String text = OkHttp.string(api);
            if (text == null || text.isEmpty()) return null;
            File dir = new File(Path.cache(), "php");
            dir.mkdirs();
            File file = new File(dir, "spider_" + Math.abs(api.hashCode()) + ".php");
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return file;
        }
        String path = api;
        if (path.startsWith("file://")) path = path.substring("file://".length());
        return new File(path);
    }

    /**
     * 调一次 php：{@code php runner.php <spider.php>}，请求 JSON 走 stdin，
     * 爬虫函数的返回值（JSON 字符串）从 stdout 原样返回。
     */
    private String call(String method, Object... args) throws Exception {
        JSONObject request = new JSONObject();
        request.put("method", method);
        request.put("args", new JSONArray(java.util.Arrays.asList(args)));
        byte[] input = request.toString().getBytes(StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(PhpLoader.binary().getAbsolutePath(), PhpLoader.runnerFile().getAbsolutePath(), spiderFile.getAbsolutePath())
                .redirectErrorStream(false)
                .start();
        try {
            process.getOutputStream().write(input);
            process.getOutputStream().close();
            boolean done = process.waitFor(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                throw new Exception("PHP spider timed out: " + method);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int code = process.exitValue();
            if (code != 0) {
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new Exception("PHP spider failed (" + method + ", exit " + code + "): " + (error.isEmpty() ? output : error));
            }
            return output;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return call("homeContent", filter);
    }

    @Override
    public String homeVideoContent() throws Exception {
        return call("homeVideoContent");
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return call("categoryContent", tid, pg, filter, extend);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        return call("detailContent", ids);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return call("searchContent", key, quick, pg);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return call("playerContent", flag, id, vipFlags);
    }

    @Override
    public String liveContent(String url) throws Exception {
        return call("liveContent", url);
    }

    @Override
    public String action(String action) throws Exception {
        return call("action", action);
    }

    @Override
    public boolean manualVideoCheck() throws Exception {
        return Boolean.parseBoolean(call("manualVideoCheck"));
    }

    @Override
    public boolean isVideoFormat(String url) throws Exception {
        return Boolean.parseBoolean(call("isVideoFormat", url));
    }

    /**
     * 代理：runner 的 proxy 函数约定返回 {@code {"code":200,"type":"...","body":"<base64>"}}。
     * 爬虫没实现 proxy（或调用失败）时按 Spider 默认回 404，不抛错——播放器遇到 404
     * 会按原链路处理，与其它引擎的默认行为一致。
     */
    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        try {
            JSONObject json = new JSONObject(call("proxy", params));
            int code = json.optInt("code", 200);
            String type = json.optString("type", "application/octet-stream");
            byte[] body = Base64.decode(json.optString("body", ""), Base64.DEFAULT);
            return new Object[]{code, type, new ByteArrayInputStream(body)};
        } catch (Exception e) {
            return super.proxy(params);
        }
    }
}
