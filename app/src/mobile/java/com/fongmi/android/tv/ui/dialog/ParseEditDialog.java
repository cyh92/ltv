package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.DialogParseEditBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ParseStore;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParseEditDialog extends BaseAlertDialog {

    private static final String[][] PRESETS = {
            {"优酷", "youku"}, {"腾讯", "qq"}, {"爱奇艺", "iqiyi"}, {"芒果TV", "mgtv"},
            {"哔哩哔哩", "bilibili"}, {"搜狐", "sohu"}, {"西瓜视频", "xigua"}, {"PPTV", "pptv"},
            {"咪咕视频", "migu"}, {"乐视", "letv"}, {"1905电影网", "1905"}, {"风行", "fengxing"}
    };

    private DialogParseEditBinding binding;
    private Listener listener;
    private Parse parse;

    public interface Listener {

        void onSaved();
    }

    public static ParseEditDialog create(Parse parse) {
        ParseEditDialog dialog = new ParseEditDialog();
        if (parse != null) {
            Bundle args = new Bundle();
            args.putString("parse", App.gson().toJson(parse));
            dialog.setArguments(args);
        }
        return dialog;
    }

    public ParseEditDialog onSaved(Listener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    private Parse getParseArg() {
        if (parse == null && getArguments() != null) {
            try {
                parse = App.gson().fromJson(getArguments().getString("parse"), Parse.class);
            } catch (Exception e) {
                parse = null;
            }
        }
        return parse;
    }

    private boolean isEditing() {
        return getParseArg() != null;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogParseEditBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(isEditing() ? R.string.parse_edit : R.string.parse_add).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, null).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof AlertDialog dialog) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> onPositive());
        }
    }

    @Override
    protected void initView() {
        Parse item = getParseArg();
        List<String> flags = item == null ? new ArrayList<>() : item.getExt().getFlag();
        for (String[] preset : PRESETS) addChip(preset[0], preset[1], flags.contains(preset[1]), false);
        for (String flag : flags) if (!isPreset(flag)) addChip(flag, flag, true, true);
        if (item == null) {
            binding.typeToggle.check(R.id.typeJson);
            return;
        }
        binding.name.setText(item.getName());
        binding.url.setText(item.getUrl());
        binding.typeToggle.setVisibility(View.GONE);
        binding.typeLabel.setVisibility(View.VISIBLE);
        binding.typeLabel.setText(getTypeRes(item.getType()));
        Map<String, String> header = item.getHeader();
        binding.header.setText(header.isEmpty() ? "" : App.gson().toJson(header));
    }

    @Override
    protected void initEvent() {
        binding.addFlag.setOnClickListener(v -> {
            String code = binding.customFlag.getText().toString().trim();
            if (code.isEmpty()) return;
            Chip chip = findChip(code);
            if (chip != null) chip.setChecked(true);
            else addChip(code, code, true, true);
            binding.customFlag.setText("");
        });
    }

    private void onPositive() {
        String name = binding.name.getText().toString().trim();
        String url = binding.url.getText().toString().trim();
        if (name.isEmpty()) {
            Notify.show(R.string.parse_need_name);
            return;
        }
        if (url.isEmpty()) {
            Notify.show(R.string.parse_need_url);
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Notify.show(R.string.parse_bad_url);
            return;
        }
        Parse old = getParseArg();
        for (Parse item : ParseStore.getUserParses()) {
            if (item.getName().equals(name) && (old == null || !item.getName().equals(old.getName()))) {
                Notify.show(R.string.parse_name_exists);
                return;
            }
        }
        Map<String, String> header = new HashMap<>();
        String headerText = binding.header.getText().toString().trim();
        if (!headerText.isEmpty()) {
            try {
                header = App.gson().fromJson(headerText, new TypeToken<Map<String, String>>() {}.getType());
                if (header == null) header = new HashMap<>();
            } catch (JsonSyntaxException e) {
                Notify.show(R.string.parse_bad_header);
                return;
            }
        }
        Parse item = new Parse();
        item.setName(name);
        item.setUrl(url);
        item.setType(isEditing() ? old.getType() : (binding.typeToggle.getCheckedButtonId() == R.id.typeJson ? 1 : 0));
        List<String> flags = getFlags();
        if (!flags.isEmpty()) item.getExt().setFlag(flags);
        if (!header.isEmpty()) item.getExt().setHeader(header);
        boolean wasSelected = old != null && VodConfig.get().getParse().getName().equals(old.getName());
        if (old != null) ParseStore.remove(old);
        ParseStore.add(item);
        VodConfig.get().updateParses();
        if (wasSelected && !name.equals(old.getName())) VodConfig.get().setParse(VodConfig.get().getParse(name));
        if (listener != null) listener.onSaved();
        dismiss();
    }

    private Chip addChip(String label, String code, boolean checked, boolean closable) {
        Chip chip = new Chip(new ContextThemeWrapper(requireContext(), com.google.android.material.R.style.Widget_Material3_Chip_Filter));
        chip.setText(label);
        chip.setTag(code);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setId(View.generateViewId());
        if (closable) {
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> binding.platformGroup.removeView(chip));
        }
        binding.platformGroup.addView(chip);
        return chip;
    }

    private Chip findChip(String code) {
        for (int i = 0; i < binding.platformGroup.getChildCount(); i++) {
            if (binding.platformGroup.getChildAt(i) instanceof Chip chip && code.equals(chip.getTag())) return chip;
        }
        return null;
    }

    private boolean isPreset(String code) {
        for (String[] preset : PRESETS) if (preset[1].equals(code)) return true;
        return false;
    }

    private List<String> getFlags() {
        List<String> flags = new ArrayList<>();
        for (int id : binding.platformGroup.getCheckedChipIds()) {
            Chip chip = binding.platformGroup.findViewById(id);
            if (chip != null && chip.getTag() instanceof String code) flags.add(code);
        }
        return flags;
    }

    private int getTypeRes(int type) {
        if (type == 1) return R.string.parse_type_json;
        if (type == 2) return R.string.parse_type_json_ext;
        if (type == 3) return R.string.parse_type_json_mix;
        return R.string.parse_type_web;
    }
}
