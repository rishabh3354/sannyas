#!/usr/bin/env python3
"""Build the app's bundled `discourses.json` from the harvested catalogue.

Carries over only factual metadata — titles, artwork, durations, audio URLs and
language. Series blurbs are generated here from that metadata; the discourse
transcripts published on the source site are deliberately not redistributed.
"""
import json, re, sys, unicodedata, urllib.parse, urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

AUDIO_BASE = "https://oshoworld.com"
IMAGE_BASE = "https://oshoworld.com/"
HDRS = {"User-Agent": "Mozilla/5.0", "Accept": "application/json"}

catalog = json.load(open("/tmp/osho2/catalog.json"))


# --------------------------------------------------------------- categories
def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=HDRS), timeout=35) as r:
            return json.loads(r.read().decode())
    except Exception:
        return None


cat_slugs = set()
with open("/tmp/osho2/harvest.py"):
    pass
sitemap = urllib.request.urlopen(
    urllib.request.Request("https://oshoworld.com/sitemap.xml", headers=HDRS), timeout=60
).read().decode()
for m in re.finditer(r"<loc>https://oshoworld\.com/*([^<]*)</loc>", sitemap):
    s = m.group(1).strip().strip("/")
    if re.match(r"^audio-(master|topic|category)", s):
        cat_slugs.add(s)


def probe_category(slug):
    for api in ("topic", "master", "category", "series"):
        d = get(f"https://oshoworld.com/api/audio/{api}/{slug}")
        if d and not d.get("error") and d.get("categoryData"):
            cd = d["categoryData"]
            return cd.get("title"), cd.get("language"), [
                i.get("slug") for i in (d.get("listData") or [])
            ]
    return None


slug_to_category = {}
with ThreadPoolExecutor(max_workers=16) as ex:
    for r in ex.map(probe_category, sorted(cat_slugs)):
        if not r:
            continue
        title, _lang, members = r
        if not title:
            continue
        title = title.strip().title()
        for member in members:
            if member:
                slug_to_category.setdefault(member, title)

print(f"category assignments: {len(slug_to_category)}", file=sys.stderr)

DEFAULT_CATEGORY = "Discourses"

BLURB = {
    "Zen And Zen Masters": "Osho turns to the Zen tradition — its masters, its stories and its refusal of easy answers.",
    "Buddha And Buddhist": "Talks on Gautama Buddha and the masters who followed him.",
    "Sufism": "The Sufi way of ecstasy, poetry and devotion, read through Osho's eyes.",
    "Tao": "Osho on the Taoist masters and the art of moving with things rather than against them.",
    "Yoga": "A reading of the yogic path as a science of the inner rather than a set of postures.",
    "Tantra": "Osho on the tantric approach: acceptance of the body as a door rather than an obstacle.",
    "The Upanishads": "Osho speaks on the Upanishads, the contemplative core of the Indian tradition.",
    "Jesus": "Osho's reading of Jesus and the sayings attributed to him.",
    "Jewish Mystics": "Talks on the mystical current running through the Jewish tradition.",
    "Western Mystics": "Osho on mystics and thinkers from the Western tradition.",
    "Kabir": "Osho on Kabir — the weaver-poet who dismissed scripture in favour of direct experience.",
    "Baul Mystics": "The Bauls of Bengal: wandering singers who made the body their temple.",
    "Meditation": "Practical talks on meditation — what it is, and what it is not.",
    "Responses To Questions": "Osho answering questions put to him directly by seekers.",
    "Interview With The World Press": "Osho in conversation with journalists from around the world.",
    "Talks In America": "Talks given during Osho's years in the United States.",
    "The World Tour": "Talks given while travelling, after the American years.",
    "Osho'S Vision For The World": "Osho on society, politics, education and the future he argued for.",
    "The Mantra Series": "Short talks built around a single mantra or sutra.",
    DEFAULT_CATEGORY: "A series of talks by Osho.",
}


