package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TmdbEpisodeMatcher {

    private static final Pattern QUALITY = Pattern.compile("(?i)\\b(?:2160p|1080p|720p|480p|4k|h26[45]|x26[45]|mp4|mkv)\\b");
    private static final Pattern EPISODE_MARKER = Pattern.compile("(?i)(?:第\\s*[零〇一二三四五六七八九十百千万两0-9]+\\s*[集话話回章期]|(?:ep|episode|e)\\s*\\d{1,4}|s\\d{1,2}\\s*e\\d{1,4}|\\b\\d{1,4}\\b)");

    private TmdbEpisodeMatcher() {
    }

    public static boolean shouldApply(Episode episode, TmdbEpisode tmdbEpisode) {
        if (tmdbEpisode == null) return false;
        return shouldApply(episode, tmdbEpisode.getNumber(), tmdbEpisode.getTitle());
    }

    public static boolean shouldApply(Episode episode, int number, String tmdbTitle) {
        if (episode == null || isEmpty(tmdbTitle)) return true;
        String sourceTitle = sourceEpisodeTitle(episode, number);
        String targetTitle = normalize(tmdbTitle);
        if (isEmpty(sourceTitle) || isEmpty(targetTitle)) return true;
        return sourceTitle.contains(targetTitle) || targetTitle.contains(sourceTitle);
    }

    private static String sourceEpisodeTitle(Episode episode, int number) {
        String title = titleAfterNumber(episode.getName(), number);
        if (!isEmpty(title)) return title;
        return titleAfterNumber(episode.getRawDisplayName(), number);
    }

    private static String titleAfterNumber(String text, int number) {
        if (isEmpty(text) || number <= 0) return "";
        Matcher matcher = exactNumberMarker(number).matcher(text);
        String title = "";
        while (matcher.find()) {
            String tail = normalize(text.substring(matcher.end()));
            if (tail.length() > title.length()) title = tail;
        }
        return title;
    }

    private static Pattern exactNumberMarker(int number) {
        String value = String.valueOf(number);
        return Pattern.compile("(?i)(?:第\\s*0*" + value + "\\s*[集话話回章期]?|(?:ep|episode|e)\\s*0*" + value + "\\b|\\b0*" + value + "\\b)");
    }

    private static String normalize(String text) {
        if (isEmpty(text)) return "";
        String value = QUALITY.matcher(text).replaceAll(" ");
        value = value.replaceAll("[\\[\\]【】()（）{}<>《》]", " ");
        value = EPISODE_MARKER.matcher(value).replaceAll(" ");
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
