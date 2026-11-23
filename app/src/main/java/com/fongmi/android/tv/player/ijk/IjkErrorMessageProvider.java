package com.fongmi.android.tv.player.ijk;

import androidx.media3.common.PlaybackException;

/** IJK 播放错误的文案（与 MpvErrorMessageProvider 同款式）。 */
public class IjkErrorMessageProvider {

    public String get(PlaybackException e) {
        String base = switch (e.errorCode) {
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
        // 0.1.96：把 IJK 的原始错误细节带上。适配器在 PlaybackException 的 message 里
        // 放了 "IJK error what/extra"（如 "IJK error 1/-1004"），之前只显示 "IJK IO Error"，
        // 看不出是协议不支持、网络失败还是解码失败，直播报错没法定性。带上细节后用户报
        // 错时能直接看到 what/extra，才能区分是流协议问题还是网络问题。
        String detail = e.getMessage();
        return detail == null || detail.isEmpty() ? base : base + "（" + detail + "）";
    }
}
