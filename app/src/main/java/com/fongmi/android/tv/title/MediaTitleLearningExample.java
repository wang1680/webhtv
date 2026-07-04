package com.fongmi.android.tv.title;

public final class MediaTitleLearningExample {

    public static final String SOURCE_TMDB_MANUAL = "TMDB_MANUAL";
    public static final String SOURCE_DANMAKU_MANUAL = "DANMAKU_MANUAL";
    public static final String SOURCE_DANMAKU_AUTO = "DANMAKU_AUTO";
    public static final String SOURCE_SUBTITLE_MANUAL = "SUBTITLE_MANUAL";
    public static final String SOURCE_SUBTITLE_AUTO = "SUBTITLE_AUTO";
    public static final String SOURCE_TMDB_AUTO = "TMDB_AUTO";

    private String rawTitle;
    private String ruleTitle;
    private String expectedTitle;
    private String mediaType;
    private String siteKey;
    private String vodId;
    private int year;
    private int seasonNumber;
    private String source;
    private int hitCount;
    private long updatedAt;
    private boolean manual;
    private boolean superseded;
    private boolean conflict;

    public MediaTitleLearningExample() {
    }

    public static MediaTitleLearningExample manual(String rawTitle, String ruleTitle, String expectedTitle, String mediaType, int year, int seasonNumber, String source) {
        MediaTitleLearningExample example = new MediaTitleLearningExample();
        example.rawTitle = clean(rawTitle);
        example.ruleTitle = clean(ruleTitle);
        example.expectedTitle = clean(expectedTitle);
        example.mediaType = normalizeMediaType(mediaType);
        example.year = validYear(year) ? year : 0;
        example.seasonNumber = seasonNumber > 0 ? seasonNumber : -1;
        example.source = clean(source);
        example.hitCount = 1;
        example.updatedAt = System.currentTimeMillis();
        example.manual = true;
        return example;
    }

    public MediaTitleLearningExample identity(String siteKey, String vodId) {
        this.siteKey = clean(siteKey);
        this.vodId = clean(vodId);
        return this;
    }

    public String getSiteKey() {
        return clean(siteKey);
    }

    public String getVodId() {
        return clean(vodId);
    }

    public String getRawTitle() {
        return clean(rawTitle);
    }

    public String getRuleTitle() {
        return clean(ruleTitle);
    }

    public String getExpectedTitle() {
        return clean(expectedTitle);
    }

    public String getMediaType() {
        return normalizeMediaType(mediaType);
    }

    public int getYear() {
        return validYear(year) ? year : 0;
    }

    public int getSeasonNumber() {
        return seasonNumber > 0 ? seasonNumber : -1;
    }

    public String getSource() {
        return clean(source);
    }

    public int getHitCount() {
        return Math.max(0, hitCount);
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean isManual() {
        return manual;
    }

    public boolean isSuperseded() {
        return superseded;
    }

    public void setSuperseded(boolean superseded) {
        this.superseded = superseded;
    }

    public boolean isConflict() {
        return conflict;
    }

    public void setConflict(boolean conflict) {
        this.conflict = conflict;
    }

    public boolean isUsable() {
        return !getExpectedTitle().isEmpty() && !superseded && !conflict;
    }

    public void bump() {
        hitCount = Math.max(0, hitCount) + 1;
        updatedAt = System.currentTimeMillis();
    }

    static String normalizeMediaType(String value) {
        String text = clean(value).toLowerCase();
        if ("tv".equals(text) || "movie".equals(text)) return text;
        return "unknown";
    }

    private static boolean validYear(int year) {
        return year >= 1900 && year <= 2099;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
