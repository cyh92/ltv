package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 0.1.104：缓冲改成三档预设单选（省流 1× / 均衡 3× / 流畅 6×），
 * 存在同一个 "buffer" 键里；老的 1..10 数值继续有效，打开时显示最近一档。
 * 点确定才写，点取消不动。
 */
public final class BufferDialog {

    private BufferDialog() {
    }

    public static void show(Fragment fragment) {
        int checked = PlayerSetting.getBufferPreset();
        int[] selected = {checked};
        new MaterialAlertDialogBuilder(fragment.requireActivity())
                .setTitle(R.string.player_buffer)
                .setSingleChoiceItems(ResUtil.getStringArray(R.array.select_buffer_preset), checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> ((BufferListener) fragment).setBuffer(PlayerSetting.getBufferPresetValue(selected[0])))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }
}
