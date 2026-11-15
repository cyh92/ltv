package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityConfigBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.ConfigSubAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScriptManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

public class ConfigActivity extends BaseActivity implements ConfigSubAdapter.OnClickListener {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ConfigActivity.class));
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
    private ConfigSubAdapter mAdapter;
    private int tab = ConfigManager.TAB_VOD;
    private int pickMode = PICK_SINGLE;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.toolbar.setNavigationOnClickListener(v -> finish());
        mAdapter = new ConfigSubAdapter(this);
        mBinding.recycler.setAdapter(mAdapter);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        setTab(ConfigManager.TAB_VOD);
    }

    @Override
    protected void initEvent() {
        mBinding.tabVod.setOnClickListener(v -> setTab(ConfigManager.TAB_VOD));
        mBinding.tabLive.setOnClickListener(v -> setTab(ConfigManager.TAB_LIVE));
        mBinding.add.setOnClickListener(v -> onAdd());
        mBinding.scan.setOnClickListener(v -> folderLauncher.launch(new Intent(this, FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true)));
        mBinding.single.setOnClickListener(v -> {
            pickMode = PICK_SINGLE;
            FileChooser.from(fileLauncher).show();
        });
        mBinding.local.setOnClickListener(v -> {
            pickMode = PICK_LOCAL;
            FileChooser.from(fileLauncher).show();
        });
    }

    private void setTab(int tab) {
        this.tab = tab;
        mBinding.tabVod.setSelected(tab == ConfigManager.TAB_VOD);
        mBinding.tabLive.setSelected(tab == ConfigManager.TAB_LIVE);
        mBinding.scan.setVisibility(tab == ConfigManager.TAB_VOD ? View.VISIBLE : View.GONE);
        mBinding.single.setVisibility(tab == ConfigManager.TAB_VOD ? View.VISIBLE : View.GONE);
        mBinding.local.setText(tab == ConfigManager.TAB_VOD ? R.string.config_local_pack : R.string.config_local_file);
        refresh();
    }

    private void refresh() {
        List<ConfigManager.Sub> subs = ConfigManager.getSubs(tab);
        mAdapter.refresh(subs);
        mBinding.empty.setVisibility(subs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(ConfigActivity.this);
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
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.config_name);
        EditText linkInput = new EditText(this);
        linkInput.setHint(R.string.config_link);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(linkInput);
        new MaterialAlertDialogBuilder(this)
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
                    ConfigManager.activateVod(sub, getCallback());
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
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        EditText nameInput = new EditText(this);
        nameInput.setText(item.name);
        EditText linkInput = new EditText(this);
        linkInput.setText(item.link);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(linkInput);
        new MaterialAlertDialogBuilder(this)
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
        new MaterialAlertDialogBuilder(this)
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
