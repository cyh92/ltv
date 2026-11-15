package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScriptManager {

    private static final String KEY = "script_folders";
    private static final String KEY_FILES = "script_files";

    private static class Loader {
        static final ScriptManager INSTANCE = new ScriptManager();
    }

    private ScriptManager() {
    }

    public static ScriptManager get() {
        return Loader.INSTANCE;
    }

    public List<String> getFolders() {
        try {
            List<String> folders = App.gson().fromJson(Prefers.getString(KEY), new TypeToken<List<String>>() {
            }.getType());
            return folders == null ? new ArrayList<>() : folders;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean addFolder(String path) {
        List<String> folders = getFolders();
        if (folders.contains(path)) return false;
        folders.add(path);
        save(folders);
        return true;
    }

    public void removeFolder(String path) {
        List<String> folders = getFolders();
        if (folders.remove(path)) save(folders);
    }

    private void save(List<String> folders) {
        Prefers.put(KEY, App.gson().toJson(folders));
    }

    /** 单独导入的 .py/.js 文件（不在文件夹里的单个源）。 */
    public List<String> getFiles() {
        try {
            List<String> files = App.gson().fromJson(Prefers.getString(KEY_FILES), new TypeToken<List<String>>() {
            }.getType());
            return files == null ? new ArrayList<>() : files;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean addFile(String path) {
        List<String> files = getFiles();
        if (files.contains(path)) return false;
        files.add(path);
        saveFiles(files);
        return true;
    }

    public void removeFile(String path) {
        List<String> files = getFiles();
        if (files.remove(path)) saveFiles(files);
    }

    private void saveFiles(List<String> files) {
        Prefers.put(KEY_FILES, App.gson().toJson(files));
    }

    public List<File> scanFiles() {
        List<File> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String folder : getFolders()) {
            File dir = new File(folder);
            File[] children = dir.isDirectory() ? dir.listFiles() : null;
            if (children == null) continue;
            for (File file : children) {
                if (!file.isFile()) continue;
                String lower = file.getName().toLowerCase();
                if ((lower.endsWith(".py") || lower.endsWith(".js")) && seen.add(file.getAbsolutePath())) files.add(file);
            }
        }
        for (String path : getFiles()) {
            File file = new File(path);
            if (!file.isFile()) continue;
            String lower = file.getName().toLowerCase();
            if ((lower.endsWith(".py") || lower.endsWith(".js")) && seen.add(file.getAbsolutePath())) files.add(file);
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files;
    }

    public List<Site> scan() {
        List<Site> sites = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (File file : scanFiles()) {
            String name = file.getName();
            String base = name.substring(0, name.lastIndexOf('.'));
            String key = "script_" + base;
            if (!keys.add(key)) continue;
            JsonObject object = new JsonObject();
            object.addProperty("key", key);
            object.addProperty("name", base + (name.toLowerCase().endsWith(".py") ? "[py]" : "[js]"));
            object.addProperty("type", 3);
            object.addProperty("api", "file://" + file.getAbsolutePath());
            sites.add(Site.objectFrom(object, ""));
        }
        return sites;
    }
}