def audio_url(path):
    """Percent-encode exactly once.

    English paths arrive raw (with literal spaces); Hindi paths arrive already
    encoded (`Hindi%20Audio`). Quoting blindly turns the latter's `%` into `%25`
    and the URL 404s, so normalise with unquote first.
    """
    return AUDIO_BASE + urllib.parse.quote(urllib.parse.unquote(path))


def to_seconds(hms):
    parts = [int(p) for p in re.findall(r"\d+", hms or "")]
    if len(parts) == 3:
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    if len(parts) == 2:
        return parts[0] * 60 + parts[1]
    return parts[0] if parts else 0


def hms(total):
    h, rem = divmod(int(total), 3600)
    m, s = divmod(rem, 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


def clean(t):
    t = unicodedata.normalize("NFKC", (t or "").strip())
    t = re.sub(r"\s+\d{2}-\d{2,3}$", "", t)
    return re.sub(r"\s{2,}", " ", t).strip()


def human_hours(seconds):
    hours = seconds / 3600.0
    if hours < 1:
        return f"{int(round(seconds / 60.0))} minutes"
    if hours < 2:
        return "about an hour and a half"
    return f"about {int(round(hours))} hours"


discourses = []
for entry in catalog:
    tracks = [t for t in entry["tracks"] if t.get("file")]
    if not tracks:
        continue

    slug = entry["slug"]
    title = clean(entry["title"])
    language = entry.get("language", "english")
    category = slug_to_category.get(slug, DEFAULT_CATEGORY)
    # Prefer a series-specific cover, but fall back to whatever is there: the
    # "no_image" files are misleadingly named — they hold a real photograph of
    # Osho, which is far better than showing nothing.
    image_file = next(
        (t.get("imageFile") for t in tracks
         if t.get("imageFile") and not re.search(r"no[_-]?image", t["imageFile"], re.I)),
        next((t.get("imageFile") for t in tracks if t.get("imageFile")), None),
    )

    out_tracks = [
        {
            "title": clean(t.get("title")) or f"{title} {i + 1:02d}",
            "url": audio_url(t["file"]),
            "duration": t.get("duration") or "00:00:00",
        }
        for i, t in enumerate(tracks)
    ]

    total = sum(to_seconds(t["duration"]) for t in out_tracks)
    n = len(out_tracks)
    blurb = BLURB.get(category, BLURB[DEFAULT_CATEGORY])
    lang_note = "" if language == "english" else " Spoken in Hindi."
    description = (
        f"{blurb}{lang_note} {n} talk{'s' if n != 1 else ''}, {human_hours(total)} of listening."
    )

    discourses.append({
        "id": slug,
        "title": title,
        "description": description,
        "image": IMAGE_BASE + image_file.lstrip("/") if image_file else "",
        "category": category,
        "duration": hms(total),
        "speaker": "Osho",
        "language": language,
        "tags": [category.lower(), language],
        "tracks": out_tracks,
    })

discourses.sort(key=lambda d: d["title"].lower())
for rank, d in enumerate(sorted(discourses, key=lambda x: -len(x["tracks"]))):
    d["popularity"] = max(0, 2000 - rank * 5)

counts = defaultdict(int)
for d in discourses:
    counts[d["category"]] += 1

categories = [
    {"id": re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-"),
     "name": name,
     "description": BLURB.get(name, BLURB[DEFAULT_CATEGORY]),
     "discourseCount": counts[name]}
    for name in sorted(counts, key=lambda n: -counts[n])
]

doc = {
    "version": 2,
    "source": "OshoWorld (https://oshoworld.com) — audio streamed directly from the source.",
    "categories": categories,
    "discourses": discourses,
}
with open("/tmp/osho2/discourses.json", "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=1)

by_lang = defaultdict(int)
for d in discourses:
    by_lang[d["language"]] += 1
print(f"{len(discourses)} discourses "
      f"({dict(by_lang)}), {sum(len(d['tracks']) for d in discourses)} tracks, "
      f"{len(categories)} categories")
for c in categories[:14]:
    print(f"  {c['discourseCount']:3d}  {c['name']}")
