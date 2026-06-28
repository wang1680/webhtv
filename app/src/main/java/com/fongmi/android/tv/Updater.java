package com.fongmi.android.tv;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback, UpdateListener {

    private static final String SOURCE_CNB = "cnb";
    private static final String SOURCE_GITHUB = "github";

    private UpdateDialog dialog;
    private Download download;
    private Update stable;
    private Update beta;
    private Update selected;
    private boolean force;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getName() {
        return BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
    }

    public Updater force() {
        force = true;
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        stable = getUpdate(Update.CHANNEL_STABLE);
        beta = getUpdate(Update.CHANNEL_BETA);
        if (!stable.hasUpdate() && !beta.hasUpdate()) {
            if (force && (stable.hasManifest() || beta.hasManifest())) {
                selected = stable;
                App.post(() -> show(activity));
                return;
            }
            if (force) App.post(() -> Notify.show(hasErrorOnly() ? R.string.update_failed : R.string.update_latest));
            return;
        }
        selected = stable;
        App.post(() -> show(activity));
    }

    private Update getUpdate(String channel) {
        Update update = readUpdate(channel, Github.getCnbAsset(getManifestName(channel)), SOURCE_CNB);
        if (update.hasManifest()) return update;
        Update fallback = Update.CHANNEL_BETA.equals(channel) ? getGithubBetaUpdate(channel) : readUpdate(channel, Github.getGithubLatestAsset(getManifestName(channel)), SOURCE_GITHUB);
        return fallback.hasManifest() ? fallback : update;
    }

    private Update getGithubBetaUpdate(String channel) {
        String manifestName = getManifestName(channel);
        try {
            JSONArray releases = new JSONArray(OkHttp.string(Github.getReleasesApi()));
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || !isBetaRelease(release)) continue;
                String tag = release.optString("tag_name");
                String url = findAssetUrl(release.optJSONArray("assets"), manifestName);
                if (TextUtils.isEmpty(url) && !TextUtils.isEmpty(tag)) url = Github.getGithubReleaseAsset(tag, manifestName);
                if (TextUtils.isEmpty(url)) continue;
                Update update = readUpdate(channel, url, SOURCE_GITHUB);
                if (update.hasManifest()) return update;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Update.empty(channel);
    }

    private boolean isBetaRelease(JSONObject release) {
        String tag = release.optString("tag_name");
        return release.optBoolean("prerelease") || tag.contains("-beta-");
    }

    private String findAssetUrl(JSONArray assets, String name) {
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !name.equals(asset.optString("name"))) continue;
            return asset.optString("browser_download_url");
        }
        return "";
    }

    private Update readUpdate(String channel, String manifestUrl, String source) {
        Update update = Update.empty(channel);
        try {
            String text = OkHttp.string(manifestUrl);
            if (TextUtils.isEmpty(text)) throw new IllegalStateException("Empty update manifest: " + manifestUrl);
            JSONObject object = new JSONObject(text);
            update.name = object.optString("name");
            update.desc = object.optString("desc");
            update.notes = object.optString("notes");
            update.channel = object.optString("channel", channel);
            update.code = object.optInt("code");
            update.apk = object.optString("apk");
            update.size = object.optLong("size");
            update.apkUrl = getApkUrl(update, source);
            if (TextUtils.isEmpty(update.notes)) update.notes = getReleaseNotes(update.name);
        } catch (Exception e) {
            e.printStackTrace();
            update.error = e.getMessage();
        }
        return update;
    }

    private String getManifestName(String channel) {
        return getAssetName(channel, "json");
    }

    private String getDefaultApkName(String channel) {
        return getAssetName(channel, "apk");
    }

    private String getAssetName(String channel, String ext) {
        String suffix = Update.CHANNEL_BETA.equals(channel) ? "-beta" : "";
        return getName() + suffix + "." + ext;
    }

    private String getApkUrl(Update update, String source) {
        String apk = TextUtils.isEmpty(update.apk) ? getDefaultApkName(update.channel) : update.apk;
        if (apk.startsWith("http://") || apk.startsWith("https://")) return apk;
        if (SOURCE_GITHUB.equals(source) && !TextUtils.isEmpty(update.name)) return Github.getGithubReleaseAsset(update.name, apk);
        return Github.getCnbAsset(apk);
    }

    private String getReleaseNotes(String tag) {
        if (TextUtils.isEmpty(tag)) return "";
        String notes = readReleaseNotes(tag);
        if (!TextUtils.isEmpty(notes) || tag.startsWith("v")) return notes;
        return readReleaseNotes("v" + tag);
    }

    private String readReleaseNotes(String tag) {
        try {
            return new JSONObject(OkHttp.string(Github.getReleaseApi(tag))).optString("body");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasErrorOnly() {
        return !stable.hasManifest() && !beta.hasManifest() && (!TextUtils.isEmpty(stable.error) || !TextUtils.isEmpty(beta.error));
    }

    private void show(FragmentActivity activity) {
        dismiss();
        dialog = UpdateDialog.create().stable(stable).beta(beta).selected(selected.channel).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (selected == null || !selected.hasUpdate()) {
            Notify.show(R.string.update_latest);
            return;
        }
        view.setEnabled(false);
        if (dialog != null) dialog.setProgress(0);
        download = Download.create(selected.apkUrl, getFile()).tag(selected.apkUrl);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    @Override
    public void onChannel(String channel) {
        Setting.putUpdateChannel(channel);
        selected = Update.CHANNEL_BETA.equals(channel) ? beta : stable;
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void progress(int progress, long bytes, long total, long speed, long elapsed) {
        if (dialog != null) dialog.setProgress(progress, bytes, total, speed, elapsed);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        FileUtil.openFile(file);
        dismiss();
    }
}
