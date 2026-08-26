#!/usr/bin/env python3
"""Refresh app/src/main/res/raw/airports.json from liveatc.net.

liveatc.net sits behind a Cloudflare challenge that plain HTTP clients do not
pass, and its Icecast servers ban IPs that probe mount names in bulk. So this
script drives a real browser instead: Chrome on an adb-connected phone, via
the DevTools protocol. One page load per airport, no stream connections.

Setup (once):
    python3 -m venv .venv && .venv/bin/pip install websocket-client
    adb forward tcp:9222 localabstract:chrome_devtools_remote
    # open any liveatc.net page in Chrome on the phone so a tab exists

Run:
    .venv/bin/python tools/liveatc_feeds.py            # refresh every airport
    .venv/bin/python tools/liveatc_feeds.py KJFK EHAM  # just these

Airport metadata (name, city, coordinates, region) is kept from the existing
JSON; only the feed lists are replaced. Feeds are written preferred-first:
the mount that currently heads the list stays first if it is still UP, then
Tower, App/Dep, Ground, Delivery, ATIS, everything else. The app probes and
plays feeds in file order, so reorder by hand if a different default is
wanted for some airport.
"""

import datetime
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

import websocket  # pip install websocket-client

JSON_PATH = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/airports.json"
DEVTOOLS = "http://localhost:9222"


def devtools_tab():
    tabs = json.load(urllib.request.urlopen(f"{DEVTOOLS}/json"))
    for t in tabs:
        if t["type"] == "page" and "liveatc.net" in t["url"]:
            return t
    for t in tabs:
        if t["type"] == "page":
            return t
    sys.exit("no Chrome tab found — is Chrome open and `adb forward tcp:9222 localabstract:chrome_devtools_remote` set?")


class Chrome:
    def __init__(self, tab):
        self.ws = websocket.create_connection(tab["webSocketDebuggerUrl"], suppress_origin=True)
        self.seq = 0

    def call(self, method, **params):
        self.seq += 1
        self.ws.send(json.dumps({"id": self.seq, "method": method, "params": params}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self.seq:
                return msg.get("result", {})

    def eval(self, expression):
        return self.call("Runtime.evaluate", expression=expression, returnByValue=True)["result"]["value"]

    def load(self, url, marker, timeout=40):
        self.call("Page.navigate", url=url)
        for _ in range(timeout):
            time.sleep(1)
            state = self.eval("location.href + '|' + document.readyState")
            if marker in state and state.endswith("complete"):
                time.sleep(1)
                break
        html = self.eval("document.documentElement.outerHTML")
        if "Just a moment" in html:  # Cloudflare challenge; Chrome clears it by itself
            time.sleep(15)
            html = self.eval("document.documentElement.outerHTML")
        return html


def scrape_feeds(html):
    """Return [(mount, name, status)] from an airport feed page."""
    feeds = []
    for block in re.split(r'<td bgcolor="lightblue"><strong>', html)[1:]:
        name = re.match(r"([^<]+)</strong>", block)
        mount = re.search(r"/play/([a-z0-9_\-]+)\.pls", block)
        if not name or not mount:
            continue
        status = re.search(r"Feed Status:</strong>\s*<font[^>]*><strong>(UP|DOWN)</strong>", block)
        feeds.append((mount.group(1), name.group(1).strip(), status.group(1) if status else "?"))
    return feeds


def rank(name):
    n = name.lower()
    if "twr" in n or "tower" in n:
        return 0
    if any(k in n for k in ("app", "dep", "arr", "radar", "director")):
        return 1
    if "gnd" in n or "ground" in n:
        return 2
    if "del" in n or "clearance" in n:
        return 3
    if "atis" in n:
        return 4
    return 5


def label(icao, name):
    return re.sub(rf"^{icao}\s+", "", name).strip() or name


def main(argv):
    doc = json.loads(JSON_PATH.read_text())
    wanted = set(a.upper() for a in argv) or {a["icao"] for a in doc["airports"]}
    chrome = Chrome(devtools_tab())

    for airport in doc["airports"]:
        icao = airport["icao"]
        if icao not in wanted:
            continue
        html = chrome.load(f"https://www.liveatc.net/search/?icao={icao}", f"icao={icao}")
        up = [(m, n) for m, n, st in scrape_feeds(html) if st == "UP"]
        if not up:
            print(f"{icao}: no UP feeds on liveatc.net — left unchanged", file=sys.stderr)
            continue
        current = airport["feeds"][0]["mount"] if airport["feeds"] else None
        seen, uniq = set(), []
        for m, n in up:
            if m not in seen:
                seen.add(m)
                uniq.append((m, n))
        uniq.sort(key=lambda f: (0 if f[0] == current else 1, rank(f[1]), f[1]))
        airport["feeds"] = [{"mount": m, "label": label(icao, n)} for m, n in uniq]
        print(f"{icao}: {len(uniq)} feeds, default {uniq[0][0]} ({label(icao, uniq[0][1])})")

    doc["updated"] = datetime.date.today().isoformat()
    JSON_PATH.write_text(json.dumps(doc, indent=1, ensure_ascii=False) + "\n")
    print(f"wrote {JSON_PATH}")


if __name__ == "__main__":
    main(sys.argv[1:])
