#!/usr/bin/env python3
"""Harvest the complete OshoWorld audio catalogue — English *and* Hindi.

Supersedes the earlier one-language pass, which silently dropped tracks whose
per-track fetch failed once (leaving e.g. 'Jesus Crucified Again' with a single
talk numbered 18). Every network read here retries with backoff, and any series
that still ends up short of its declared count is reported rather than shipped
quietly.
"""
import json, re, sys, threading, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

BUILD = "GDKJ7Di3SgtWra1ew6cJj"
HDRS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}
LOCK = threading.Lock()
COUNT = [0]


def get(url, attempts=4):
    """GET returning parsed JSON, retrying transient failures."""
    for i in range(attempts):
        try:
            req = urllib.request.Request(url, headers=HDRS)
            with urllib.request.urlopen(req, timeout=40) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None          # genuinely absent; no point retrying
        except Exception:
            pass
        time.sleep(0.6 * (i + 1))
    return None


def tick(total, label):
    with LOCK:
        COUNT[0] += 1
        if COUNT[0] % 250 == 0:
            print(f"  {label} {COUNT[0]}/{total}", file=sys.stderr, flush=True)


# ------------------------------------------------------------------ discovery
def sitemap_slugs():
    with urllib.request.urlopen(
        urllib.request.Request("https://oshoworld.com/sitemap.xml", headers=HDRS), timeout=60
    ) as r:
        xml = r.read().decode("utf-8")
    slugs = set()
    for m in re.finditer(r"<loc>https://oshoworld\.com/*([^<]*)</loc>", xml):
        s = m.group(1).strip().strip("/")
        if s and "/" not in s and not s.startswith(
            ("store_", "book-", "books-", "audio-detail-", "video-", "photo-", "news-")
        ):
            slugs.add(s)
    return sorted(slugs)


CANDIDATES = sitemap_slugs()
print(f"pass 1: probing {len(CANDIDATES)} slugs for series (any language)",
      file=sys.stderr, flush=True)


def probe(slug):
    tick(len(CANDIDATES), "probe")
    d = get(f"https://oshoworld.com/api/audio/series/{slug}")
    if not d or d.get("error"):
        return None
    cd = d.get("categoryData") or {}
    if cd.get("category_type") != "series":
        return None
    lang = cd.get("language")
    if lang not in ("english", "hindi"):
        return None
    tracks = [t for t in (d.get("listData") or []) if t.get("file")]
    if not tracks:
        return None
    return {"slug": slug, "language": lang, "title": cd.get("title"),
            "total": int(d.get("total") or len(tracks)), "page1": tracks}


with ThreadPoolExecutor(max_workers=20) as ex:
    series = [r for r in ex.map(probe, CANDIDATES) if r]

by_lang = {}
for s in series:
    by_lang[s["language"]] = by_lang.get(s["language"], 0) + 1
print(f"pass 1: {len(series)} series -> {by_lang}", file=sys.stderr, flush=True)
json.dump(series, open("/tmp/osho2/series.json", "w"))


# ------------------------------------------------------------------- tracks
def slug_pattern(page1):
    """Infer how a track slug encodes its 1-based index."""
    for t in reversed(page1):
        m = re.match(r"^(.*?)(\d+)$", t["slug"] or "")
        if m and int(m.group(2)) == t["audio_index"]:
            return m.group(1), len(m.group(2))
    return None


jobs = []
for s in series:
    pat = slug_pattern(s["page1"])
    s["_pat"] = pat
    if not pat:
        continue
    base, width = pat
    have = {t["audio_index"] for t in s["page1"]}
    for i in range(1, s["total"] + 1):
        if i not in have:
            jobs.append((s["slug"], f"{base}{str(i).zfill(width)}", i))

COUNT[0] = 0
print(f"pass 2: fetching {len(jobs)} additional tracks", file=sys.stderr, flush=True)


def fetch_track(job):
    series_slug, track_slug, idx = job
    tick(len(jobs), "track")
    d = get(f"https://oshoworld.com/_next/data/{BUILD}/{track_slug}.json")
    try:
        a = d["pageProps"]["data"]["pageData"]["audioData"]
    except Exception:
        return None
    if not a.get("audioFile"):
        return None
    # `description` is deliberately dropped: those are full discourse
    # transcripts and are not redistributed by this app.
    return series_slug, {
        "audio_index": a.get("audio_index", idx),
        "title": a.get("title"),
        "duration": a.get("duration"),
        "file": a.get("audioFile"),
        "imageFile": a.get("imageFile"),
    }


extra = {}
with ThreadPoolExecutor(max_workers=20) as ex:
    for r in ex.map(fetch_track, jobs):
        if r:
            extra.setdefault(r[0], []).append(r[1])

out, short = [], []
for s in series:
    merged, seen = [], set()
    for t in sorted(s["page1"] + extra.get(s["slug"], []),
                    key=lambda x: x.get("audio_index") or 0):
        i = t.get("audio_index")
        if i in seen:
            continue
        seen.add(i)
        merged.append(t)
    if len(merged) < s["total"]:
        short.append((s["slug"], len(merged), s["total"]))
    out.append({"slug": s["slug"], "language": s["language"], "title": s["title"],
                "declared_total": s["total"], "tracks": merged})

json.dump(out, open("/tmp/osho2/catalog.json", "w"), indent=1)
print(f"DONE {len(out)} series / {sum(len(s['tracks']) for s in out)} tracks",
      file=sys.stderr, flush=True)
print(f"still short of declared count: {len(short)}", file=sys.stderr, flush=True)
for s in short[:20]:
    print(f"   {s}", file=sys.stderr, flush=True)
