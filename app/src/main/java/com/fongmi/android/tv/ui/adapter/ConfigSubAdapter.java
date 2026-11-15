package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterConfigSubBinding;
import com.fongmi.android.tv.utils.ConfigManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ConfigSubAdapter extends RecyclerView.Adapter<ConfigSubAdapter.ViewHolder> {

    public interface OnClickListener {
        void onToggle(ConfigManager.Sub item, boolean enabled);

        void onEdit(ConfigManager.Sub item);

        void onDelete(ConfigManager.Sub item);
    }

    private final OnClickListener listener;
    private final List<ConfigManager.Sub> items = new ArrayList<>();

    public ConfigSubAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void refresh(List<ConfigManager.Sub> subs) {
        items.clear();
        items.addAll(subs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigSubBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConfigManager.Sub item = items.get(position);
        holder.binding.name.setText(item.name);
        holder.binding.link.setText(item.getDisplayLink());
        holder.binding.kind.setText(kindText(holder.itemView.getContext(), item));
        holder.binding.active.setVisibility(item.id.equals(ConfigManager.getActiveId()) ? View.VISIBLE : View.GONE);
        holder.binding.toggle.setOnCheckedChangeListener(null);
        holder.binding.toggle.setChecked(item.enabled);
        holder.binding.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onToggle(item, isChecked));
        holder.binding.more.setOnClickListener(v -> showMenu(holder.itemView, item));
    }

    private void showMenu(View anchor, ConfigManager.Sub item) {
        new MaterialAlertDialogBuilder(anchor.getContext())
                .setItems(new CharSequence[]{anchor.getContext().getString(R.string.config_edit), anchor.getContext().getString(R.string.config_delete)}, (dialog, which) -> {
                    if (which == 0) listener.onEdit(item);
                    else listener.onDelete(item);
                })
                .show();
    }

    private String kindText(Context context, ConfigManager.Sub item) {
        int resId;
        if (item.isFolder()) resId = R.string.config_kind_folder;
        else if (item.isFile()) resId = R.string.config_kind_file;
        else if (item.isUrl()) resId = R.string.config_kind_url;
        else if (item.isJson()) resId = R.string.config_kind_json;
        else if (item.isLiveUrl()) resId = R.string.config_kind_live_url;
        else resId = R.string.config_kind_live_file;
        return context.getString(resId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterConfigSubBinding binding;

        public ViewHolder(@NonNull AdapterConfigSubBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
