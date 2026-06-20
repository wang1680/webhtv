package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.BufferDialog;
import com.fongmi.android.tv.ui.dialog.SpeedDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DecimalFormat;

public class SettingPlayerActivity extends BaseActivity implements UaListener, BufferListener, SpeedListener {

    private ActivitySettingPlayerBinding mBinding;
    private DecimalFormat format;
    private String[] caption;
    private String[] kernel;
    private String[] render;
    private String[] scale;
    private String[] osd;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setVisible();
        format = new DecimalFormat("0.#");
        mBinding.render.requestFocus();
        mBinding.uaText.setText(Setting.getUa());
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        mBinding.adblockText.setText(getSwitch(Setting.isAdblock()));
        mBinding.speedText.setText(format.format(PlayerSetting.getSpeed()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.backgroundText.setText(getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
        mBinding.osdText.setText(getOsdText(osd = ResUtil.getStringArray(R.array.select_player_osd)));
        mBinding.kernelText.setText((kernel = ResUtil.getStringArray(R.array.select_player_kernel))[PlayerSetting.getPlayer()]);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.renderText.setText((render = ResUtil.getStringArray(R.array.select_render))[PlayerSetting.getRender()]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[PlayerSetting.isCaption() ? 1 : 0]);
    }

    @Override
    protected void initEvent() {
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.kernel.setOnClickListener(this::setKernel);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.osd.setOnClickListener(this::onOsd);
        mBinding.speed.setOnClickListener(this::onSpeed);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.caption.setOnClickListener(this::setCaption);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.caption.setOnLongClickListener(this::onCaption);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.musicNotification.setOnClickListener(this::setMusicNotification);
        mBinding.audioBookNotification.setOnClickListener(this::setAudioBookNotification);
        mBinding.audioDecode.setOnClickListener(this::setAudioDecode);
        mBinding.videoDecode.setOnClickListener(this::setVideoDecode);
    }

    private void setVisible() {
        if (PlayerSetting.getBackground() == 2) PlayerSetting.putBackground(1);
        mBinding.caption.setVisibility(PlayerSetting.hasCaption() ? View.VISIBLE : View.GONE);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        mBinding.uaText.setText(ua);
        Setting.putUa(ua);
    }

    private void setAAC(View view) {
        PlayerSetting.putPreferAAC(!PlayerSetting.isPreferAAC());
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
    }

    private void setKernel(View view) {
        int index = (PlayerSetting.getPlayer() + 1) % kernel.length;
        mBinding.kernelText.setText(kernel[index]);
        PlayerSetting.putPlayer(index);
    }

    private void setScale(View view) {
        int index = (PlayerSetting.getScale() + 1) % scale.length;
        mBinding.scaleText.setText(scale[index]);
        PlayerSetting.putScale(index);
    }

    private void onOsd(View view) {
        boolean[] checked = getOsdChecked();
        new MaterialAlertDialogBuilder(this).setTitle(R.string.player_osd).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            setOsdChecked(checked);
            mBinding.osdText.setText(getOsdText(osd));
        }).setMultiChoiceItems(osd, checked, (dialog, which, isChecked) -> checked[which] = isChecked).show();
    }

    private boolean[] getOsdChecked() {
        return new boolean[]{PlayerSetting.isOsdTitle(), PlayerSetting.isOsdTime(), PlayerSetting.isOsdProgress(), PlayerSetting.isOsdTraffic(), PlayerSetting.isOsdMini()};
    }

    private void setOsdChecked(boolean[] checked) {
        PlayerSetting.putOsdTitle(checked[0]);
        PlayerSetting.putOsdTime(checked[1]);
        PlayerSetting.putOsdProgress(checked[2]);
        PlayerSetting.putOsdTraffic(checked[3]);
        PlayerSetting.putOsdMini(checked[4]);
    }

    private String getOsdText(String[] items) {
        boolean[] checked = getOsdChecked();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < checked.length; i++) {
            if (!checked[i]) continue;
            if (builder.length() > 0) builder.append(" / ");
            builder.append(items[i]);
        }
        return builder.length() == 0 ? getString(R.string.setting_off) : builder.toString();
    }

    private void onSpeed(View view) {
        SpeedDialog.show(this);
    }

    @Override
    public void setSpeed(float speed) {
        mBinding.speedText.setText(format.format(speed));
        PlayerSetting.putSpeed(speed);
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        PlayerSetting.putBuffer(times);
    }

    private void setRender(View view) {
        if (PlayerSetting.isTunnel() && PlayerSetting.getRender() == 0) setTunnel(view);
        int index = (PlayerSetting.getRender() + 1) % render.length;
        mBinding.renderText.setText(render[index]);
        PlayerSetting.putRender(index);
    }

    private void setTunnel(View view) {
        PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        if (PlayerSetting.isTunnel() && PlayerSetting.getRender() == 1) setRender(view);
    }

    private void setCaption(View view) {
        PlayerSetting.putCaption(!PlayerSetting.isCaption());
        mBinding.captionText.setText(caption[PlayerSetting.isCaption() ? 1 : 0]);
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(getSwitch(Setting.isAdblock()));
    }

    private boolean onCaption(View view) {
        if (PlayerSetting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return PlayerSetting.isCaption();
    }

    private void setMusicNotification(View view) {
        PlayerSetting.putMusicNotification(!PlayerSetting.isMusicNotification());
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
    }

    private void setAudioBookNotification(View view) {
        PlayerSetting.putAudioBookNotification(!PlayerSetting.isAudioBookNotification());
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
    }

    private void setAudioDecode(View view) {
        PlayerSetting.putAudioPrefer(!PlayerSetting.isAudioPrefer());
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
    }

    private void setVideoDecode(View view) {
        PlayerSetting.putVideoPrefer(!PlayerSetting.isVideoPrefer());
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(getSwitch(PlayerSetting.isBackgroundOn()));
    }
}
