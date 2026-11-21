package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Parse;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class ParseStore {

    private static final String KEY = "parse_user";

    public static List<Parse> getUserParses() {
        try {
            String json = Prefers.getString(KEY);
            if (json == null || json.isEmpty()) return new ArrayList<>();
            List<Parse> items = App.gson().fromJson(json, new TypeToken<List<Parse>>() {}.getType());
            return items == null ? new ArrayList<>() : items;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveUserParses(List<Parse> items) {
        Prefers.put(KEY, App.gson().toJson(items));
    }

    public static void add(Parse parse) {
        List<Parse> items = getUserParses();
        items.remove(parse);
        items.add(parse);
        saveUserParses(items);
    }

    public static void remove(Parse parse) {
        List<Parse> items = getUserParses();
        items.remove(parse);
        saveUserParses(items);
    }
}
