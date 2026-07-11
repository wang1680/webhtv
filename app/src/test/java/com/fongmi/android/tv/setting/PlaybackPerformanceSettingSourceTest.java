package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class PlaybackPerformanceSettingSourceTest {

    @Test
    public void originalProfileKeepsPersistedCustomValueStable() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));

        assertTrue(source.contains("PROFILE_CUSTOM = 2"));
        assertTrue(source.contains("PROFILE_ORIGINAL = 3"));
        assertTrue(source.contains("profile == PROFILE_ORIGINAL"));
    }

    @Test
    public void originalDefaultsMatchBehaviorBeforePerformancePresets() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "setting", "PlaybackPerformanceSetting.java"));
        String method = methodBody(source, "public static void applyOriginal()", "public static void markCustom()");

        assertContainsAll(method,
                "put(KEY_CODEC_ASYNC_QUEUEING, false)",
                "put(KEY_DYNAMIC_SCHEDULING, false)",
                "put(KEY_VIDEO_DURATION_PROGRESS, false)",
                "put(KEY_LATE_DROP_INPUT, false)",
                "put(KEY_TRACK_LIMIT, false)",
                "put(KEY_ADAPTIVE_DOWNGRADE, false)",
                "put(KEY_LOAD_ONLY_SELECTED_TRACKS, false)",
                "put(KEY_SURFACE_FIXED_SIZE, false)",
                "put(KEY_DECODER_FALLBACK, true)",
                "put(KEY_SOFT_VIDEO_TUNE, true)",
                "put(KEY_HIGH_BUFFER, false)",
                "put(KEY_BANDWIDTH_METER, false)",
                "Prefers.put(\"render\", PlayerSetting.RENDER_SURFACE)",
                "Prefers.put(\"tunnel\", false)",
                "Prefers.put(\"buffer\", 1)",
                "Prefers.put(\"buffer_bytes\", 0)",
                "Prefers.put(\"back_buffer\", 0)",
                "Prefers.put(\"play_cache\", 0)",
                "Prefers.put(\"preload\", false)",
                "Prefers.put(\"preload_threads\", 1)",
                "Prefers.put(\"preload_size\", 128)",
                "Prefers.put(\"preload_time\", 120)",
                "Prefers.put(\"audio_pass_through\", true)",
                "Prefers.put(\"prefer_aac\", false)",
                "Prefers.put(\"audio_prefer\", false)",
                "Prefers.put(\"video_prefer\", false)",
                "Prefers.put(\"exo_4k_compat\", false)",
                "Prefers.put(KEY_PROFILE, PROFILE_ORIGINAL)");
    }

    @Test
    public void performanceDialogExposesOriginalDefaultsPreset() throws Exception {
        String source = read(sourcePath("main", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "PlaybackPerformanceDialog.java"));

        assertTrue(source.contains("R.string.player_performance_original"));
        assertTrue(source.contains("PlaybackPerformanceSetting.applyOriginal()"));
        assertTrue(source.contains("“原版默认”恢复播放性能设置引入前的参数"));
    }

    private static void assertContainsAll(String source, String... values) {
        for (String value : values) assertTrue("Missing: " + value, source.contains(value));
    }

    private static String methodBody(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath(String... parts) {
        Path path = Path.of("src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src", parts[0], parts[1]);
        for (int i = 2; i < parts.length; i++) path = path.resolve(parts[i]);
        return path;
    }
}
