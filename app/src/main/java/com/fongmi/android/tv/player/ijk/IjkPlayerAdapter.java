package com.fongmi.android.tv.player.ijk;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.SimpleBasePlayer;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;

/**
 * IJK 播放器适配器：把 IjkMediaPlayer 包装成 media3 的 {@link Player}。
 * <p>
 * 做法：继承 media3 的 {@link SimpleBasePlayer}（与 TimeshiftPlayback、PlaybackService
 * 同一个基类），IJK 的回调（prepared / error / info / completion / seek / 尺寸变化 /
 * 内嵌字幕）触发 {@link #invalidateState()}，由 {@link #getState()} 把当前状态
 * （播放状态、进度、缓冲、尺寸、内嵌轨道、字幕 cues）翻译成 media3 的 State。
 * 这样上层（播放器控制栏、倍速、缩放、片头片尾、弹幕、字幕、进度条）不用改，
 * 它们只认 media3 Player 接口。
 */
@UnstableApi
public class IjkPlayerAdapter extends SimpleBasePlayer {

    /** 内嵌字幕（OnTimedText）无后续行时，多久后自动清除，避免最后一行一直留在屏幕上。 */
    private static final long CUE_CLEAR_DELAY_MS = 5000;

    private final Context context;
    private final IjkMediaPlayer ijk;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private List<MediaItem> mediaItems = List.of();
    private int currentIndex;
    private long startPositionMs;
    private long pendingSeekMs = C.TIME_UNSET;

    private int playbackState = Player.STATE_IDLE;
    private boolean playWhenReady;
    private boolean prepared;
    private boolean released;
    private PlaybackParameters playbackParameters = PlaybackParameters.DEFAULT;
    private float volume = 1f;
    private VideoSize videoSize = VideoSize.UNKNOWN;
    private PlaybackException playerError;
    private CueGroup currentCues = CueGroup.EMPTY_TIME_ZERO;

    /** 缓冲进度（0~100），来自 OnBufferingUpdate。 */
    private int bufferedPercent;
    /** 内嵌轨道（prepare 完成后从 getTrackInfo() 取）。 */
    private Tracks currentTracks = Tracks.EMPTY;
    /** 每个 TrackGroup 对应的 IJK 轨道序号（selectTrack / deselectTrack 用）。 */
    private final Map<TrackGroup, Integer> groupToIjkIndex = new HashMap<>();
    /** 当前选中的内嵌轨道序号（-1 = 未选）。 */
    private int selectedIjkTrack = -1;
    /** 请求头（由引擎在 setMediaItem 之前设置；MediaItemFactory 把请求头放进
     * RequestMetadata extras 而不是 LocalConfiguration，所以不能从 MediaItem 里取）。 */
    private Map<String, String> headers = Map.of();

    /** 设置下一次 setDataSource 用的请求头（引擎在 setMediaItem 之前调用）。 */
    public void setHeaders(@Nullable Map<String, String> headers) {
        this.headers = headers == null ? Map.of() : headers;
    }

    public IjkPlayerAdapter(Context context, IjkMediaPlayer ijk) {
        super(Looper.getMainLooper());
        this.context = context.getApplicationContext();
        this.ijk = ijk;
        bindListeners();
    }

