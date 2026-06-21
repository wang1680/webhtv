package com.fongmi.android.tv.player.lut;

import android.text.TextUtils;

import com.fongmi.android.tv.App;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LutStore {

    private static final String ASSET_DIR = "lut_presets";
    private static final String USER_DIR = "lut_presets";
    private static final Object LOCK = new Object();
    private static List<LutPreset> presets;

    public static List<LutPreset> getPresets() {
        synchronized (LOCK) {
            if (presets == null) presets = scanAssets();
            return new ArrayList<>(presets);
        }
    }

    public static LutPreset find(String id) {
        if (TextUtils.isEmpty(id)) return null;
        for (LutPreset preset : getPresets()) if (id.equals(preset.getId())) return preset;
        return null;
    }

    public static LutPreset getSelectedPreset() {
        return LutSetting.isEnabled() ? find(LutSetting.getPresetId()) : null;
    }

    public static String getSelectedName() {
        LutPreset preset = getSelectedPreset();
        return preset == null ? "" : preset.getName();
    }

    public static String getSelectedShortName() {
        LutPreset preset = getSelectedPreset();
        return preset == null ? "" : preset.getShortName();
    }

    public static void clearCache() {
        synchronized (LOCK) {
            presets = null;
        }
    }

    private static List<LutPreset> scanAssets() {
        List<LutPreset> items = new ArrayList<>();
        scanAssets(ASSET_DIR, items);
        scanFiles(getUserDir(), items);
        Collections.sort(items, Comparator.comparing(LutPreset::getName, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private static void scanAssets(String dir, List<LutPreset> items) {
        String[] children;
        try {
            children = App.get().getAssets().list(dir);
        } catch (IOException e) {
            return;
        }
        if (children == null) return;
        for (String child : children) {
            String path = dir + "/" + child;
            LutPreset.Format format = LutPreset.formatOf(child);
            if (format != null) {
                items.add(new LutPreset("asset:" + path, displayName(child), path, format, true));
            } else {
                scanAssets(path, items);
            }
        }
    }

    private static void scanFiles(File dir, List<LutPreset> items) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanFiles(file, items);
                continue;
            }
            LutPreset.Format format = LutPreset.formatOf(file.getName());
            if (format == null) continue;
            items.add(new LutPreset("file:" + file.getAbsolutePath(), displayName(file.getName()), file.getAbsolutePath(), format, false));
        }
    }

    public static LutPreset importFile(String path) throws IOException {
        if (TextUtils.isEmpty(path)) throw new IOException("Empty LUT path");
        File source = new File(path);
        LutPreset.Format format = LutPreset.formatOf(source.getName());
        if (format == null) throw new IOException("Unsupported LUT format");
        File target = uniqueFile(source.getName());
        try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        LutPreset preset = new LutPreset("file:" + target.getAbsolutePath(), displayName(target.getName()), target.getAbsolutePath(), format, false);
        try {
            LutEffectFactory.validate(preset);
        } catch (IOException e) {
            target.delete();
            throw e;
        }
        clearCache();
        return preset;
    }

    private static File uniqueFile(String name) throws IOException {
        File dir = getUserDir();
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Unable to create LUT directory");
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        File file = new File(dir, safe);
        int index = 1;
        while (file.exists()) file = new File(dir, base + "_" + index++ + ext);
        return file;
    }

    private static File getUserDir() {
        return new File(App.get().getFilesDir(), USER_DIR);
    }

    private static String displayName(String file) {
        int dot = file.lastIndexOf('.');
        String name = dot > 0 ? file.substring(0, dot) : file;
        name = name.replace('_', ' ').replace('-', ' ').trim();
        if (TextUtils.isEmpty(name)) return file;
        String[] words = name.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (TextUtils.isEmpty(word)) continue;
            if (builder.length() > 0) builder.append(' ');
            if (word.length() == 1) builder.append(word.toUpperCase(Locale.ROOT));
            else builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.length() == 0 ? file : builder.toString();
    }
}
