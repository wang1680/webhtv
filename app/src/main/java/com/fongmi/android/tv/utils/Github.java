package com.fongmi.android.tv.utils;

public class Github {

    private static final String GITHUB = "https://github.com/Silent1566/webhtv/releases/latest/download";

    private static String getUrl(String name) {
        return GITHUB + "/" + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
