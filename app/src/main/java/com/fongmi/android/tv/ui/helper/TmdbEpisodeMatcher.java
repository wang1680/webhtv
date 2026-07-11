package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

public final class TmdbEpisodeMatcher {

    private TmdbEpisodeMatcher() {
    }

    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode) {
        return tmdbEpisode != null;
    }

    public static boolean shouldApply(Episode episode, int number, String tmdbTitle) {
        return true;
    }
}
