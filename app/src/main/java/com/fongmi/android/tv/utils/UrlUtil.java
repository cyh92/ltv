package com.fongmi.android.tv.utils;

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.server.Server;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.UriUtil;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UrlUtil {

    private static final String WEBVIEW_PREFIX = "webview://";

    public static boolean isWebView(String url) {
        return url != null && url.regionMatches(true, 0, WEBVIEW_PREFIX, 0, WEBVIEW_PREFIX.length());
    }

    public static String unwrapWebView(String url) {
        return isWebView(url) ? url.substring(WEBVIEW_PREFIX.length()) : "";
    }

    public static String toLocalUrl(Uri uri) {
        String path = uri.getPath();
        String root = Path.rootPath();
        if (!ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme()) || TextUtils.isEmpty(path) || !path.startsWith(root + File.separator)) return uri.toString();
        return "file://" + path.substring(root.length() + 1);
    }

    public static String convert(String url) {
        String scheme = UriUtil.scheme(url);
        String prefix = scheme + "://";
        return switch (scheme) {
            case "assets" -> url.replace(prefix, Server.get().getAddress("/"));
            case "proxy" -> url.replace(prefix, Server.get().getAddress("/proxy?"));
            case "file" -> Server.get().getAddress("/file/") + Uri.encode(url.substring((prefix).length()), "/");
            default -> url;
        };
    }

    /**
     * Resolve a relative spider/api path against the config file's location,
     * so a config can keep its jar/py/js files next to it. Handles "./", "../"
     * and bare relative paths ("py/x.py", "jar/vox.jar", "x.py"). Absolute URLs
     * (http/file/assets/proxy), csp_* names and JSON ext strings are returned
     * unchanged. A "file://" base that is relative to the external storage
     * root ("file://Download/...") is made absolute, so the jar/py/js loaders
     * can open the real file.
     */
    public static String resolveRelative(String base, String url) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(base)) return url;
        url = url.trim();
        base = base.trim();
        if (url.isEmpty() || base.isEmpty()) return url;
        if (!isRelativePath(url)) return url;
        int slash = base.lastIndexOf('/');
        if (slash < 0) return url;
        String resolved = normalizePath(base.substring(0, slash + 1) + url);
        if (resolved.startsWith("file://") && !resolved.startsWith("file:///")) {
            resolved = "file://" + Path.rootPath() + "/" + resolved.substring("file://".length());
        }
        return resolved;
    }

    /**
     * 0.1.103：有些配置包把爬虫/规则文件名存成 #Uxxxx 编码（如 #U679c#U679c.js），
     * 配置里写的是中文名（./js/果果.js）。解压后文件名还是编码的，app 按中文名
     * 找不到文件，这些源就打不开。这里本地文件按中文名找不到时，改试编码名，
     * 让这种包不用改名就能直接用。只对本地 file:// 路径生效，网址和 JSON 不动。
     */
    public static String resolveEncodedFile(String url) {
        if (TextUtils.isEmpty(url) || !url.startsWith("file://")) return url;
        String path = url.substring("file://".length());
        if (new File(path).exists()) return url;
        String encoded = encodeFileName(path);
        if (encoded != null && new File(encoded).exists()) return "file://" + encoded;
        return url;
    }

    /** 把路径里文件名的非 ASCII 字符编成 #Uxxxx（小写十六进制，4 位），ASCII 字符不动。 */
    private static String encodeFileName(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0) return null;
        String dir = path.substring(0, slash + 1);
        String name = path.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c > 127) {
                sb.append(String.format("#U%04x", (int) c));
                changed = true;
            } else {
                sb.append(c);
            }
        }
        return changed ? dir + sb.toString() : null;
    }

    private static boolean isRelativePath(String url) {
        if (url.startsWith("./") || url.startsWith("../")) return true;
        if (url.startsWith("/") || url.startsWith("csp_") || url.startsWith("node:") || url.startsWith("{") || url.startsWith("[")) return false;
        int colon = url.indexOf(':');
        if (colon > 0 && url.substring(0, colon).matches("[a-zA-Z][a-zA-Z0-9+.-]*")) return false;
        return url.contains("/") || url.endsWith(".py") || url.endsWith(".js") || url.endsWith(".jar") || url.endsWith(".json");
    }

    private static String normalizePath(String path) {
        int schemeEnd = path.indexOf("://");
        String head = schemeEnd >= 0 ? path.substring(0, schemeEnd + 3) : "";
        String rest = schemeEnd >= 0 ? path.substring(schemeEnd + 3) : path;
        String[] parts = rest.split("/", -1);
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == 0) {
                kept.add(part);
                continue;
            }
            if (part.equals(".")) continue;
            if (part.equals("..")) {
                if (kept.size() > 1) kept.remove(kept.size() - 1);
                continue;
            }
            kept.add(part);
        }
        return head + TextUtils.join("/", kept);
    }

    public static String fixHeader(String key) {
        if (HttpHeaders.USER_AGENT.equalsIgnoreCase(key) || "ua".equalsIgnoreCase(key)) return HttpHeaders.USER_AGENT;
        if (HttpHeaders.REFERER.equalsIgnoreCase(key)) return HttpHeaders.REFERER;
        if (HttpHeaders.COOKIE.equalsIgnoreCase(key)) return HttpHeaders.COOKIE;
        return key;
    }
}
