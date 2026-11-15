package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityThemeBinding;
import com.fongmi.android.tv.databinding.ItemThemeBgBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;

public class ThemeActivity extends BaseActivity {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ThemeActivity.class));
    }

    private static final int[] BGS = {0, R.drawable.wallpaper_1, R.drawable.wallpaper_2, R.drawable.wallpaper_3, R.drawable.wallpaper_4, R.drawable.wallpaper_5, R.drawable.wallpaper_6, R.drawable.wallpaper_7, R.drawable.wallpaper_8};
    private static final int[] BG_NAMES = {R.string.theme_bg_none, R.string.theme_bg_1, R.string.theme_bg_2, R.string.theme_bg_3, R.string.theme_bg_4, R.string.theme_bg_5, R.string.theme_bg_6, R.string.theme_bg_7, R.string.theme_bg_8};

    private ActivityThemeBinding mBinding;
    private View[] mModeViews;
    private View[] mBgViews;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityThemeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());
        mModeViews = new View[]{mBinding.modeLight, mBinding.modeDark, mBinding.modeSystem};
        for (int i = 0; i < mModeViews.length; i++) {
            int mode = i;
            mModeViews[i].setOnClickListener(v -> setThemeMode(mode));
        }
        mBgViews = new View[BGS.length];
        for (int i = 0; i < BGS.length; i++) {
            ItemThemeBgBinding item = ItemThemeBgBinding.inflate(LayoutInflater.from(this), mBinding.bgGrid, false);
            item.name.setText(BG_NAMES[i]);
            if (BGS[i] == 0) {
                item.image.setImageResource(R.drawable.ic_action_close);
                item.image.setScaleType(ImageView.ScaleType.CENTER);
            } else {
                item.image.setImageResource(BGS[i]);
            }
            int index = i;
            item.getRoot().setOnClickListener(v -> selectBg(index));
            mBinding.bgGrid.addView(item.getRoot());
            mBgViews[i] = item.getRoot();
        }
        mBinding.alphaSeek.setMax(100);
        mBinding.alphaSeek.setProgress(Setting.getWallAlpha());
        mBinding.alphaText.setText(getString(R.string.theme_bg_alpha_value, Setting.getWallAlpha()));
        mBinding.alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mBinding.alphaText.setText(getString(R.string.theme_bg_alpha_value, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Setting.putWallAlpha(seekBar.getProgress());
                ConfigEvent.wall();
            }
        });
        updateMode();
        updateBg();
    }

    private void setThemeMode(int mode) {
        Setting.putThemeMode(mode);
        Setting.applyThemeMode();
        updateMode();
        RefreshEvent.theme();
    }

    private void updateMode() {
        int mode = Setting.getThemeMode();
        for (int i = 0; i < mModeViews.length; i++) mModeViews[i].setSelected(i == mode);
    }

    private void selectBg(int index) {
        Setting.putWall(index);
        Setting.putWallType(0);
        ConfigEvent.wall();
        updateBg();
    }

    private void updateBg() {
        int wall = Setting.getWall();
        for (int i = 0; i < mBgViews.length; i++) {
            mBgViews[i].setBackgroundResource(i == wall ? R.drawable.shape_theme_bg_selected : R.drawable.shape_theme_bg);
        }
    }
}
