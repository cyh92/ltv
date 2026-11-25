package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteOrder {

    // Dialog layout: single column (list) or double column (grid). Tapping the toggle switches.
    public static final int LAYOUT_SINGLE = 1;
    public static final int LAYOUT_DOUBLE = 2;

    private static final String KEY_ORDER = "site_order";
    private static final String KEY_REMOVED = "site_removed";
    private static final String KEY_LAYOUT = "site_layout";
    private static final String KEY_NAMES = "site_names";

    public static int getLayout() {
        return Prefers.getInt(KEY_LAYOUT, LAYOUT_SINGLE);
    }

    public static void putLayout(int layout) {
        Prefers.put(KEY_LAYOUT, layout);
    }

    public static List<String> getOrder() {
        List<String> order = App.gson().fromJson(Prefers.getString(KEY_ORDER), new TypeToken<List<String>>() {
        });
        return order == null ? new ArrayList<>() : order;
    }

    public static void putOrder(List<String> order) {
        Prefers.put(KEY_ORDER, App.gson().toJson(order));
    }

    public static List<String> getRemoved() {
        List<String> removed = App.gson().fromJson(Prefers.getString(KEY_REMOVED), new TypeToken<List<String>>() {
        });
        return removed == null ? new ArrayList<>() : removed;
    }

    public static boolean isRemoved(String key) {
        return getRemoved().contains(key);
    }

    public static void addRemoved(String key) {
        List<String> removed = getRemoved();
        if (!removed.contains(key)) removed.add(key);
        Prefers.put(KEY_REMOVED, App.gson().toJson(removed));
    }

    // Renamed display names for non-local sources (local script sources rename the file itself instead).
    public static String getName(String key) {
        return getNames().get(key);
    }

    public static void putName(String key, String name) {
        Map<String, String> names = getNames();
        names.put(key, name);
        Prefers.put(KEY_NAMES, App.gson().toJson(names));
    }

    private static Map<String, String> getNames() {
        Map<String, String> names = App.gson().fromJson(Prefers.getString(KEY_NAMES), new TypeToken<Map<String, String>>() {
        });
        return names == null ? new HashMap<>() : names;
    }

    // A site's key changed (local file renamed): keep its place in the custom order.
    public static void renameKey(String oldKey, String newKey) {
        List<String> order = getOrder();
        int index = order.indexOf(oldKey);
        if (index >= 0) {
            order.set(index, newKey);
            putOrder(order);
        }
        Map<String, String> names = getNames();
        if (names.remove(oldKey) != null) Prefers.put(KEY_NAMES, App.gson().toJson(names));
    }

    public static List<String> keysOf(List<Site> sites) {
        List<String> keys = new ArrayList<>();
        for (Site site : sites) keys.add(site.getKey());
        return keys;
    }

    // Custom drag order: sites in the stored order first, new sites appended in original order.
    public static List<Site> applyOrder(List<Site> sites) {
        List<String> order = getOrder();
        if (order.isEmpty()) return new ArrayList<>(sites);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < order.size(); i++) index.put(order.get(i), i);
        List<Site> result = new ArrayList<>(sites);
        Collections.sort(result, (a, b) -> {
            Integer ia = index.get(a.getKey());
            Integer ib = index.get(b.getKey());
            if (ia == null && ib == null) return 0;
            if (ia == null) return 1;
            if (ib == null) return -1;
            return ia - ib;
        });
        return result;
    }

    // 0.1.105: a site appended from the phone's local script files
    // (ScriptManager.scan), not a site defined by the current interface's own
    // config. Do NOT use isLocalFile() for this: a config's own XBPQ/drpy2
    // sites also point at local files, but their keys are not "script_".
    public static boolean isLocalScript(Site site) {
        String key = site.getKey();
        return key != null && key.startsWith("script_");
    }

    // A site is a local file when we can resolve it to a real file on the phone.
    // NOTE: never test api.startsWith("file://") — Site.objectFrom runs
    // UrlUtil.convert on the api, so a script source's api is actually
    // "http://<local-server>/file/<encoded-path>", not "file://...".
    public static boolean isLocalFile(Site site) {
        return getLocalPath(site) != null;
    }

    // Resolve the real local path of a script/config source, or null when it is not a local file.
    public static String getLocalPath(Site site) {
        String key = site.getKey();
        if (key != null && key.startsWith("script_")) {
            String base = key.substring("script_".length());
            for (File file : ScriptManager.get().scanFiles()) {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                if (dot > 0 && name.substring(0, dot).equals(base)) return file.getAbsolutePath();
            }
        }
        String api = site.getApi();
        if (api != null) {
            int idx = api.indexOf("/file/");
            if (idx >= 0) {
                try {
                    String path = URLDecoder.decode(api.substring(idx + 6), "UTF-8");
                    if (new File(path).isFile()) return path;
                } catch (Exception ignored) {
                }
            }
            if (api.startsWith("file://")) {
                String path = api.substring(7);
                if (new File(path).isFile()) return path;
            }
        }
        return null;
    }

    public static long getTime(Site site) {
        String path = getLocalPath(site);
        return path == null ? 0 : new File(path).lastModified();
    }
}
