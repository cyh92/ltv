package com.fongmi.android.tv.ui.fragment;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.FilterListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.WebActivity;
import com.fongmi.android.tv.ui.adapter.SectionAdapter;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.FilterDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class VodFragment extends BaseFragment implements ConfigListener, SiteListener, FilterListener, TypeAdapter.OnClickListener, SectionAdapter.OnClickListener {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;
    private SectionAdapter mSectionAdapter;
    private Result mResult;
    private FolderFragment mFocused;
    private Class mFocusedType;
    private final Set<String> mObservedSections = new HashSet<>();
    private boolean mAutoFirst = true;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private boolean isFocused() {
        return mFocused != null;
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getHome().getKey());
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        setRecyclerView();
        setViewModel();
        showProgress();
        setTitle();
        setIface();
    }

    @Override
    protected void initEvent() {
        mBinding.ifacePill.setOnClickListener(this::onIface);
        mBinding.sourcePill.setOnClickListener(this::onSite);
        mBinding.typeMenu.setOnClickListener(this::onTypeMenu);
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.filter.setOnClickListener(this::onFilter);
        mBinding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
    }

    private void setRecyclerView() {
        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.sections.setHasFixedSize(false);
        mBinding.sections.setItemAnimator(null);
        mBinding.sections.setAdapter(mSectionAdapter = new SectionAdapter(this));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
    }

    private void setAdapter(Result result) {
        mAdapter.addAll(mResult = result);
        List<Class> types = new ArrayList<>();
        for (int i = 0; i < mAdapter.getItemCount(); i++) types.add(mAdapter.get(i));
        mSectionAdapter.setSections(getHome().getKey(), types);
        for (Class type : types) {
            if (type.isHome()) mSectionAdapter.setSectionItems(type, result.getList());
            else loadSection(type);
        }
        setFabVisible();
        hideProgress();
        showContent();
        if (mAutoFirst && mAdapter.getItemCount() > 0 && !isFocused()) {
            mAutoFirst = false;
            onItemClick(0, mAdapter.get(0));
        }
    }

    private void loadSection(Class type) {
        SiteViewModel vm = new ViewModelProvider(this).get("section_" + type.getTypeId(), SiteViewModel.class);
        if (mObservedSections.add(type.getTypeId())) {
            vm.getResult().observe(getViewLifecycleOwner(), result -> onSectionResult(type, result));
        }
        vm.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, getExtend(type));
    }

    private void onSectionResult(Class type, Result result) {
        if (result.getList().isEmpty()) mSectionAdapter.removeSection(type);
        else mSectionAdapter.setSectionItems(type, result.getList());
    }

    private HashMap<String, String> getExtend(Class type) {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : type.getFilters()) if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    private void setFabVisible() {
        mBinding.top.setVisibility(View.INVISIBLE);
        if (isFocused() && mFocusedType != null && !mFocusedType.getFilters().isEmpty()) mBinding.filter.show();
        else mBinding.filter.setVisibility(View.GONE);
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void setIface() {
        String name = ConfigManager.getActiveName();
        mBinding.iface.setText(name == null ? getString(R.string.config_all_interfaces) : name);
    }

    private void onIface(View view) {
        List<ConfigManager.Sub> subs = ConfigManager.getSubs(ConfigManager.TAB_VOD);
        List<ConfigManager.Sub> enabled = new ArrayList<>();
        for (ConfigManager.Sub sub : subs) if (sub.enabled) enabled.add(sub);
        // 0.1.96：在线接口排在前面、本地包排在后面。之前按添加顺序混排，在线
        // （demo.json / 影视py）和本地包（RRZY）穿插，用着本地包时想切回在线要满列表
        // 翻找、滑到最上面才看到。分组后在线接口固定在顶部，一眼找到。
        // 用稳定排序原地排（各组内保持添加顺序），不重赋值 enabled（lambda 要捕获它）。
        enabled.sort((a, b) -> Boolean.compare(!a.isInterface(), !b.isInterface()));
        String[] items = new String[enabled.size() + 1];
        items[0] = getString(R.string.config_all_interfaces);
        for (int i = 0; i < enabled.size(); i++) items[i + 1] = enabled.get(i).name;
        int checked = 0;
        String activeId = ConfigManager.getActiveId();
        for (int i = 0; i < enabled.size(); i++) if (enabled.get(i).id.equals(activeId)) checked = i + 1;
        int checkedIndex = checked;
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.config_interface).setSingleChoiceItems(items, checked, (d, which) -> {
            d.dismiss();
            if (which == 0) {
                ConfigManager.setActiveId("");
                ConfigManager.reloadVod(getCallback());
            } else {
                ConfigManager.activateVod(enabled.get(which - 1), getCallback());
            }
            setIface();
        }).show();
        // 0.1.96：打开对话框时把列表定位到当前选中的接口。单选列表默认不一定滚到选中项，
        // 列表长时用户打开只能看到顶部，当前项（比如 RRZY）在下面看不到，切换时容易迷路。
        dialog.setOnShowListener(d -> {
            android.widget.ListView listView = dialog.getListView();
            if (listView != null) listView.setSelection(checkedIndex);
        });
    }

    private void onTypeMenu(View view) {
        int count = mAdapter.getItemCount();
        if (count == 0) return;
        String[] items = new String[count];
        for (int i = 0; i < count; i++) items[i] = mAdapter.get(i).getTypeName();
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.home_all_types).setItems(items, (dialog, which) -> onItemClick(which, mAdapter.get(which))).show();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                mAutoFirst = true;
                showProgress();
                hideContent();
                setTitle();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void enterFocused(Class type) {
        exitFocused();
        mFocusedType = type;
        mFocused = FolderFragment.newInstance(getHome().getKey(), type, 4);
        getChildFragmentManager().beginTransaction().replace(R.id.focusedContainer, mFocused).commit();
        mBinding.focusedContainer.setVisibility(View.VISIBLE);
        mBinding.sections.setVisibility(View.GONE);
        setFabVisible();
    }

    private void exitFocused() {
        if (mFocused == null) return;
        getChildFragmentManager().beginTransaction().remove(mFocused).commit();
        mFocused = null;
        mFocusedType = null;
        mBinding.focusedContainer.setVisibility(View.GONE);
        mBinding.sections.setVisibility(View.VISIBLE);
        setFabVisible();
    }

    private void onTop(View view) {
        if (isFocused()) mFocused.scrollToTop();
        else mBinding.sections.smoothScrollToPosition(0);
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.show();
    }

    private void onSite(View view) {
        SiteDialog.create().change().show(this);
    }

    private void onFilter(View view) {
        if (isFocused() && mFocused.getFilters() != null && !mFocused.getFilters().isEmpty()) FilterDialog.create().filter(mFocused.getFilters()).show(this);
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.search) SearchActivity.start(requireActivity());
        else if (item.getItemId() == R.id.keep) KeepActivity.start(requireActivity());
        else if (item.getItemId() == R.id.history) HistoryActivity.start(requireActivity());
        return true;
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void hideContent() {
        mBinding.type.setVisibility(View.INVISIBLE);
        mBinding.content.setVisibility(View.INVISIBLE);
    }

    private void showContent() {
        mBinding.type.setVisibility(View.VISIBLE);
        mBinding.content.setVisibility(View.VISIBLE);
    }

    private void homeContent() {
        exitFocused();
        showProgress();
        setFabVisible();
        mAdapter.clear();
        mSectionAdapter.clear();
        mAutoFirst = true;
        mViewModel.homeContent();
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
                setIface();
            case SIZE:
                homeContent();
                break;
            case CATEGORY:
                if (isFocused()) mFocused.onRefresh();
                else homeContent();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.type()) {
            case EMPTY:
                hideProgress();
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                mAutoFirst = true;
                showProgress();
                hideContent();
                setTitle();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(int position, Class item) {
        onSectionAllClick(item);
        mAdapter.setSelected(position);
    }

    @Override
    public void setFilter(String key, Value value) {
        if (isFocused()) mFocused.setFilter(key, value);
    }

    @Override
    public void onSectionAllClick(Class type) {
        enterFocused(type);
    }

    @Override
    public void onSectionItemClick(Class type, Vod item) {
        if (item.isAction()) {
            mViewModel.action(getHome().getKey(), item.getAction());
        } else if (item.isFolder()) {
            enterFocused(type);
            mBinding.focusedContainer.post(() -> {
                if (mFocused != null) mFocused.openFolder(item.getId(), getExtend(type));
            });
        } else {
            if (getSite().isIndex()) SearchActivity.start(requireActivity(), item.getName());
            else VideoActivity.start(requireActivity(), getHome().getKey(), item, type.isFolder() ? item.getName() : null);
        }
    }

    @Override
    public boolean onSectionItemLongClick(Class type, Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        SearchActivity.start(requireActivity(), item.getName());
        return true;
    }

    @Override
    public boolean canBack() {
        if (isFocused()) {
            if (mFocused.canBack()) {
                mFocused.goBack();
                return false;
            }
            exitFocused();
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
