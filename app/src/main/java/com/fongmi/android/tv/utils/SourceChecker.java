package com.fongmi.android.tv.utils;

import android.os.SystemClock;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 源检测：按源的导入方式验有效性，分绿/黄/红三档。
 * 在线源用共享 OkHttp client 拉一次（每次调用单独设 30 秒超时，不动共享 client）并计时；
 * 单个脚本验它背后的站能不能打开（从文件里抽第一个 http(s) 地址探站，8 秒超时）；
 * 文件夹里每个脚本单独探，同站只探一次，4 线程并行。
 * 只做“能不能用”的粗验，不跑脚本、不切换当前配置。
 * 成绩按站点 key 存进 CheckStore，换源列表据此画色点。
 */
public class SourceChecker {

    public static final int COUNT_NONE = 0;
    public static final int COUNT_SITES = 1;
    public static final int COUNT_CHANNELS = 2;
    public static final int COUNT_SOURCES = 3;

    /** 三档成绩：绿=优质（快、资源全），黄=慢或部分失效，红=失效。 */
    public static final int GRADE_NONE = 0;
    public static final int GRADE_GREEN = 1;
    public static final int GRADE_YELLOW = 2;
    public static final int GRADE_RED = 3;

    /** 黄档细分：慢 / 部分失效 / 验不了。 */
    public static final int YELLOW_NONE = 0;
    public static final int YELLOW_SLOW = 1;
    public static final int YELLOW_PARTIAL = 2;
    public static final int YELLOW_UNVERIFIED = 3;

