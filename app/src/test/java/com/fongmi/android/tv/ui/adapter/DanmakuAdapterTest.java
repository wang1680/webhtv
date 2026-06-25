package com.fongmi.android.tv.ui.adapter;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DanmakuAdapterTest {

    @Test
    public void parseEpisodeSupportsCommonDanmakuNames() {
        assertEquals(3, DanmakuAdapter.parseEpisode("第03集"));
        assertEquals(12, DanmakuAdapter.parseEpisode("S01E12"));
        assertEquals(7, DanmakuAdapter.parseEpisode("Episode 07"));
    }
}
