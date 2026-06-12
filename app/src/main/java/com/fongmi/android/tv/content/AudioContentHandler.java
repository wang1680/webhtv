package com.fongmi.android.tv.content;

import android.app.Activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.ui.activity.AudioActivity;
import com.fongmi.android.tv.utils.AudioUtil;

import java.util.List;

public class AudioContentHandler implements ContentHandler {

    @Override
    public boolean canHandleSite(String key, String name) {
        return AudioUtil.isAudioSiteEnabled(key);
    }

    @Override
    public boolean canHandleUrl(String url) {
        return AudioUtil.isAudioUrl(url);
    }

    @Override
    public boolean handleSite(Activity activity, String key, String id, String name, String pic, String mark) {
        return AudioActivity.startSite(activity, key, id, name, pic, mark);
    }

    @Override
    public boolean handleUrl(Activity activity, String url, String title) {
        AudioActivity.start(activity, url, title, "", null);
        return true;
    }

    @Override
    public boolean handleResult(Activity activity, String historyKey, String siteKey, String flag, String vodName, String vodPic, List<Episode> episodes, int position, Result result, long timeout) {
        return AudioActivity.startIfAudio(activity, historyKey, siteKey, flag, vodName, vodPic, episodes, position, result, timeout);
    }
}
