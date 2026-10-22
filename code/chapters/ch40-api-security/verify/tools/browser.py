"""Drives Chromium through the BrowserLab scenarios and prints what the server saw and what the pages could read.

    python3 tools/browser.py <port>      (BrowserLab must be running on that port)
"""
import http.client
import json
import ssl
import sys
import time

from playwright.sync_api import sync_playwright

PORT = int(sys.argv[1])
APP = f"https://foodfleet.test:{PORT}"


def site(host):
    return f"https://{host}:{PORT}"


def server_log():
    conn = http.client.HTTPSConnection("127.0.0.1", PORT, context=ssl._create_unverified_context())
    conn.request("GET", "/__log", headers={"Host": f"foodfleet.test:{PORT}"})
    return json.loads(conn.getresponse().read())


def run(page, host, kind, cors="none", session="s_lax"):
    url = f"{site(host)}/attack?kind={kind}&cors={cors}&session={session}"
    if kind in ("form", "link"):
        page.goto(url)
        page.wait_for_url("**/bff/**", timeout=10_000)
        page.wait_for_load_state()
        outcome = "navigated"
    else:
        page.goto(url)
        page.wait_for_function("window.done === true", timeout=10_000)
        outcome = page.inner_text("#out")
    time.sleep(0.2)
    return outcome, server_log()


def flags(cookies):
    names = cookies.split(",") if cookies else []
    return " ".join(label if f"s_{name}" in names else "·" for name, label in
                    (("lax", "Lax"), ("strict", "Strict"), ("none", "None"), ("unset", "unset")))


def yes(b):
    return "accepted" if b else "refused"


with sync_playwright() as p:
    browser = p.chromium.launch(args=[
        "--host-resolver-rules=MAP *.test 127.0.0.1, MAP foodfleet.test 127.0.0.1",
        "--ignore-certificate-errors", "--proxy-server=direct://", "--proxy-bypass-list=*"])
    context = browser.new_context(ignore_https_errors=True)
    page = context.new_page()
    print(f"Chromium {browser.version}")
    page.goto(f"{APP}/login")
    login_time = time.time()
    server_log()

    writes = [
        ("evil.test", "form", "none", "form POST (top-level navigation)"),
        ("evil.test", "fetch-simple", "none", "fetch POST, no-cors, text/plain"),
        ("evil.test", "fetch-json", "none", "fetch POST, JSON (needs preflight)"),
        ("evil.test", "fetch-json", "reflect", "fetch POST, JSON, server reflects Origin"),
        ("reviews.foodfleet.test", "form", "none", "form POST (top-level navigation)"),
        ("reviews.foodfleet.test", "fetch-simple", "none", "fetch POST, no-cors, text/plain"),
        ("evil.test", "link", "none", "link to /bff/orders (GET navigation)"),
    ]
    print()
    print("state-changing requests to foodfleet.test/bff, the user logged in there (session cookies set with each SameSite value)")
    print(f"{'page':24} {'request':42} {'reached server':16} {'cookies sent':26} {'Sec-Fetch-Site':15} {'CSRF token':11} Fetch Metadata")
    rows = [(h, k, c, d) for h, k, c, d in writes]
    rows.append(("foodfleet.test", "app", "none", "the app's own fetch, token in header"))
    for host, kind, cors, desc in rows:
        if kind == "app":
            page.goto(f"{APP}/app")
            page.wait_for_function("window.done === true", timeout=10_000)
            log = server_log()
        else:
            _, log = run(page, host, kind, cors)
        main = [e for e in log if e["method"] != "OPTIONS"]
        pre = [e for e in log if e["method"] == "OPTIONS"]
        if not main:
            reached = "no (preflight " + ("refused" if pre else "?") + ")"
            print(f"{host:24} {desc:42} {reached:16}")
            continue
        e = main[0]
        reached = "yes" + (" + preflight" if pre else "")
        token = yes(e["tokenOk"]) if e["method"] != "GET" else "n/a (GET)"
        print(f"{host:24} {desc:42} {reached:16} {flags(e['cookies']):26} {e['site']:15} {token:11} "
              f"{yes(e['fetchMetadataOk'])} ({e['fetchMetadataReason']})")

    reads = [
        ("evil.test", "read", "reflect", "s_lax"),
        ("evil.test", "read", "reflect", "s_none"),
        ("evil.test", "read", "wildcard", "s_none"),
        ("evil.test", "read", "allowlist", "s_none"),
        ("evil.test", "read-sandboxed", "allowlist-null", "s_none"),
        ("evil.test", "read-sandboxed", "allowlist", "s_none"),
        ("reviews.foodfleet.test", "read", "reflect", "s_lax"),
        ("reviews.foodfleet.test", "read", "allowlist", "s_lax"),
        ("www.foodfleet.test", "read", "allowlist", "s_lax"),
    ]
    print()
    print("a page reading the user's orders from foodfleet.test/bff/orders with fetch(..., {credentials: 'include'})")
    print(f"{'page':24} {'request Origin':38} {'server CORS':35} {'session cookie':16} what the page got")
    cors_desc = {"reflect": "reflects any Origin + credentials", "wildcard": "* + credentials",
                 "allowlist": "exact allow-list (www only)", "allowlist-null": "allow-list + \"null\""}
    for host, kind, cors, session in reads:
        outcome, log = run(page, host, kind, cors, session)
        e = [x for x in log if x["method"] == "GET"]
        origin = e[0]["origin"] if e else "?"
        cookie = "SameSite=" + session[2:].capitalize()
        got = ("the orders" if "o-81723" in outcome else "empty list (no cookie sent)") if outcome.startswith("read") \
            else "nothing (browser blocked the read)"
        print(f"{host:24} {origin:38} {cors_desc[cors]:35} {cookie:16} {got}")

    # Chrome's "Lax by default" lets a cookie set without SameSite ride along on a top-level cross-site POST
    # for two minutes after it was set. Measure after that.
    wait = 125 - (time.time() - login_time)
    if wait > 0:
        time.sleep(wait)
    _, log = run(page, "evil.test", "form")
    e = [x for x in log if x["method"] == "POST"][0]
    print()
    print(f"the cross-site form POST again, {time.time() - login_time:.0f} s after login: cookies sent {flags(e['cookies'])}")
    browser.close()
