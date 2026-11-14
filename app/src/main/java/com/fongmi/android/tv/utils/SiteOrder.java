package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SiteOrder {

    public static final int SORT_DEFAULT = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_TIME = 2;

    // Dialog layout: single column (list) or double column (grid). Tapping the toggle switches.
    public static final int LAYOUT_SINGLE = 1;
    public static final int LAYOUT_DOUBLE = 2;

    private static final String KEY_ORDER = "site_order";
    private static final String KEY_REMOVED = "site_removed";
    private static final String KEY_SORT = "site_sort";
    private static final String KEY_LAYOUT = "site_layout";

    public static int getSort() {
        return Prefers.getInt(KEY_SORT, SORT_DEFAULT);
    }

    public static void putSort(int sort) {
        Prefers.put(KEY_SORT, sort);
    }

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

    // Alphabetical: Collator with Chinese locale sorts Chinese names by pinyin.
    public static void sortByName(List<Site> sites) {
        Collator collator = Collator.getInstance(Locale.CHINA);
        sites.sort((a, b) -> collator.compare(a.getName(), b.getName()));
    }

    // By added time, newest first. Script sources use the local file's lastModified;
    // config sources have no added time and keep their original order (time 0).
    public static void sortByTime(List<Site> sites) {
        sites.sort((a, b) -> Long.compare(getTime(b), getTime(a)));
    }

    public static long getTime(Site site) {
        String api = site.getApi();
        if (api != null && api.startsWith("file://")) {
            return new File(api.substring(7)).lastModified();
        }
        return 0;
    }

    public static boolean isLocalFile(Site site) {
        String api = site.getApi();
        return api != null && api.startsWith("file://");
    }

    public static String getLocalPath(Site site) {
        return site.getApi().substring(7);
    }
}
