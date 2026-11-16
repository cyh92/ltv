package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityConfigBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.FileActivity;
import com.fongmi.android.tv.ui.adapter.ConfigSubAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScriptManager;
import com.fongmi.android.tv.utils.SourceChecker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigFragment extends BaseFragment implements ConfigSubAdapter.OnClickListener {

    public static ConfigFragment newInstance(boolean embedded) {
        ConfigFragment fragment = new ConfigFragment();
        Bundle args = new Bundle();
        args.putBoolean("embedded", embedded);
        fragment.setArguments(args);
        return fragment;
    }

    private static final int PICK_SINGLE = 0;
    private static final int PICK_LOCAL = 1;

    private final ActivityResultLauncher<Intent> folderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = result.getData().getData().getPath();
        if (path == null) return;
        onFolderPicked(path);
    });

    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getFileUri(result, this::onFilePicked));

    private ActivityConfigBinding mBinding;
    private ConfigSubAdapter mAdapterLocal;
    private ConfigSubAdapter mAdapterFolder;
    private ConfigSubAdapter mAdapterSingle;
    private ConfigSubAdapter mAdapterOnline;
    private ConfigSubAdapter mAdapterLiveLocal;
    private ConfigSubAdapter mAdapterLiveOnline;
    private int tab = ConfigManager.TAB_VOD;
    private int pickMode = PICK_SINGLE;
    private boolean embedded;
    private ExecutorService checkExecutor;
    private volatile boolean checkCancelled;
    private boolean checking;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) embedded = getArguments().getBoolean("embedded", false);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = ActivityConfigBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (embedded) mBinding.toolbar.setVisibility(View.GONE);
        else mBinding.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
        mAdapterLocal = new ConfigSubAdapter(this);
        mAdapterFolder = new ConfigSubAdapter(this);
        mAdapterSingle = new ConfigSubAdapter(this);
        mAdapterOnline = new ConfigSubAdapter(this);
        mAdapterLiveLocal = new ConfigSubAdapter(this);
        mAdapterLiveOnline = new ConfigSubAdapter(this);
        bindSection(mBinding.recyclerLocal, mAdapterLocal);
        bindSection(mBinding.recyclerFolder, mAdapterFolder);
        bindSection(mBinding.recyclerSingle, mAdapterSingle);
        bindSection(mBinding.recyclerOnline, mAdapterOnline);
        bindSection(mBinding.recyclerLiveLocal, mAdapterLiveLocal);
        bindSection(mBinding.recyclerLiveOnline, mAdapterLiveOnline);
        refresh();
    }

    private void bindSection(RecyclerView recycler, ConfigSubAdapter adapter) {
        recycler.setAdapter(adapter);
        recycler.setItemAnimator(null);
    }

    @Override
    protected void initEvent() {
        mBinding.checkAll.setOnClickListener(v -> startCheckAll());
        mBinding.importLocal.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_VOD);
            pickMode = PICK_LOCAL;
            FileChooser.from(fileLauncher).show();
        });
        mBinding.importFolder.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_VOD);
            folderLauncher.launch(new Intent(requireActivity(), FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true));
        });
        mBinding.importSingle.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_VOD);
            pickMode = PICK_SINGLE;
            FileChooser.from(fileLauncher).show();
        });
        mBinding.importOnline.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_VOD);
            onAdd();
        });
        mBinding.importLiveLocal.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_LIVE);
            pickMode = PICK_LOCAL;
            FileChooser.from(fileLauncher).show();
        });
        mBinding.importLiveOnline.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_LIVE);
            onAdd();
        });
    }

    /** 只记下当前类型（onAdd / onFilePicked 靠它定 kind）；列表不再按类型切页，6 组一直都在。 */
    private void setTab(int tab) {
        this.tab = tab;
    }

    /** 导入的源按导入方式分组，分别列在各自的导入行下边；没源的组只留标题。 */
    private void refresh() {
        List<ConfigManager.Sub> vod = ConfigManager.getSubs(ConfigManager.TAB_VOD);
        List<ConfigManager.Sub> live = ConfigManager.getSubs(ConfigManager.TAB_LIVE);
        refreshSection(mBinding.recyclerLocal, mAdapterLocal, filter(vod, ConfigManager.KIND_JSON));
        refreshSection(mBinding.recyclerFolder, mAdapterFolder, filter(vod, ConfigManager.KIND_FOLDER));
        refreshSection(mBinding.recyclerSingle, mAdapterSingle, filter(vod, ConfigManager.KIND_FILE));
        refreshSection(mBinding.recyclerOnline, mAdapterOnline, filter(vod, ConfigManager.KIND_URL));
        refreshSection(mBinding.recyclerLiveLocal, mAdapterLiveLocal, filter(live, ConfigManager.KIND_LIVE_FILE));
        refreshSection(mBinding.recyclerLiveOnline, mAdapterLiveOnline, filter(live, ConfigManager.KIND_LIVE_URL));
    }

    private void refreshSection(RecyclerView recycler, ConfigSubAdapter adapter, List<ConfigManager.Sub> subs) {
        adapter.refresh(subs);
        recycler.setVisibility(subs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<ConfigManager.Sub> filter(List<ConfigManager.Sub> subs, String kind) {
        List<ConfigManager.Sub> result = new ArrayList<>();
        for (ConfigManager.Sub sub : subs) if (kind.equals(sub.kind)) result.add(sub);
        return result;
    }

    @Override
    public void onDestroyView() {
        checkCancelled = true;
        if (checkExecutor != null) checkExecutor.shutdownNow();
        super.onDestroyView();
    }

    /** 检测全部源：一个一个来（网慢，并行会拖死），每出一个结果就标在它下边。 */
    private void startCheckAll() {
        if (checking) return;
        checking = true;
        checkCancelled = false;
        List<ConfigManager.Sub> all = new ArrayList<>();
        all.addAll(ConfigManager.getSubs(ConfigManager.TAB_VOD));
        all.addAll(ConfigManager.getSubs(ConfigManager.TAB_LIVE));
        for (ConfigSubAdapter adapter : new ConfigSubAdapter[]{mAdapterLocal, mAdapterFolder, mAdapterSingle, mAdapterOnline, mAdapterLiveLocal, mAdapterLiveOnline}) adapter.clearStatus();
        mBinding.checkAll.setText(getString(R.string.config_checking, 0, all.size()));
        mBinding.checkAll.setEnabled(false);
        checkExecutor = Executors.newSingleThreadExecutor();
        checkExecutor.execute(() -> {
            int okCount = 0;
            int failCount = 0;
            for (int i = 0; i < all.size(); i++) {
                if (checkCancelled) break;
                ConfigManager.Sub sub = all.get(i);
                SourceChecker.Result result = SourceChecker.check(sub);
                if (result.ok) okCount++;
                else failCount++;
                int done = i + 1;
                App.post(() -> {
                    if (getView() == null) return;
                    setStatus(sub, result);
                    mBinding.checkAll.setText(getString(R.string.config_checking, done, all.size()));
                });
            }
            int ok = okCount;
            int fail = failCount;
            App.post(() -> {
                if (getView() == null) return;
                checking = false;
                mBinding.checkAll.setEnabled(true);
                mBinding.checkAll.setText(R.string.config_check_all);
                if (!checkCancelled) Notify.show(getString(R.string.config_check_done, ok, fail));
            });
        });
    }

    private void setStatus(ConfigManager.Sub sub, SourceChecker.Result result) {
        String text = statusText(result);
        if (sub.isJson()) mAdapterLocal.setStatus(sub.id, text, result.ok);
        else if (sub.isFolder()) mAdapterFolder.setStatus(sub.id, text, result.ok);
        else if (sub.isFile()) mAdapterSingle.setStatus(sub.id, text, result.ok);
        else if (sub.isUrl()) mAdapterOnline.setStatus(sub.id, text, result.ok);
        else if (sub.isLiveFile()) mAdapterLiveLocal.setStatus(sub.id, text, result.ok);
        else if (sub.isLiveUrl()) mAdapterLiveOnline.setStatus(sub.id, text, result.ok);
    }

    private String statusText(SourceChecker.Result result) {
        if (!result.ok) return getString(R.string.config_check_fail, getString(result.reason));
        switch (result.countKind) {
            case SourceChecker.COUNT_SITES:
                return getString(R.string.config_check_ok_sites, result.count);
            case SourceChecker.COUNT_CHANNELS:
                return getString(R.string.config_check_ok_channels, result.count);
            case SourceChecker.COUNT_SOURCES:
                return getString(R.string.config_check_ok_sources, result.count);
            default:
                return getString(R.string.config_check_ok);
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                refresh();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                refresh();
            }
        };
    }

    /** “添加接口”：名字 + 链接（在线接口或直播源地址）。 */
    private void onAdd() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        EditText nameInput = new EditText(requireContext());
        nameInput.setHint(R.string.config_name);
        EditText linkInput = new EditText(requireContext());
        linkInput.setHint(R.string.config_link);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(linkInput);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_add_interface)
                .setView(layout)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String link = linkInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Notify.show(R.string.config_need_name);
                        return;
                    }
                    if (link.isEmpty()) {
                        Notify.show(R.string.config_need_link);
                        return;
                    }
                    String kind = tab == ConfigManager.TAB_VOD ? ConfigManager.KIND_URL : ConfigManager.KIND_LIVE_URL;
                    ConfigManager.Sub sub = ConfigManager.add(tab, kind, name, link);
                    refresh();
                    if (tab == ConfigManager.TAB_VOD) ConfigManager.activateVod(sub, getCallback());
                    else ConfigManager.activateLive(sub, getCallback());
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    /** 扫描文件夹：加成一个本地接口，里面的 .py/.js 都会变成源。 */
    private void onFolderPicked(String path) {
        File dir = new File(path);
        if (!dir.isDirectory()) return;
        if (ScriptManager.get().getFolders().contains(path)) {
            Notify.show(R.string.config_folder_exists);
            return;
        }
        int count = countSources(dir);
        ConfigManager.add(ConfigManager.TAB_VOD, ConfigManager.KIND_FOLDER, dir.getName(), path);
        Notify.show(getString(R.string.config_merged, count));
        ConfigManager.reloadVod(getCallback());
        refresh();
    }

    private int countSources(File dir) {
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File file : children) {
            String lower = file.getName().toLowerCase();
            if (file.isFile() && (lower.endsWith(".py") || lower.endsWith(".js"))) count++;
        }
        return count;
    }

    /** 单个源文件 / 本地包 / 直播文件。 */
    private void onFilePicked(Uri uri) {
        if (uri == null || uri.getPath() == null) return;
        String path = uri.getPath();
        String lower = path.toLowerCase();
        if (pickMode == PICK_SINGLE) {
            if (!lower.endsWith(".py") && !lower.endsWith(".js")) {
                Notify.show(R.string.config_bad_single);
                return;
            }
            if (ScriptManager.get().getFiles().contains(path)) {
                Notify.show(R.string.config_file_exists);
                return;
            }
            File file = new File(path);
            String base = file.getName().substring(0, file.getName().lastIndexOf('.'));
            ConfigManager.add(ConfigManager.TAB_VOD, ConfigManager.KIND_FILE, base, path);
            ConfigManager.reloadVod(getCallback());
        } else if (tab == ConfigManager.TAB_VOD) {
            if (!lower.endsWith(".json")) {
                Notify.show(R.string.config_bad_json);
                return;
            }
            ConfigManager.add(ConfigManager.TAB_VOD, ConfigManager.KIND_JSON, new File(path).getName(), path);
        } else {
            if (!lower.endsWith(".txt") && !lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) {
                Notify.show(R.string.config_bad_live);
                return;
            }
            ConfigManager.add(ConfigManager.TAB_LIVE, ConfigManager.KIND_LIVE_FILE, new File(path).getName(), path);
        }
        refresh();
    }

    @Override
    public void onToggle(ConfigManager.Sub item, boolean enabled) {
        if (item.tab == ConfigManager.TAB_VOD) {
            if (enabled) ConfigManager.activateVod(item, getCallback());
            else ConfigManager.deactivateVod(item, getCallback());
        } else {
            if (enabled) ConfigManager.activateLive(item, getCallback());
            else ConfigManager.deactivateLive(item, getCallback());
        }
    }

    @Override
    public void onEdit(ConfigManager.Sub item) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        EditText nameInput = new EditText(requireContext());
        nameInput.setText(item.name);
        EditText linkInput = new EditText(requireContext());
        linkInput.setText(item.link);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(linkInput);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_edit_sub)
                .setView(layout)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String link = linkInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Notify.show(R.string.config_need_name);
                        return;
                    }
                    if (link.isEmpty()) {
                        Notify.show(R.string.config_need_link);
                        return;
                    }
                    boolean wasActive = item.id.equals(ConfigManager.getActiveId());
                    item.name = name;
                    item.link = link;
                    ConfigManager.update(item);
                    refresh();
                    if (wasActive && item.isInterface()) {
                        if (item.tab == ConfigManager.TAB_VOD) ConfigManager.activateVod(item, getCallback());
                        else ConfigManager.activateLive(item, getCallback());
                    }
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    @Override
    public void onDelete(ConfigManager.Sub item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.config_delete_confirm, item.name))
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    boolean wasActive = item.id.equals(ConfigManager.getActiveId());
                    ConfigManager.remove(item);
                    refresh();
                    if (item.tab == ConfigManager.TAB_VOD) ConfigManager.reloadVod(getCallback());
                    else if (wasActive) LiveConfig.load(Config.live(), getCallback());
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }
}
