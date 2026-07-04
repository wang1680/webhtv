package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TmdbMatcherTest {

    @Test
    public void cleanVideoName_removesPushObfuscationTokens() {
        TmdbMatcher matcher = new TmdbMatcher(null, new TmdbConfig());

        assertEquals("凡人修仙传", matcher.cleanVideoName("F 凡人#修仙传 动漫 A"));
        assertEquals("凡人修仙传", matcher.cleanVideoName("F 凡人#修仙传 动漫 B"));
    }
}
