package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.utils.SiteOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> all = new ArrayList<>();
    private final List<Site> items = new ArrayList<>();
    private String keyword = "";

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        for (Site site : VodConfig.get().getSites()) if (!site.isHide()) all.add(site);
        List<Site> ordered = SiteOrder.applyOrder(all);
        all.clear();
        all.addAll(ordered);
        items.addAll(all);
    }

    public interface OnClickListener {

        void onTextClick(Site item);

        void onEditClick(Site item);

        void onDeleteClick(Site item);
    }

    public List<Site> getItems() {
        return items;
    }

    public boolean isFiltering() {
        return keyword != null && !keyword.trim().isEmpty();
    }

    public void filter(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
        items.clear();
        if (!isFiltering()) {
            items.addAll(all);
        } else {
            String key = this.keyword.toLowerCase(Locale.getDefault());
            for (Site site : all) if (site.getName().toLowerCase(Locale.getDefault()).contains(key)) items.add(site);
        }
        notifyDataSetChanged();
    }

    // Drag is always allowed (unless filtering); dragging keeps the custom order.
    public boolean canDrag() {
        return !isFiltering();
    }

    public void onMove(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        Collections.swap(items, from, to);
        int allFrom = all.indexOf(items.get(to));
        int allTo = all.indexOf(items.get(from));
        if (allFrom >= 0 && allTo >= 0) Collections.swap(all, allFrom, allTo);
        SiteOrder.putOrder(SiteOrder.keysOf(all));
        notifyItemMoved(from, to);
    }

    public void remove(Site item) {
        all.remove(item);
        int position = items.indexOf(item);
        if (position >= 0) {
            items.remove(position);
            notifyItemRemoved(position);
        }
        SiteOrder.putOrder(SiteOrder.keysOf(all));
    }

    // The site object is updated in place by the rename; just refresh its row.
    public void rename(Site item) {
        int position = items.indexOf(item);
        if (position >= 0) notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = items.get(position);
        holder.binding.text.setText(item.getName());
        // Selected state drives the capsule fill, the text color and the icon tints (all use selector colors).
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.edit.setSelected(item.isSelected());
        holder.binding.close.setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(v -> listener.onTextClick(item));
        holder.binding.edit.setOnClickListener(v -> listener.onEditClick(item));
        holder.binding.close.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
