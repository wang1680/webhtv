package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeTitleCompact {

    private static final Pattern EXTENSION = Pattern.compile("(?i)\\.(mp4|mkv|avi|mov|flv|wmv|ts|m2ts|m3u8|rmvb|webm)$");
    private static final Pattern SIZE_SUFFIX = Pattern.compile("(?i)\\s*[\\[\\(（【]?\\s*\\d+(?:\\.\\d+)?\\s*(?:GB|G|MB|M)\\s*[\\]\\)）】]?\\s*$");
    private static final Pattern HASH_SUFFIX = Pattern.compile("(?i)\\s*[\\[\\(（【]\\s*[A-F0-9]{8,32}\\s*[\\]\\)）】]\\s*$");
    private static final Pattern EPISODE_START = Pattern.compile("(?i)^(?:第\\s*[0-9一二三四五六七八九十百]+\\s*(?:集|话|話|期|章|回)|[0-9]{1,4}\\s*(?:集|话|話|期|章|回)|(?:EP|E)\\s*[0-9]{1,4}|S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}|[0-9]{1,4}(?:\\D|$)|[上下](?:集|部)?|前篇|后篇|後篇|正片|预告|預告|花絮)");
    private static final Pattern EPISODE_TOKEN = Pattern.compile("(?i)(S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}|第\\s*[0-9一二三四五六七八九十百]+\\s*(?:集|话|話|期|章|回)|[0-9]{1,4}\\s*(?:集|话|話|期|章|回)|(?:EP|E)\\s*[0-9]{1,4}|[上下](?:集|部)?|前篇|后篇|後篇|正片|预告|預告|花絮)");
    private static final Pattern TECH_SUFFIX = Pattern.compile("(?i)^[\\s._\\-\\[\\]()（）【】]+(?:4K|8K|2160P|1080P|720P|HDR|HDR10|DV|DOLBY|HEVC|H265|H\\.265|H264|H\\.264|AV1|AAC|FLAC|WEB-DL|WEBRIP|BLURAY|BD|HD|国语|国配|粤语|中字|中英双字|简中|繁中|内嵌字幕|无字)(?:[\\s._\\-\\[\\]()（）【】]+(?:4K|8K|2160P|1080P|720P|HDR|HDR10|DV|DOLBY|HEVC|H265|H\\.265|H264|H\\.264|AV1|AAC|FLAC|WEB-DL|WEBRIP|BLURAY|BD|HD|国语|国配|粤语|中字|中英双字|简中|繁中|内嵌字幕|无字))*[\\s._\\-\\[\\]()（）【】]*$");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》]+|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》]+$");
    private static final int MAX_COMPACT_LENGTH = 14;

    private EpisodeTitleCompact() {
    }

    public static void apply(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return;
        if (!Setting.isCompactEpisodeTitle()) {
            for (Episode episode : episodes) episode.setDisplayName(null);
            return;
        }
        List<String> names = new ArrayList<>();
        for (Episode episode : episodes) names.add(cleanFileNoise(episode.getRawDisplayName()));
        if (episodes.size() < 2) {
            for (int i = 0; i < episodes.size(); i++) episodes.get(i).setDisplayName(names.get(i));
            return;
        }
        int prefix = findPrefix(names);
        int suffix = findSuffix(names, prefix);
        List<String> compacted = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        Map<String, Integer> count = new HashMap<>();
        for (String name : names) {
            String compact = cleanupEdge(name.substring(Math.min(prefix, name.length()), Math.max(Math.min(name.length() - suffix, name.length()), Math.min(prefix, name.length()))));
            if (TextUtils.isEmpty(compact)) compact = name;
            String display = preferEpisodeToken(name, compact);
            compacted.add(display);
            fallback.add(compact);
            count.put(display, count.getOrDefault(display, 0) + 1);
        }
        for (int i = 0; i < episodes.size(); i++) {
            String compact = compacted.get(i);
            episodes.get(i).setDisplayName(count.get(compact) > 1 ? fallback.get(i) : compact);
        }
    }

    private static String cleanFileNoise(String value) {
        String text = TextUtils.isEmpty(value) ? "" : value.trim();
        text = EXTENSION.matcher(text).replaceFirst("");
        text = HASH_SUFFIX.matcher(text).replaceFirst("");
        text = SIZE_SUFFIX.matcher(text).replaceFirst("");
        return cleanupEdge(text);
    }

    private static int findPrefix(List<String> names) {
        int prefix = commonPrefix(names);
        for (int i = prefix; i > 0; i--) {
            if (isUsefulPrefix(names, i)) return i;
        }
        return 0;
    }

    private static int commonPrefix(List<String> names) {
        int prefix = names.get(0).length();
        for (String name : names) {
            prefix = Math.min(prefix, name.length());
            for (int i = 0; i < prefix; i++) {
                if (names.get(0).charAt(i) != name.charAt(i)) {
                    prefix = i;
                    break;
                }
            }
        }
        return prefix;
    }

    private static boolean isUsefulPrefix(List<String> names, int index) {
        if (index < 2) return false;
        for (String name : names) {
            if (index > name.length()) return false;
            String rest = cleanupEdge(name.substring(index));
            if (TextUtils.isEmpty(rest)) return false;
            if (!isBoundary(name, index) && !canStartEpisodeAfterPrefix(name, index, rest)) return false;
        }
        return index >= 6 || allRestStartsEpisode(names, index);
    }

    private static boolean allRestStartsEpisode(List<String> names, int index) {
        for (String name : names) {
            if (!startsEpisode(cleanupEdge(name.substring(index)))) return false;
        }
        return true;
    }

    private static int findSuffix(List<String> names, int prefix) {
        int suffix = commonSuffix(names, prefix);
        for (int i = suffix; i > 0; i--) {
            if (isUsefulSuffix(names, i, prefix)) return i;
        }
        return 0;
    }

    private static int commonSuffix(List<String> names, int prefix) {
        int suffix = names.get(0).length() - Math.min(prefix, names.get(0).length());
        for (String name : names) {
            int max = name.length() - Math.min(prefix, name.length());
            suffix = Math.min(suffix, max);
            for (int i = 0; i < suffix; i++) {
                if (names.get(0).charAt(names.get(0).length() - 1 - i) != name.charAt(name.length() - 1 - i)) {
                    suffix = i;
                    break;
                }
            }
        }
        return suffix;
    }

    private static boolean isUsefulSuffix(List<String> names, int suffix, int prefix) {
        if (suffix < 2) return false;
        for (String name : names) {
            int start = name.length() - suffix;
            if (start <= prefix || !isBoundary(name, start)) return false;
            if (TextUtils.isEmpty(cleanupEdge(name.substring(prefix, start)))) return false;
        }
        String removed = names.get(0).substring(names.get(0).length() - suffix);
        return suffix >= 6 || TECH_SUFFIX.matcher(removed.toUpperCase(Locale.ROOT)).matches();
    }

    private static boolean isBoundary(String text, int index) {
        if (index <= 0 || index >= text.length()) return true;
        return isSeparator(text.charAt(index - 1)) || isSeparator(text.charAt(index));
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || "-_.·|/\\:：,，;；[]()（）【】《》".indexOf(c) >= 0;
    }

    private static boolean startsEpisode(String text) {
        return EPISODE_START.matcher(text).find();
    }

    private static boolean canStartEpisodeAfterPrefix(String text, int index, String rest) {
        if (!startsEpisode(rest)) return false;
        char previous = text.charAt(index - 1);
        return !Character.isDigit(previous) && !isAsciiLetter(previous);
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static String preferEpisodeToken(String name, String compact) {
        if (compact.length() <= MAX_COMPACT_LENGTH) return compact;
        String token = findEpisodeToken(name);
        return TextUtils.isEmpty(token) ? compact : token;
    }

    private static String findEpisodeToken(String text) {
        Matcher matcher = EPISODE_TOKEN.matcher(text);
        return matcher.find() ? normalizeEpisodeToken(matcher.group()) : "";
    }

    private static String normalizeEpisodeToken(String token) {
        return token == null ? "" : token.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String cleanupEdge(String text) {
        return EDGE_SEPARATORS.matcher(text == null ? "" : text.trim()).replaceAll("");
    }
}
