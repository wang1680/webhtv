package com.fongmi.android.tv.remote;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class RemoteConfigOps {

    private RemoteConfigOps() {
    }

    public static RemoteCommandResult list() {
        return RemoteCommandResult.success("", data());
    }

    public static RemoteCommandResult upsert(JsonObject payload) {
        int type = number(payload, "type", 0);
        String url = string(payload, "url");
        String name = string(payload, "name");
        if (TextUtils.isEmpty(url)) return RemoteCommandResult.failure("Missing config url");
        Config.find(url, type).name(name).save();
        return RemoteCommandResult.success("Config saved", data());
    }

    public static RemoteCommandResult use(JsonObject payload) {
        int type = number(payload, "type", 0);
        String url = string(payload, "url");
        if (TextUtils.isEmpty(url)) return RemoteCommandResult.failure("Missing config url");
        Config config = Config.find(url, type);
        if (config.isEmpty()) return RemoteCommandResult.failure("Config not found");
        App.post(() -> {
            if (type == 1) LiveConfig.load(config, new Callback());
            else if (type == 2) WallConfig.load(config, new Callback());
            else VodConfig.load(config, new Callback());
        });
        return RemoteCommandResult.success("Config switched", data());
    }

    public static RemoteCommandResult delete(JsonObject payload) {
        int type = number(payload, "type", 0);
        String url = string(payload, "url");
        if (TextUtils.isEmpty(url)) return RemoteCommandResult.failure("Missing config url");
        Config.find(url, type).delete();
        return RemoteCommandResult.success("Config deleted", data());
    }

    private static JsonObject data() {
        JsonObject object = new JsonObject();
        JsonArray items = new JsonArray();
        for (int type = 0; type <= 2; type++) {
            for (Config config : Config.getAll(type)) items.add(item(config, false));
            Config current = current(type);
            if (!current.isEmpty() && !contains(items, current)) items.add(item(current, true));
        }
        object.add("items", items);
        return object;
    }

    private static JsonObject item(Config config, boolean forceActive) {
        JsonObject item = new JsonObject();
        item.addProperty("type", config.getType());
        item.addProperty("typeName", typeName(config.getType()));
        item.addProperty("name", config.getName());
        item.addProperty("url", config.getUrl());
        item.addProperty("desc", config.getDesc());
        item.addProperty("time", config.getTime());
        item.addProperty("active", forceActive || isCurrent(config));
        return item;
    }

    private static boolean contains(JsonArray items, Config config) {
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            if (item.get("type").getAsInt() == config.getType() && TextUtils.equals(item.get("url").getAsString(), config.getUrl())) return true;
        }
        return false;
    }

    private static boolean isCurrent(Config config) {
        return TextUtils.equals(current(config.getType()).getUrl(), config.getUrl());
    }

    private static Config current(int type) {
        if (type == 1) return LiveConfig.get().getConfig();
        if (type == 2) return WallConfig.get().getConfig();
        return VodConfig.get().getConfig();
    }

    private static String typeName(int type) {
        if (type == 1) return "直播";
        if (type == 2) return "壁纸";
        return "点播";
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        return object.get(key).getAsString().trim();
    }

    private static int number(JsonObject object, String key, int fallback) {
        try {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
            return object.get(key).getAsInt();
        } catch (Throwable e) {
            return fallback;
        }
    }
}
