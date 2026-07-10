package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbEpisodeMatcherTest {

    @Test
    public void allowsMatchedSourceEpisodeTitle() {
        Episode episode = Episode.create("106. 六圣紫霄议封神", "http://example.test/106");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(106, "六圣紫霄议封神", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode));
    }

    @Test
    public void rejectsDifferentSourceEpisodeTitleForSameNumber() {
        Episode episode = Episode.create("106. 通天宫中通天抗压", "http://example.test/106");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(106, "六圣紫霄议封神", "", "", "", 0, 0);

        assertFalse(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode));
    }

    @Test
    public void allowsNumberOnlySourceEpisodeName() {
        Episode episode = Episode.create("第106集", "http://example.test/106");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(106, "六圣紫霄议封神", "", "", "", 0, 0);

        assertTrue(TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode));
    }
}