    /** IJK 回调 → 更新内部状态 → invalidateState()，media3 侧自动刷新。 */
    private void bindListeners() {
        ijk.setOnPreparedListener(mp -> {
            prepared = true;
            playbackState = Player.STATE_READY;
            buildTracks();
            if (pendingSeekMs != C.TIME_UNSET) {
                ijk.seekTo(pendingSeekMs);
                pendingSeekMs = C.TIME_UNSET;
            } else if (startPositionMs > 0) {
                ijk.seekTo(startPositionMs);
            }
            if (playWhenReady) ijk.start();
            invalidateState();
        });
        ijk.setOnErrorListener((mp, what, extra) -> {
            playerError = new PlaybackException("IJK error " + what + "/" + extra, null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            playbackState = Player.STATE_IDLE;
            invalidateState();
            return true;
        });
        ijk.setOnInfoListener((mp, what, extra) -> {
            if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                playbackState = Player.STATE_BUFFERING;
                invalidateState();
            } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END || what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                playbackState = Player.STATE_READY;
                invalidateState();
            }
            return true;
        });
        ijk.setOnCompletionListener(mp -> {
            playbackState = Player.STATE_ENDED;
            invalidateState();
        });
        ijk.setOnSeekCompleteListener(mp -> invalidateState());
        ijk.setOnVideoSizeChangedListener((mp, width, height, sarNum, sarDen) -> {
            float ratio = sarNum > 0 && sarDen > 0 ? (float) sarNum / sarDen : 1f;
            videoSize = new VideoSize(width, height, ratio);
            invalidateState();
        });
        ijk.setOnBufferingUpdateListener((mp, percent) -> {
            bufferedPercent = percent;
            invalidateState();
        });
        ijk.setOnTimedTextListener((mp, text) -> {
            if (text == null) return;
            showTimedText(text.getText());
        });
    }

    /** 内嵌字幕行：显示 + 5 秒后自动清除。 */
    private void showTimedText(String text) {
        mainHandler.removeCallbacksAndMessages(null);
        if (text == null || text.isEmpty()) {
            currentCues = CueGroup.EMPTY_TIME_ZERO;
        } else {
            Cue cue = new Cue.Builder().setText(text).build();
            currentCues = new CueGroup(List.of(cue), getCurrentPosition() * 1000);
            mainHandler.postDelayed(() -> {
                currentCues = CueGroup.EMPTY_TIME_ZERO;
                invalidateState();
            }, CUE_CLEAR_DELAY_MS);
        }
        invalidateState();
    }

    /** prepare 完成后，把 IJK 的内嵌轨道翻译成 media3 的 Tracks（供字幕/音轨选择器用）。 */
    private void buildTracks() {
        List<Tracks.Group> groups = new ArrayList<>();
        groupToIjkIndex.clear();
        try {
            IjkTrackInfo[] infos = ijk.getTrackInfo();
            if (infos == null) return;
            for (int i = 0; i < infos.length; i++) {
                IjkTrackInfo info = infos[i];
                String mime = mimeFor(info.getTrackType());
                if (mime == null) continue;
                Format format = new Format.Builder()
                        .setSampleMimeType(mime)
                        .setLanguage(info.getLanguage())
                        .build();
                TrackGroup group = new TrackGroup(format);
                groupToIjkIndex.put(group, i);
                boolean selected = i == selectedIjkTrack;
                groups.add(new Tracks.Group(group, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{selected}));
            }
        } catch (Throwable ignored) {
        }
        currentTracks = groups.isEmpty() ? Tracks.EMPTY : new Tracks(groups);
    }

    /** IJK 轨道类型 → media3 的 mime（决定它在 UI 里算视频/音频/字幕）。 */
    @Nullable
    private static String mimeFor(int ijkType) {
        if (ijkType == ITrackInfo.MEDIA_TRACK_TYPE_VIDEO) return MimeTypes.VIDEO_H264;
        if (ijkType == ITrackInfo.MEDIA_TRACK_TYPE_AUDIO) return MimeTypes.AUDIO_AAC;
        if (ijkType == ITrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT || ijkType == ITrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) return MimeTypes.TEXT_VTT;
        return null;
    }

    @Override
    protected State getState() {
        List<MediaItemData> playlist = new ArrayList<>();
        for (int i = 0; i < mediaItems.size(); i++) {
            MediaItem item = mediaItems.get(i);
            playlist.add(new MediaItemData.Builder(item.mediaId)
                    .setMediaItem(item)
                    .setDurationUs(prepared ? ijk.getDuration() * 1000 : C.TIME_UNSET)
                    .setIsSeekable(true)
                    .setTracks(i == currentIndex ? currentTracks : Tracks.EMPTY)
                    .build());
        }
        return new State.Builder()
                .setAvailableCommands(buildCommands())
                .setPlaylist(playlist)
                .setCurrentMediaItemIndex(currentIndex)
                .setPlaybackState(playbackState)
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .setPlaybackParameters(playbackParameters)
                .setVolume(volume)
                .setVideoSize(videoSize)
                .setPlayerError(playerError)
                .setCurrentCues(currentCues)
                .setContentPositionMs(() -> prepared ? ijk.getCurrentPosition() : 0)
                .setContentBufferedPositionMs(() -> prepared ? ijk.getDuration() * bufferedPercent / 100 : 0)
                .build();
    }

    private Player.Commands buildCommands() {
        return new Player.Commands.Builder()
                .addAll(
                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_GET_TIMELINE,
                        Player.COMMAND_GET_METADATA,
                        Player.COMMAND_GET_AUDIO_ATTRIBUTES,
                        Player.COMMAND_GET_VOLUME,
                        Player.COMMAND_GET_TEXT,
                        Player.COMMAND_GET_TRACKS,
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_PREPARE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                        Player.COMMAND_SET_SPEED_AND_PITCH,
                        Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                        Player.COMMAND_SET_VIDEO_SURFACE,
                        Player.COMMAND_SET_VOLUME,
                        Player.COMMAND_SET_AUDIO_ATTRIBUTES,
                        Player.COMMAND_SET_MEDIA_ITEM,
                        Player.COMMAND_CHANGE_MEDIA_ITEMS,
                        Player.COMMAND_SET_PLAYLIST_METADATA)
                .build();
    }

    /** 给指定 MediaItem 设置数据源（URL + 请求头）。调用前请先 ijk.reset()。 */
    private void setDataSourceFor(MediaItem item) {
        try {
            MediaItem.LocalConfiguration config = item.localConfiguration;
            if (config == null) return;
            if (!headers.isEmpty()) {
                ijk.setDataSource(context, config.uri, headers);
            } else {
                ijk.setDataSource(context, config.uri);
            }
        } catch (Exception e) {
            playerError = new PlaybackException("IJK setDataSource failed: " + e.getMessage(), e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            playbackState = Player.STATE_IDLE;
        }
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        this.mediaItems = new ArrayList<>(mediaItems);
        this.currentIndex = startIndex;
        this.startPositionMs = startPositionMs;
        this.pendingSeekMs = C.TIME_UNSET;
        this.prepared = false;
        this.playerError = null;
        this.currentTracks = Tracks.EMPTY;
        this.playbackState = Player.STATE_BUFFERING;
        if (!this.mediaItems.isEmpty()) {
            ijk.reset();
            setDataSourceFor(this.mediaItems.get(startIndex));
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handlePrepare() {
        if (!mediaItems.isEmpty() && !prepared) ijk.prepareAsync();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
        this.playWhenReady = playWhenReady;
        if (prepared) {
            if (playWhenReady) {
                if (!ijk.isPlaying()) ijk.start();
            } else {
                ijk.pause();
            }
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (mediaItemIndex != currentIndex) {
            // 跨集 seek：换数据源，播完 prepare 后再 seek
            currentIndex = mediaItemIndex;
            pendingSeekMs = positionMs;
            prepared = false;
            ijk.reset();
            setDataSourceFor(mediaItems.get(mediaItemIndex));
            ijk.prepareAsync();
        } else if (prepared) {
            ijk.seekTo(positionMs);
        } else {
            startPositionMs = positionMs;
        }
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        if (prepared) ijk.setSpeed(playbackParameters.speed);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVolume(float volume) {
        this.volume = volume;
        ijk.setVolume(volume, volume);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetVideoOutput(@Nullable Object videoOutput) {
        if (videoOutput == null) {
            ijk.setSurface(null);
        } else if (videoOutput instanceof Surface surface) {
            ijk.setSurface(surface);
        } else if (videoOutput instanceof SurfaceHolder holder) {
            ijk.setDisplay(holder);
        } else if (videoOutput instanceof SurfaceView surfaceView) {
            ijk.setDisplay(surfaceView.getHolder());
        } else if (videoOutput instanceof TextureView textureView) {
            if (textureView.getSurfaceTexture() != null) ijk.setSurface(new Surface(textureView.getSurfaceTexture()));
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
        ijk.setSurface(null);
        return Futures.immediateVoidFuture();
    }

    /** 字幕/音轨选择：把 media3 的轨道选择映射回 IJK 的 selectTrack / deselectTrack。 */
    @Override
    protected ListenableFuture<?> handleSetTrackSelectionParameters(TrackSelectionParameters parameters) {
        for (Map.Entry<TrackGroup, Integer> entry : groupToIjkIndex.entrySet()) {
            TrackGroup group = entry.getKey();
            int ijkIndex = entry.getValue();
            TrackSelectionOverride override = parameters.overrides.get(group);
            boolean selected = override != null && !override.trackIndices.isEmpty();
            try {
                if (selected) {
                    ijk.selectTrack(ijkIndex);
                    selectedIjkTrack = ijkIndex;
                } else if (ijkIndex == selectedIjkTrack) {
                    ijk.deselectTrack(ijkIndex);
                    selectedIjkTrack = -1;
                    currentCues = CueGroup.EMPTY_TIME_ZERO;
                }
            } catch (Throwable ignored) {
            }
        }
        buildTracks();
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetAudioAttributes(androidx.media3.common.AudioAttributes audioAttributes, boolean handleAudioFocus) {
        // IJK 用 AudioTrack + STREAM_MUSIC，不走 media3 的音频焦点管理
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(@Player.RepeatMode int repeatMode) {
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
        this.mediaItems.addAll(index, mediaItems);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
        mediaItems.subList(fromIndex, toIndex).clear();
        if (currentIndex >= mediaItems.size()) currentIndex = Math.max(0, mediaItems.size() - 1);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleMoveMediaItems(int fromIndex, int toIndex, int newIndex) {
        List<MediaItem> moved = new ArrayList<>(mediaItems.subList(fromIndex, toIndex));
        mediaItems.subList(fromIndex, toIndex).clear();
        mediaItems.addAll(newIndex > fromIndex ? newIndex - moved.size() : newIndex, moved);
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleStop() {
        if (prepared) ijk.stop();
        prepared = false;
        playbackState = Player.STATE_IDLE;
        invalidateState();
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
        mainHandler.removeCallbacksAndMessages(null);
        released = true;
        try {
            ijk.release();
        } catch (Throwable ignored) {
        }
        return Futures.immediateVoidFuture();
    }

    public boolean isReleased() {
        return released;
    }
}
