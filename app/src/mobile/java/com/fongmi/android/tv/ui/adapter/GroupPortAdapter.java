package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.databinding.AdapterGroupPortBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖屏直播分类 tab（横向一排）。
 * 与侧边列表的 GroupAdapter 共用同一批 Group 对象和点击回调。
 * <p>
 * 0.1.101：选中态不再靠 Group.isSelected() + selector 画出来——0.1.100 及之前就是这么做的，
 * 但在真机上分组 tab 的琥珀色永远卡在"央视频道"不跟随切组（run100/run101 已预警，
 * 0.1.100 录屏证实）。根因和 0.1.99 的旧网格一模一样：点 tab 时侧边列表先改了共用
 * Group 对象的 selected 标志，竖屏 tab 再调 setSelected 会因标志已是目标值而直接 return、
 * 不发 notifyItemChanged，tab 就永远不重绘、选中态看不到。
 * 所以照搬 0.1.100 网格的修法：适配器自己记 selectedPosition，完整绑定里按
 * position == selectedPosition 直接换底色、换字色（非 selector 的 shape + 显式颜色值），
 * 不经过 selected 状态；通知也不带 payload，永远走完整绑定。
 * setSelected 还照常调（无害，保持 view 自身状态同步），但外观不再依赖它。
 */
public class GroupPortAdapter extends RecyclerView.Adapter<GroupPortAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Group> mItems;
    private int selectedPosition = -1;
    private int textColorSelected;
    private int textColorNormal;
    private boolean colorsReady;

    public GroupPortAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Group item);
    }

    public void clear() {
        mItems.clear();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        selectedPosition = -1;
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
        if (selectedPosition != -1) return selectedPosition;
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
        if (position == -1 || position >= mItems.size()) return;
        int old = selectedPosition;
        selectedPosition = position;
        // 不管 Group 对象的 flag 是什么，都通知新旧两项重绘——flag 是和侧边列表共用的，
        // 侧边先改过就和目标值一样了，靠 flag 判断会漏通知（0.1.100 之前就是这么漏的）。
        // 不带 payload 通知，永远走完整绑定（0.1.99 的 payload 局部绑定在真机上没起作用）。
        if (old != -1 && old != position && old < mItems.size()) {
            mItems.get(old).setSelected(false);
            notifyItemChanged(old);
        }
        mItems.get(position).setSelected(true);
        if (old != position) notifyItemChanged(position);
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
        boolean selected = position == selectedPosition;
        ensureColors(holder.itemView.getContext());
        holder.binding.name.setText(item.getName());
        // 外观直接按 selectedPosition 定，不经过 selector / selected 状态——0.1.100 之前那条链路在真机上没起作用。
        holder.binding.getRoot().setBackgroundResource(selected ? R.drawable.shape_live_grid_selected : R.drawable.shape_live_grid_normal);
        holder.binding.name.setTextColor(selected ? textColorSelected : textColorNormal);
        holder.binding.getRoot().setSelected(selected);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        // 0.1.101：不走 payload 局部绑定，永远完整绑定（0.1.99 的 payload 分支在真机上没让 tab 变色）。
        onBindViewHolder(holder, position);
    }

    private void ensureColors(Context context) {
        if (colorsReady) return;
        textColorSelected = ContextCompat.getColor(context, R.color.black);
        TypedValue value = new TypedValue();
        // 注意：colorOnSurface 不在 app 自己的 R.attr 里（attrs.xml 没定义），必须用 Material 库的 R。
        // 代码库里已有先例：DeviceAdapter 用 com.google.android.material.R.attr.colorOnSurfaceVariant；
        // ChannelGridAdapter 0.1.100 也这么取（直接用 R.attr.colorOnSurface 会编译失败，构建 #128 的教训）。
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, value, true);
        textColorNormal = value.data;
        colorsReady = true;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupPortBinding binding;

        ViewHolder(@NonNull AdapterGroupPortBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