    private static final int TIMEOUT_SECONDS = 30;
    private static final int PROBE_TIMEOUT_SECONDS = 8;
    private static final long SLOW_CONFIG_MS = 10_000;
    private static final long SLOW_SCRIPT_MS = 5_000;

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[A-Za-z0-9._~:/?#@!$&'()*+,;=%\\-]+");

    public static class Result {
        public final boolean ok;
        /** 失败原因（R.string），ok 时为 0。 */
        public final int reason;
        public final int count;
        public final int countKind;
        public final int grade;
        public final int yellowKind;
        /** 好用的个数（部分失效时小于 total）。 */
        public final int good;
        public final int total;
        /** 站点 key → 成绩（换源列表画色点用）。 */
        public final Map<String, Integer> siteGrades = new HashMap<>();

        private Result(boolean ok, int reason, int count, int countKind, int grade, int yellowKind, int good, int total) {
            this.ok = ok;
            this.reason = reason;
            this.count = count;
            this.countKind = countKind;
            this.grade = grade;
            this.yellowKind = yellowKind;
            this.good = good;
            this.total = total;
        }

        public static Result green(int count, int countKind) {
            return new Result(true, 0, count, countKind, GRADE_GREEN, YELLOW_NONE, count, count);
        }

        public static Result yellow(int count, int countKind, int yellowKind) {
            return new Result(true, 0, count, countKind, GRADE_YELLOW, yellowKind, count, count);
        }

        public static Result partial(int good, int total, int countKind) {
            return new Result(true, 0, good, countKind, GRADE_YELLOW, YELLOW_PARTIAL, good, total);
        }

        public static Result red(int reason) {
            return new Result(false, reason, 0, COUNT_NONE, GRADE_RED, YELLOW_NONE, 0, 0);
        }
    }

    /** 成绩对应的点颜色（绿/黄/红）。 */
    public static int gradeColor(int grade) {
        switch (grade) {
            case GRADE_GREEN:
                return 0xFF2E9E5B;
            case GRADE_YELLOW:
                return 0xFFE6A700;
            case GRADE_RED:
                return 0xFFD32F2F;
            default:
                return 0xFF888888;
        }
    }

    public static Result check(ConfigManager.Sub sub) {
        try {
            if (sub.isFolder()) return checkFolder(sub);
            if (sub.isFile()) return gradeScript(new File(sub.link), new ConcurrentHashMap<>());
            if (sub.isJson()) {
                if (!new File(sub.link).exists()) return Result.red(R.string.config_reason_no_file);
                return checkJsonConfig(readFile(sub.link), 0, sub.id);
            }
            if (sub.isUrl()) {
                long start = SystemClock.elapsedRealtime();
                String text = fetch(sub.link);
                return checkJsonConfig(text, SystemClock.elapsedRealtime() - start, sub.id);
            }
            if (sub.isLiveFile()) {
                if (!new File(sub.link).exists()) return Result.red(R.string.config_reason_no_file);
                return checkLive(readFile(sub.link), 0);
            }
            if (sub.isLiveUrl()) {
                long start = SystemClock.elapsedRealtime();
                String text = fetch(sub.link);
                return checkLive(text, SystemClock.elapsedRealtime() - start);
            }
        } catch (SocketTimeoutException e) {
            return Result.red(R.string.config_reason_timeout);
        } catch (IOException e) {
            return Result.red(R.string.config_reason_open_fail);
        } catch (Exception e) {
            return Result.red(R.string.config_reason_open_fail);
        }
        return Result.red(R.string.config_reason_bad_format);
    }

    /** 文件夹：每个脚本单独探站；全好变绿，部分坏变黄，全坏变红。 */
    private static Result checkFolder(ConfigManager.Sub sub) {
        File dir = new File(sub.link);
        if (!dir.exists() || !dir.isDirectory()) return Result.red(R.string.config_reason_no_file);
        File[] children = dir.listFiles();
        if (children == null) return Result.red(R.string.config_reason_empty);
        List<File> scripts = new ArrayList<>();
        for (File file : children) {
            String lower = file.getName().toLowerCase();
            if (file.isFile() && (lower.endsWith(".py") || lower.endsWith(".js"))) scripts.add(file);
        }
        if (scripts.isEmpty()) return Result.red(R.string.config_reason_empty);
        ConcurrentHashMap<String, Probe> cache = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Result>> futures = new ArrayList<>();
        for (File file : scripts) futures.add(pool.submit(() -> gradeScript(file, cache)));
        Map<String, Integer> siteGrades = new HashMap<>();
        int good = 0;
        int green = 0;
        for (int i = 0; i < scripts.size(); i++) {
            Result one;
            try {
                one = futures.get(i).get();
            } catch (Exception e) {
                one = Result.red(R.string.config_reason_open_fail);
            }
            siteGrades.put(scriptKey(scripts.get(i)), one.grade);
            if (one.grade != GRADE_RED) good++;
            if (one.grade == GRADE_GREEN) green++;
        }
        pool.shutdownNow();
        int red = scripts.size() - good;
        Result result;
        if (good == 0) result = Result.red(R.string.config_reason_open_fail);
        else if (red == 0 && green == scripts.size()) result = Result.green(good, COUNT_SOURCES);
        else if (red > 0) result = Result.partial(good, scripts.size(), COUNT_SOURCES);
        else result = Result.yellow(good, COUNT_SOURCES, YELLOW_SLOW);
        result.siteGrades.putAll(siteGrades);
        return result;
    }

    /** 单个脚本源：文件在且非空，再探它背后的站；抽不到地址就标“未验证”。 */
    private static Result gradeScript(File file, ConcurrentHashMap<String, Probe> cache) {
        if (!file.exists() || !file.isFile()) return Result.red(R.string.config_reason_no_file);
        if (file.length() == 0) return Result.red(R.string.config_reason_empty);
        String text;
        try {
            text = readTextLimited(file);
        } catch (IOException e) {
            return Result.red(R.string.config_reason_open_fail);
        }
        String url = firstUrl(text);
        if (url == null) {
            Result result = Result.yellow(0, COUNT_NONE, YELLOW_UNVERIFIED);
            result.siteGrades.put(scriptKey(file), GRADE_YELLOW);
            return result;
        }
        Probe probe = probeHost(url, cache);
        Result result;
        if (!probe.reachable) result = probe.timeout ? Result.red(R.string.config_reason_timeout) : Result.red(R.string.config_reason_open_fail);
        else if (probe.ms < SLOW_SCRIPT_MS) result = Result.green(0, COUNT_NONE);
        else result = Result.yellow(0, COUNT_NONE, YELLOW_SLOW);
        result.siteGrades.put(scriptKey(file), result.grade);
        return result;
    }

    /** 脚本文件对应的站点 key（和 ScriptManager.scan() 的规则一致：script_ + 文件名去后缀）。 */
    private static String scriptKey(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return "script_" + base;
    }

    private static String firstUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /** 探一个站的根地址（同站只探一次，靠 cache）。只要服务器回了任何 HTTP 状态就算通。 */
    private static Probe probeHost(String url, ConcurrentHashMap<String, Probe> cache) {
        String root = rootOf(url);
        if (root == null) return new Probe(false, false, 0);
        Probe cached = cache.get(root);
        if (cached != null) return cached;
        Probe probe = probeOnce(root);
        cache.put(root, probe);
        return probe;
    }

    private static String rootOf(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) return null;
        String scheme = url.substring(0, schemeEnd);
        String rest = url.substring(schemeEnd + 3);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String hostPort = rest.substring(0, end);
        if (hostPort.isEmpty()) return null;
        return scheme + "://" + hostPort;
    }

    private static Probe probeOnce(String root) {
        long start = SystemClock.elapsedRealtime();
        try {
            Call call = OkHttp.client().newCall(new Request.Builder().url(root).head().build());
            call.timeout().timeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try (Response response = call.execute()) {
                return new Probe(true, false, SystemClock.elapsedRealtime() - start);
            }
        } catch (SocketTimeoutException e) {
            return new Probe(false, true, SystemClock.elapsedRealtime() - start);
        } catch (IOException e) {
            return new Probe(false, false, SystemClock.elapsedRealtime() - start);
        }
    }

    private static class Probe {
        final boolean reachable;
        final boolean timeout;
        final long ms;

        Probe(boolean reachable, boolean timeout, long ms) {
            this.reachable = reachable;
            this.timeout = timeout;
            this.ms = ms;
        }
    }

