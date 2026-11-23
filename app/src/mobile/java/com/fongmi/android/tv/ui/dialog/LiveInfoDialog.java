package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.databinding.DialogLiveInfoBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 竖屏直播“简介”弹窗：频道号 + 频道名、当前节目、播出时间、线路。
 * 节目信息来自 EPG（EpgData 只有节目名和起止时间，没有节目梗概，所以简介只能给到这个程度）；
 * 直播源没有 EPG 时只显示频道和线路。
 */
public class LiveInfoDialog extends BaseAlertDialog {

    private DialogLiveInfoBinding binding;
    private Channel channel;
    private EpgData epg;

    public static LiveInfoDialog create(Channel channel, EpgData epg) {
        LiveInfoDialog dialog = new LiveInfoDialog();
        dialog.channel = channel;
        dialog.epg = epg == null ? new EpgData() : epg;
        return dialog;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLiveInfoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        String number = channel.getNumber();
        binding.title.setText(number.isEmpty() ? channel.getShow() : number + "  " + channel.getShow());
        binding.program.setText(epg.getTitle());
        binding.program.setVisibility(TextUtils.isEmpty(epg.getTitle()) ? View.GONE : View.VISIBLE);
        binding.time.setText(epg.getTime());
        binding.time.setVisibility(TextUtils.isEmpty(epg.getTime()) ? View.GONE : View.VISIBLE);
        binding.line.setText(channel.getLine());
        binding.line.setVisibility(channel.getLineVisible());
    }
}
