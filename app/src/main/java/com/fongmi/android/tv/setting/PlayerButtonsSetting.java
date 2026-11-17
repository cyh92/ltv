package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 播放器按钮配置：控制栏 18 个按钮的顺序与显隐（Prefers 存 JSON）。
 * 顺序缺失或多余的 id 会自动补齐/丢弃，保证以后加按钮也不崩。
 */
public class PlayerButtonsSetting {

    public static final String[] DEFAULT_ORDER = {"episodes", "parse", "player", "decoder", "reset", "replay", "repeat", "speed", "scale", "text", "audio", "video", "danmaku", "edition", "chapter", "disc_menu", "opening", "ending"};

    private static final String KEY_ORDER = "player_buttons_order";
    private static final String KEY_HIDDEN = "player_buttons_hidden";

    public static List<String> defaultOrder() {
        return new ArrayList<>(Arrays.asList(DEFAULT_ORDER));
    }

    public static List<String> getOrder() {
        try {
            List<String> order = App.gson().fromJson(Prefers.getString(KEY_ORDER), new TypeToken<List<String>>() {
            }.getType());
            if (order == null || order.isEmpty()) return defaultOrder();
            List<String> result = new ArrayList<>();
            for (String id : order) if (isKnown(id) && !result.contains(id)) result.add(id);
            for (String id : DEFAULT_ORDER) if (!result.contains(id)) result.add(id);
            return result;
        } catch (Exception e) {
            return defaultOrder();
        }
    }

    public static void putOrder(List<String> order) {
        Prefers.put(KEY_ORDER, App.gson().toJson(order));
    }

    public static boolean isVisible(String id) {
        return !readHidden().contains(id);
    }

    public static void setVisible(String id, boolean visible) {
        Set<String> hidden = readHidden();
        if (visible) hidden.remove(id);
        else hidden.add(id);
        Prefers.put(KEY_HIDDEN, App.gson().toJson(hidden));
    }

    public static void reset() {
        Prefers.put(KEY_ORDER, App.gson().toJson(defaultOrder()));
        Prefers.put(KEY_HIDDEN, "[]");
    }

    public static void putHidden(Set<String> hidden) {
        Prefers.put(KEY_HIDDEN, App.gson().toJson(hidden == null ? new HashSet<>() : hidden));
    }

    public static Set<String> getHidden() {
        return readHidden();
    }

    public static boolean isKnown(String id) {
        return Arrays.asList(DEFAULT_ORDER).contains(id);
    }

    private static Set<String> readHidden() {
        try {
            Set<String> hidden = App.gson().fromJson(Prefers.getString(KEY_HIDDEN), new TypeToken<Set<String>>() {
            }.getType());
            return hidden == null ? new HashSet<>() : hidden;
        } catch (Exception e) {
            return new HashSet<>();
        }
    }
}
