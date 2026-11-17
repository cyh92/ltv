package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogPlayerButtonsBinding;
import com.fongmi.android.tv.databinding.ItemPlayerButtonBinding;
import com.fongmi.android.tv.setting.PlayerButtonsSetting;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 播放器按钮编辑弹窗：18 个控制栏按钮，每行眼睛开关 + 上/下移位，重置恢复默认，确定保存。
 * 未点确定就退出则不保存。
 */
public final class PlayerButtonsEditDialog {

    private Runnable onChanged;

    public static PlayerButtonsEditDialog create() {
        return new PlayerButtonsEditDialog();
    }

    public PlayerButtonsEditDialog onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        for (Fragment fragment : manager.getFragments()) if (fragment instanceof BottomSheet) return;
        BottomSheet sheet = new BottomSheet();
        sheet.onChanged = onChanged;
        sheet.show(manager, null);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private Runnable onChanged;
        private DialogPlayerButtonsBinding binding;
        private Adapter adapter;
        private List<String> order = new ArrayList<>();
        private Set<String> hidden = new HashSet<>();

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = DialogPlayerButtonsBinding.inflate(inflater, container, false);
        }

        @Override
        protected int getMaxHeight() {
            return ResUtil.getScreenHeight() * 3 / 4;
        }

        @Override
        protected void initView() {
            order = PlayerButtonsSetting.getOrder();
            hidden = PlayerButtonsSetting.getHidden();
            binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.list.setAdapter(adapter = new Adapter());
            updateCount();
        }

        @Override
        protected void initEvent() {
            binding.reset.setOnClickListener(v -> {
                order = PlayerButtonsSetting.defaultOrder();
                hidden = new HashSet<>();
                adapter.notifyDataSetChanged();
                updateCount();
            });
            binding.confirm.setOnClickListener(v -> {
                PlayerButtonsSetting.putOrder(order);
                PlayerButtonsSetting.putHidden(hidden);
                if (onChanged != null) onChanged.run();
                dismiss();
            });
        }

        private void updateCount() {
            int visible = 0;
            for (String id : order) if (!hidden.contains(id)) visible++;
            binding.count.setText(getString(R.string.player_buttons_visible, visible, order.size()));
        }

        private int nameRes(String id) {
            return switch (id) {
                case "episodes" -> R.string.detail_episode;
                case "parse" -> R.string.parse;
                case "player" -> R.string.play_exo;
                case "decoder" -> R.string.decoder_action;
                case "reset" -> R.string.play_reset;
                case "replay" -> R.string.play_replay;
                case "repeat" -> R.string.play_repeat;
                case "speed" -> R.string.play_speed;
                case "scale" -> R.string.play_scale;
                case "text" -> R.string.play_track_text;
                case "audio" -> R.string.play_track_audio;
                case "video" -> R.string.play_track_video;
                case "danmaku" -> R.string.danmaku;
                case "edition" -> R.string.play_edition;
                case "chapter" -> R.string.play_chapter;
                case "disc_menu" -> R.string.play_disc_menu;
                case "opening" -> R.string.play_op;
                case "ending" -> R.string.play_ed;
                default -> R.string.play_exo;
            };
        }

        private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(ItemPlayerButtonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                String id = order.get(position);
                holder.binding.name.setText(nameRes(id));
                holder.binding.eye.setImageResource(hidden.contains(id) ? R.drawable.ic_display_eye_off : R.drawable.ic_display_eye);
                holder.binding.up.setEnabled(position > 0);
                holder.binding.up.setAlpha(position > 0 ? 1f : 0.3f);
                holder.binding.down.setEnabled(position < order.size() - 1);
                holder.binding.down.setAlpha(position < order.size() - 1 ? 1f : 0.3f);
                holder.binding.eye.setOnClickListener(v -> {
                    if (hidden.contains(id)) hidden.remove(id);
                    else hidden.add(id);
                    notifyItemChanged(holder.getBindingAdapterPosition());
                    updateCount();
                });
                holder.binding.up.setOnClickListener(v -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos <= 0) return;
                    Collections.swap(order, pos, pos - 1);
                    notifyItemMoved(pos, pos - 1);
                });
                holder.binding.down.setOnClickListener(v -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos < 0 || pos >= order.size() - 1) return;
                    Collections.swap(order, pos, pos + 1);
                    notifyItemMoved(pos, pos + 1);
                });
            }

            @Override
            public int getItemCount() {
                return order.size();
            }

            private final class Holder extends RecyclerView.ViewHolder {
                private final ItemPlayerButtonBinding binding;

                private Holder(ItemPlayerButtonBinding binding) {
                    super(binding.getRoot());
                    this.binding = binding;
                }
            }
        }

        @Override
        public void onDestroyView() {
            binding = null;
            adapter = null;
            super.onDestroyView();
        }
    }
}
