package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.node.NodeRuntime;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.ParseStore;
import com.fongmi.android.tv.utils.ScriptManager;
import com.fongmi.android.tv.utils.SiteOrder;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.runtime.NodeRuntimeApi;
import com.github.catvod.utils.Decoder;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;
    private List<Parse> configParses;

    /**
     * 0.1.97：切换接口时把首页重置到新接口自己的第一个源（不是存着的旧首页）。
     * <p>
     * 根因（“切换后还是显示原来的接口”）：首页内容由首页源（home）决定，而 home 是按
     * 接口存着的（Config.getHome()）。用户常把本地 py 源（比如 4K河豚[py]）当首页源，
     * 这个 py 源在所有接口下都可见（0.1.91 起本地脚本源永不隐藏），所以切到 RRZY/
     * 影视py 后首页源还是同一个 py 源，首页内容一点没变——顶栏字变了，内容还是原来的。
     * 只有在真正“切换接口”（activateVod 切到另一个订阅）时才置位，加载完成后消费掉；
     * 平时重载/重启不置位，存着的首页源照常用，不打扰用户。
     */
    private boolean resetHome;

    /** 切换接口时调用：下一次加载完成后把首页重置到新接口自己的第一个源。 */
    public void setResetHome(boolean resetHome) {
        this.resetHome = resetHome;
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    /**
     * 0.1.103：加载失败不再清空能用的配置。之前这里先 clear 再加载，新配置加载失败时
     * app 剩个空壳（源列表被清掉），只能重启。现在加载前先快照当前能用的状态，失败时
     * 恢复快照，app 留在旧接口上，首页照常显示，不用重启。
     */
    public static void load(Config config, Callback callback) {
        VodConfig vod = get();
        List<Site> oldSites = vod.sites;
        Site oldHome = vod.home;
        Parse oldParse = vod.parse;
        List<Parse> oldParses = vod.parses;
        List<Parse> oldConfigParses = vod.configParses;
        String oldWall = vod.wall;
        List<Doh> oldDoh = vod.doh;
        List<Rule> oldRules = vod.rules;
        List<String> oldAds = vod.ads;
        List<String> oldFlags = vod.flags;
        Config oldConfig = vod.getConfig();
        vod.clear().config(config).load(new Callback() {
            @Override
            public void error(String msg) {
                vod.sites = oldSites;
                vod.home = oldHome;
                vod.parse = oldParse;
                vod.parses = oldParses;
                vod.configParses = oldConfigParses;
                vod.wall = oldWall;
                vod.doh = oldDoh;
                vod.rules = oldRules;
                vod.ads = oldAds;
                vod.flags = oldFlags;
                vod.config(oldConfig);
                callback.error(msg);
            }

            @Override
            public void error() {
                error("");
            }

            @Override
            public void success() {
                callback.success();
            }

            @Override
            public void success(String result) {
                callback.success(result);
            }

            @Override
            public void start() {
                callback.start();
            }
        });
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        return clear(false);
    }

    public void destroy() {
        clear(true);
    }

    private VodConfig clear(boolean exiting) {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        configParses = null;
        if (exiting) BaseLoader.get().destroy();
        else BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void cancelLoad() {
        NodeRuntime.get().cancelLoad();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        if (config.isEmpty()) {
            parseConfig(config, new JsonObject());
            return;
        }
        boolean node = NodeRuntime.isConfig(config.getUrl());
        NodeRuntime runtime = NodeRuntime.get();
        if (!node) runtime.clear();
        NodeRuntimeApi.LoadedConfig loaded = null;
        try {
            if (node) loaded = runtime.loadConfig(config.getUrl());
            String json = node ? loaded.json() : isLocalFile(config.getUrl()) ? readLocalText(config.getUrl()) : Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
            runtime.prune(loaded, Config.getAll(VOD).stream().map(Config::getUrl).toList(), config.getUrl());
            checkJson(config, Json.parse(json).getAsJsonObject(), loaded);
        } catch (Throwable e) {
            if (node) runtime.fail(loaded);
            throw e;
        }
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    private void checkJson(Config config, JsonObject object, NodeRuntimeApi.LoadedConfig loaded) throws Throwable {
        if (object.has("msg")) throw new Exception(object.get("msg").getAsString());
        if (object.has("urls")) {
            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            if (items.isEmpty()) throw new Exception("Depot urls is empty");
            if (loaded != null) NodeRuntime.get().accept(loaded);
            parseDepot(config, items);
            return;
        }
        parseConfig(config, object);
        if (loaded != null) NodeRuntime.get().accept(loaded);
    }

    private void parseDepot(Config config, List<Depot> items) throws Throwable {
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initSite(config, object);
        initParse(config, object);
        config.setLogo(Json.safeString(object, "logo"));
        config.setAssrt(Json.safeString(object, "assrt"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object) {
        String spider = UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(config.getUrl(), Json.safeString(object, "spider")));
        BaseLoader.get().parseJar(spider, true);
        setSites(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        for (Site site : getSites()) {
            site.setApi(UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(config.getUrl(), site.getApi())));
            site.setExt(UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(config.getUrl(), site.getExt())));
        }
        Set<String> keys = getSites().stream().map(Site::getKey).collect(Collectors.toSet());
        ScriptManager.get().scan().stream().filter(site -> keys.add(site.getKey())).forEach(site -> getSites().add(site));     
        getSites().removeIf(site -> SiteOrder.isRemoved(site.getKey()) || ConfigManager.isScriptDisabled(site) || !ConfigManager.isSiteVisible(site));
        getSites().forEach(site -> { String name = SiteOrder.getName(site.getKey()); if (name != null) site.setName(name); });
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        getSites().forEach(site -> site.sync(items.get(site.getKey())));
        setHome(config, selectHomeSite(config), false);
    }

    /**
     * 0.1.97：选首页源。平时（重载/重启）按存着的首页源恢复；切换接口时（resetHome），
     * 忽略存着的首页源（它常是跨接口共享的本地 py 源，恢复它等于没切），改用新接口
     * 自己的第一个源——接口自己的源排在前（config 的 sites 在先、脚本源后加），
     * 所以第一个“非本地脚本”源就是该接口自己的源；文件夹接口下过滤后只剩文件夹
     * 自己的源，直接取第一个。resetHome 一次性，选完就清掉。
     */
    private Site selectHomeSite(Config config) {
        if (getSites().isEmpty()) return new Site();
        if (!resetHome) {
            return getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().get(0));
        }
        resetHome = false;
        for (Site site : getSites()) if (SiteOrder.getLocalPath(site) == null) return site;
        return getSites().get(0);
    }

    private void initParse(Config config, JsonObject object) {
        configParses = Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new));
        rebuildParses();
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    private void rebuildParses() {
        List<Parse> items = new ArrayList<>(configParses == null ? Collections.emptyList() : configParses);
        for (Parse item : ParseStore.getUserParses()) if (!items.contains(item)) items.add(item);
        // 0.1.104：接口没给 parses、用户也没加过时，补 4 条自带解析线兜底，
        // 新装的烛龙也能解析；给了就不用，不抢用户自己的配置。
        if (items.isEmpty()) items.addAll(Parse.builtin());
        setParses(items);
    }

    public void updateParses() {
        rebuildParses();
        if (getConfig() == null) return;
        Parse current = getParse();
        Parse selected = getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(current.getName())).findFirst().orElse(getParses().get(0));
        setParse(getConfig(), selected, !selected.getName().equals(current.getName()));
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
