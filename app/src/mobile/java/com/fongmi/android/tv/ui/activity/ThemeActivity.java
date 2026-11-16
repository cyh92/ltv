package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFragmentBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.ThemeFragment;

public class ThemeActivity extends BaseActivity {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ThemeActivity.class));
    }

    private ActivityFragmentBinding mBinding;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFragmentBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) getSupportFragmentManager().beginTransaction().add(R.id.container, ThemeFragment.newInstance(false)).commit();
    }
}
