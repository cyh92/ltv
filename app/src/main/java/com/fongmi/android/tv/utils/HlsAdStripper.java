package com.fongmi.android.tv.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 0.1.104：HLS 广告切除。m3u8 播放列表里夹的广告 ts 段（量子、非凡、索尼、
 * 暴风、星星、快看、一起看）会在播放时插广告或卡住。这里按广告域名特征把
 * 广告段从播放列表里删掉，只留正片段。
 *
 * 纯函数工具类，无 Android 依赖。规则集中在这里一个地方，广告方换域名时
 * 只改 RULES 即可。用法：HlsAdStripper.strip(m3u8文本)。
 */
public final class HlsAdStripper {

    /** 7 条广告段 URL 特征（按广告方分的域名子串/正则）。 */
    public static final List<Pattern> RULES = List.of(
            // 量子广告
            Pattern.compile("vip\\.lz|hd\\.lz|\\.cdnlz"),
            // 非凡广告
            Pattern.compile("vip\\.ffzy|hd\\.ffzy"),
            // 索尼广告
            Pattern.compile("suonizy"),
            // 暴风广告
            Pattern.compile("bfzy"),
            // 星星广告
            Pattern.compile("aws\\.ulivetv\\.net"),
            // 快看广告
            Pattern.compile("kuaikan"),
            // 一起看广告
            Pattern.compile("yqk88")
    );

    private HlsAdStripper() {
    }

    public static boolean isAdSegment(String url) {
        if (url == null || url.isEmpty() || url.startsWith("#")) return false;
        for (Pattern rule : RULES) if (rule.matcher(url).find()) return true;
        return false;
    }

    /**
     * 删掉 m3u8 里的广告段。一个段是两行：#EXTINF:时长, + URL 行。
     * 删 URL 行时要连同它前面紧邻的 #EXTINF 行一起删，只删 URL 行会留一个
     * 空 #EXTINF 导致播放器解析错。其余行原样保留。
     */
    public static String strip(String m3u8) {
        if (m3u8 == null || m3u8.isEmpty()) return m3u8;
        String[] lines = m3u8.split("\n", -1);
        List<String> kept = new ArrayList<>();
        int before = 0;
        for (String line : lines) {
            if (line.startsWith("#EXTINF")) before++;
            if (isAdSegment(line)) {
                if (!kept.isEmpty() && kept.get(kept.size() - 1).startsWith("#EXTINF")) kept.remove(kept.size() - 1);
                continue;
            }
            kept.add(line);
        }
        // 删空保护：原列表有段、剥完一个不剩，说明规则误判了合法 CDN，原样返回（宁可留广告也不播不了）。
        int after = 0;
        for (String line : kept) if (line.startsWith("#EXTINF")) after++;
        if (before > 0 && after == 0) return m3u8;
        return String.join("\n", kept);
    }
}
