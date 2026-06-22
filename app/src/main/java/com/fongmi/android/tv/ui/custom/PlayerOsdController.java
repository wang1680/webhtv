package com.fongmi.android.tv.ui.custom;

import android.net.TrafficStats;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlayerOsdController {

    public interface Source {
        PlayerManager getPlayer();

        String getTitle();
    }

    private static final DecimalFormat SPEED_FORMAT = new DecimalFormat("#.0");
    private static final int UID = App.get().getApplicationInfo().uid;

    private final SimpleDateFormat timeFormat;
    private final TextView topLeft;
    private final TextView topRight;
    private final TextView bottomLeft;
    private final TextView bottomRight;
    private final MiniProgressView miniProgress;
    private final Runnable update;
    private final Source source;
    private final View root;
    private final float normalSp;
    private final float miniSp;

    private long lastTotalRxBytes;
    private long lastTimeStamp;

    public PlayerOsdController(View root, TextView topLeft, TextView topRight, TextView bottomLeft, TextView bottomRight, MiniProgressView miniProgress, Source source, float normalSp, float miniSp) {
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.miniProgress = miniProgress;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
        this.topRight = topRight;
        this.topLeft = topLeft;
        this.normalSp = normalSp;
        this.miniSp = miniSp;
        this.source = source;
        this.root = root;
        this.update = this::update;
    }

    public void start() {
        if (!PlayerSetting.isOsdEnabled()) {
            root.setVisibility(View.GONE);
            return;
        }
        resetSpeed();
        App.post(update, 0);
    }

    public void stop() {
        App.removeCallbacks(update);
    }

    public void release() {
        stop();
    }

    private void update() {
        boolean enabled = PlayerSetting.isOsdEnabled();
        root.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) return;
        setTextSize(PlayerSetting.isOsdMini() ? miniSp : normalSp);
        PlayerManager player = source.getPlayer();
        setTopLeft(player);
        setTopRight();
        setBottomLeft(player);
        setBottomRight();
        setMiniProgress(player);
        App.post(update, 1000);
    }

    private void setTopLeft(PlayerManager player) {
        if (!PlayerSetting.isOsdTitle()) {
            topLeft.setVisibility(View.GONE);
            return;
        }
        String title = source.getTitle();
        String size = player == null ? "" : player.getSizeText();
        if (TextUtils.isEmpty(title)) topLeft.setText(size);
        else if (TextUtils.isEmpty(size)) topLeft.setText(title);
        else topLeft.setText(title + "\n" + size);
        topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
    }

    private void setTopRight() {
        topRight.setVisibility(PlayerSetting.isOsdTime() ? View.VISIBLE : View.GONE);
        if (PlayerSetting.isOsdTime()) topRight.setText(timeFormat.format(new Date()));
    }

    private void setBottomLeft(PlayerManager player) {
        if (!PlayerSetting.isOsdProgress() || player == null || player.isLive()) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        long position = Math.max(0, player.getPosition());
        long duration = Math.max(0, player.getDuration());
        bottomLeft.setText(Util.timeMs(position) + " / " + Util.timeMs(duration));
        bottomLeft.setVisibility(View.VISIBLE);
    }

    private void setBottomRight() {
        bottomRight.setVisibility(PlayerSetting.isOsdTraffic() ? View.VISIBLE : View.GONE);
        if (!PlayerSetting.isOsdTraffic()) return;
        String speed = getSpeed();
        bottomRight.setText(speed);
        bottomRight.setVisibility(TextUtils.isEmpty(speed) ? View.GONE : View.VISIBLE);
    }

    private void setMiniProgress(PlayerManager player) {
        if (!PlayerSetting.isOsdProgress() || player == null || player.isLive()) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        miniProgress.setProgress(player.getPosition(), duration);
        miniProgress.setVisibility(View.VISIBLE);
    }

    private void setTextSize(float sp) {
        topLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        topRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    private String getSpeed() {
        long total = TrafficStats.getUidRxBytes(UID);
        if (total == TrafficStats.UNSUPPORTED) return "";
        long now = System.currentTimeMillis();
        long rxKb = total / 1024;
        long speed = (rxKb - lastTotalRxBytes) * 1000 / Math.max(now - lastTimeStamp, 1);
        lastTimeStamp = now;
        lastTotalRxBytes = rxKb;
        return speed < 1000 ? speed + " KB/s" : SPEED_FORMAT.format(speed / 1024f) + " MB/s";
    }

    private void resetSpeed() {
        long total = TrafficStats.getUidRxBytes(UID);
        lastTotalRxBytes = total == TrafficStats.UNSUPPORTED ? 0 : total / 1024;
        lastTimeStamp = System.currentTimeMillis();
    }
}
