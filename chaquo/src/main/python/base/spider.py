import json
import re
from urllib.parse import urlencode

from com.github.catvod import Proxy
from com.github.catvod.crawler import Spider as JavaSpider


class Spider:
    _instance = None

    def __init__(self):
        self.extend = ""

    def __new__(cls, *args, **kwargs):
        if cls.__dict__.get("_instance") is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    def init(self, extend=""):
        pass

    def homeContent(self, filter):
        return {"class": []}

    def homeVideoContent(self):
        return {"list": []}

    def categoryContent(self, tid, pg, filter, extend):
        return {"list": []}

    def detailContent(self, ids):
        return {"list": []}

    def searchContent(self, key, quick, pg="1"):
        return {"list": []}

    def playerContent(self, flag, id, vipFlags):
        return {}

    def liveContent(self, url):
        return ""

    def proxy(self, params):
        return [404, "text/plain", ""]

    def isVideoFormat(self, url):
        return False

    def manualVideoCheck(self):
        return False

    def action(self, action):
        return {}

    def danmaku(self, id, emit):
        raise NotImplementedError("Spider does not support live danmaku")

    def destroy(self):
        pass

    def getActivity(self):
        from com.github.catvod import Runtime

        return Runtime.activity()

    def getDependence(self):
        return []

    def loadSpider(self, name):
        spider = self.runtime.spider(name)
        if hasattr(self, "net"):
            spider.net = self.net
            spider.local = self.local
            spider.js = self.js
        if hasattr(self, "dialog"):
            spider.dialog = self.dialog
        return spider

    def loadModule(self, name):
        return self.runtime.load_reference(name)

    def getName(self):
        pass

    def localProxy(self, param):
        pass

    def regStr(self, reg, src, group=1):
        match = re.search(reg, src)
        return match.group(group) if match else ''

    def removeHtmlTags(self, src):
        return re.sub('<.*?>', '', src)

    def cleanText(self, src):
        return re.sub('[\U0001F600-\U0001F64F\U0001F300-\U0001F5FF\U0001F680-\U0001F6FF\U0001F1E0-\U0001F1FF]', '', src)

    def fetch(self, url, params=None, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects=True):
        import requests

        rsp = requests.get(url, params=params, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects)
        rsp.encoding = 'utf-8'
        return rsp

    def post(self, url, params=None, data=None, json=None, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects=True):
        import requests

        rsp = requests.post(url, params=params, data=data, json=json, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects)
        rsp.encoding = 'utf-8'
        return rsp

    def html(self, content):
        from lxml import etree

        return etree.HTML(content)

    @staticmethod
    def str2json(str):
        return json.loads(str)

    @staticmethod
    def json2str(str):
        return json.dumps(str, ensure_ascii=False)

    def getProxyUrl(self, params=None, local=True):
        if isinstance(params, bool):
            local, params = params, None
        return self.proxyUrl(params, local)

    def getDanmakuUrl(self, id):
        return self.danmakuUrl(id)

    def proxyUrl(self, params=None, local=True):
        params = {} if params is None else params
        if not isinstance(params, dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in params.items()):
            raise TypeError("Proxy parameters require a dict with string keys and values")
        if not isinstance(local, bool):
            raise TypeError("Proxy local flag requires a boolean")
        if "do" in params or "siteKey" in params:
            raise ValueError("Reserved proxy parameter")
        url = str(Proxy.getUrl(local))
        query = urlencode({"do": "py", "siteKey": self.siteKey, **params})
        return url + "?" + query

    def danmakuUrl(self, id):
        return str(JavaSpider.danmakuUrl(self.siteKey, id))

    def log(self, msg):
        if isinstance(msg, (dict, list)):
            print(json.dumps(msg, ensure_ascii=False))
        else:
            print(f"{msg}")

    @staticmethod
    def link(name, target):
        if not isinstance(target, dict):
            raise TypeError("link requires a target dict")
        target = json.dumps(target, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
        return f"[a=cr:{target}/]{name!s}[/a]"

    @staticmethod
    def page(items, pg, limit, total=None):
        pg = max(1, int(pg))
        if isinstance(limit, bool) or not isinstance(limit, int) or limit < 1:
            raise ValueError("page requires a positive limit")
        if total is None:
            total = len(items)
            items = items[(pg - 1) * limit : pg * limit]
        if isinstance(total, bool) or not isinstance(total, int) or total < 0:
            raise ValueError("page requires a nonnegative total")
        return {"list": items, "page": pg, "pagecount": max(1, (total + limit - 1) // limit), "limit": limit, "total": total}
