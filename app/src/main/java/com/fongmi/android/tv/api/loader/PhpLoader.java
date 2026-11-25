package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.exception.SpiderLoadException;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.Map;

/**
 * PHP 爬虫加载器（0.1.106 新增）。
 * <p>
 * 检测：源的 api 里含 ".php"（如 file://.../xxx.php 或 https://.../xxx.php）。
 * 执行：每次爬虫调用都起一个 php 进程（{@link PhpSpider}），跑 assets/php/runner.php。
 * <p>
 * 解释器：{@code <filesDir>/bin/php}。0.1.106 不自带解释器（见 {@link #isAvailable()} 与 NOTES.md）：
 * 首次使用 .php 源且解释器缺失时，加载直接失败并报明原因，不会静默当成空源。
 */
public class PhpLoader extends SpiderLoader {

    private volatile String recent;

    public PhpLoader() {
    }

    PhpLoader(PhpLoader previous) {
        // 无状态（每次调用都起新进程），复位时直接新建即可。
    }

    /** PHP 解释器二进制（约定位置，与 TV+ 同款：bin/php）。 */
    public static File binary() {
        return new File(Path.files(), "bin/php");
    }

    /** 解释器是否可用（文件存在且可执行）。 */
    public static boolean isAvailable() {
        File php = binary();
        return Path.exists(php) && php.canExecute();
    }

    /** runner.php 在私有目录里的落点（首次使用时从 assets 复制过去）。 */
    public static File runnerFile() {
        return new File(Path.files(), "php/runner.php");
    }

    @Override
    protected void clearReferences() {
        recent = null;
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        return load(key, App.get(), ext, () -> newSpider(key, api));
    }

    Spider newSpider(String key, String api) {
        if (!isAvailable()) {
            throw new SpiderLoadException(new IllegalStateException("PHP interpreter is missing: " + binary().getAbsolutePath() + " (PHP 解释器未安装，.php 爬虫源无法运行；见 0.1.106 说明)"));
        }
        return new PhpSpider(key, api);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Spider spider = findSpider(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
