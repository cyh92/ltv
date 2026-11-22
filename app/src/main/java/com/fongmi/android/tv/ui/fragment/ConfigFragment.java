package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.fongmi.android.tv.utils.CheckStore;
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
        boolean edit = pickingEditPath;
        pickingEditPath = false;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = result.getData().getData().getPath();
        if (path == null) return;
        if (edit && editLinkInput != null) {
            editLinkInput.setText(path);
            return;
        }
        onFolderPicked(path);
    });

    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getFileUri(result, this::onFilePicked));

    private final ActivityResultLauncher<Intent> editFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getFileUri(result, uri -> {
        if (uri != null && uri.getPath() != null && editLinkInput != null) editLinkInput.setText(uri.getPath());
    }));

    private ActivityConfigBinding mBinding;
    private ConfigSubAdapter mAdapterLocal;
    private ConfigSubAdapter mAdapterFolder;
    private ConfigSubAdapter mAdapterSingle;
    private ConfigSubAdapter mAdapterOnline;
    private ConfigSubAdapter mAdapterLiveLocal;
    private ConfigSubAdapter mAdapterLiveOnline;
    private int tab = ConfigManager.TAB_VOD;
    private int pickMode = PICK_SINGLE;
    private boolean pickingEditPath;
    private EditText editLinkInput;
    private boolean embedded;
    private ExecutorService checkExecutor;
    private volatile boolean checkCancelled;
    private boolean checking;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressItem;
    private TextView progressCount;

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
        dismissProgressDialog();
        super.onDestroyView();
    }

    /** 检测全部源：一个一个来（网慢，并行会拖死），每出一个结果就标在它下边；进度框里能看到正在测哪个、测到第几个。 */
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
        showProgressDialog();
        checkExecutor = Executors.newSingleThreadExecutor();
        checkExecutor.execute(() -> {
            int greenCount = 0;
            int yellowCount = 0;
            int redCount = 0;
            for (int i = 0; i < all.size(); i++) {
                if (checkCancelled) break;
                ConfigManager.Sub sub = all.get(i);
                int subIndex = i + 1;
                App.post(() -> {
                    if (getView() == null || progressDialog == null) return;
                    progressBar.setIndeterminate(true);
                    progressItem.setText(sub.name);
                    progressCount.setText(getString(R.string.config_check_progress_sub, subIndex, all.size()));
                });
                SourceChecker.Result result = SourceChecker.check(sub, new SourceChecker.ProgressListener() {
                    @Override
                    public void onItem(String itemName, int done, int total) {
                        App.post(() -> {
                            if (getView() == null || progressDialog == null) return;
                            progressBar.setIndeterminate(false);
                            progressBar.setMax(total);
                            progressBar.setProgress(done);
                            progressItem.setText(sub.name + " · " + itemName);
                            progressCount.setText(getString(R.string.config_check_progress, done, total, subIndex, all.size()));
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return checkCancelled;
                    }
                });
                if (checkCancelled) break;
                if (result.grade == SourceChecker.GRADE_GREEN) greenCount++;
                else if (result.grade == SourceChecker.GRADE_YELLOW) yellowCount++;
                else redCount++;
                CheckStore.putSubGrade(sub.id, result.grade);
                CheckStore.putSiteGrades(result.siteGrades);
                int done = i + 1;
                App.post(() -> {
                    if (getView() == null) return;
                    setStatus(sub, result);
                    mBinding.checkAll.setText(getString(R.string.config_checking, done, all.size()));
                });
            }
            int green = greenCount;
            int yellow = yellowCount;
            int red = redCount;
            App.post(() -> {
                if (getView() == null) return;
                dismissProgressDialog();
                checking = false;
                mBinding.checkAll.setEnabled(true);
                mBinding.checkAll.setText(R.string.config_check_all);
                if (!checkCancelled) Notify.show(getString(R.string.config_check_done, green, yellow, red));
            });
        });
    }

    /** 检测进度框：当前源名 + 横向进度条 + 计数；取消按钮停掉检测。 */
    private void showProgressDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, ResUtil.dp2px(8));
        progressItem = new TextView(requireContext());
        progressItem.setTextSize(16);
        progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = ResUtil.dp2px(12);
        progressBar.setLayoutParams(barParams);
        progressBar.setIndeterminate(true);
        progressCount = new TextView(requireContext());
        progressCount.setTextSize(14);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = ResUtil.dp2px(8);
        progressCount.setLayoutParams(countParams);
        layout.addView(progressItem);
        layout.addView(progressBar);
        layout.addView(progressCount);
        progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_checking_title)
                .setView(layout)
                .setNegativeButton(R.string.config_check_cancel, (dialog, which) -> checkCancelled = true)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
    }

    private void setStatus(ConfigManager.Sub sub, SourceChecker.Result result) {
        String text = statusText(result);
        if (sub.isJson()) mAdapterLocal.setStatus(sub.id, text, result.grade);
        else if (sub.isFolder()) mAdapterFolder.setStatus(sub.id, text, result.grade);
        else if (sub.isFile()) mAdapterSingle.setStatus(sub.id, text, result.grade);
        else if (sub.isUrl()) mAdapterOnline.setStatus(sub.id, text, result.grade);
        else if (sub.isLiveFile()) mAdapterLiveLocal.setStatus(sub.id, text, result.grade);
        else if (sub.isLiveUrl()) mAdapterLiveOnline.setStatus(sub.id, text, result.grade);
    }

    private String statusText(SourceChecker.Result result) {
        if (result.grade == SourceChecker.GRADE_RED) return getString(R.string.config_check_fail, getString(result.reason));
        if (result.grade == SourceChecker.GRADE_YELLOW) {
            switch (result.yellowKind) {
                case SourceChecker.YELLOW_SLOW:
                    switch (result.countKind) {
                        case SourceChecker.COUNT_SITES:
                            return getString(R.string.config_check_slow_sites, result.count);
                        case SourceChecker.COUNT_CHANNELS:
                            return getString(R.string.config_check_slow_channels, result.count);
                        case SourceChecker.COUNT_SOURCES:
                            return getString(R.string.config_check_slow_sources, result.count);
                        default:
                            return getString(R.string.config_check_slow);
                    }
                case SourceChecker.YELLOW_PARTIAL:
                    if (result.countKind == SourceChecker.COUNT_SITES) return getString(R.string.config_check_partial_sites, result.good, result.total);
                    return getString(R.string.config_check_partial_sources, result.good, result.total);
                case SourceChecker.YELLOW_UNVERIFIED:
                    return getString(R.string.config_check_unverified);
                default:
                    return getString(R.string.config_check_slow);
            }
        }
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
        return countSources(dir, 0);
    }

    private int countSources(File dir, int depth) {
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File file : children) {
            String lower = file.getName().toLowerCase();
            if (file.isFile() && (lower.endsWith(".py") || lower.endsWith(".js"))) count++;
            else if (file.isDirectory() && depth < 2 && !file.getName().startsWith(".")) count += countSources(file, depth + 1);
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
            if (!enabled && item.enabled && !item.id.equals(ConfigManager.getActiveId())) {
                // 点的是已开但不是当前用的：切成当前用的，不关掉。
                ConfigManager.activateVod(item, getCallback());
                refresh();
                return;
            }
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
        if (!item.isUrl() && !item.isLiveUrl()) {
            Button pickButton = new Button(requireContext());
            pickButton.setText(R.string.setting_choose);
            pickButton.setOnClickListener(v -> {
                editLinkInput = linkInput;
                if (item.isFolder()) {
                    pickingEditPath = true;
                    folderLauncher.launch(new Intent(requireActivity(), FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true));
                } else {
                    FileChooser.from(editFileLauncher).show();
                }
            });
            layout.addView(pickButton);
        }
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
                    String oldLink = item.link;
                    item.name = name;
                    item.link = link;
                    ConfigManager.updateLink(item, oldLink);
                    refresh();
                    if (wasActive) {
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
