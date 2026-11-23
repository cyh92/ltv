import * as entry from %s;
import host from 'catvod:host';
import {Spider} from 'catvod';
import {stringify} from 'assets://js/lib/net.js';
import {dialogActions} from 'assets://js/lib/dialog.js';

// --- drpy support (0.1.94): auto-detect the api shape, wrap it in a Spider ---
// Detection order (first match wins). Nothing here changes the Spider-class path.
//  1. Spider subclass (existing behaviour, unchanged).
//  2. drpy2 engine object: has getRule + init/home/category/detail/play/search.
//     The api was drpy2.min.js; Java substituted the bundled engine for it, so the
//     rule file (site ext) is the only thing that still comes from the user.
//  3. drpy v1 rule object: plain object with init/home/category/detail/play/search.
//     Java appended `export default {...}` to the rule source so it is reachable.
//  4. cat rule object: plain object with 推荐/一级/二级/搜索 (a `var rule = {...}` file).
//     Java appended `export default rule;`; the bundled drpy2 engine interprets it.
// Anything else keeps the original error, so already-working sources are untouched.

function isSpiderClass(fn) {
    return typeof fn === 'function' && fn.prototype instanceof Spider;
}

function hasDrpyMethods(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return ['init', 'home', 'category', 'detail', 'play', 'search'].some(k => typeof obj[k] === 'function');
}

function isDrpy2Engine(obj) {
    return hasDrpyMethods(obj) && typeof obj.getRule === 'function';
}

function isRuleObject(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return obj['推荐'] !== undefined || obj['一级'] !== undefined || obj['二级'] !== undefined || obj['搜索'] !== undefined;
}

let drpyGlobalsInstalled = false;

async function installDrpyGlobals() {
    if (drpyGlobalsInstalled) return;
    const mod = await import('assets://js/lib/drpy.js');
    mod.installDrpyGlobals();
    drpyGlobalsInstalled = true;
}

// Wraps the drpy2 engine. `engine` is the engine object, or null when the api was
// a cat rule object (`rule`): then the bundled engine is loaded lazily in init().
class Drpy2Spider extends Spider {
    constructor(engine, rule) {
        super();
        this.engine = engine;
        this.rule = rule;
    }

    async init(extend) {
        await installDrpyGlobals();
        if (!this.engine) {
            const mod = await import('assets://js/lib/drpy2/drpy2.min.js');
            this.engine = mod.default;
        }
        if (this.rule) await this.engine.init(this.rule);
        else await this.engine.init(extend);
    }

    async homeContent(filter) {
        return this.engine.home(filter);
    }

    async homeVideoContent() {
        return this.engine.homeVod();
    }

    async categoryContent(tid, pg, filter, extend) {
        return this.engine.category(tid, pg, filter, extend);
    }

    async detailContent(ids) {
        return this.engine.detail(ids[0]);
    }

    async searchContent(key, quick, pg = '1') {
        return this.engine.search(key, quick, pg);
    }

    async playerContent(flag, id, vipFlags) {
        return this.engine.play(flag, id, vipFlags);
    }

    async proxy(params) {
        if (typeof this.engine.proxy === 'function') return this.engine.proxy(params);
        return super.proxy(params);
    }

    async action(action) {
        if (typeof this.engine.runMain === 'function') return this.engine.runMain(action, '');
        return super.action(action);
    }

    isVideoFormat(url) {
        if (typeof this.engine.isVideo === 'function') return this.engine.isVideo(url);
        return super.isVideoFormat(url);
    }
}

// Wraps a drpy v1 rule object: top-level functions init/home/homeVod/category/
// detail/play/search (the Java side appended `export default {...}` to the source).
class Drpy1Spider extends Spider {
    constructor(rule) {
        super();
        this.rule = rule;
    }

    async init(extend) {
        await installDrpyGlobals();
        if (typeof this.rule.init === 'function') {
            await this.rule.init({skey: this.siteKey, stype: 3, ext: extend});
        }
    }

