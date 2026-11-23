package com.fongmi.android.tv.setting;

import static androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL;
import static androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT;
import static androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM;

import com.github.catvod.utils.Prefers;

public class PlayerSetting {

    public static final int ENGINE_EXO = 0;
    public static final int ENGINE_MPV = 1;
    public static final int ENGINE_IJK = 2;
    public static final int RENDER_SURFACE = 0;
    public static final int RENDER_TEXTURE = 1;
    public static final int MIN_SCALE = 0;
    public static final int MAX_SCALE = 4;
    private static final int MIN_SIZE = 0;
    private static final int MAX_SIZE = 3;
    private static final int MIN_BUFFER = 1;
    private static final int MAX_BUFFER = 10;
    private static final int MIN_BACKGROUND = 0;
    private static final int MAX_BACKGROUND = 2;

    public static int getEngine() {
        // 0.1.97：一次性把默认内核重置为 EXO（用户要求全部默认 EXO 播放）。
        // 只做一次（player_engine_reset_exo_0197 标记）；之后用户仍可在设置里手动切 IJK/MPV。
        // IJK 本身仍可用（0.1.97 的 IJK 修复都在），只是默认不再是 IJK。
        if (!Prefers.getBoolean("player_engine_reset_exo_0197", false)) {
            Prefers.put("player_engine_reset_exo_0197", true);
            Prefers.put("player_engine", ENGINE_EXO);
        }
        return Math.clamp(Prefers.getInt("player_engine", ENGINE_EXO), ENGINE_EXO, getEngineCount() - 1);
    }

    public static int getEngineCount() {
        // IJK 是内置的（不依赖插件安装），三个内核槽位一直都在；
        // MPV 没装时由工厂回退到 EXO、设置页隐藏 MPV 按钮，不用靠 count 拦。
        return ENGINE_IJK + 1;
    }

    public static void putEngine(int engine) {
        Prefers.put("player_engine", Math.clamp(engine, ENGINE_EXO, ENGINE_IJK));
        if (isExo() && DecodeSetting.isTunnel()) putRender(RENDER_SURFACE);
    }

    public static boolean isExo() {
        return getEngine() == ENGINE_EXO;
    }

    public static boolean isMpv() {
        return getEngine() == ENGINE_MPV;
    }

    public static boolean isIjk() {
        return getEngine() == ENGINE_IJK;
    }

    public static boolean isDebug() {
        return Prefers.getBoolean("player_debug");
    }

    public static void putDebug(boolean debug) {
        Prefers.put("player_debug", debug);
    }

    public static boolean isLibass() {
        return Prefers.getBoolean("player_libass", true);
    }

    public static void putLibass(boolean libass) {
        Prefers.put("player_libass", libass);
    }

    public static boolean isMpvGpuNext() {
        return Prefers.getBoolean("mpv_gpu_next");
    }

    public static void putMpvGpuNext(boolean gpuNext) {
        Prefers.put("mpv_gpu_next", gpuNext);
    }

    public static boolean isMpvVulkan() {
        return Prefers.getBoolean("mpv_vulkan");
    }

    public static void putMpvVulkan(boolean vulkan) {
        Prefers.put("mpv_vulkan", vulkan);
    }

    public static int getMpvMotion() {
        return Math.clamp(Prefers.getInt("mpv_motion"), 0, 2);
    }

    public static void putMpvMotion(int motion) {
        Prefers.put("mpv_motion", Math.clamp(motion, 0, 2));
    }

    public static int getMpvMotionFps() {
        return switch (getMpvMotion()) {
            case 1 -> 60;
            case 2 -> 120;
            default -> 0;
        };
    }

    public static int getExoMotion() {
        return Math.clamp(Prefers.getInt("exo_motion"), 0, 2);
    }

    public static void putExoMotion(int motion) {
        Prefers.put("exo_motion", Math.clamp(motion, 0, 2));
    }

    public static int getExoMotionFps() {
        return switch (getExoMotion()) {
            case 1 -> 60;
            case 2 -> 120;
            default -> 0;
        };
    }

    public static int getRender() {
        return Math.clamp(Prefers.getInt("render", RENDER_SURFACE), RENDER_SURFACE, RENDER_TEXTURE);
    }

    public static void putRender(int render) {
        Prefers.put("render", Math.clamp(render, RENDER_SURFACE, RENDER_TEXTURE));
        if (isExo() && DecodeSetting.isTunnel() && getRender() == RENDER_TEXTURE) DecodeSetting.putTunnel(false);
    }

    public static int getSize() {
        return Math.clamp(Prefers.getInt("size", 2), MIN_SIZE, MAX_SIZE);
    }

    public static void putSize(int size) {
        Prefers.put("size", Math.clamp(size, MIN_SIZE, MAX_SIZE));
    }

    public static int getScale() {
        return Math.clamp(Prefers.getInt("scale"), MIN_SCALE, MAX_SCALE);
    }

    public static void putScale(int scale) {
        Prefers.put("scale", Math.clamp(scale, MIN_SCALE, MAX_SCALE));
    }

    /**
     * 播放倍速：全局保存，换集/重进后在 onPrepare 里恢复。
     * 键用 speed_normal——SpeedSetting.getLongPress() 拿 "speed" 当长按倍速的 fallback，复用会改掉长按默认值。
     */
    public static float getSpeed() {
        return SpeedSetting.clamp(Prefers.getFloat("speed_normal", SpeedSetting.NORMAL));
    }

    public static void putSpeed(float speed) {
        Prefers.put("speed_normal", SpeedSetting.clamp(speed));
    }

    public static int getScaleMode(int index) {
        return index == 1 ? RESIZE_MODE_FILL : index == 2 ? RESIZE_MODE_ZOOM : RESIZE_MODE_FIT;
    }

    /**
     * 固定宽高比：3 是 16:9，4 是 4:3，其余不固定（返回 0）。
     */
    public static float getAspectRatio(int index) {
        return index == 3 ? 16f / 9f : index == 4 ? 4f / 3f : 0f;
    }

    public static int getBackground() {
        return Math.clamp(Prefers.getInt("background", 2), MIN_BACKGROUND, MAX_BACKGROUND);
    }

    public static void putBackground(int background) {
        Prefers.put("background", Math.clamp(background, MIN_BACKGROUND, MAX_BACKGROUND));
    }

    public static boolean isBackgroundOff() {
        return getBackground() == 0;
    }

    public static boolean isBackgroundOn() {
        return getBackground() == 1 || getBackground() == 2;
    }

    public static boolean isBackgroundPiP() {
        return getBackground() == 2;
    }

    public static int getBuffer() {
        return Math.clamp(Prefers.getInt("buffer"), MIN_BUFFER, MAX_BUFFER);
    }

    public static void putBuffer(int buffer) {
        Prefers.put("buffer", Math.clamp(buffer, MIN_BUFFER, MAX_BUFFER));
    }
}
