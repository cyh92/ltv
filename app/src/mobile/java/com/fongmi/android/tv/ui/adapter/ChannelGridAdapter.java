package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.databinding.AdapterChannelGridBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖屏直播频道网格（4 列，只显示频道名，当前频道描边高亮）。
 * 与侧边列表的 ChannelAdapter 共用同一批 Channel 对象和点击回调，
 * 选中态由 Channel.isSelected() 驱动，两边自动同步。
 */
public class ChannelGridAdapter extends RecyclerView.Adapter<ChannelGridAdapter.ViewHolder> {

    private final ChannelAdapter.OnClickListener listener;
    private final List<Channel> mItems;

    public ChannelGridAdapter(ChannelAdapter.OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    public void setSelected(int position) {
        if (position == -1) return;
        for (int i = 0; i < mItems.size(); i++) updateSelection(i, i == position);
    }

    private void updateSelection(int position, boolean shouldSelect) {
        Channel item = mItems.get(position);
        if (item.isSelected() == shouldSelect) return;
        item.setSelected(shouldSelect);
        notifyItemChanged(position, Boolean.TRUE);
    }

    public int setSelected(Channel channel) {
        int position = mItems.indexOf(channel);
        setSelected(position);
        return position;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ChannelGridAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelGridAdapter.ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        holder.binding.name.setText(item.getShow());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.stream().allMatch(Boolean.TRUE::equals)) holder.binding.getRoot().setSelected(mItems.get(position).isSelected());
        else onBindViewHolder(holder, position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterChannelGridBinding binding;

        ViewHolder(@NonNull AdapterChannelGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
