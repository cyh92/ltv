package com.fongmi.android.tv.player.ijk;

import android.util.Log;

import androidx.media3.common.DecoderMode;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * IJK 播放器工具类：加载 native 库、创建播放器、设置默认参数。
 * <p>
 * 原生库：TVBoxOSC 2023-08-02 三库分体（FFmpeg 4.0 / ijk 0.8.8，版本串
 * {@code TVBoxOSC Ffmpeg4.0 20230802}），与 TV+、MBox 所用的同一份构建：
 * {@code libijkffmpeg.so} + {@code libijksdl.so} + {@code libijkplayer.so}，
 * 放在 {@code app/src/main/jniLibs/<abi>/}，Java API 是同项目（takagen99/Box）
 * 的 {@code tv.danmaku.ijk.media.player} 源码（见 {@code app/src/main/java/tv/}）。
 * arm64-v8a 三个库 LOAD 段全是 0x10000（16KB 对齐），md5：
 * libijkffmpeg e6e45d37…、libijksdl 9bcec12c…、libijkplayer 04a83c86…。
 * <p>
 * 0.1.95–0.1.97 用的 debugly 单体 AAR（{@code libijkplayer.so} 一个文件、
 * 4KB 对齐）已废弃：它在 Android 16 的 16KB 页手机上加载失败，IJK 根本进不去。
 * <p>
 * 加载失败不要静默：{@link #isAvailable()} 里显式逐个加载三个库，失败时记
 * {@link Log#e} 并把原因留给 {@link #getLoadError()}，否则下次再有人报
 * “IJK 不能用”还是查不到原因。注意不要用
 * {@code IjkMediaPlayer.loadLibrariesOnce} 来判断可用性——TVBoxOSC 版的这个
 * 方法内部吞掉加载异常（加载失败也照常返回），拿它判断会误判为可用，之后
 * {@code new IjkMediaPlayer()} 才在 {@code native_init()} 炸。
 */
public class IjkUtil {

    private static final String TAG = "IjkUtil";

    /** 是否已尝试加载过 native 库（只试一次，避免重复抛异常）。 */
    private static volatile boolean loadAttempted;
    /** native 库是否加载成功。 */
    private static volatile boolean available;
    /** 最近一次加载失败的原因（成功时为 null，供诊断用）。 */
    private static volatile String loadError;

    /**
     * IJK 是否可用：按依赖顺序显式加载三个 native 库。
     * 第一次调用时加载，之后返回缓存结果。
     */
    public static synchronized boolean isAvailable() {
        if (!loadAttempted) {
            loadAttempted = true;
            try {
                // 显式按依赖顺序加载：ijkplayer 依赖 ijkffmpeg + ijksdl。
                // 不用 IjkMediaPlayer.loadLibrariesOnce 做判断（它内部吞异常，
                // 加载失败也照常返回，误判为可用）。
                System.loadLibrary("ijkffmpeg");
                System.loadLibrary("ijksdl");
                System.loadLibrary("ijkplayer");
                available = true;
                Log.i(TAG, "IJK native libraries loaded (TVBoxOSC 20230802, three-lib)");
            } catch (Throwable e) {
                available = false;
                loadError = String.valueOf(e);
                Log.e(TAG, "IJK native libraries failed to load, IJK disabled: " + e, e);
            }
        }
        return available;
    }

    /** 最近一次 native 库加载失败的原因（加载成功时为 null）。 */
    public static String getLoadError() {
        return loadError;
    }

    /** 创建一个带默认参数的 IJK 播放器（调用前请先确认 isAvailable() 为 true）。 */
    public static IjkMediaPlayer createPlayer() {
        IjkMediaPlayer player = new IjkMediaPlayer();
        applyDefaultOptions(player);
        return player;
    }

    /** 默认参数：各家壳子通用的 ijkplayer 常用配置。 */
    private static void applyDefaultOptions(IjkMediaPlayer player) {
        // 格式层：探测时间 1s，快速 seek，断开自动重连
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1000000L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 102400L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek");
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L);
        // 0.1.97：HLS 分片失败时重连（0.1.96 播约 2 分半后卡在 08:34 不动，就是
        // 分片拉不到后 demuxer 停住、一直报 buffering。只开 reconnect 不够，段尾/
        // 流式重连和最大重连延迟也要开，不然一次分片 404/超时就卡死）。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_at_eof", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 5L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 30000000L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0L);
        // 0.1.97：切台/切源换域名时清掉 DNS 缓存。ijkplayer 的 DNS 缓存跨域名不失效，
        // 多个源来回切会在旧 IP 上报 -10000（MEDIA_ERROR_IJK_PLAYER），社区常见修法。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1L);
        // 解码层：跳过环路滤波省算力（软解时更明显）
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_frame", 0L);
        // 播放器层：丢帧保流畅、变速不变调、硬解自动旋转
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L);
        // 0.1.106：start-on-prepared 保持 0（不改成参考壳的 1）。适配器在 onPrepared
        // 里先 seekTo(startPositionMs) 再 ijk.start()；改成 1 会让 IJK 在 seek 前就
        // 自动开播，播 0 位置闪一下再跳。参考壳用 1 是因为 Doikki 包装层自己管时序，
        // media3 适配器路线不能照搬。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L);
        // 0.1.106：overlay-format=842225234（YV12）。参考壳 TV+ / OKTV / MBox 三家
        // 都有，FongMi 之前没设；默认 overlay 格式不对可能黑屏或不出画面。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", 842225234L);
        // 0.1.106：关精确 seek（参考值），seek 更快更稳。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 0L);
        // 0.1.97：packet-buffering 改回 1（默认）。0.1.95 设成 0 后 IJK 几乎不预缓冲，
        // 网络一抖就进 buffering，而且容易停在 buffering 里出不来（0.1.96《一千零一夜》
        // 08:34 卡住就是这个状态）。开回 1 并给缓冲上限，让它有缓冲可吃、抖动能恢复。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L);
        // 0.1.106：max-buffer-size 改为 5MB（参考壳的值；0.1.97 是 15MB）。
        // 08:34 卡住的根因是 packet-buffering=0，那项已保持 1，所以 5MB 不会重现；
        // 若真机弱网下觉得缓冲不够，再调回 15MB。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 5242880L);
        player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
    }

    /**
     * 按解码方式设置硬解/软解。
     * AUTO 默认硬解（mediacodec=1）；HARDWARE 强制硬解；SOFTWARE 强制软解（mediacodec=0，
     * 此时走 FFmpeg 软解，与 Exo 的 lib-decoder-ffmpeg 是两份独立实现，不冲突）。
     * 注意：prepare 之后改 mediacodec 不生效，须在 setDataSource 之前设置。
     */
    public static void setDecodeMode(IjkMediaPlayer player, DecoderMode mode) {
        long mediacodec = mode == DecoderMode.SOFTWARE ? 0L : 1L;
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", mediacodec);
    }
}