    /** 点播配置（在线链接或本地 .json）：必须是含 sites 数组的 JSON 对象；缺 key 或 api 的站不算好站。 */
    private static Result checkJsonConfig(String text, long ms, String subId) {
        if (text == null) return Result.red(R.string.config_reason_open_fail);
        JsonElement parsed;
        try {
            parsed = App.gson().fromJson(stripBom(text), JsonElement.class);
        } catch (JsonSyntaxException e) {
            return Result.red(R.string.config_reason_bad_format);
        }
        if (parsed == null || !parsed.isJsonObject()) return Result.red(R.string.config_reason_bad_format);
        JsonObject obj = parsed.getAsJsonObject();
        if (!obj.has("sites") || !obj.get("sites").isJsonArray()) return Result.red(R.string.config_reason_bad_format);
        JsonArray sites = obj.getAsJsonArray("sites");
        if (sites.size() == 0) return Result.red(R.string.config_reason_empty);
        Map<String, Integer> siteGrades = new HashMap<>();
        int good = 0;
        for (JsonElement element : sites) {
            if (!element.isJsonObject()) continue;
            JsonObject site = element.getAsJsonObject();
            String key = site.has("key") && site.get("key").isJsonPrimitive() ? site.get("key").getAsString() : "";
            String api = site.has("api") && site.get("api").isJsonPrimitive() ? site.get("api").getAsString() : "";
            boolean usable = !key.isEmpty() && !api.isEmpty();
            if (usable) good++;
            if (!key.isEmpty()) siteGrades.put(subId + "|" + key, usable ? GRADE_GREEN : GRADE_RED);
        }
        if (good == 0) {
            Result result = Result.red(R.string.config_reason_bad_format);
            result.siteGrades.putAll(siteGrades);
            return result;
        }
        Result result;
        if (good == sites.size() && ms < SLOW_CONFIG_MS) result = Result.green(good, COUNT_SITES);
        else if (good < sites.size()) result = Result.partial(good, sites.size(), COUNT_SITES);
        else result = Result.yellow(good, COUNT_SITES, YELLOW_SLOW);
        if (result.yellowKind == YELLOW_SLOW) {
            for (Map.Entry<String, Integer> entry : siteGrades.entrySet()) {
                if (entry.getValue() == GRADE_GREEN) entry.setValue(GRADE_YELLOW);
            }
        }
        result.siteGrades.putAll(siteGrades);
        return result;
    }

    /** 直播源（在线链接或本地 .txt/.m3u/.m3u8）：JSON 数组播单，或 m3u/txt 播单里至少有一个频道。 */
    private static Result checkLive(String text, long ms) {
        if (text == null) return Result.red(R.string.config_reason_open_fail);
        String content = stripBom(text);
        JsonElement parsed = null;
        try {
            parsed = App.gson().fromJson(content, JsonElement.class);
        } catch (JsonSyntaxException ignored) {
        }
        if (parsed != null && parsed.isJsonArray()) {
            JsonArray array = parsed.getAsJsonArray();
            if (array.size() == 0) return Result.red(R.string.config_reason_empty);
            if (ms >= SLOW_CONFIG_MS) return Result.yellow(array.size(), COUNT_CHANNELS, YELLOW_SLOW);
            return Result.green(array.size(), COUNT_CHANNELS);
        }
        if (parsed != null && parsed.isJsonObject()) return Result.red(R.string.config_reason_bad_format);
        int channels = countChannels(content);
        if (channels == 0) return Result.red(R.string.config_reason_empty);
        if (ms >= SLOW_CONFIG_MS) return Result.yellow(channels, COUNT_CHANNELS, YELLOW_SLOW);
        return Result.green(channels, COUNT_CHANNELS);
    }

    private static int countChannels(String text) {
        int count = 0;
        boolean m3u = text.contains("#EXTM3U");
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#genre#")) continue;
            if (m3u) {
                if (line.startsWith("#EXTINF")) count++;
            } else if (!line.startsWith("#")) {
                int comma = line.indexOf(',');
                if (comma > 0 && line.substring(comma + 1).contains("://")) count++;
            }
        }
        return count;
    }

    /** 打开一个链接拿全文（跟随跳转；不是 http(s) 直接判打不开）。 */
    private static String fetch(String url) throws IOException {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IOException("not http(s): " + url);
        }
        Call call = OkHttp.client().newCall(new Request.Builder().url(url).build());
        call.timeout().timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("http " + response.code());
            return response.body().string();
        }
    }

    /** 读本地文件全文；文件不存在抛 IOException（上层判“文件不存在”）。 */
    private static String readFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) throw new IOException("no file: " + path);
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** 读脚本文件的前 1MB（抽地址够用，大文件也不卡）。 */
    private static String readTextLimited(File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buffer)) != -1) {
                int take = Math.min(n, 1024 * 1024 - total);
                if (take <= 0) break;
                out.write(buffer, 0, take);
                total += take;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
