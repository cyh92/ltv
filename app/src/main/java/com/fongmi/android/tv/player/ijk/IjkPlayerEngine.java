package com.fongmi.android.tv.player.ijk;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DecoderMode;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * IJK 播放器内核：把 {@link IjkPlayerAdapter} 接进烛龙的播放器引擎体系。
 * <p>
 * 与 Exo / MPV 一样实现 {@link PlayerEngine}，上层（PlayerManager、
 * VodPlaybackController、播放器控制栏）只认 PlayerEngine 接口，不用改。
 * 生命周期：一个引擎实例持有一个 IjkMediaPlayer + 一个 adapter，
 * 换集只调 {@link #setMediaItem}（复用同一个 player）；解码方式在
 * setMediaItem 时应用到 mediacodec 选项（prepare 之后改不生效，
 * 所以切解码方式后下一集才生效，与 Exo 一致）。
 */
public class IjkPlayerEngine implements PlayerEngine {

    private final IjkErrorMessageProvider provider = new IjkErrorMessageProvider();
    private final IjkMediaPlayer ijk;
    private final IjkPlayerAdapter adapter;

    public IjkPlayerEngine(Player.Listener listener) {
        this.ijk = IjkUtil.isAvailable() ? IjkUtil.createPlayer() : null;
        this.adapter = ijk == null ? null : new IjkPlayerAdapter(App.get(), ijk);
        if (adapter != null && listener != null) adapter.addListener(listener);
    }

    /** IJK 是否能建出来（native 库加载成功）。工厂用它判断能不能选 IJK。 */
    public static boolean isAvailable() {
        return IjkUtil.isAvailable();
    }

    @Override
    public Type getType() {
        return Type.IJK;
    }

    @Override
    public Player getPlayer() {
        return adapter;
    }

    /**
     * 0.1.96：把视频 Surface 接到 IJK 上。
     * <p>
     * 根因：PlayerView 的视频 Surface 是接到“后端播放器”（setBackendPlayer）上的，
     * ExoPlayerEngine.bindPlayerView() 就是这么做的；IJK 之前没有重写 bindPlayerView，
     * 所以 Surface 从来没接到 IJK 适配器上——只有声音、没有画面，PlayerView 一直
     * 显示海报（artwork）、画面尺寸也一直是 UNKNOWN（看着像“小屏幕”）。
     * 这里照抄 Exo 的做法，把 adapter 设为后端播放器。
     */
    @Override
    public void bindPlayerView(PlayerView playerView) {
        if (adapter != null) playerView.setBackendPlayer(adapter);
    }

    @Override
    public boolean setMediaItem(PlaySpec spec, long startPositionMs) {
        if (adapter == null || adapter.isReleased()) return false;
        MediaItem item = MediaItemFactory.from(spec);
        // 解码方式必须在 setDataSource / prepare 之前设置（mediacodec 选项）
        IjkUtil.setDecodeMode(ijk, getDecoderMode(C.TRACK_TYPE_VIDEO));
        adapter.setHeaders(spec.getHeaders());
        adapter.setMediaItem(item, startPositionMs);
        return true;
    }

    @Override
    public void stop() {
        if (adapter != null) adapter.stop();
    }

    @Override
    public void release() {
        if (adapter != null) adapter.release();
    }

    @Override
    public int getAudioChannelCount() {
        // IJK 不直接暴露声道数，绝大多数源是双声道，返回 2（仅用于显示/日志）
        return 2;
    }

    @Override
    public List<DecoderMode> getSupportedDecoderModes(@C.TrackType int trackType) {
        // 视频：自动 / 硬解 / 软解（软解就是 FFmpeg 软解，与 Exo 的 lib-decoder-ffmpeg
        // 是两份独立实现，不冲突）。音频固定 FFmpeg 解码，没有模式可选。
        // 注意：不提供 FFMPEG 模式——在 IJK 里“软解”本身就是 FFmpeg，没有第二个 FFmpeg 模式。
        if (trackType == C.TRACK_TYPE_VIDEO) return List.of(DecoderMode.AUTO, DecoderMode.HARDWARE, DecoderMode.SOFTWARE);
        return List.of();
    }

    @Override
    @Nullable
    public DecoderMode getDecoderMode(@C.TrackType int trackType) {
        if (trackType != C.TRACK_TYPE_VIDEO) return null;
        return DecodeSetting.getDecoderMode(PlayerSetting.ENGINE_IJK, trackType);
    }

    @Override
    public void setDecoderMode(@C.TrackType int trackType, DecoderMode mode) {
        if (trackType != C.TRACK_TYPE_VIDEO) return;
        DecodeSetting.putDecoderMode(PlayerSetting.ENGINE_IJK, trackType, mode);
        // mediacodec 选项只在下一次 setMediaItem（换集）时应用到 player
    }

    @Override
    public void setSecondarySubtitleSelection(@Nullable TrackSelectionOverride selection) {
        if (adapter == null || selection == null) return;
        TrackSelectionParameters params = adapter.getTrackSelectionParameters().buildUpon().addOverride(selection).build();
        adapter.setTrackSelectionParameters(params);
    }

    @Override
    public void restoreSecondarySubtitleSelection(@Nullable TrackSelectionOverride selection) {
        setSecondarySubtitleSelection(selection);
    }

    @Override
    public boolean addSubtitle(Sub sub) {
        // v1 不支持：IJK 没有往已播视频里注入外挂字幕（srt/vtt 文件）的接口。
        // 外挂字幕请用 Exo / MPV；IJK 只支持视频内嵌的字幕轨道。
        return false;
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return ErrorAction.FATAL;
    }
}
