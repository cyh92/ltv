package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

/**
 * 屏显设置（播放器里显示哪些东西）。
 * 标题/分辨率/时间/进度默认开，网速/迷你默认关，播放参数直接用 PlayerSetting 的调试开关。
 */
public class DisplaySetting {

    public static boolean isTitle() {
        return Prefers.getBoolean("display_title", true);
    }

    public static void putTitle(boolean visible) {
        Prefers.put("display_title", visible);
    }

    public static boolean isResolution() {
        return Prefers.getBoolean("display_resolution", true);
    }

    public static void putResolution(boolean visible) {
        Prefers.put("display_resolution", visible);
    }

    public static boolean isTime() {
        return Prefers.getBoolean("display_time", true);
    }

    public static void putTime(boolean visible) {
        Prefers.put("display_time", visible);
    }

    public static boolean isProgress() {
        return Prefers.getBoolean("display_progress", true);
    }

    public static void putProgress(boolean visible) {
        Prefers.put("display_progress", visible);
    }

    public static boolean isSpeed() {
        return Prefers.getBoolean("display_speed", false);
    }

    public static void putSpeed(boolean visible) {
        Prefers.put("display_speed", visible);
    }

    public static boolean isMini() {
        return Prefers.getBoolean("display_mini", false);
    }

    public static void putMini(boolean visible) {
        Prefers.put("display_mini", visible);
    }

    public static boolean isParams() {
        return PlayerSetting.isDebug();
    }

    public static void putParams(boolean visible) {
        PlayerSetting.putDebug(visible);
    }
}
