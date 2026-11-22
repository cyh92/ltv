package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.WebActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private final List<Collect> mTabs = new ArrayList<>();
    private String mPinnedKey;

    public static CollectFragment newInstance(String keyword) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        setRecyclerView();
        setViewModel();
        setSites();
        setWidth();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnClickListener(this::onEdit);
    }

    private void onEdit(View view) {
        Bundle result = new Bundle();
        result.putBoolean("edit", true);
        getParentFragmentManager().setFragmentResult("result", result);
        getParentFragmentManager().popBackStack();
    }

    private void setRecyclerView() {
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(getCount());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getResult().observe(this, this::setSearch);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
    }

    private void setSites() {
        mSites = VodConfig.get().getSites().stream().filter(Site::isSearchable).toList();
    }

    private void setWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int maxWidth = ResUtil.getScreenWidth() / (getCount() + 1) - ResUtil.dp2px(40);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getName(), 14));
        int contentWidth = width + space;
        int minWidth = ResUtil.dp2px(120);
        int finalWidth = Math.clamp(contentWidth, minWidth, Math.max(minWidth, maxWidth));
        ViewGroup.LayoutParams params = mBinding.collect.getLayoutParams();
        params.width = finalWidth;
        mBinding.collect.setLayoutParams(params);
    }

    private void search() {
        if (mSites.isEmpty()) return;
        mPinnedKey = null;
        mTabs.clear();
        mTabs.add(Collect.all());
        mCollectAdapter.setItems(new ArrayList<>(mTabs), () -> mViewModel.searchContent(mSites, getKeyword(), false));
    }

    private int getCount() {
        int count = ResUtil.isLand(requireActivity()) ? 2 : 1;
        if (ResUtil.isPad()) count++;
        return count;
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        mTabs.get(0).getList().addAll(result.getList());
        mTabs.add(Collect.create(result.getList()));
        List<Collect> visible = getVisibleTabs();
        mCollectAdapter.setItems(new ArrayList<>(visible));
        if (mCollectAdapter.getPosition() == 0) mSearchAdapter.setItems(new ArrayList<>(visible.get(0).getList()));
    }

    /**
     * 搜索关键词归一化：全角 ASCII 转半角，只保留字母与数字并转小写。
     * “生化危机3：灭绝”归一化后为“生化危机3灭绝”，可与片名直接比较。
     */
    private static String normalizeKeyword(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '！' && c <= '～') c = (char) (c - '！' + '!');
            if (c == '　') c = ' ';
            if (Character.isLetterOrDigit(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** 片名是否与关键词相关：归一化后互相包含即算相关。 */
    private static boolean matchesKeyword(String name, String keyword) {
        String n = normalizeKeyword(name);
        String k = normalizeKeyword(keyword);
        if (n.isEmpty() || k.isEmpty()) return false;
        return n.contains(k) || k.contains(n);
    }

    /** 该源的搜索结果里是否至少有一条与关键词相关。 */
    private static boolean hasMatch(Collect tab, String keyword) {
        for (Vod vod : tab.getList()) if (matchesKeyword(vod.getName(), keyword)) return true;
        return false;
    }

    /**
     * 当前应展示的源 tab 列表。
     * 任一源有相关结果时，只展示有相关结果的源（外加用户亲手点开的源），
     * 展示用的“全部”只汇总相关源的条目；没有任何源有相关结果时（例如搜演员名，
     * 片名里本来就没有演员名），原样展示全部结果，不做过滤。
     * mTabs 本身始终保存全部源的完整结果，不受过滤影响。
     */
    private List<Collect> getVisibleTabs() {
        String keyword = getKeyword();
        boolean anyMatch = false;
        for (int i = 1; i < mTabs.size(); i++) if (hasMatch(mTabs.get(i), keyword)) {
            anyMatch = true;
            break;
        }
        if (!anyMatch) return new ArrayList<>(mTabs);
        List<Collect> visible = new ArrayList<>();
        List<Vod> all = new ArrayList<>();
        for (int i = 1; i < mTabs.size(); i++) {
            Collect tab = mTabs.get(i);
            boolean match = hasMatch(tab, keyword);
            if (match || tab.getSite().getKey().equals(mPinnedKey)) {
                visible.add(tab);
                if (match) all.addAll(tab.getList());
            }
        }
        visible.add(0, new Collect(mTabs.get(0).getSite(), all));
        return visible;
    }

    /** 展示用的“全部”列表：按当前过滤规则从 mTabs 实时汇总，点“全部”时总是最新的。 */
    private List<Vod> getVisibleAllList() {
        return getVisibleTabs().get(0).getList();
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getVod().getSite());
        if (!same) return;
        Collect activated = mCollectAdapter.getActivated();
        activated.getList().addAll(result.getList());
        if (!"all".equals(activated.getSite().getKey())) mTabs.get(0).getList().addAll(result.getList());
        mSearchAdapter.setItems(new ArrayList<>(activated.getList()));
    }

    @Override
    public void onItemClick(int position, Collect item) {
        boolean all = "all".equals(item.getSite().getKey());
        mPinnedKey = all ? null : item.getSite().getKey();
        mSearchAdapter.setItems(all ? new ArrayList<>(getVisibleAllList()) : item.getList(), () -> mBinding.recycler.scrollToPosition(0));
        mCollectAdapter.setSelected(position);
        mScroller.setPage(item.getPage());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(item.getSiteKey(), item.getAction());
        else if (item.isFolder()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item);
    }

    @Override
    public boolean onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if ("all".equals(activated.getSite().getKey())) return false;
        mViewModel.searchContent(activated.getSite(), getKeyword(), false, page);
        activated.setPage(Integer.parseInt(page));
        return true;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) requireActivity().getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewModel.stopSearch();
        requireActivity().removeMenuProvider(this);
    }
}
