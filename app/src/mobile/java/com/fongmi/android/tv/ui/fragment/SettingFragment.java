package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.DeviceConnectDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private static final String STATE_TAB = "tab";

    private FragmentSettingBinding mBinding;
    private TextView[] mTabs;
    private int mTab;

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) mTab = savedInstanceState.getInt(STATE_TAB, 0);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, mTab);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mTabs = new TextView[]{mBinding.tabConfig, mBinding.tabModule, mBinding.tabPlayer, mBinding.tabTheme, mBinding.tabData};
        for (int i = 0; i < mTabs.length; i++) {
            int index = i;
            mTabs[i].setOnClickListener(v -> selectTab(index));
        }
        selectTab(mTab);
    }

    @Override
    protected void initEvent() {
        mBinding.settingToolbar.setOnMenuItemClickListener(this::onMenuItemClick);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() != R.id.deviceConnect) return false;
        DeviceConnectDialog.show(requireActivity());
        return true;
    }

    private void selectTab(int index) {
        mTab = index;
        for (int i = 0; i < mTabs.length; i++) mTabs[i].setSelected(i == index);
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        for (int i = 0; i < mTabs.length; i++) {
            Fragment f = fm.findFragmentByTag("tab" + i);
            if (i == index) {
                if (f == null) ft.add(R.id.container, createTab(i), "tab" + i);
                else ft.show(f);
            } else if (f != null) {
                ft.hide(f);
            }
        }
        ft.commit();
        refreshTab(index);
    }

    private Fragment createTab(int index) {
        return switch (index) {
            case 0 -> ConfigFragment.newInstance(true);
            case 1 -> ModuleFragment.newInstance(true);
            case 2 -> SettingPlayerFragment.newInstance(true);
            case 3 -> ThemeFragment.newInstance(true);
            case 4 -> DataFragment.newInstance();
            default -> null;
        };
    }

    private void refreshTab(int index) {
        Fragment f = getChildFragmentManager().findFragmentByTag("tab" + index);
        if (f == null || !f.isAdded() || f.getView() == null) return;
        if (f instanceof ModuleFragment) ((ModuleFragment) f).refresh();
        else if (f instanceof SettingPlayerFragment) ((SettingPlayerFragment) f).refresh();
        else if (f instanceof DataFragment) ((DataFragment) f).refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        refreshTab(mTab);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) PermissionUtil.requestFile(this, allGranted -> load(config));
        else load(config);
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    public void reloadVod() {
        VodConfig.load(VodConfig.get().getConfig(), getCallback());
    }
}
