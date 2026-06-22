package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TmdbUIAdapterTest {

    @Test
    public void applyTmdbTitle_updatesVodNameToScrapedTitle() {
        Vod vod = new FakeVod();
        vod.setName("源站标题");
        TmdbItem item = new TmdbItem(123, "tv", "刮削后的标题", "", "", "", "");

        assertTrue(TmdbUIAdapter.applyTmdbTitle(vod, item));

        assertEquals("刮削后的标题", vod.getName());
    }

    private static final class FakeVod extends Vod {

        private String name;

        @Override
        public String getName() {
            return name == null ? "" : name;
        }

        @Override
        public void setName(String vodName) {
            name = vodName;
        }
    }
}
