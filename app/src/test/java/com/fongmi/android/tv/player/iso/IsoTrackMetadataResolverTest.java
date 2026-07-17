package com.fongmi.android.tv.player.iso;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

public class IsoTrackMetadataResolverTest {

    @Test
    public void pidMatchWinsOverOrdinalFallback() {
        IsoTrackMetadataResolver.Snapshot snapshot = new IsoTrackMetadataResolver.Snapshot(List.of(
                track(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1200, "eng"),
                track(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1201, "zho")));

        assertEquals("zho", snapshot.language(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1201, 0));
    }

    @Test
    public void emptyTracksRemainInOrdinalSequence() {
        IsoTrackMetadataResolver.Snapshot snapshot = new IsoTrackMetadataResolver.Snapshot(List.of(
                track(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1200, "eng"),
                track(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1201, ""),
                track(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1202, "zho")));

        assertNull(snapshot.language(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, -1, 1));
        assertEquals("zho", snapshot.language(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, -1, 2));
        assertEquals(3, snapshot.subtitleCount());
    }

    @Test
    public void audioAndSubtitleOrdinalsAreIndependent() {
        IsoTrackMetadataResolver.Snapshot snapshot = new IsoTrackMetadataResolver.Snapshot(List.of(
                track(IsoTrackMetadataResolver.TRACK_TYPE_AUDIO, 1100, "eng"),
                track(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, 1200, "jpn"),
                track(IsoTrackMetadataResolver.TRACK_TYPE_AUDIO, 1101, "zho")));

        assertEquals("zho", snapshot.language(IsoTrackMetadataResolver.TRACK_TYPE_AUDIO, -1, 1));
        assertEquals("jpn", snapshot.language(IsoTrackMetadataResolver.TRACK_TYPE_SUBTITLE, -1, 0));
        assertEquals(2, snapshot.audioCount());
        assertEquals(1, snapshot.subtitleCount());
    }

    private static IsoTrackMetadataResolver.Track track(int type, int pid, String language) {
        return new IsoTrackMetadataResolver.Track(type, pid, 0, 0, language);
    }
}
