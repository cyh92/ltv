package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 配置管理：订阅列表（文件夹 / 单个源 / 在线接口 / 本地包 / 直播源）。
 * 订阅只存在本地（Prefers "config_subs"），不依赖任何内置源。
 */
public class ConfigManager {

    public static final int TAB_VOD = 0;
    public static final int TAB_LIVE = 1;

    public static final String KIND_FOLDER = "folder";
    public static final String KIND_FILE = "file";
    public static final String KIND_URL = "url";
    public static final String KIND_JSON = "json";
    public static final String KIND_LIVE_URL = "live_url";
    public static final String KIND_LIVE_FILE = "live_file";

    private static final String KEY_SUBS = "config_subs";
    private static final String KEY_ACTIVE = "config_active_iface";

    public static class Sub {
        public String id;
        public int tab;
        public String kind;
        public String name;
        public String link;
        public boolean enabled;

        public boolean isFolder() {
            return KIND_FOLDER.equals(kind);
        }

        public boolean isFile() {
            return KIND_FILE.equals(kind);
        }

        public boolean isUrl() {
            return KIND_URL.equals(kind);
        }

        public boolean isJson() {
            return KIND_JSON.equals(kind);
        }

        public boolean isInterface() {
            return isUrl() || isJson();
        }

        public boolean isLiveUrl() {
            return KIND_LIVE_URL.equals(kind);
        }

        public boolean isLiveFile() {
            return KIND_LIVE_FILE.equals(kind);
        }

        /** 加载这个订阅要用的配置地址（在线链接原样，本地文件拼 file://）。 */
        public String getConfigUrl() {
            if (isUrl() || isLiveUrl()) return link;
            return "file://" + link;
        }

        /** 列表里显示的链接（文件夹显示成 folderpack://…，和其他的一样短）。 */
        public String getDisplayLink() {
            if (isFolder()) return "folderpack://" + Crypto.md5(link).substring(0, 12);
            return link;
        }
    }

