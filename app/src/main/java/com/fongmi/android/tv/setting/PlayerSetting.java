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
        // 0.1.106：IJK 加回。存着的值按 [EXO, IJK] clamp——从 0.1.96 直接升上来的用户
        // 若存的是 IJK=2，恢复成 IJK，不落到 EXO。0.1.97 的一次性重置块（player_engine_reset_exo_0197）
        // 不恢复：它在 0.1.97 已对走过 0.1.97 的用户执行过；现在再跑会清掉跳过 0.1.97 的用户存着的 IJK 偏好。
        // fresh 安装默认仍是 EXO。
        return Math.clamp(Prefers.getInt("player_engine", ENGINE_EXO), ENGINE_EXO, getEngineCount() - 1);
    }

    public static int getEngineCount() {
        // 0.1.106：IJK 加回，三个内核槽位；IJK 是内置的（不依赖插件安装），
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

    // 0.1.107：Cronet 网络引擎开关，默认关（回落 OkHttp）。
    public static boolean isCronet() {
        return Prefers.getBoolean("player_cronet", false);
    }

    public static void putCronet(boolean cronet) {
        Prefers.put("player_cronet", cronet);
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

    /**
     * 0.1.104：缓冲三档预设（存在同一个 "buffer" 键里，老的 1..10 值继续有效）。
     * 省流 1×（默认，跟原来一样）、均衡 3×、流畅 6×。
     */
    public static final int BUFFER_PRESET_SAVER = 1;
    public static final int BUFFER_PRESET_BALANCED = 3;
    public static final int BUFFER_PRESET_SMOOTH = 6;

    /** 当前缓冲值取最近的一档：0 省流 / 1 均衡 / 2 流畅。 */
    public static int getBufferPreset() {
        int buffer = getBuffer();
        if (buffer <= 1) return 0;
        if (buffer <= 4) return 1;
        return 2;
    }

    public static int getBufferPresetValue(int preset) {
        return switch (preset) {
            case 1 -> BUFFER_PRESET_BALANCED;
            case 2 -> BUFFER_PRESET_SMOOTH;
            default -> BUFFER_PRESET_SAVER;
        };
    }
}
