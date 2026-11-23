package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.databinding.AdapterGroupPortBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖屏直播分类 tab（横向一排）。
 * 与侧边列表的 GroupAdapter 共用同一批 Group 对象和点击回调，选中态由 Group.isSelected() 驱动。
 * 不复用 GroupAdapter 是因为侧边列表的条目是白字配深底，在竖屏壁纸上看不清，
 * 这里用主题卡片底色 + 主题正文色；而且竖屏 tab 不需要 setWidth（不改侧边列表宽度）。
 */
public class GroupPortAdapter extends RecyclerView.Adapter<GroupPortAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Group> mItems;

    public GroupPortAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Group item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void add(Group item) {
        mItems.add(item);
        notifyItemInserted(getItemCount() - 1);
    }

    public Group get(int position) {
        return mItems.get(position);
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public int indexOf(Group group) {
        return mItems.indexOf(group);
    }

    public void setSelected(Group group) {
        setSelected(indexOf(group));
    }

    public void setSelected(int position) {
        if (position == -1) return;
        for (int i = 0; i < mItems.size(); i++) updateSelection(i, i == position);
    }

    private void updateSelection(int position, boolean shouldSelect) {
        Group item = mItems.get(position);
        if (item.isSelected() == shouldSelect) return;
        item.setSelected(shouldSelect);
        notifyItemChanged(position, Boolean.TRUE);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGroupPortBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.stream().allMatch(Boolean.TRUE::equals)) holder.binding.getRoot().setSelected(mItems.get(position).isSelected());
        else onBindViewHolder(holder, position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupPortBinding binding;

        ViewHolder(@NonNull AdapterGroupPortBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
