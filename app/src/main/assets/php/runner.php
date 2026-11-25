<?php
/**
 * 烛龙 PHP 爬虫运行器（0.1.106 新增，TV+ 同款式）。
 *
 * Java 侧（PhpSpider）每次爬虫调用起一个 php 进程：
 *   php runner.php <spider.php>
 * 请求 JSON 从 stdin 读：{"method":"homeContent","args":[true]}
 * 爬虫函数的返回值从 stdout 原样输出（已经是 JSON 字符串就原样输出；是数组就 json_encode）。
 *
 * 爬虫 .php 文件的约定（与 TVBox Spider 接口同名，全局函数）：
 *   homeContent($filter) / homeVideoContent() /
 *   categoryContent($tid, $pg, $filter, $extend) / detailContent($ids) /
 *   searchContent($key, $quick, $pg) / playerContent($flag, $id, $vipFlags) /
 *   liveContent($url) / action($action) / manualVideoCheck() / isVideoFormat($url)
 * 每个函数返回 JSON 字符串或数组。proxy 额外约定返回
 *   ["code" => 200, "type" => "video/mp4", "body" => base64_encode($bytes)]。
 *
 * 解释器不在 APK 里（见 0.1.106 说明）：bin/php 缺失时 Java 侧直接报“PHP 解释器未安装”，
 * 不会走到这里。
 */
$spiderFile = $argv[1] ?? '';
$input = stream_get_contents(STDIN);
$req = json_decode($input, true);
if (!is_array($req) || !isset($req['method']) || !is_string($spiderFile) || !is_file($spiderFile)) {
    fwrite(STDERR, "bad request\n");
    exit(2);
}
require_once $spiderFile;
$method = $req['method'];
$args = isset($req['args']) && is_array($req['args']) ? $req['args'] : [];
if (!function_exists($method)) {
    fwrite(STDERR, "method not found: $method\n");
    exit(3);
}
$result = $method(...$args);
if (is_string($result)) {
    echo $result;
} elseif (is_bool($result)) {
    echo $result ? 'true' : 'false';
} else {
    echo json_encode($result, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
}
