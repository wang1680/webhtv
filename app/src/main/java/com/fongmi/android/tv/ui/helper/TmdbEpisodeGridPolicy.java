package com.fongmi.android.tv.ui.helper;

public final class TmdbEpisodeGridPolicy {

    public static final int WRAP_CONTENT = -2;
    public static final int GRID_CARD_HEIGHT_DP = 118;
    public static final int GRID_CARD_BOTTOM_MARGIN_DP = 10;
    public static final int GRID_FALLBACK_IMAGE_LIMIT = 24;

    private static final int MAX_VISIBLE_ROWS = 3;

    private TmdbEpisodeGridPolicy() {
    }

    public static Layout layout(boolean gridMode, int itemCount, int spanCount, int rowHeightPx) {
        if (!gridMode || itemCount <= 0 || spanCount <= 0 || rowHeightPx <= 0) return Layout.wrap();
        int rows = (itemCount + spanCount - 1) / spanCount;
        if (rows <= MAX_VISIBLE_ROWS) return Layout.wrap();
        return new Layout(rowHeightPx * MAX_VISIBLE_ROWS, true);
    }

    public static boolean shouldUseFallbackImage(boolean gridMode, int itemCount) {
        return gridMode && itemCount <= GRID_FALLBACK_IMAGE_LIMIT;
    }

    public static boolean shouldUseFallbackImage(boolean gridMode, int itemCount, boolean hasTmdbEpisodeData) {
        return gridMode;
    }

    public record Layout(int heightPx, boolean nestedScrolling) {

        static Layout wrap() {
            return new Layout(WRAP_CONTENT, false);
        }
    }
}
