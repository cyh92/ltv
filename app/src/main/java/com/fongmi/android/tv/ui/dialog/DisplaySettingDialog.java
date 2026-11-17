package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogDisplaySettingBinding;
import com.fongmi.android.tv.setting.DisplaySetting;
import com.fongmi.android.tv.utils.ResUtil;

/**
 * 屏显设置弹窗：7 行眼睛开关（标题/分辨率/时间/进度/网速/迷你/播放参数），点确定关闭。
 */
public final class DisplaySettingDialog {

    public static DisplaySettingDialog create() {
        return new DisplaySettingDialog();
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof BottomSheet) return;
        new BottomSheet().show(manager, null);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private DialogDisplaySettingBinding binding;

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DialogDisplaySettingBinding.inflate(inflater, container, false);
        }

        @Override
        protected int getMaxHeight() {
            return ResUtil.getScreenHeight() / 2;
        }

        @Override
        protected void initView() {
            refresh();
        }

        @Override
        protected void initEvent() {
            binding.rowTitle.setOnClickListener(v -> {
                DisplaySetting.putTitle(!DisplaySetting.isTitle());
                refresh();
            });
            binding.rowResolution.setOnClickListener(v -> {
                DisplaySetting.putResolution(!DisplaySetting.isResolution());
                refresh();
            });
            binding.rowTime.setOnClickListener(v -> {
                DisplaySetting.putTime(!DisplaySetting.isTime());
                refresh();
            });
            binding.rowProgress.setOnClickListener(v -> {
                DisplaySetting.putProgress(!DisplaySetting.isProgress());
                refresh();
            });
            binding.rowSpeed.setOnClickListener(v -> {
                DisplaySetting.putSpeed(!DisplaySetting.isSpeed());
                refresh();
            });
            binding.rowMini.setOnClickListener(v -> {
                DisplaySetting.putMini(!DisplaySetting.isMini());
                refresh();
            });
            binding.rowParams.setOnClickListener(v -> {
                DisplaySetting.putParams(!DisplaySetting.isParams());
                refresh();
            });
            binding.confirm.setOnClickListener(v -> dismiss());
        }

        private void refresh() {
            setEye(binding.eyeTitle, DisplaySetting.isTitle());
            setEye(binding.eyeResolution, DisplaySetting.isResolution());
            setEye(binding.eyeTime, DisplaySetting.isTime());
            setEye(binding.eyeProgress, DisplaySetting.isProgress());
            setEye(binding.eyeSpeed, DisplaySetting.isSpeed());
            setEye(binding.eyeMini, DisplaySetting.isMini());
            setEye(binding.eyeParams, DisplaySetting.isParams());
        }

        private void setEye(ImageView eye, boolean on) {
            eye.setImageResource(on ? R.drawable.ic_display_eye : R.drawable.ic_display_eye_off);
        }

        @Override
        public void onDestroyView() {
            binding = null;
            super.onDestroyView();
        }
    }
}
