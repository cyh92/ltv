package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentModuleBinding;
import com.fongmi.android.tv.databinding.ItemModuleBinding;
import com.fongmi.android.tv.model.ModuleViewModel;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.ModuleDialog;
import com.fongmi.android.tv.utils.ModuleUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.RuntimeInstaller;
import com.github.catvod.utils.RuntimeModule;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ModuleFragment extends BaseFragment {

    private FragmentModuleBinding mBinding;
    private ModuleViewModel[] models;
    private ItemModuleBinding[] items;
    private boolean embedded;
    private boolean suppressToggle;

    public static ModuleFragment newInstance() {
        return newInstance(false);
    }

    public static ModuleFragment newInstance(boolean embedded) {
        ModuleFragment fragment = new ModuleFragment();
        Bundle args = new Bundle();
        args.putBoolean("embedded", embedded);
        fragment.setArguments(args);
        return fragment;
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) embedded = getArguments().getBoolean("embedded", false);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentModuleBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (embedded) mBinding.toolbar.setVisibility(View.GONE);
        mBinding.toolbar.getMenu().add(R.string.module_check).setIcon(R.drawable.ic_setting_refresh).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mBinding.toolbar.setOnMenuItemClickListener(this::onCheck);
        models = new ModuleViewModel[]{ModuleViewModel.get(getRoot(), RuntimeModule.PYTHON), ModuleViewModel.get(getRoot(), RuntimeModule.NODEJS), ModuleViewModel.get(getRoot(), RuntimeModule.QUICKJS), ModuleViewModel.get(getRoot(), RuntimeModule.THUNDER), ModuleViewModel.get(getRoot(), RuntimeModule.MPV)};
        items = new ItemModuleBinding[]{mBinding.python, mBinding.nodejs, mBinding.quickjs, mBinding.thunder, mBinding.mpv};
        for (int i = 0; i < items.length; i++) bind(items[i], models[i], i);
        updateCount();
    }

    private boolean onCheck(MenuItem item) {
        for (ModuleViewModel model : models) model.check();
        return true;
    }

    private void bind(ItemModuleBinding binding, ModuleViewModel model, int index) {
        binding.icon.setImageResource(model.getIcon());
        binding.title.setText(model.getTitle());
        binding.desc.setText(getDescRes(index));
        binding.toggle.setOnCheckedChangeListener((button, checked) -> onToggle(index, checked));
        binding.getRoot().setOnClickListener(view -> select(index));
        model.getState().observe(getViewLifecycleOwner(), state -> render(binding, model, state));
    }

    private int getDescRes(int index) {
        return switch (index) {
            case 0 -> R.string.module_desc_python;
            case 1 -> R.string.module_desc_nodejs;
            case 2 -> R.string.module_desc_quickjs;
            case 3 -> R.string.module_desc_thunder;
            case 4 -> R.string.module_desc_mpv;
            default -> 0;
        };
    }

    private void onToggle(int index, boolean checked) {
        if (suppressToggle) return;
        ModuleViewModel model = models[index];
        ModuleViewModel.State state = model.getState().getValue();
        if (checked) {
            if (state.canInstall()) model.install();
            else render(items[index], model, state);
        } else {
            if (state.canRemove()) confirmRemove(index);
            else render(items[index], model, state);
        }
    }

    private void confirmRemove(int index) {
        ModuleViewModel model = models[index];
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.module_remove_confirm, model.getTitle()))
                .setPositiveButton(R.string.module_remove, (d, which) -> model.remove())
                .setNegativeButton(R.string.module_cancel, (d, which) -> render(items[index], model, model.getState().getValue()))
                .create();
        dialog.show();
    }

    private void select(int index) {
        ModuleViewModel model = models[index];
        ModuleViewModel.State state = model.getState().getValue();
        if (state.isPending()) confirmInstallation(model, state);
        else if (state.canInstall()) model.install();
        else ModuleDialog.create(index).show(getRoot());
    }

    private void confirmInstallation(ModuleViewModel model, ModuleViewModel.State state) {
        Notify.show(model.getStatusText(state));
        RuntimeInstaller.confirmInstallation(getRoot());
    }

    private void render(ItemModuleBinding binding, ModuleViewModel model, ModuleViewModel.State state) {
        ModuleUtil.render(binding, model, state);
        mBinding.toolbar.getMenu().getItem(0).setEnabled(!ModuleUtil.isBusy(models));
        ModuleViewModel.Status runtime = state.runtime();
        boolean installed = runtime != null && runtime.installedVersion() != null;
        boolean busy = state.isBusy();
        binding.status.setVisibility(View.GONE);
        binding.toggle.setVisibility(busy ? View.GONE : View.VISIBLE);
        binding.toggle.setEnabled(!busy && (installed ? state.canRemove() : state.canInstall()));
        suppressToggle = true;
        binding.toggle.setChecked(installed);
        suppressToggle = false;
        binding.info.setText(getInfoText(model, state));
        updateCount();
    }

    private String getInfoText(ModuleViewModel model, ModuleViewModel.State state) {
        ModuleViewModel.Status runtime = state.runtime();
        String status = getString(model.getStatusText(state));
        if (state.phase() == ModuleViewModel.Phase.INSTALLING && state.total() > 0 && state.downloaded() >= 0) {
            return status + " " + (int) (100L * state.downloaded() / state.total()) + "%";
        }
        if (runtime == null) return status;
        if (runtime.installedVersion() != null) {
            String text = status + " · " + runtime.installedVersion();
            if (runtime.updateAvailable() && runtime.availableVersion() != null) text += " → " + runtime.availableVersion();
            return text;
        }
        if (runtime.availableVersion() != null) return status + " · " + runtime.availableVersion();
        return status;
    }

    private void updateCount() {
        if (mBinding == null || models == null) return;
        int count = 0;
        for (ModuleViewModel model : models) {
            ModuleViewModel.State state = model.getState().getValue();
            if (state != null && state.runtime() != null && state.runtime().installedVersion() != null) count++;
        }
        mBinding.installedCount.setText(getString(R.string.module_installed_count, count));
    }

    public void refresh() {
        if (mBinding != null && !isHidden()) for (ModuleViewModel model : models) model.refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        for (ItemModuleBinding item : items) item.progress.setWaveSpeed(0);
        mBinding = null;
        models = null;
        items = null;
        super.onDestroyView();
    }
}
