package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private static final String STATE_TAB = "tab";

    public static int openTab = -1;

    private FragmentSettingBinding mBinding;
    private TextView[] mTabs;
    private View[] mModeViews;
    private NestedScrollView mTabScroll;
    private int mOffset;
    private int mContainerBaseMargin = -1;
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
        mModeViews = new View[]{mBinding.mineModeLight, mBinding.mineModeDark, mBinding.mineModeSystem};
        for (int i = 0; i < mModeViews.length; i++) {
            int mode = i;
            mModeViews[i].setOnClickListener(v -> setThemeMode(mode));
        }
        mBinding.mineStatKeep.setOnClickListener(v -> KeepActivity.start(requireActivity()));
        mBinding.mineStatHistory.setOnClickListener(v -> HistoryActivity.start(requireActivity()));
        mBinding.mineStatSite.setOnClickListener(v -> selectTab(0));
        mBinding.mineThemeMore.setOnClickListener(v -> selectTab(3));
        mBinding.mineBgRow.setOnClickListener(v -> selectTab(3));
        if (openTab >= 0) {
            mTab = openTab;
            openTab = -1;
        }
        mOffset = 0;
        mContainerBaseMargin = -1;
        mTabScroll = null;
        refreshMine();
        selectTab(mTab);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMine();
        attachScrollListener(mTab, 0);
    }

    public void refreshMine() {
        refreshStats();
        updateMode();
        updateBg();
    }

    private void refreshStats() {
        if (mBinding == null) return;
        mBinding.mineKeepNum.setText(String.valueOf(Keep.getVod().size() + Keep.getLive().size()));
        mBinding.mineHistoryNum.setText(String.valueOf(History.get().size()));
        mBinding.mineSiteNum.setText(String.valueOf(VodConfig.get().getSites().size()));
    }

    private void setThemeMode(int mode) {
        Setting.putThemeMode(mode);
        Setting.applyThemeMode();
        updateMode();
        RefreshEvent.theme();
    }

    private void updateMode() {
        if (mBinding == null || mModeViews == null) return;
        int mode = Setting.getThemeMode();
        for (int i = 0; i < mModeViews.length; i++) mModeViews[i].setSelected(i == mode);
    }

    private void updateBg() {
        if (mBinding == null) return;
        mBinding.mineBgThumb.setImageResource(ThemeFragment.BGS[Setting.getWall()]);
        mBinding.mineBgName.setText(ThemeFragment.BG_NAMES[Setting.getWall()]);
    }

    private final NestedScrollView.OnScrollChangeListener mScrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> applyOffset(scrollY);

    private void applyOffset(int scrollY) {
        if (mBinding == null) return;
        int headerH = mBinding.headerWrap.getHeight();
        if (headerH <= 0) return;
        if (mContainerBaseMargin < 0) mContainerBaseMargin = ((LinearLayout.LayoutParams) mBinding.container.getLayoutParams()).topMargin;
        int offset = Math.min(scrollY, headerH);
        if (offset == mOffset) return;
        mOffset = offset;
        mBinding.headerWrap.setTranslationY(-offset);
        mBinding.tabRow.setTranslationY(-offset);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mBinding.container.getLayoutParams();
        lp.topMargin = mContainerBaseMargin - offset;
        mBinding.container.setLayoutParams(lp);
    }

    private void resetOffset() {
        if (mBinding == null) return;
        mOffset = 0;
        mBinding.headerWrap.setTranslationY(0);
        mBinding.tabRow.setTranslationY(0);
        if (mContainerBaseMargin >= 0) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mBinding.container.getLayoutParams();
            lp.topMargin = mContainerBaseMargin;
            mBinding.container.setLayoutParams(lp);
        }
    }

    private void attachScrollListener(int index, int retry) {
        if (mBinding == null) return;
        Fragment f = getChildFragmentManager().findFragmentByTag("tab" + index);
        if (f == null || !f.isAdded() || f.getView() == null) {
            if (index == mTab && retry < 2) mBinding.container.post(() -> attachScrollListener(index, retry + 1));
            return;
        }
        NestedScrollView scroll = f.getView().findViewById(R.id.tabScroll);
        if (scroll == null) return;
        if (mTabScroll != null && mTabScroll != scroll) mTabScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) null);
        mTabScroll = scroll;
        mTabScroll.setOnScrollChangeListener(mScrollListener);
        applyOffset(scroll.getScrollY());
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
        resetOffset();
        mBinding.container.post(() -> attachScrollListener(index, 0));
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
        refreshMine();
        if (openTab >= 0) {
            selectTab(openTab);
            openTab = -1;
            return;
        }
        if (mBinding != null) mBinding.container.post(() -> attachScrollListener(mTab, 0));
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
