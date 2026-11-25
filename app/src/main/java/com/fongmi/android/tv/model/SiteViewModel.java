package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.utils.Notify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class SiteViewModel extends ViewModel {

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;

    private final ViewModelTaskRunner<TaskType> tasks;
    private final ViewModelSearchRunner searches;

    /** 首页内存缓存：key = 接口 URL + 首页源 key。只放内存，退出即丢；只用来先显示，后台一定再拉一次（见 homeContent）。 */
    private final Map<String, Result> homeCache = new HashMap<>();

    public SiteViewModel() {
        result = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        tasks = new ViewModelTaskRunner<>(TaskType.class);
        searches = new ViewModelSearchRunner();
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        Site home = VodConfig.get().getHome();
        String key = homeCacheKey(home);
        Result cached = homeCache.get(key);
        // 先显示缓存（切回已打开过的接口秒出内容），同时一定再拉一次新的，回来后更新缓存并刷新界面。
        if (cached != null) result.postValue(cached);
        execute(TaskType.RESULT, result, () -> {
            Result fresh = SiteApi.homeContent(home);
            if (fresh != null) homeCache.put(key, fresh);
            return fresh;
        });
    }

    private static String homeCacheKey(Site home) {
        return VodConfig.getUrl() + "|" + (home == null ? "" : home.getKey());
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id));
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(TaskType.RESULT, result, new SearchTask(site, keyword, quick, page));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        searches.start(sites, site -> new SearchTask(site, keyword, quick, "1"), search::postValue);
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        tasks.execute(type, Constant.TIMEOUT_VOD, callable, liveData::postValue, error -> handleError(type, liveData, error));
    }

    private void handleError(TaskType type, MutableLiveData<Result> liveData, Throwable error) {
        String message = Notify.getError(error);
        liveData.postValue(message != null ? Result.error(message) : Result.empty());
        if (type == TaskType.RESULT && message != null) App.post(() -> Notify.show(message));
        error.printStackTrace();
    }

    public void stopSearch() {
        searches.stop();
    }

    @Override
    protected void onCleared() {
        stopSearch();
        tasks.cancelAll();
    }

    private record SearchTask(Site site, String keyword, boolean quick, String page) implements Callable<Result> {

        @Override
        public Result call() throws Exception {
            if (quick && !site.isQuickSearch()) return Result.empty();
            return SiteApi.searchContent(site, keyword, quick, page);
        }
    }

    private enum TaskType {RESULT, ACTION}
}
