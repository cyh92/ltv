package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.databinding.ActivityParseBinding;
import com.fongmi.android.tv.ui.adapter.ParseManageAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.ParseEditDialog;
import com.fongmi.android.tv.utils.ParseStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ParseActivity extends BaseActivity implements ParseManageAdapter.OnClickListener {

    private ActivityParseBinding mBinding;
    private ParseManageAdapter mAdapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ParseActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityParseBinding.inflate(getLayoutInflater());
    }

    @Override
    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        super.setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setSupportActionBar(mBinding.toolbar);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recycler.setAdapter(mAdapter = new ParseManageAdapter(this));
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        mAdapter.setItems(ParseStore.getUserParses());
        if (mAdapter.getItemCount() == 0) mBinding.progressLayout.showEmpty();
        else mBinding.progressLayout.showContent();
    }

    private void onAdd() {
        ParseEditDialog.create(null).onSaved(this::refresh).show(this);
    }

    @Override
    public void onItemClick(Parse item) {
        ParseEditDialog.create(item).onSaved(this::refresh).show(this);
    }

    @Override
    public void onItemRadio(Parse item) {
        VodConfig.get().setParse(item);
        refresh();
    }

    @Override
    public void onItemDelete(Parse item) {
        new MaterialAlertDialogBuilder(this).setTitle(getString(R.string.parse_delete_confirm, item.getName())).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            ParseStore.remove(item);
            VodConfig.get().updateParses();
            refresh();
        }).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_parse, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) onBackInvoked();
        else if (item.getItemId() == R.id.add) onAdd();
        return super.onOptionsItemSelected(item);
    }
}
