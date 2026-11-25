package com.fongmi.android.tv.ui.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.SiteOrder;

import java.util.ArrayList;
import java.util.List;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> mItems;
    private int type;

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
        this.addAll();
    }

    public interface OnClickListener {

        void onItemClick(Site item);
    }

    public void setType(int type) {
        this.type = type;
        notifyDataSetChanged();
    }

    public void selectAll() {
        setEnable(type != 3);
    }

    public void cancelAll() {
        setEnable(type == 3);
    }

    private void addAll() {
        // 0.1.105: 接口隔离——当前是 JSON/在线接口时，只显示接口自己的源；
        // 手机本地 py/js 脚本源（key 以 script_ 开头）是跨接口追加进来的，
        // 不在这里显示（与移动端同款过滤；见移动端 SiteAdapter）。
        // 安全网：过滤后为空时回退到不过滤，绝不让列表因此变空白。
        ConfigManager.Sub active = ConfigManager.getActiveSub();
        boolean isolate = active != null && active.isInterface();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isHide() && (!isolate || !SiteOrder.isLocalScript(site))) mItems.add(site);
        }
        if (mItems.isEmpty()) for (Site site : VodConfig.get().getSites()) if (!site.isHide()) mItems.add(site);
        // 0.1.113: 当前首页源置顶（与移动端同款），打开列表第一个就是正在用的首页源。
        Site home = VodConfig.get().getHome();
        if (home != null && home.getKey() != null) {
            for (int i = 0; i < mItems.size(); i++) {
                if (home.getKey().equals(mItems.get(i).getKey())) {
                    if (i > 0) mItems.add(0, mItems.remove(i));
                    break;
                }
            }
        }
    }

    public List<Site> getItems() {
        return mItems;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.check.setChecked(getChecked(item));
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.check.setVisibility(type == 0 ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setOnLongClickListener(v -> setLongListener(item));
        holder.binding.getRoot().setOnClickListener(v -> setListener(item, position));
        holder.binding.text.setGravity(Setting.getSiteMode() == 0 ? Gravity.CENTER : Gravity.START);
    }

    private boolean getChecked(Site item) {
        if (type == 1) return item.isSearchable();
        if (type == 2) return item.isChangeable();
        return false;
    }

    private void setListener(Site item, int position) {
        if (type == 0) listener.onItemClick(item);
        if (type == 1) item.setSearchable(!item.isSearchable()).saveSettings();
        if (type == 2) item.setChangeable(!item.isChangeable()).saveSettings();
        if (type != 0) notifyItemChanged(position);
    }

    private boolean setLongListener(Site item) {
        if (type == 1) setEnable(!item.isSearchable());
        if (type == 2) setEnable(!item.isChangeable());
        return true;
    }

    private void setEnable(boolean enable) {
        if (type == 1) for (Site site : mItems) site.setSearchable(enable);
        if (type == 2) for (Site site : mItems) site.setChangeable(enable);
        if (type == 1 || type == 2) Site.saveSettings(mItems);
        notifyItemRangeChanged(0, getItemCount());
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
