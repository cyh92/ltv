package com.fongmi.android.tv.player.ijk;

import androidx.media3.common.DecoderMode;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * IJK 播放器工具类：加载 native 库、创建播放器、设置默认参数。
 * <p>
 * IJK 来自 Bilibili ijkplayer 的 debugly fork（k0.8.9，FFmpeg n5.1.3），
 * 用预编译 AAR（ijkplayer-cmake-release.aar）接入，不从源码编译。
 * 该 AAR 是 CMake 单 so 构建：libijkplayer.so 一个文件里同时包含
 * FFmpeg + SDL + 播放器，loadLibrariesOnce 只加载 "ijkplayer" 一个库。
 */
public class IjkUtil {

    /** 是否已尝试加载过 native 库（只试一次，避免重复抛异常）。 */
    private static volatile boolean loadAttempted;
    /** native 库是否加载成功。 */
    private static volatile boolean available;

    /**
     * IJK 是否可用：尝试加载 libijkplayer.so。
     * 第一次调用时加载，之后返回缓存结果。
     */
    public static synchronized boolean isAvailable() {
        if (!loadAttempted) {
            loadAttempted = true;
            try {
                IjkMediaPlayer.loadLibrariesOnce(null);
                available = true;
            } catch (Throwable e) {
                available = false;
            }
        }
        return available;
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
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L);
        // 0.1.97：packet-buffering 改回 1（默认）。0.1.95 设成 0 后 IJK 几乎不预缓冲，
        // 网络一抖就进 buffering，而且容易停在 buffering 里出不来（0.1.96《一千零一夜》
        // 08:34 卡住就是这个状态）。开回 1 并给 15MB 上限，让它有缓冲可吃、抖动能恢复。
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 15L * 1024L * 1024L);
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
