#!/usr/bin/env python3
"""Fill in series the pattern-based pass left short.

Two things defeated the simple inference:

  * Volume-spanning series. 'the-golden-gate-series' declares 20 talks but its
    slugs read '…-vol-1-01'…'-vol-1-10'; talks 11-20 live under '…-vol-2-…'.
  * Slug numbers are the *published* talk number, not the position. Where the
    source skips a number (no 'That Art Thou 04'), audio_index 10 sits at slug
    '…-11', so extrapolating from the position undershoots.

Rather than infer harder, this probes a generous candidate space per series and
keeps whatever actually resolves, de-duplicated by audio file.
"""
import json, re, sys, threading, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

BUILD = "GDKJ7Di3SgtWra1ew6cJj"
HDRS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}
LOCK = threading.Lock()
N = [0]


def get(url, attempts=3):
    for i in range(attempts):
        try:
            with urllib.request.urlopen(
                urllib.request.Request(url, headers=HDRS), timeout=35
            ) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
        except Exception:
            pass
        time.sleep(0.4 * (i + 1))
    return None


catalog = json.load(open("/tmp/osho2/catalog.json"))
short = [s for s in catalog if len(s["tracks"]) < s["declared_total"]]
print(f"filling {len(short)} short series", file=sys.stderr, flush=True)


def candidates(series):
    """Slug candidates worth trying for one series."""
    bases = set()
    for t in series["tracks"]:
        m = re.match(r"^(.*?)(\d+)$", t.get("slug") or "")
        if m:
            bases.add((m.group(1), len(m.group(2))))
    if not bases:
        return []

    total = series["declared_total"]
    out = []
    for base, width in bases:
        # Numbers can run past `total` when the source skips some.
        hi = total + 12
        for i in range(1, hi + 1):
            out.append(f"{base}{str(i).zfill(width)}")

        # Volume rollover: '…-vol-1-' -> '…-vol-2-', '…-vol-3-', …
        vm = re.match(r"^(.*?vol-)(\d+)(-)$", base)
        if vm:
            head, _, tail = vm.groups()
            for vol in range(2, 9):
                for i in range(1, 41):
                    out.append(f"{head}{vol}{tail}{str(i).zfill(width)}")
    return out


jobs = []
for s in short:
    have = {t.get("slug") for t in s["tracks"]}
    for c in candidates(s):
        if c not in have:
            jobs.append((s["slug"], c))

print(f"probing {len(jobs)} candidate track slugs", file=sys.stderr, flush=True)


def fetch(job):
    series_slug, track_slug = job
    with LOCK:
        N[0] += 1
        if N[0] % 500 == 0:
            print(f"  {N[0]}/{len(jobs)}", file=sys.stderr, flush=True)
    d = get(f"https://oshoworld.com/_next/data/{BUILD}/{track_slug}.json")
    try:
        a = d["pageProps"]["data"]["pageData"]["audioData"]
    except Exception:
        return None
    if not a.get("audioFile"):
        return None
    return series_slug, {
        "audio_index": a.get("audio_index"),
        "title": a.get("title"),
        "duration": a.get("duration"),
        "file": a.get("audioFile"),
        "slug": a.get("slug"),
        "imageFile": a.get("imageFile"),
    }


found = {}
with ThreadPoolExecutor(max_workers=24) as ex:
    for r in ex.map(fetch, jobs):
        if r:
            found.setdefault(r[0], []).append(r[1])

import os

by_slug = {s["slug"]: s for s in catalog}
for series_slug, extra in found.items():
    s = by_slug[series_slug]
    # Volume rollover guessing can resolve to a *neighbouring* volume's slugs
    # (probing from 'vol-4-' happily finds 'vol-5-…'), which would merge whole
    # series together. The directories already present are the boundary: a
    # track that lives somewhere else is not part of this series.
    allowed = {os.path.dirname(t["file"]) for t in s["tracks"] if t.get("file")}
    seen = {t["file"] for t in s["tracks"]}
    for t in extra:
        if t["file"] in seen or os.path.dirname(t["file"]) not in allowed:
            continue
        seen.add(t["file"])
        s["tracks"].append(t)
    # Order by audio file path, which sorts naturally within a volume and keeps
    # volumes in sequence — audio_index restarts at 1 for each volume.
    s["tracks"].sort(key=lambda t: (t.get("file") or ""))

json.dump(catalog, open("/tmp/osho2/catalog.json", "w"), indent=1)

still = [(s["slug"], len(s["tracks"]), s["declared_total"])
         for s in catalog if len(s["tracks"]) < s["declared_total"]]
print(f"DONE. total tracks {sum(len(s['tracks']) for s in catalog)}", file=sys.stderr)
print(f"still short: {len(still)}", file=sys.stderr)
for x in still:
    print(f"   {x}", file=sys.stderr)
