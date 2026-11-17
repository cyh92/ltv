package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogScaleBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 缩放比例弹窗：5 行（原始/填充/裁剪/16:9/4:3），选中高亮，点一行立即生效并关闭。
 */
public final class ScaleDialog {

    private Runnable onChanged;

    public static ScaleDialog create() {
        return new ScaleDialog();
    }

    public ScaleDialog onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof BottomSheet) return;
        BottomSheet sheet = new BottomSheet();
        sheet.onChanged = onChanged;
        sheet.show(manager, null);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private Runnable onChanged;
        private DialogScaleBinding binding;
        private final List<MaterialTextView> rows = new ArrayList<>();

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DialogScaleBinding.inflate(inflater, container, false);
        }

        @Override
        protected int getMaxHeight() {
            return ResUtil.getScreenHeight() / 2;
        }

        @Override
        protected void initView() {
            String[] names = ResUtil.getStringArray(R.array.select_scale);
            rows.add(binding.scale0);
            rows.add(binding.scale1);
            rows.add(binding.scale2);
            rows.add(binding.scale3);
            rows.add(binding.scale4);
            for (int i = 0; i < rows.size(); i++) {
                int index = i;
                rows.get(i).setText(names[i]);
                rows.get(i).setOnClickListener(v -> select(index));
            }
            refresh();
        }

        @Override
        protected void initEvent() {
            binding.cancel.setOnClickListener(v -> dismiss());
        }

        private void refresh() {
            int current = PlayerSetting.getScale();
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).setSelected(i == current);
                rows.get(i).setBackgroundResource(i == current ? R.drawable.shape_scale_selected : R.drawable.shape_item);
            }
        }

        private void select(int index) {
            PlayerSetting.putScale(index);
            refresh();
            if (onChanged != null) onChanged.run();
            dismiss();
        }

        @Override
        public void onDestroyView() {
            binding = null;
            rows.clear();
            super.onDestroyView();
        }
    }
}
