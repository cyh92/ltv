package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.AdapterParseManageBinding;

import java.util.ArrayList;
import java.util.List;

public class ParseManageAdapter extends RecyclerView.Adapter<ParseManageAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Parse> mItems = new ArrayList<>();

    public ParseManageAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Parse item);

        void onItemRadio(Parse item);

        void onItemDelete(Parse item);
    }

    public void setItems(List<Parse> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterParseManageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Parse item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.url.setText(item.getUrl());
        holder.binding.type.setText(item.getType() == 1 ? R.string.parse_type_json : R.string.parse_type_web);
        holder.binding.radio.setChecked(item.getName().equals(VodConfig.get().getParse().getName()));
        holder.binding.radio.setOnClickListener(v -> listener.onItemRadio(item));
        holder.binding.delete.setOnClickListener(v -> listener.onItemDelete(item));
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterParseManageBinding binding;

        ViewHolder(@NonNull AdapterParseManageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
