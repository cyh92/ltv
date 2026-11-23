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
 * 竖屏直播频道网格（5 列，只显示频道名，当前频道琥珀实底 + 黑字）。
 * 与侧边列表的 ChannelAdapter 共用同一批 Channel 对象和点击回调，
 * 选中态由本适配器自己记的 selectedPosition 驱动——不能只靠 Channel.isSelected()：
 * 点台时侧边列表先改 flag，网格再调 setSelected 会因 flag 已是目标值而直接 return、
 * 不发 notifyItemChanged，网格就永远不重绘、选中态看不到。
 */
public class ChannelGridAdapter extends RecyclerView.Adapter<ChannelGridAdapter.ViewHolder> {

    private final ChannelAdapter.OnClickListener listener;
    private final List<Channel> mItems;
    private int selectedPosition = -1;

    public ChannelGridAdapter(ChannelAdapter.OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public void clear() {
        mItems.clear();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        if (position == selectedPosition) selectedPosition = -1;
        else if (position < selectedPosition) selectedPosition--;
        notifyItemRemoved(position);
    }

    public void setSelected(int position) {
        if (position == -1 || position >= mItems.size()) return;
        int old = selectedPosition;
        selectedPosition = position;
        // 不管 Channel 对象的 flag 是什么，都通知新旧两项重绘——flag 是和侧边列表共用的，
        // 侧边先改过就和目标值一样了，靠 flag 判断会漏通知。
        if (old != -1 && old != position && old < mItems.size()) {
            mItems.get(old).setSelected(false);
            notifyItemChanged(old, Boolean.TRUE);
        }
        mItems.get(position).setSelected(true);
        if (old != position) notifyItemChanged(position, Boolean.TRUE);
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
        holder.binding.getRoot().setSelected(position == selectedPosition);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.stream().allMatch(Boolean.TRUE::equals)) holder.binding.getRoot().setSelected(position == selectedPosition);
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
