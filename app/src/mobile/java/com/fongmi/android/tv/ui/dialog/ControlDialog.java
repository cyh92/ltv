package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.DecoderMode;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.databinding.DialogControlBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.utils.ResUtil;

public class ControlDialog extends BaseBottomSheetDialog {

    private DialogControlBinding binding;
    private ActivityVideoBinding parent;
    private PlayerManager player;

    /** 倍速档位：和参考菜单一致的 10 档。 */
    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f, 5.0f};
    /** 缩放格子显示顺序 → PlayerSetting 下标（select_scale 数组顺序是 原始/填充/裁剪/16:9/4:3，不能动）。 */
    private static final int[] SCALES = {0, 3, 4, 1, 2};
    /** 解码方式格子：自动 / 硬解 / 软解（MPV 没有软解）。 */
    private static final DecoderMode[] MODES = {DecoderMode.AUTO, DecoderMode.HARDWARE, DecoderMode.SOFTWARE};

    private TextView[] speedViews;
    private TextView[] scaleViews;
    private TextView[] decodeViews;

    public static ControlDialog create() {
        return new ControlDialog();
    }

    public ControlDialog parent(ActivityVideoBinding parent) {
        this.parent = parent;
        return this;
    }

    public ControlDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public ControlDialog show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof ControlDialog) return this;
        show(activity.getSupportFragmentManager(), null);
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogControlBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.opening.setText(parent.control.action.opening.getText());
        binding.ending.setText(parent.control.action.ending.getText());
        speedViews = new TextView[]{binding.speed0, binding.speed1, binding.speed2, binding.speed3, binding.speed4, binding.speed5, binding.speed6, binding.speed7, binding.speed8, binding.speed9};
        scaleViews = new TextView[]{binding.scale0, binding.scale1, binding.scale2, binding.scale3, binding.scale4};
        decodeViews = new TextView[]{binding.decode0, binding.decode1, binding.decode2};
        binding.decode2.setVisibility(PlayerSetting.isMpv() ? View.GONE : View.VISIBLE);
        refreshDecode();
        for (int i = 0; i < speedViews.length; i++) speedViews[i].setText(SpeedSetting.formatValue(SPEEDS[i]));
        String[] names = ResUtil.getStringArray(R.array.select_scale);
        for (int i = 0; i < scaleViews.length; i++) scaleViews[i].setText(names[SCALES[i]]);
        refreshSpeed();
        refreshScale();
    }

    @Override
    protected void initEvent() {
        binding.opening.setOnClickListener(v -> click(binding.opening, parent.control.action.opening));
        binding.ending.setOnClickListener(v -> click(binding.ending, parent.control.action.ending));
        binding.opening.setOnLongClickListener(v -> longClick(binding.opening, parent.control.action.opening));
        binding.ending.setOnLongClickListener(v -> longClick(binding.ending, parent.control.action.ending));
        for (int i = 0; i < speedViews.length; i++) {
            int index = i;
            speedViews[i].setOnClickListener(v -> setSpeed(index));
        }
        for (int i = 0; i < scaleViews.length; i++) {
            int index = i;
            scaleViews[i].setOnClickListener(v -> setScale(index));
        }
        for (int i = 0; i < decodeViews.length; i++) {
            int index = i;
            decodeViews[i].setOnClickListener(v -> setDecode(index));
        }
    }

    private void refreshDecode() {
        DecoderMode current = DecodeSetting.getDecoderMode(PlayerSetting.getEngine(), C.TRACK_TYPE_VIDEO);
        for (int i = 0; i < decodeViews.length; i++) decodeViews[i].setSelected(MODES[i] == current);
    }

    private void setDecode(int index) {
        DecodeSetting.putDecoderMode(PlayerSetting.getEngine(), C.TRACK_TYPE_VIDEO, MODES[index]);
        refreshDecode();
    }

    private void refreshSpeed() {
        float current = player.getSpeed();
        for (int i = 0; i < speedViews.length; i++) speedViews[i].setSelected(Math.abs(SPEEDS[i] - current) < 0.001f);
    }

    private void setSpeed(int index) {
        player.setSpeed(SPEEDS[index]);
        refreshSpeed();
        ((Listener) requireActivity()).onSpeedChanged();
    }

    private void refreshScale() {
        int current = PlayerSetting.getScale();
        for (int i = 0; i < scaleViews.length; i++) scaleViews[i].setSelected(SCALES[i] == current);
    }

    private void setScale(int index) {
        PlayerSetting.putScale(SCALES[index]);
        refreshScale();
        ((Listener) requireActivity()).onScaleChanged();
    }

    private void click(TextView view, TextView target) {
        target.performClick();
        view.setText(target.getText());
    }

    private boolean longClick(TextView view, TextView target) {
        target.performLongClick();
        view.setText(target.getText());
        return true;
    }

    public interface Listener {

        void onParse(Parse item);

        void onScaleChanged();

        default void onSpeedChanged() {
        }
    }
}