    async homeContent(filter) {
        if (typeof this.rule.home === 'function') return this.rule.home(filter);
        return super.homeContent(filter);
    }

    async homeVideoContent() {
        if (typeof this.rule.homeVod === 'function') return this.rule.homeVod();
        return super.homeVideoContent();
    }

    async categoryContent(tid, pg, filter, extend) {
        if (typeof this.rule.category === 'function') return this.rule.category(tid, pg, filter, extend);
        return super.categoryContent(tid, pg, filter, extend);
    }

    async detailContent(ids) {
        if (typeof this.rule.detail === 'function') return this.rule.detail(ids[0]);
        return super.detailContent(ids);
    }

    async searchContent(key, quick, pg = '1') {
        if (typeof this.rule.search === 'function') return this.rule.search(key, quick, pg);
        return super.searchContent(key, quick, pg);
    }

    async playerContent(flag, id, vipFlags) {
        if (typeof this.rule.play === 'function') return this.rule.play(flag, id, vipFlags);
        return super.playerContent(flag, id, vipFlags);
    }

    async proxy(params) {
        if (typeof this.rule.proxy === 'function') return this.rule.proxy(params);
        return super.proxy(params);
    }

    async action(action) {
        if (typeof this.rule.action === 'function') return this.rule.action(action);
        return super.action(action);
    }
}

let api;
if (isSpiderClass(entry.default)) {
    api = new entry.default();
} else if (isDrpy2Engine(entry.default)) {
    api = new Drpy2Spider(entry.default, null);
} else if (hasDrpyMethods(entry.default)) {
    api = new Drpy1Spider(entry.default);
} else if (isRuleObject(entry.default)) {
    api = new Drpy2Spider(null, entry.default);
} else {
    throw new TypeError('Spider requires a default class extending Spider');
}
host.attach(createSpider(api), dialogActions);

function createSpider(api) {
    const wrapped = Object.create(api);
    for (const name of ['homeContent', 'homeVideoContent', 'categoryContent', 'detailContent', 'searchContent', 'playerContent', 'liveContent', 'action']) bindSpiderMethod(wrapped, api, name, true);
    for (const name of ['init', 'proxy', 'manualVideoCheck', 'isVideoFormat']) bindSpiderMethod(wrapped, api, name, false);
    bindDanmaku(wrapped, api);
    return wrapped;
}

function output(value, method) {
    if (method === 'liveContent' && typeof value === 'string') return value;
    if (!value || typeof value !== 'object') throw new TypeError('Spider data must be an object or array');
    const json = stringify(value);
    if (json[0] !== '{' && json[0] !== '[') throw new TypeError('Spider data must be an object or array');
    return json;
}

function bindSpiderMethod(wrapped, api, name, serialize) {
    Object.defineProperty(wrapped, name, {
        value: async function(...args) {
            const value = await api[name](...args);
            return serialize ? output(value, name) : value;
        },
        writable: true, configurable: true, enumerable: true
    });
}

function bindDanmaku(wrapped, api) {
    const danmakus = new Map();
    wrapped.startDanmaku = async function(id, token) {
        const stop = await api.danmaku(id, item => {
            if (!item || typeof item !== 'object' || Array.isArray(item)) throw new TypeError('Danmaku must be an object');
            host.emitDanmaku(token, item);
        });
        if (typeof stop !== 'function') throw new TypeError('danmaku must return a stop function');
        danmakus.set(token, stop);
    };
    wrapped.stopDanmaku = async function(token) {
        const stop = danmakus.get(token);
        danmakus.delete(token);
        if (stop) await stop();
    };
    async function destroy() {
        const failures = [];
        for (const token of danmakus.keys()) {
            try {
                await wrapped.stopDanmaku(token);
            } catch (error) {
                failures.push(error);
            }
        }
        try {
            await api.destroy();
        } catch (error) {
            failures.push(error);
        }
        if (failures.length) throw failures[0];
    }

    Object.defineProperty(wrapped, 'destroy', {value: destroy, writable: true, configurable: true, enumerable: true});
}
