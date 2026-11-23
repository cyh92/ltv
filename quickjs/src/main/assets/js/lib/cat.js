// cat.js — minimal compatibility shim for drpy v1 / cat-style rules.
// Bundled so rules that do `import {Crypto, _} from 'assets://js/lib/cat.js'` load.
// Crypto comes from the bundled drpy-core-lite (same CryptoJS the drpy2 engine uses).
// `_` is a minimal lodash subset covering what rules actually call.
// If a rule needs a CryptoJS/lodash member not present here, add it (do not invent APIs).
import {CryptoJS} from './drpy2/drpy-core-lite.min.js';

export const Crypto = CryptoJS;

const isFn = v => typeof v === 'function';
const isArr = Array.isArray;
const isObj = v => v !== null && typeof v === 'object';
const isStr = v => typeof v === 'string';

function iteratee(fn) {
    if (isFn(fn)) return fn;
    if (isStr(fn)) return v => (v == null ? undefined : v[fn]);
    return v => v;
}

export const _ = {
    map(list, fn) {
        if (list == null) return [];
        const f = iteratee(fn);
        return isArr(list) ? list.map((v, i) => f(v, i, list)) : Object.keys(list).map(k => f(list[k], k, list));
    },
    forEach(list, fn) {
        if (list == null) return list;
        const f = iteratee(fn);
        if (isArr(list)) list.forEach((v, i) => f(v, i, list));
        else Object.keys(list).forEach(k => f(list[k], k, list));
        return list;
    },
    filter(list, fn) {
        if (list == null) return [];
        const f = iteratee(fn);
        return isArr(list) ? list.filter((v, i) => f(v, i, list)) : Object.keys(list).map(k => list[k]).filter(v => f(v));
    },
    find(list, fn) {
        if (list == null) return undefined;
        const f = iteratee(fn);
        if (isArr(list)) return list.find((v, i) => f(v, i, list));
        for (const k of Object.keys(list)) if (f(list[k], k, list)) return list[k];
        return undefined;
    },
    findIndex(list, fn) {
        if (list == null) return -1;
        const f = iteratee(fn);
        return isArr(list) ? list.findIndex((v, i) => f(v, i, list)) : -1;
    },
    includes(list, value, fromIndex) {
        if (list == null) return false;
        if (isStr(list) || isArr(list)) return list.includes(value, fromIndex);
        return Object.values(list).includes(value);
    },
    get(obj, path, def) {
        if (obj == null) return def;
        const parts = isArr(path) ? path : String(path).replace(/\[(\w+)\]/g, '.$1').split('.');
        let cur = obj;
        for (const p of parts) {
            if (cur == null) return def;
            cur = cur[p];
        }
        return cur === undefined ? def : cur;
    },
    keys(obj) { return obj == null ? [] : Object.keys(obj); },
    values(obj) { return obj == null ? [] : Object.values(obj); },
    isEmpty(v) {
        if (v == null) return true;
        if (isStr(v) || isArr(v)) return v.length === 0;
        if (isObj(v)) return Object.keys(v).length === 0;
        return false;
    },
    isArray: isArr,
    isString: isStr,
    isObject: v => v !== null && typeof v === 'object',
    isNumber: v => typeof v === 'number',
    isFunction: isFn,
    random(a, b) {
        if (b === undefined) { b = a; a = 0; }
        return Math.floor(Math.random() * (b - a + 1)) + a;
    },
    uniq(list) { return isArr(list) ? [...new Set(list)] : []; },
    flatten(list) { return isArr(list) ? list.flat(Infinity) : []; },
    first(list) { return isArr(list) ? list[0] : undefined; },
    last(list) { return isArr(list) ? list[list.length - 1] : undefined; },
    size(v) {
        if (v == null) return 0;
        if (isStr(v) || isArr(v)) return v.length;
        if (isObj(v)) return Object.keys(v).length;
        return 0;
    },
    clone(v) { return isArr(v) ? v.slice() : (isObj(v) ? {...v} : v); },
    cloneDeep(v) { return v === undefined ? undefined : JSON.parse(JSON.stringify(v)); },
    pick(obj, keys) {
        const out = {};
        if (obj == null) return out;
        for (const k of keys) if (k in obj) out[k] = obj[k];
        return out;
    },
    omit(obj, keys) {
        const out = {};
        if (obj == null) return out;
        const drop = new Set(keys);
        for (const k of Object.keys(obj)) if (!drop.has(k)) out[k] = obj[k];
        return out;
    },
    groupBy(list, fn) {
        const f = iteratee(fn);
        const out = {};
        if (list == null) return out;
        for (const v of list) {
            const k = f(v);
            (out[k] = out[k] || []).push(v);
        }
        return out;
    },
    sortBy(list, fn) {
        if (list == null) return [];
        const f = iteratee(fn);
        return [...list].sort((a, b) => {
            const x = f(a), y = f(b);
            return x < y ? -1 : (x > y ? 1 : 0);
        });
    },
    sum(list) { return isArr(list) ? list.reduce((a, v) => a + (Number(v) || 0), 0) : 0; },
    range(a, b, step = 1) {
        if (b === undefined) { b = a; a = 0; }
        const out = [];
        for (let i = a; i < b; i += step) out.push(i);
        return out;
    },
};
