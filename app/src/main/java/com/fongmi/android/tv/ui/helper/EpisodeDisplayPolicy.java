package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;

import java.util.List;

public final class EpisodeDisplayPolicy {

    private EpisodeDisplayPolicy() {
    }

    public static boolean hasTmdbEpisodeData(List<Episode> items) {
        if (items == null || items.isEmpty()) return false;
        for (Episode item : items) {
            if (TmdbEpisodeMatcher.shouldApply(item, item == null ? null : item.getTmdbEpisode())) return true;
        }
        return false;
    }

    public static boolean shouldUseTmdbEpisodeCards(boolean tmdbSourceEnabled, List<Episode> items) {
        return tmdbSourceEnabled && hasTmdbEpisodeData(items);
    }

    public static boolean shouldWaitForTmdbEpisodes(boolean tmdbSourceEnabled, boolean tmdbDetailLoading, boolean tmdbAdapterReady, boolean tmdbAdapterLoaded, List<Episode> items) {
        return tmdbSourceEnabled && tmdbDetailLoading && tmdbAdapterReady && !tmdbAdapterLoaded && items != null && !items.isEmpty() && !hasTmdbEpisodeData(items);
    }

    public static boolean shouldShowTmdbEpisodeChrome(boolean tmdbSourceEnabled, boolean waitingForTmdbEpisodes, List<Episode> items) {
        return tmdbSourceEnabled && (hasTmdbEpisodeData(items) || waitingForTmdbEpisodes);
    }

    public static boolean shouldShowEpisodeGroup(int groupCount, boolean tmdbDetailLayout) {
        return groupCount > 1;
    }
}
