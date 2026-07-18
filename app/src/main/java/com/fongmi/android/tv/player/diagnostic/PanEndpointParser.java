package com.fongmi.android.tv.player.diagnostic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PanEndpointParser {

    private static final int MAX_DECODE_PASSES = 3;

    private PanEndpointParser() {
    }

    public static PanEndpoint parse(String playbackUrl, Map<String, String> playbackHeaders) {
        if (!isHttp(playbackUrl)) throw new IllegalArgumentException("HTTP playback URL required");
        Map<String, String> query = query(playbackUrl);
        String upstreamUrl = decode(query.get("url"));
        Map<String, String> upstreamHeaders = parseHeaders(decode(query.get("header")));
        if (upstreamHeaders.isEmpty() && playbackHeaders != null) upstreamHeaders.putAll(playbackHeaders);
        if (!isRemoteHttp(upstreamUrl)) upstreamUrl = "";
        int configuredThreads = parseThreads(query.get("thread"));
        PanProvider provider = PanProvider.fromHost(host(upstreamUrl));
        return new PanEndpoint(playbackUrl, upstreamUrl, upstreamHeaders, provider, configuredThreads);
    }

    private static Map<String, String> query(String url) {
        String raw = URI.create(url).getRawQuery();
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            int split = pair.indexOf('=');
            String key = decode(split < 0 ? pair : pair.substring(0, split));
            String value = split < 0 ? "" : pair.substring(split + 1);
            if (!values.containsKey(key)) values.put(key, value);
        }
        return values;
    }

    private static Map<String, String> parseHeaders(String value) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) return headers;
        try {
            JsonElement element = JsonParser.parseString(value);
            if (!element.isJsonObject()) return headers;
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                headers.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (RuntimeException ignored) {
        }
        return headers;
    }

    private static int parseThreads(String value) {
        try {
            return PanBenchmarkPlan.normalizeThreads(Integer.parseInt(decode(value)));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) return "";
        String current = value;
        for (int i = 0; i < MAX_DECODE_PASSES; i++) {
            String decoded;
            try {
                decoded = URLDecoder.decode(current, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return current;
            }
            if (decoded.equals(current)) break;
            current = decoded;
        }
        return current;
    }

    private static boolean isHttp(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            String scheme = URI.create(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isRemoteHttp(String value) {
        if (!isHttp(value)) return false;
        String host = host(value);
        return !(host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1"));
    }

    private static String host(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            String host = URI.create(value).getHost();
            return host == null ? "" : host;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
