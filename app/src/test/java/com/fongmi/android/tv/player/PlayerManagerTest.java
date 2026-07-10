package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.setting.PlayerSetting;

import org.junit.Test;

import java.util.List;

public class PlayerManagerTest {

    @Test
    public void fallbackDecode_resetsToHardWhenChangingPlayerCore() {
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.EXO, PlayerSetting.IJK, PlayerEngine.SOFT));
    }

    @Test
    public void fallbackDecode_keepsCurrentDecodeWhenCoreDoesNotChange() {
        assertEquals(PlayerEngine.SOFT, PlayerManager.fallbackDecode(PlayerSetting.EXO, PlayerSetting.EXO, PlayerEngine.SOFT));
    }

    @Test
    public void fallbackDecode_sanitizesUnknownDecodeToHard() {
        assertEquals(PlayerEngine.HARD, PlayerManager.fallbackDecode(PlayerSetting.EXO, PlayerSetting.EXO, 99));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_blocksFallbackWhileManualSwitchIsPending() {
        assertEquals(true, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsRecoveredManualErrors() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(true, PlayerEngine.ErrorAction.RECOVERED));
    }

    @Test
    public void shouldStopOnManualSwitchFailure_allowsAutomaticFallbacks() {
        assertEquals(false, PlayerManager.shouldStopOnManualSwitchFailure(false, PlayerEngine.ErrorAction.FATAL));
    }

    @Test
    public void findSubtitleSub_matchesSelectedExternalSubtitleByLabelAndMime() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Sub chinese = Sub.create("Chinese", "/tmp/chinese.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("English")
                .setLanguage("eng")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        Sub matched = PlayerManager.findSubtitleSub(List.of(chinese, english), selected);

        assertSame(english, matched);
    }

    @Test
    public void findSubtitleSub_ignoresInternalSubtitleWithoutMatchingSub() {
        Sub english = Sub.create("English", "/tmp/english.srt", "en", MimeTypes.APPLICATION_SUBRIP);
        Format selected = new Format.Builder()
                .setLabel("Embedded English")
                .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
                .build();

        assertNull(PlayerManager.findSubtitleSub(List.of(english), selected));
    }

    @Test
    public void findSelectedSubtitleSub_usesFirstExternalSubtitleBeforeTracksLoad() {
        Sub manual = Sub.create("Manual", "/tmp/manual.srt", "zh-Hans", MimeTypes.APPLICATION_SUBRIP);

        assertSame(manual, PlayerManager.findSelectedSubtitleSub(List.of(manual), Tracks.EMPTY));
    }
}
