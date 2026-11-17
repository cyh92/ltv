package com.fongmi.android.tv.ui.fragment;

import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.DecoderMode;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.player.mpv.MpvUtil;
import com.fongmi.android.tv.setting.DecodeSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.BufferDialog;
import com.fongmi.android.tv.ui.dialog.DisplaySettingDialog;
import com.fongmi.android.tv.ui.dialog.MpvConfDialog;
import com.fongmi.android.tv.ui.dialog.PlayerButtonsEditDialog;
import com.fongmi.android.tv.ui.dialog.ScaleDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.RuntimeModule;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingPlayerFragment extends BaseFragment implements UaListener, BufferListener {

    private FragmentSettingPlayerBinding mBinding;
    private String[] background;
    private String[] render;
    private String[] engine;
    private boolean embedded;

    public static SettingPlayerFragment newInstance() {
        return newInstance(false);
    }

    public static SettingPlayerFragment newInstance(boolean embedded) {
        SettingPlayerFragment fragment = new SettingPlayerFragment();
        Bundle args = new Bundle();
        args.putBoolean("embedded", embedded);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) embedded = getArguments().getBoolean("embedded", false);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPlayerBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (embedded) mBinding.toolbar.setVisibility(View.GONE);
        setVisible();
        render = ResUtil.getStringArray(R.array.select_render);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.libassText.setText(Setting.getSwitch(PlayerSetting.isLibass()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        setMpvOptions();
        mBinding.backgroundText.setText((background = ResUtil.getStringArray(R.array.select_background))[PlayerSetting.getBackground()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
        refreshEngineCard();
        refreshDecodeCard();
        refreshLongPressText();
        refreshScaleText();
    }

    @Override
    protected void initEvent() {
        mBinding.engineGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.engineExo) selectEngine(PlayerSetting.ENGINE_EXO);
            else if (checkedId == R.id.engineMpv) selectEngine(PlayerSetting.ENGINE_MPV);
        });
        mBinding.decodeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.decodeAuto) selectDecodeMode(DecoderMode.AUTO);
            else if (checkedId == R.id.decodeHardware) selectDecodeMode(DecoderMode.HARDWARE);
            else if (checkedId == R.id.decodeSoftware) selectDecodeMode(DecoderMode.SOFTWARE);
        });
        mBinding.decodeAdvanced.setOnClickListener(this::onDecode);
        mBinding.displaySetting.setOnClickListener(v -> DisplaySettingDialog.create().show(requireActivity()));
        mBinding.playerButtons.setOnClickListener(v -> PlayerButtonsEditDialog.create().show(requireActivity()));
        mBinding.longPressSpeed.setOnClickListener(this::onLongPressSpeed);
        mBinding.scale.setOnClickListener(v -> ScaleDialog.create().onChanged(this::refreshScaleText).show(requireActivity()));
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.libass.setOnClickListener(this::setLibass);
        mBinding.mpvConf.setOnClickListener(this::onMpvConf);
        mBinding.mpvGpuNext.setOnClickListener(this::setMpvGpuNext);
        mBinding.mpvVulkan.setOnClickListener(this::setMpvVulkan);
        mBinding.mpvMotion.setOnClickListener(this::setMpvMotion);
        mBinding.exoMotion.setOnClickListener(this::setExoMotion);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.preload.setOnClickListener(this::onPreload);
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.incognito.setOnClickListener(this::setIncognito);
    }

    private void setVisible() {
        boolean exo = PlayerSetting.isExo();
        boolean exoMotion = exo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        mBinding.exoMotion.setVisibility(exoMotion ? View.VISIBLE : View.GONE);
        setExoMotionText();
        boolean vulkan = !exo && MpvUtil.isVulkanSupported();
        mBinding.mpvConf.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvVulkan.setVisibility(vulkan ? View.VISIBLE : View.GONE);
        mBinding.mpvMotion.setVisibility(vulkan ? View.VISIBLE : View.GONE);
        mBinding.mpvGpuNext.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.libass.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.buffer.setVisibility(exo ? View.VISIBLE : View.GONE);
    }

    private void refreshEngineCard() {
        engine = ResUtil.getStringArray(R.array.select_engine);
        boolean mpvInstalled = RuntimeModule.MPV.isInstalled();
        mBinding.engineMpv.setVisibility(mpvInstalled ? View.VISIBLE : View.GONE);
        mBinding.engineGroup.check(PlayerSetting.isExo() ? R.id.engineExo : R.id.engineMpv);
        mBinding.engineSubtitle.setText(getString(R.string.player_kernel_current, PlayerSetting.isExo() ? "ExoPlayer" : "MPV"));
    }

    private void selectEngine(int index) {
        if (index == PlayerSetting.getEngine()) return;
        if (index == PlayerSetting.ENGINE_MPV && !MpvUtil.checkInstalled()) return;
        PlayerSetting.putEngine(index);
        refreshEngineCard();
        refreshDecodeCard();
        setVisible();
    }

    private void refreshDecodeCard() {
        engine = ResUtil.getStringArray(R.array.select_engine);
        mBinding.decodeTitle.setText(getString(R.string.player_decode_setting) + "（" + engine[PlayerSetting.getEngine()] + "）");
        mBinding.decodeSoftware.setVisibility(PlayerSetting.isMpv() ? View.GONE : View.VISIBLE);
        DecoderMode mode = DecodeSetting.getDecoderMode(PlayerSetting.getEngine(), C.TRACK_TYPE_VIDEO);
        int checkedId = mode == DecoderMode.AUTO ? R.id.decodeAuto : mode == DecoderMode.HARDWARE ? R.id.decodeHardware : mode == DecoderMode.SOFTWARE ? R.id.decodeSoftware : 0;
        if (checkedId != 0) mBinding.decodeGroup.check(checkedId);
    }

    private void selectDecodeMode(DecoderMode mode) {
        if (mode == DecodeSetting.getDecoderMode(PlayerSetting.getEngine(), C.TRACK_TYPE_VIDEO)) return;
        DecodeSetting.putDecoderMode(PlayerSetting.getEngine(), C.TRACK_TYPE_VIDEO, mode);
        refreshDecodeCard();
    }

    private void refreshLongPressText() {
        mBinding.longPressSpeedText.setText(SpeedSetting.format(SpeedSetting.getLongPress()));
    }

    private void refreshScaleText() {
        mBinding.scaleText.setText(ResUtil.getStringArray(R.array.select_scale)[PlayerSetting.getScale()]);
    }

    private void onLongPressSpeed(View view) {
        float[] speeds = {2.0f, 2.5f, 3.0f, 4.0f, 5.0f};
        String[] items = new String[speeds.length];
        int checked = 0;
        for (int i = 0; i < speeds.length; i++) {
            items[i] = SpeedSetting.format(speeds[i]);
            if (speeds[i] == SpeedSetting.getLongPress()) checked = i;
        }
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_long_press_speed).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, checked, (dialog, which) -> {
            SpeedSetting.putLongPress(speeds[which]);
            refreshLongPressText();
            dialog.dismiss();
        }).show();
    }

    private void onMpvConf(View view) {
        MpvConfDialog.show(this);
    }

    private void setMpvGpuNext(View view) {
        PlayerSetting.putMpvGpuNext(!PlayerSetting.isMpvGpuNext());
        setMpvOptions();
    }

    private void setMpvVulkan(View view) {
        PlayerSetting.putMpvVulkan(!PlayerSetting.isMpvVulkan());
        setMpvOptions();
    }

    private void setExoMotion(View view) {
        String[] items = ResUtil.getStringArray(R.array.select_mpv_motion);
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_mpv_motion).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, PlayerSetting.getExoMotion(), this::onExoMotion).show();
    }

    private void onExoMotion(DialogInterface dialog, int which) {
        PlayerSetting.putExoMotion(which);
        setExoMotionText();
        Notify.show(R.string.player_motion_restart);
        dialog.dismiss();
    }

    private void setExoMotionText() {
        mBinding.exoMotionText.setText(ResUtil.getStringArray(R.array.select_mpv_motion)[PlayerSetting.getExoMotion()]);
    }

    private void setMpvMotion(View view) {
        String[] items = ResUtil.getStringArray(R.array.select_mpv_motion);
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_mpv_motion).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, PlayerSetting.getMpvMotion(), this::onMpvMotion).show();
    }

    private void onMpvMotion(DialogInterface dialog, int which) {
        PlayerSetting.putMpvMotion(which);
        setMpvOptions();
        dialog.dismiss();
    }

    private void setMpvOptions() {
        boolean motion = MpvUtil.isMotionEnabled();
        mBinding.mpvMotionText.setText(ResUtil.getStringArray(R.array.select_mpv_motion)[PlayerSetting.getMpvMotion()]);
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext() || motion));
        mBinding.mpvVulkanText.setText(Setting.getSwitch(PlayerSetting.isMpvVulkan() || motion));
        mBinding.mpvGpuNext.setEnabled(!motion);
        mBinding.mpvVulkan.setEnabled(!motion);
        mBinding.mpvGpuNext.setAlpha(motion ? 0.5f : 1f);
        mBinding.mpvVulkan.setAlpha(motion ? 0.5f : 1f);
    }

    private void setRender(View view) {
        int index = (PlayerSetting.getRender() + 1) % render.length;
        PlayerSetting.putRender(index);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        PlayerSetting.putBuffer(times);
    }

    private void onBackground(View view) {
        new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.player_background).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(background, PlayerSetting.getBackground(), (dialog, which) -> {
            mBinding.backgroundText.setText(background[which]);
            PlayerSetting.putBackground(which);
            dialog.dismiss();
        }).show();
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setLibass(View view) {
        PlayerSetting.putLibass(!PlayerSetting.isLibass());
        mBinding.libassText.setText(Setting.getSwitch(PlayerSetting.isLibass()));
    }

    private void onPreload(View view) {
        ((HomeActivity) requireActivity()).change(4);
    }

    private void onDecode(View view) {
        ((HomeActivity) requireActivity()).change(5);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    public void refresh() {
        if (mBinding != null) initView();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) initView();
    }
}
