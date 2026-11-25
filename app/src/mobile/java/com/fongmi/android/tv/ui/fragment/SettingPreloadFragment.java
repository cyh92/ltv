package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentSettingPreloadBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.PreloadDialog;
import com.fongmi.android.tv.utils.FileUtil;

public class SettingPreloadFragment extends BaseFragment {

    private FragmentSettingPreloadBinding mBinding;

    public static SettingPreloadFragment newInstance() {
        return new SettingPreloadFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingPreloadBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        refresh();
    }

    @Override
    protected void initEvent() {
        mBinding.preload.setOnClickListener(this::setPreload);
        mBinding.preloadWifi.setOnClickListener(this::setPreloadWifi);
        mBinding.preloadNext.setOnClickListener(this::setPreloadNext);
        mBinding.preloadDecoder.setOnClickListener(this::setPreloadDecoder);
        mBinding.preloadSize.setOnClickListener(view -> PreloadDialog.show(this, PreloadDialog.SIZE));
        mBinding.preloadTime.setOnClickListener(view -> PreloadDialog.show(this, PreloadDialog.TIME));
        mBinding.preloadThread.setOnClickListener(view -> PreloadDialog.show(this, PreloadDialog.THREADS));
    }

    private void refresh() {
        mBinding.preloadText.setText(Setting.getSwitch(PreloadSetting.isEnabled()));
        mBinding.preloadWifiText.setText(Setting.getSwitch(PreloadSetting.isWifiOnly()));
        mBinding.preloadNextText.setText(Setting.getSwitch(PreloadSetting.isNextEpisodeEnabled()));
        setPreloadDecoderText();
        setPreloadThreadsText();
        setPreloadSizeText();
        setPreloadTimeText();
        setVisible();
    }

    private void setVisible() {
        boolean preload = PreloadSetting.isEnabled();
        boolean exoPreload = preload && PlayerSetting.isExo();
        mBinding.preloadWifi.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadTime.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadNext.setVisibility(exoPreload ? View.VISIBLE : View.GONE);
        mBinding.preloadDecoder.setVisibility(exoPreload && PreloadSetting.isNextEpisodeEnabled() ? View.VISIBLE : View.GONE);
        mBinding.preloadSize.setVisibility(exoPreload ? View.VISIBLE : View.GONE);
        mBinding.preloadThread.setVisibility(exoPreload ? View.VISIBLE : View.GONE);
    }

    private void setPreload(View view) {
        PreloadSetting.putEnabled(!PreloadSetting.isEnabled());
        mBinding.preloadText.setText(Setting.getSwitch(PreloadSetting.isEnabled()));
        setVisible();
    }

    // 0.1.104：仅 Wi-Fi 下预载开关，默认开。
    private void setPreloadWifi(View view) {
        PreloadSetting.putWifiOnly(!PreloadSetting.isWifiOnly());
        mBinding.preloadWifiText.setText(Setting.getSwitch(PreloadSetting.isWifiOnly()));
    }

    private void setPreloadNext(View view) {
        PreloadSetting.putNextEpisodeEnabled(!PreloadSetting.isNextEpisodeEnabled());
        mBinding.preloadNextText.setText(Setting.getSwitch(PreloadSetting.isNextEpisodeEnabled()));
        setVisible();
    }

    private void setPreloadDecoder(View view) {
        PreloadSetting.putDecoderPrewarmingEnabled(!PreloadSetting.isDecoderPrewarmingEnabled());
        setPreloadDecoderText();
    }

    public void setPreload(int type, int value) {
        if (type == PreloadDialog.THREADS) {
            PreloadSetting.putThreads(value);
            setPreloadThreadsText();
        } else if (type == PreloadDialog.SIZE) {
            PreloadSetting.putSizeMb(value);
            setPreloadSizeText();
        } else if (type == PreloadDialog.TIME) {
            PreloadSetting.putTimeSeconds(value);
            setPreloadTimeText();
        }
    }

    private void setPreloadDecoderText() {
        mBinding.preloadDecoderText.setText(Setting.getSwitch(PreloadSetting.isDecoderPrewarmingEnabled()));
    }

    private void setPreloadSizeText() {
        mBinding.preloadSizeText.setText(FileUtil.byteCountToDisplaySize(PreloadSetting.getSizeBytes()));
    }

    private void setPreloadTimeText() {
        mBinding.preloadTimeText.setText(getString(R.string.player_preload_time_value, PreloadSetting.getTimeSeconds()));
    }

    private void setPreloadThreadsText() {
        mBinding.preloadThreadText.setText(getString(R.string.player_preload_threads_value, PreloadSetting.getThreads()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}
