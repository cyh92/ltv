package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.LruCache;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentLocalBinding;
import com.fongmi.android.tv.databinding.ItemLocalBinding;
import com.fongmi.android.tv.databinding.ItemLocalFolderBinding;
import com.fongmi.android.tv.databinding.ItemLocalFolderbarBinding;
import com.fongmi.android.tv.databinding.ItemLocalHeaderBinding;
import com.fongmi.android.tv.databinding.ItemLocalHistoryBinding;
import com.fongmi.android.tv.ui.activity.FileActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalFragment extends BaseFragment {

    private static final String KEY_SORT = "local_sort";
    private static final String KEY_HISTORY = "local_history";
    private static final String KEY_PRIVATE_PASSWORD = "local_private_password";
    private static final String KEY_PRIVATE_FOLDER = "local_private_folder";

    private static final int SORT_TIME_NEW = 0;
    private static final int SORT_TITLE_AZ = 1;
    private static final int SORT_TITLE_ZA = 2;
    private static final int SORT_TIME_OLD = 3;

    private final ActivityResultLauncher<Intent> folderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = result.getData().getData().getPath();
        if (path == null || path.isEmpty()) return;
        currentFolder = path;
        privateOpen = false;
        refreshAll();
        scrollToAllHeader();
    });

    private final ActivityResultLauncher<Intent> privateFolderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = result.getData().getData().getPath();
        if (path == null || path.isEmpty()) return;
        Prefers.put(KEY_PRIVATE_FOLDER, path);
        openPrivate();
    });

    private FragmentLocalBinding mBinding;
    private final List<LocalVideo> allVideos = new ArrayList<>();
    private final List<LocalVideo> historyVideos = new ArrayList<>();
    private final List<Folder> folders = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private List<LocalVideo> shownGrid = new ArrayList<>();
    private final LruCache<Long, Bitmap> thumbs = new LruCache<Long, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };
    private ExecutorService executor;
    private LocalAdapter adapter;
    private HistoryAdapter historyAdapter;
    private int sortMode = SORT_TIME_NEW;
    private String currentFolder;
    private boolean privateOpen;
    private String keyword = "";

    public static LocalFragment newInstance() {
        return new LocalFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentLocalBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        executor = Executors.newSingleThreadExecutor();
        sortMode = loadSortMode();
        historyAdapter = new HistoryAdapter();
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return rows.get(position).type == Row.TYPE_VIDEO ? 1 : 3;
            }
        });
        mBinding.recycler.setLayoutManager(layoutManager);
        mBinding.recycler.setAdapter(adapter = new LocalAdapter());
        mBinding.recycler.setHasFixedSize(true);
        mBinding.empty.setOnClickListener(v -> requestPermission());
        requestPermission();
    }

    @Override
    protected void initEvent() {
        mBinding.btnFolder.setOnClickListener(v -> folderLauncher.launch(new Intent(requireActivity(), FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true)));
        mBinding.btnSearch.setOnClickListener(v -> toggleSearch());
        mBinding.btnSort.setOnClickListener(v -> showSortMenu());
        mBinding.btnPrivate.setOnClickListener(v -> onPrivateClick());
        mBinding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                keyword = s.toString().trim();
                refreshAll();
            }
        });
    }

    @Override
    public void onPause() {
        lockPrivate();
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) lockPrivate();
    }

    @Override
    public void onDestroyView() {
        if (executor != null) executor.shutdownNow();
        super.onDestroyView();
    }

    private void requestPermission() {
        PermissionUtil.requestMediaVideo(this, granted -> {
            if (granted) loadVideos();
            else showEmpty(getString(R.string.local_no_permission));
        });
    }

    private void loadVideos() {
        executor.execute(() -> {
            List<LocalVideo> result = queryVideos();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                allVideos.clear();
                allVideos.addAll(result);
                refreshAll();
            });
        });
    }

    private void showEmpty(String text) {
        mBinding.empty.setText(text);
        mBinding.empty.setVisibility(View.VISIBLE);
    }

    private void updateEmpty() {
        if (allVideos.isEmpty()) {
            showEmpty(getString(R.string.local_empty));
            return;
        }
        if (!shownGrid.isEmpty()) {
            mBinding.empty.setVisibility(View.GONE);
            return;
        }
        if (privateOpen) showEmpty(getString(R.string.local_private_no_videos));
        else if (currentFolder != null) showEmpty(getString(R.string.local_folder_empty));
        else showEmpty(getString(R.string.local_empty));
    }

    private List<LocalVideo> queryVideos() {
        List<LocalVideo> result = new ArrayList<>();
        ContentResolver resolver = requireContext().getContentResolver();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATA, MediaStore.Video.Media.DATE_ADDED};
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = resolver.query(uri, projection, null, null, sortOrder)) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            int pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            int dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                long duration = cursor.getLong(durCol);
                String path = cursor.getString(pathCol);
                long dateAdded = cursor.getLong(dateCol);
                result.add(new LocalVideo(id, name, duration, ContentUris.withAppendedId(uri, id), path == null ? "" : path, dateAdded));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void loadThumb(LocalVideo video, AppCompatImageView thumb) {
        Bitmap cached = thumbs.get(video.id);
        if (cached != null) {
            thumb.setImageBitmap(cached);
            return;
        }
        thumb.setImageResource(R.drawable.ic_nav_vod);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        ContentResolver resolver = requireContext().getContentResolver();
        thumb.setTag(video.id);
        executor.execute(() -> {
            try {
                Bitmap bitmap = resolver.loadThumbnail(video.uri, new Size(320, 320), null);
                thumbs.put(video.id, bitmap);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Object tag = thumb.getTag();
                    if (tag != null && tag.equals(video.id)) thumb.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private static String formatDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    private static String getPref(String key) {
        String value = Prefers.getString(key);
        return value == null ? "" : value;
    }

    private boolean searching() {
        return !keyword.isEmpty();
    }

    private int loadSortMode() {
        try {
            int mode = Integer.parseInt(getPref(KEY_SORT));
            return mode >= SORT_TIME_NEW && mode <= SORT_TIME_OLD ? mode : SORT_TIME_NEW;
        } catch (Exception e) {
            return SORT_TIME_NEW;
        }
    }

    private boolean isPrivatePath(String path) {
        String folder = getPref(KEY_PRIVATE_FOLDER);
        return !folder.isEmpty() && path != null && path.startsWith(folder + "/");
    }

    private static String parentPath(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        return i > 0 ? path.substring(0, i) : "";
    }

    private static String folderName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private Comparator<LocalVideo> getComparator() {
        switch (sortMode) {
            case SORT_TITLE_AZ:
                return (a, b) -> a.name.compareToIgnoreCase(b.name);
            case SORT_TITLE_ZA:
                return (a, b) -> b.name.compareToIgnoreCase(a.name);
            case SORT_TIME_OLD:
                return (a, b) -> Long.compare(a.dateAdded, b.dateAdded);
            default:
                return (a, b) -> Long.compare(b.dateAdded, a.dateAdded);
        }
    }

    private List<LocalVideo> gridVideos() {
        List<LocalVideo> list = new ArrayList<>();
        for (LocalVideo video : allVideos) {
            if (!privateOpen && isPrivatePath(video.path)) continue;
            if (currentFolder != null && !video.path.startsWith(currentFolder + "/")) continue;
            if (searching() && !video.name.toLowerCase().contains(keyword.toLowerCase())) continue;
            list.add(video);
        }
        list.sort(getComparator());
        return list;
    }

    private void buildHistoryList() {
        historyVideos.clear();
        if (privateOpen) return;
        Map<Long, LocalVideo> byId = new HashMap<>();
        for (LocalVideo video : allVideos) byId.put(video.id, video);
        for (HistoryItem item : loadHistoryItems()) {
            LocalVideo video = byId.get(item.id);
            if (video != null && !isPrivatePath(video.path)) historyVideos.add(video);
        }
    }

    private void buildFolderList() {
        folders.clear();
        if (privateOpen || searching()) return;
        Map<String, Folder> map = new HashMap<>();
        for (LocalVideo video : allVideos) {
            if (isPrivatePath(video.path)) continue;
            String parent = parentPath(video.path);
            if (parent.isEmpty()) continue;
            Folder folder = map.get(parent);
            if (folder == null) {
                folder = new Folder(parent, folderName(parent));
                map.put(parent, folder);
            }
            folder.count++;
        }
        folders.addAll(map.values());
        folders.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    private void buildRows() {
        rows.clear();
        shownGrid = gridVideos();
        if (searching()) {
            rows.add(new Row(Row.TYPE_ALL_HEADER, getString(R.string.local_search_result)));
            for (LocalVideo video : shownGrid) rows.add(new Row(Row.TYPE_VIDEO, video));
            return;
        }
        if (privateOpen) {
            rows.add(new Row(Row.TYPE_FOLDER_BAR, getString(R.string.local_private_title)));
            for (LocalVideo video : shownGrid) rows.add(new Row(Row.TYPE_VIDEO, video));
            return;
        }
        if (!historyVideos.isEmpty()) {
            rows.add(new Row(Row.TYPE_HISTORY_HEADER, getString(R.string.local_history)));
            rows.add(new Row(Row.TYPE_HISTORY_STRIP));
        }
        if (!folders.isEmpty()) {
            rows.add(new Row(Row.TYPE_FOLDER_HEADER, getString(R.string.local_folders)));
            for (Folder folder : folders) rows.add(new Row(Row.TYPE_FOLDER, folder));
        }
        if (currentFolder != null) rows.add(new Row(Row.TYPE_FOLDER_BAR, folderName(currentFolder)));
        rows.add(new Row(Row.TYPE_ALL_HEADER, getString(R.string.local_all_videos)));
        for (LocalVideo video : shownGrid) rows.add(new Row(Row.TYPE_VIDEO, video));
    }

    private void refreshAll() {
        if (mBinding == null || getView() == null || adapter == null) return;
        buildHistoryList();
        buildFolderList();
        buildRows();
        adapter.notifyDataSetChanged();
        historyAdapter.notifyDataSetChanged();
        updateEmpty();
    }

    private void scrollToAllHeader() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).type == Row.TYPE_ALL_HEADER) {
                mBinding.recycler.smoothScrollToPosition(i);
                return;
            }
        }
    }

    private void playVideo(LocalVideo video) {
        recordHistory(video);
        VideoActivity.file(requireActivity(), video.uri);
    }

    private List<HistoryItem> loadHistoryItems() {
        try {
            List<HistoryItem> items = App.gson().fromJson(getPref(KEY_HISTORY), new TypeToken<List<HistoryItem>>() {
            }.getType());
            return items == null ? new ArrayList<>() : items;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void recordHistory(LocalVideo video) {
        if (privateOpen) return;
        List<HistoryItem> items = loadHistoryItems();
        items.removeIf(item -> item.id == video.id);
        items.add(0, new HistoryItem(video));
        while (items.size() > 20) items.remove(items.size() - 1);
        Prefers.put(KEY_HISTORY, App.gson().toJson(items));
        refreshAll();
    }

    private void toggleSearch() {
        boolean show = mBinding.searchInput.getVisibility() != View.VISIBLE;
        mBinding.searchInput.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            showKeyboard(mBinding.searchInput);
        } else {
            mBinding.searchInput.setText("");
            keyword = "";
            hideKeyboard();
            refreshAll();
        }
    }

    private void showKeyboard(View view) {
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view == null) view = mBinding.searchInput;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showSortMenu() {
        PopupMenu menu = new PopupMenu(requireContext(), mBinding.btnSort);
        menu.getMenu().add(0, SORT_TIME_NEW, 0, R.string.local_sort_time_new);
        menu.getMenu().add(0, SORT_TITLE_AZ, 0, R.string.local_sort_title_az);
        menu.getMenu().add(0, SORT_TITLE_ZA, 0, R.string.local_sort_title_za);
        menu.getMenu().add(0, SORT_TIME_OLD, 0, R.string.local_sort_time_old);
        MenuItem current = menu.getMenu().findItem(sortMode);
        if (current != null) {
            current.setCheckable(true);
            current.setChecked(true);
        }
        menu.setOnMenuItemClickListener(item -> {
            sortMode = item.getItemId();
            Prefers.put(KEY_SORT, String.valueOf(sortMode));
            refreshAll();
            return true;
        });
        menu.show();
    }

    private void onPrivateClick() {
        if (getPref(KEY_PRIVATE_PASSWORD).isEmpty()) {
            showSetPasswordDialog();
            return;
        }
        if (getPref(KEY_PRIVATE_FOLDER).isEmpty()) {
            showEnterPasswordDialog(this::pickPrivateFolder);
            return;
        }
        showEnterPasswordDialog(this::openPrivate);
    }

    private void pickPrivateFolder() {
        privateFolderLauncher.launch(new Intent(requireActivity(), FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true));
    }

    private void openPrivate() {
        privateOpen = true;
        currentFolder = getPref(KEY_PRIVATE_FOLDER);
        refreshAll();
        mBinding.recycler.scrollToPosition(0);
    }

    private void lockPrivate() {
        if (!privateOpen) return;
        privateOpen = false;
        currentFolder = null;
        if (mBinding != null && getView() != null) refreshAll();
    }

    private int dp(int value) {
        return (int) (value * requireContext().getResources().getDisplayMetrics().density + 0.5f);
    }

    private EditText passwordInput(int hintRes) {
        EditText input = new EditText(requireContext());
        input.setHint(hintRes);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        return input;
    }

    private LinearLayout passwordLayout(EditText... inputs) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(8), dp(24), 0);
        for (EditText input : inputs) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(8);
            layout.addView(input, params);
        }
        return layout;
    }

    private void showSetPasswordDialog() {
        EditText password = passwordInput(R.string.local_private_password);
        EditText confirm = passwordInput(R.string.local_private_confirm);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.local_private_set_password);
        builder.setView(passwordLayout(password, confirm));
        builder.setPositiveButton(R.string.dialog_positive, null);
        builder.setNegativeButton(R.string.dialog_negative, null);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String first = password.getText().toString();
            String second = confirm.getText().toString();
            if (first.isEmpty()) {
                Notify.show(R.string.local_private_empty);
                return;
            }
            if (!first.equals(second)) {
                Notify.show(R.string.local_private_mismatch);
                return;
            }
            Prefers.put(KEY_PRIVATE_PASSWORD, first);
            dialog.dismiss();
            pickPrivateFolder();
        });
    }

    private void showEnterPasswordDialog(Runnable onOk) {
        EditText input = passwordInput(R.string.local_private_password);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle(R.string.local_private_enter_password);
        builder.setView(passwordLayout(input));
        builder.setPositiveButton(R.string.dialog_positive, null);
        builder.setNegativeButton(R.string.dialog_negative, null);
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (input.getText().toString().equals(getPref(KEY_PRIVATE_PASSWORD))) {
                dialog.dismiss();
                onOk.run();
            } else {
                Notify.show(R.string.local_private_wrong);
            }
        });
    }

    private static class LocalVideo {
        final long id;
        final String name;
        final long duration;
        final Uri uri;
        final String path;
        final long dateAdded;

        LocalVideo(long id, String name, long duration, Uri uri, String path, long dateAdded) {
            this.id = id;
            this.name = name;
            this.duration = duration;
            this.uri = uri;
            this.path = path;
            this.dateAdded = dateAdded;
        }
    }

    private static class HistoryItem {
        long id;
        String name;
        long duration;
        String uri;
        long time;

        HistoryItem() {
        }

        HistoryItem(LocalVideo video) {
            id = video.id;
            name = video.name;
            duration = video.duration;
            uri = video.uri.toString();
            time = System.currentTimeMillis();
        }
    }

    private static class Folder {
        final String path;
        final String name;
        int count;

        Folder(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private static class Row {
        static final int TYPE_HISTORY_HEADER = 0;
        static final int TYPE_HISTORY_STRIP = 1;
        static final int TYPE_FOLDER_HEADER = 2;
        static final int TYPE_FOLDER = 3;
        static final int TYPE_FOLDER_BAR = 4;
        static final int TYPE_ALL_HEADER = 5;
        static final int TYPE_VIDEO = 6;

        final int type;
        final String text;
        final Folder folder;
        final LocalVideo video;

        Row(int type) {
            this(type, null, null, null);
        }

        Row(int type, String text) {
            this(type, text, null, null);
        }

        Row(int type, Folder folder) {
            this(type, null, folder, null);
        }

        Row(int type, LocalVideo video) {
            this(type, null, null, video);
        }

        private Row(int type, String text, Folder folder, LocalVideo video) {
            this.type = type;
            this.text = text;
            this.folder = folder;
            this.video = video;
        }
    }

    private class LocalAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            switch (viewType) {
                case Row.TYPE_HISTORY_STRIP: {
                    RecyclerView strip = new RecyclerView(parent.getContext());
                    strip.setLayoutManager(new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
                    strip.setAdapter(historyAdapter);
                    strip.setHasFixedSize(true);
                    strip.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    return new StripHolder(strip);
                }
                case Row.TYPE_FOLDER:
                    return new FolderHolder(ItemLocalFolderBinding.inflate(inflater, parent, false));
                case Row.TYPE_FOLDER_BAR:
                    return new BarHolder(ItemLocalFolderbarBinding.inflate(inflater, parent, false));
                case Row.TYPE_VIDEO:
                    return new VideoHolder(ItemLocalBinding.inflate(inflater, parent, false));
                default:
                    return new HeaderHolder(ItemLocalHeaderBinding.inflate(inflater, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            switch (row.type) {
                case Row.TYPE_HISTORY_HEADER:
                case Row.TYPE_FOLDER_HEADER:
                case Row.TYPE_ALL_HEADER:
                    ((HeaderHolder) holder).binding.title.setText(row.text);
                    break;
                case Row.TYPE_HISTORY_STRIP:
                    break;
                case Row.TYPE_FOLDER: {
                    FolderHolder folderHolder = (FolderHolder) holder;
                    folderHolder.binding.name.setText(row.folder.name);
                    folderHolder.binding.count.setText(getString(R.string.local_folder_videos, row.folder.count));
                    folderHolder.binding.getRoot().setOnClickListener(v -> {
                        currentFolder = row.folder.path;
                        privateOpen = false;
                        refreshAll();
                        scrollToAllHeader();
                    });
                    break;
                }
                case Row.TYPE_FOLDER_BAR: {
                    BarHolder barHolder = (BarHolder) holder;
                    barHolder.binding.name.setText(row.text);
                    barHolder.binding.close.setOnClickListener(v -> {
                        currentFolder = null;
                        privateOpen = false;
                        refreshAll();
                    });
                    break;
                }
                case Row.TYPE_VIDEO: {
                    VideoHolder videoHolder = (VideoHolder) holder;
                    LocalVideo video = row.video;
                    videoHolder.binding.name.setText(video.name);
                    videoHolder.binding.duration.setText(formatDuration(video.duration));
                    videoHolder.binding.getRoot().setOnClickListener(v -> playVideo(video));
                    loadThumb(video, videoHolder.binding.thumb);
                    break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        class HeaderHolder extends RecyclerView.ViewHolder {
            final ItemLocalHeaderBinding binding;

            HeaderHolder(ItemLocalHeaderBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        class StripHolder extends RecyclerView.ViewHolder {
            StripHolder(RecyclerView strip) {
                super(strip);
            }
        }

        class FolderHolder extends RecyclerView.ViewHolder {
            final ItemLocalFolderBinding binding;

            FolderHolder(ItemLocalFolderBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        class BarHolder extends RecyclerView.ViewHolder {
            final ItemLocalFolderbarBinding binding;

            BarHolder(ItemLocalFolderbarBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        class VideoHolder extends RecyclerView.ViewHolder {
            final ItemLocalBinding binding;

            VideoHolder(ItemLocalBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemLocalHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            LocalVideo video = historyVideos.get(position);
            holder.binding.name.setText(video.name);
            holder.binding.duration.setText(formatDuration(video.duration));
            holder.binding.getRoot().setOnClickListener(v -> playVideo(video));
            loadThumb(video, holder.binding.thumb);
        }

        @Override
        public int getItemCount() {
            return historyVideos.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemLocalHistoryBinding binding;

            Holder(ItemLocalHistoryBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
