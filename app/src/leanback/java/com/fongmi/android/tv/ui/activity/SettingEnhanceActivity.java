package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingEnhanceBinding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.DebugLogDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;

public class SettingEnhanceActivity extends BaseActivity {

    private ActivitySettingEnhanceBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingEnhanceActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingEnhanceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.driveCheck.requestFocus();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.shellProxy.setOnClickListener(this::setShellProxy);
        mBinding.shellProxy.setOnLongClickListener(v -> {
            ShellProxyDialog.show(this, this::setText);
            return true;
        });
        mBinding.shellProxyConfig.setVisibility(View.GONE);
        mBinding.oneKeySync.setOnClickListener(v -> OneKeySyncDialog.create().show(this));
    }

    private void setText() {
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        mBinding.shellProxyText.setText(getSwitch(Setting.isShellProxy()));
        mBinding.shellProxyConfigText.setText(getString(R.string.setting_proxy_rule_count, ProxySetting.count()));
    }

    private void setDriveCheck(View view) {
        Setting.putDriveCheck(!Setting.isDriveCheck());
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
    }

    private void setDebugLog(View view) {
        Setting.putDebugLog(!Setting.isDebugLog());
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        if (!Setting.isDebugLog()) return;
        DebugLogDialog.show(this);
    }

    private void setShellProxy(View view) {
        if (!Setting.isShellProxy()) {
            ShellProxyDialog.show(this, () -> {
                Setting.putShellProxy(true);
                setText();
            });
            return;
        }
        Setting.putShellProxy(false);
        setText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }
}
