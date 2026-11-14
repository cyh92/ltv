package com.fongmi.android.tv.ui.fragment;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.FragmentLocalBinding;
import com.fongmi.android.tv.databinding.ItemLocalBinding;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.PermissionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalFragment extends BaseFragment {

    private FragmentLocalBinding mBinding;
    private final List<LocalVideo> videos = new ArrayList<>();
    private final LruCache<Long, Bitmap> thumbs = new LruCache<Long, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(Long key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };
    private ExecutorService executor;
    private LocalAdapter adapter;

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
        adapter = new LocalAdapter();
        mBinding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mBinding.recycler.setAdapter(adapter);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.empty.setOnClickListener(v -> requestPermission());
        requestPermission();
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
                videos.clear();
                videos.addAll(result);
                adapter.notifyDataSetChanged();
                if (videos.isEmpty()) showEmpty(getString(R.string.local_empty));
                else mBinding.empty.setVisibility(View.GONE);
            });
        });
    }

    private void showEmpty(String text) {
        mBinding.empty.setText(text);
        mBinding.empty.setVisibility(View.VISIBLE);
    }

    private List<LocalVideo> queryVideos() {
        List<LocalVideo> result = new ArrayList<>();
        ContentResolver resolver = requireContext().getContentResolver();
        Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION};
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = resolver.query(uri, projection, null, null, sortOrder)) {
            if (cursor == null) return result;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
            int durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idCol);
                String name = cursor.getString(nameCol);
                long duration = cursor.getLong(durCol);
                result.add(new LocalVideo(id, name, duration, ContentUris.withAppendedId(uri, id)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void loadThumb(LocalVideo video, ItemLocalBinding binding) {
        Bitmap cached = thumbs.get(video.id);
        if (cached != null) {
            binding.thumb.setImageBitmap(cached);
            return;
        }
        binding.thumb.setImageResource(R.drawable.ic_nav_vod);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        ContentResolver resolver = requireContext().getContentResolver();
        binding.thumb.setTag(video.id);
        executor.execute(() -> {
            try {
                Bitmap bitmap = resolver.loadThumbnail(video.uri, new Size(320, 320), null);
                thumbs.put(video.id, bitmap);
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Object tag = binding.thumb.getTag();
                    if (tag != null && tag.equals(video.id)) binding.thumb.setImageBitmap(bitmap);
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

    private static class LocalVideo {
        final long id;
        final String name;
        final long duration;
        final Uri uri;

        LocalVideo(long id, String name, long duration, Uri uri) {
            this.id = id;
            this.name = name;
            this.duration = duration;
            this.uri = uri;
        }
    }

    private class LocalAdapter extends RecyclerView.Adapter<LocalAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemLocalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            LocalVideo video = videos.get(position);
            holder.binding.name.setText(video.name);
            holder.binding.duration.setText(formatDuration(video.duration));
            holder.binding.getRoot().setOnClickListener(v -> VideoActivity.file(requireActivity(), video.uri));
            loadThumb(video, holder.binding);
        }

        @Override
        public int getItemCount() {
            return videos.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemLocalBinding binding;

            Holder(ItemLocalBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
