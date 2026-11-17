package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.util.HashMap;
import java.util.Map;

/**
 * 检测成绩的存取（Prefers 存 JSON 表，下次打开换源列表还能画色点）。
 * 站点成绩按站点 key 存：脚本源是 script_ + 文件名（和 ScriptManager.scan() 一致），
 * 配置里的站是 subId + "|" + 站点 key；接口成绩按 sub id 存。
 * 没成绩返回 GRADE_NONE（不画点）。
 */
public class CheckStore {

    private static final String KEY_SITE = "check_site_grades";
    private static final String KEY_SUB = "check_sub_grades";

    public static int getSiteGrade(String key) {
        if (key == null || key.isEmpty()) return SourceChecker.GRADE_NONE;
        Integer grade = readSite().get(key);
        return grade == null ? SourceChecker.GRADE_NONE : grade;
    }

    public static int getSubGrade(String subId) {
        if (subId == null || subId.isEmpty()) return SourceChecker.GRADE_NONE;
        Integer grade = readSub().get(subId);
        return grade == null ? SourceChecker.GRADE_NONE : grade;
    }

    public static void putSiteGrades(Map<String, Integer> grades) {
        if (grades == null || grades.isEmpty()) return;
        Map<String, Integer> all = readSite();
        all.putAll(grades);
        Prefers.put(KEY_SITE, App.gson().toJson(all));
    }

    public static void putSubGrade(String subId, int grade) {
        if (subId == null || subId.isEmpty()) return;
        Map<String, Integer> all = readSub();
        all.put(subId, grade);
        Prefers.put(KEY_SUB, App.gson().toJson(all));
    }

    public static void clear() {
        Prefers.put(KEY_SITE, "{}");
        Prefers.put(KEY_SUB, "{}");
    }

    private static Map<String, Integer> readSite() {
        return read(KEY_SITE);
    }

    private static Map<String, Integer> readSub() {
        return read(KEY_SUB);
    }

    private static Map<String, Integer> read(String key) {
        try {
            Map<String, Integer> map = App.gson().fromJson(Prefers.getString(key), new TypeToken<Map<String, Integer>>() {
            }.getType());
            return map == null ? new HashMap<>() : map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
