package com.fongmi.android.tv.player.ijk;

import androidx.media3.common.PlaybackException;

/** IJK 播放错误的文案（与 MpvErrorMessageProvider 同款式）。 */
public class IjkErrorMessageProvider {

    public String get(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_BAD_VALUE -> "IJK Bad Value";
            case PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "IJK Runtime Error";
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IJK IO Error";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "IJK Container Unsupported";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "IJK Audio Init Failed";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "IJK Decoder Init Failed";
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "IJK Decoder Query Failed";
            case PlaybackException.ERROR_CODE_DECODING_FAILED -> "IJK Decoding Failed";
            default -> "IJK Playback Error";
        };
    }
}
