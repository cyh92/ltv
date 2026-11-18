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
     * Resolve a relative spider/api path ("./" or "../") against the config file's location,
     * so a config can keep its jar/py/js files next to it. Absolute URLs (http/file/assets/proxy)
     * and csp_* names are returned unchanged.
     */
    public static String resolveRelative(String base, String url) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(base)) return url;
        if (!url.startsWith("./") && !url.startsWith("../")) return url;
        int slash = base.lastIndexOf('/');
        if (slash < 0) return url;
        return normalizePath(base.substring(0, slash + 1) + url);
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