    public static List<Sub> getAll() {
        try {
            List<Sub> subs = App.gson().fromJson(Prefers.getString(KEY_SUBS), new TypeToken<List<Sub>>() {
            }.getType());
            return subs == null ? new ArrayList<>() : subs;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Sub> getSubs(int tab) {
        List<Sub> result = new ArrayList<>();
        for (Sub sub : getAll()) if (sub.tab == tab) result.add(sub);
        return result;
    }

    public static Sub find(String id) {
        if (id == null) return null;
        for (Sub sub : getAll()) if (id.equals(sub.id)) return sub;
        return null;
    }

    public static Sub add(int tab, String kind, String name, String link) {
        List<Sub> subs = getAll();
        Sub sub = new Sub();
        sub.id = UUID.randomUUID().toString();
        sub.tab = tab;
        sub.kind = kind;
        sub.name = name;
        sub.link = link;
        sub.enabled = true;
        subs.add(sub);
        save(subs);
        if (sub.isFolder()) ScriptManager.get().addFolder(link);
        if (sub.isFile()) ScriptManager.get().addFile(link);
        return sub;
    }

    public static void update(Sub sub) {
        List<Sub> subs = getAll();
        for (int i = 0; i < subs.size(); i++) if (subs.get(i).id.equals(sub.id)) subs.set(i, sub);
        save(subs);
    }

    /** 改本地订阅的地址：同时把 ScriptManager 里登记的旧地址换成新地址，不然源还从旧地址读。 */
    public static void updateLink(Sub sub, String oldLink) {
        update(sub);
        if (TextUtils.equals(oldLink, sub.link)) return;
        if (sub.isFolder()) {
            ScriptManager.get().removeFolder(oldLink);
            ScriptManager.get().addFolder(sub.link);
        } else if (sub.isFile()) {
            ScriptManager.get().removeFile(oldLink);
            ScriptManager.get().addFile(sub.link);
        }
    }

    public static void remove(Sub sub) {
        List<Sub> subs = getAll();
        subs.removeIf(item -> item.id.equals(sub.id));
        save(subs);
        if (sub.isFolder()) ScriptManager.get().removeFolder(sub.link);
        if (sub.isFile()) ScriptManager.get().removeFile(sub.link);
        if (sub.id.equals(getActiveId())) setActiveId("");
    }

    private static void save(List<Sub> subs) {
        Prefers.put(KEY_SUBS, App.gson().toJson(subs));
    }

    public static String getActiveId() {
        return Prefers.getString(KEY_ACTIVE, "");
    }

    public static void setActiveId(String id) {
        Prefers.put(KEY_ACTIVE, id == null ? "" : id);
    }

    public static Sub getActiveSub() {
        return find(getActiveId());
    }

    /** 这个脚本源是不是被关掉的订阅里的（文件夹或单文件被关掉时，它的源不显示）。 */
    public static boolean isScriptDisabled(Site site) {
        String path = SiteOrder.getLocalPath(site);
        if (path == null) return false;
        List<Sub> matches = new ArrayList<>();
        for (Sub sub : getSubs(TAB_VOD)) {
            if (sub.isFile() && sub.link.equals(path)) matches.add(sub);
            else if (sub.isFolder() && path.startsWith(sub.link + "/")) matches.add(sub);
        }
        if (matches.isEmpty()) return false;
        for (Sub sub : matches) if (sub.enabled) return false;
        return true;
    }

    /** 当前接口下，这个源要不要显示（""=全部接口，都显示）。 */
    public static boolean isSiteVisible(Site site) {
        Sub active = getActiveSub();
        if (active == null || active.tab != TAB_VOD) return true;
        String path = SiteOrder.getLocalPath(site);
        if (active.isFolder()) return path != null && path.startsWith(active.link + "/");
        if (active.isFile()) return path != null && path.equals(active.link);
        if (active.isInterface()) return path == null;
        return true;
    }

    /** 当前接口的名字（主页上显示），没有选就是“全部接口”。 */
    public static String getActiveName() {
        Sub active = getActiveSub();
        return active == null ? null : active.name;
    }

    /** 打开一个点播订阅：在线接口/本地包会加载成当前配置，文件夹/单文件会切成当前接口；开关互不影响，多个接口可以同时开着。 */
    public static void activateVod(Sub sub, Callback callback) {
        sub.enabled = true;
        update(sub);
        setActiveId(sub.id);
        if (sub.isInterface()) VodConfig.load(Config.find(sub.getConfigUrl(), 0), callback);
        else reloadVod(callback);
    }

    /** 关掉一个点播订阅：是在用的，就切到另一个还开着的接口；没有就回到只有本地源。 */
    public static void deactivateVod(Sub sub, Callback callback) {
        sub.enabled = false;
        update(sub);
        boolean wasActive = sub.id.equals(getActiveId());
        if (wasActive) {
            for (Sub item : getSubs(TAB_VOD)) {
                if (item.isInterface() && item.enabled) {
                    setActiveId(item.id);
                    VodConfig.load(Config.find(item.getConfigUrl(), 0), callback);
                    return;
                }
            }
            setActiveId("");
        }
        if (sub.isInterface() && wasActive) VodConfig.load(Config.vod(), callback);
        else reloadVod(callback);
    }

    /** 打开一个直播源；开关互不影响，多个可以同时开着（最后打开的是当前用的）。 */
    public static void activateLive(Sub sub, Callback callback) {
        sub.enabled = true;
        update(sub);
        LiveConfig.load(Config.find(sub.getConfigUrl(), 1), callback);
    }

    /** 关掉一个直播源：还有开着的就切过去，没有就回到默认直播配置。 */
    public static void deactivateLive(Sub sub, Callback callback) {
        sub.enabled = false;
        update(sub);
        for (Sub item : getSubs(TAB_LIVE)) {
            if (item.enabled) {
                LiveConfig.load(Config.find(item.getConfigUrl(), 1), callback);
                return;
            }
        }
        LiveConfig.load(Config.live(), callback);
    }

    /** 用当前配置重新加载一次（接口切换、开关文件夹后刷新主页）。 */
    public static void reloadVod(Callback callback) {
        VodConfig.load(VodConfig.get().getConfig(), callback);
    }
}
