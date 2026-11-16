package com.fongmi.android.tv.utils;

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
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 源检测：按源的导入方式验有效性。
 * 在线源用共享 OkHttp client 拉一次（每次调用单独设 30 秒超时，不动共享 client）；
 * 本地源直接读文件。只做“能不能用”的粗验，不跑脚本、不切换当前配置。
 */
public class SourceChecker {

    public static final int COUNT_NONE = 0;
    public static final int COUNT_SITES = 1;
    public static final int COUNT_CHANNELS = 2;
    public static final int COUNT_SOURCES = 3;

    private static final int TIMEOUT_SECONDS = 30;

    public static class Result {
        public final boolean ok;
        /** 失败原因（R.string），ok 时为 0。 */
        public final int reason;
        public final int count;
        public final int countKind;

        private Result(boolean ok, int reason, int count, int countKind) {
            this.ok = ok;
            this.reason = reason;
            this.count = count;
            this.countKind = countKind;
        }

        public static Result ok() {
            return new Result(true, 0, 0, COUNT_NONE);
        }

        public static Result ok(int count, int countKind) {
            return new Result(true, 0, count, countKind);
        }

        public static Result fail(int reason) {
            return new Result(false, reason, 0, COUNT_NONE);
        }
    }

    public static Result check(ConfigManager.Sub sub) {
        try {
            if (sub.isFolder()) return checkFolder(sub);
            if (sub.isFile()) return checkScriptFile(sub);
            if (sub.isJson()) {
                if (!new File(sub.link).exists()) return Result.fail(R.string.config_reason_no_file);
                return checkJsonConfig(readFile(sub.link));
            }
            if (sub.isUrl()) return checkJsonConfig(fetch(sub.link));
            if (sub.isLiveFile()) {
                if (!new File(sub.link).exists()) return Result.fail(R.string.config_reason_no_file);
                return checkLive(readFile(sub.link));
            }
            if (sub.isLiveUrl()) return checkLive(fetch(sub.link));
        } catch (SocketTimeoutException e) {
            return Result.fail(R.string.config_reason_timeout);
        } catch (IOException e) {
            return Result.fail(R.string.config_reason_open_fail);
        } catch (Exception e) {
            return Result.fail(R.string.config_reason_open_fail);
        }
        return Result.fail(R.string.config_reason_bad_format);
    }

    /** 文件夹：存在且里面至少有一个 .py/.js。 */
    private static Result checkFolder(ConfigManager.Sub sub) {
        File dir = new File(sub.link);
        if (!dir.exists() || !dir.isDirectory()) return Result.fail(R.string.config_reason_no_file);
        File[] children = dir.listFiles();
        if (children == null) return Result.fail(R.string.config_reason_empty);
        int count = 0;
        for (File file : children) {
            String lower = file.getName().toLowerCase();
            if (file.isFile() && (lower.endsWith(".py") || lower.endsWith(".js"))) count++;
        }
        if (count == 0) return Result.fail(R.string.config_reason_empty);
        return Result.ok(count, COUNT_SOURCES);
    }

    /** 单个脚本源：文件存在且非空。 */
    private static Result checkScriptFile(ConfigManager.Sub sub) {
        File file = new File(sub.link);
        if (!file.exists() || !file.isFile()) return Result.fail(R.string.config_reason_no_file);
        if (file.length() == 0) return Result.fail(R.string.config_reason_empty);
        return Result.ok();
    }

    /** 点播配置（在线链接或本地 .json）：必须是含 sites 数组的 JSON 对象。 */
    private static Result checkJsonConfig(String text) {
        if (text == null) return Result.fail(R.string.config_reason_open_fail);
        JsonElement parsed;
        try {
            parsed = App.gson().fromJson(stripBom(text), JsonElement.class);
        } catch (JsonSyntaxException e) {
            return Result.fail(R.string.config_reason_bad_format);
        }
        if (parsed == null || !parsed.isJsonObject()) return Result.fail(R.string.config_reason_bad_format);
        JsonObject obj = parsed.getAsJsonObject();
        if (!obj.has("sites") || !obj.get("sites").isJsonArray()) return Result.fail(R.string.config_reason_bad_format);
        JsonArray sites = obj.getAsJsonArray("sites");
        if (sites.size() == 0) return Result.fail(R.string.config_reason_empty);
        return Result.ok(sites.size(), COUNT_SITES);
    }

    /** 直播源（在线链接或本地 .txt/.m3u/.m3u8）：JSON 数组播单，或 m3u/txt 播单里至少有一个频道。 */
    private static Result checkLive(String text) {
        if (text == null) return Result.fail(R.string.config_reason_open_fail);
        String content = stripBom(text);
        JsonElement parsed = null;
        try {
            parsed = App.gson().fromJson(content, JsonElement.class);
        } catch (JsonSyntaxException ignored) {
        }
        if (parsed != null && parsed.isJsonArray()) {
            JsonArray array = parsed.getAsJsonArray();
            if (array.size() == 0) return Result.fail(R.string.config_reason_empty);
            return Result.ok(array.size(), COUNT_CHANNELS);
        }
        if (parsed != null && parsed.isJsonObject()) return Result.fail(R.string.config_reason_bad_format);
        int channels = countChannels(content);
        if (channels == 0) return Result.fail(R.string.config_reason_empty);
        return Result.ok(channels, COUNT_CHANNELS);
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

    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
