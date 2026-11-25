package com.fongmi.android.tv.api.loader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 0.1.104：网页嗅探的按站点规则。有些大站的真实播放地址只有在网页里才会出现，
 * 而且地址有本站特有的特征参数（比如抖音的 is_play_url、火山的 item_id、
 * 头条的 video/tos/cn），通用正则认不出来；另外这些站的播放按钮也要按本站的
 * 选择器去点。这里只写“按什么特征认、点哪个按钮”，不复制任何站点的代码或文案。
 */
public final class WebSniffRule {

    private final String host;
    private final Pattern urlPattern;
    private final List<String> clickSelectors;

    private WebSniffRule(String host, String urlRegex, List<String> clickSelectors) {
        this.host = host;
        this.urlPattern = Pattern.compile(urlRegex);
        this.clickSelectors = clickSelectors;
    }

    public String getHost() {
        return host;
    }

    public boolean matchesUrl(String url) {
        return url != null && urlPattern.matcher(url).find();
    }

    public List<String> getClickSelectors() {
        return clickSelectors;
    }

    private static final List<WebSniffRule> RULES = new ArrayList<>();

    static {
        // 抖音：播放地址带 is_play_url 参数；播放按钮是 xgplayer 或 video.js 的大播放键。
        RULES.add(new WebSniffRule("douyin.com", "is_play_url=", List.of(".xgplayer-start", ".vjs-big-play-button")));
        // 火山：播放地址带 item_id 参数。
        RULES.add(new WebSniffRule("huoshan.com", "item_id=", List.of(".xgplayer-start", ".vjs-big-play-button")));
        // 头条：播放地址走 video/tos/cn 路径。
        RULES.add(new WebSniffRule("toutiaovod.com", "video/tos/cn", List.of(".xgplayer-start", ".vjs-big-play-button")));
    }

    public static WebSniffRule find(String url) {
        if (url == null) return null;
        for (WebSniffRule rule : RULES) if (url.contains(rule.host)) return rule;
        return null;
    }
}
