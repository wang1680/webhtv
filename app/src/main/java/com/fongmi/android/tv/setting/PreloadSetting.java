package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class PreloadSetting {

    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 10;
    public static final int MIN_SIZE_MB = 128;
    public static final int MAX_SIZE_MB = 4096;
    public static final int MIN_TIME_SECONDS = 20;
    public static final int MAX_TIME_SECONDS = 120;
    public static final int STEP_TIME_SECONDS = 10;
    private static final int[] SIZE_OPTIONS_MB = {128, 256, 512, 1024, 2048, 4096};

    public static boolean isPreload() {
        return Prefers.getBoolean("preload");
    }

    public static void putPreload(boolean preload) {
        Prefers.put("preload", preload);
    }

    public static int getPreloadThreads() {
        return clamp(Prefers.getInt("preload_threads", MIN_THREADS), MIN_THREADS, MAX_THREADS);
    }

    public static void putPreloadThreads(int threads) {
        Prefers.put("preload_threads", clamp(threads, MIN_THREADS, MAX_THREADS));
    }

    public static int getPreloadSizeMb() {
        return closestSize(Prefers.getInt("preload_size", MIN_SIZE_MB));
    }

    public static void putPreloadSizeMb(int size) {
        Prefers.put("preload_size", closestSize(size));
    }

    public static int getPreloadSizeOptionCount() {
        return SIZE_OPTIONS_MB.length;
    }

    public static int getPreloadSizeMbAt(int index) {
        return SIZE_OPTIONS_MB[clamp(index, 0, SIZE_OPTIONS_MB.length - 1)];
    }

    public static int getPreloadSizeIndex() {
        int value = getPreloadSizeMb();
        for (int i = 0; i < SIZE_OPTIONS_MB.length; i++) if (SIZE_OPTIONS_MB[i] == value) return i;
        return 0;
    }

    public static int getNextPreloadSizeMb() {
        return SIZE_OPTIONS_MB[(getPreloadSizeIndex() + 1) % SIZE_OPTIONS_MB.length];
    }

    public static long getPreloadSizeBytes() {
        return getPreloadSizeMb() * 1024L * 1024L;
    }

    public static int getPreloadTimeSeconds() {
        int seconds = clamp(Prefers.getInt("preload_time", MAX_TIME_SECONDS), MIN_TIME_SECONDS, MAX_TIME_SECONDS);
        int steps = Math.round((float) (seconds - MIN_TIME_SECONDS) / STEP_TIME_SECONDS);
        return clamp(MIN_TIME_SECONDS + steps * STEP_TIME_SECONDS, MIN_TIME_SECONDS, MAX_TIME_SECONDS);
    }

    public static void putPreloadTimeSeconds(int seconds) {
        Prefers.put("preload_time", clamp(seconds, MIN_TIME_SECONDS, MAX_TIME_SECONDS));
    }

    public static long getPreloadDurationMs() {
        return getPreloadTimeSeconds() * 1000L;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static int closestSize(int value) {
        int closest = SIZE_OPTIONS_MB[0];
        int distance = Math.abs(value - closest);
        for (int option : SIZE_OPTIONS_MB) {
            int current = Math.abs(value - option);
            if (current >= distance) continue;
            closest = option;
            distance = current;
        }
        return closest;
    }
}
