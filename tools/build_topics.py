#!/usr/bin/env python3
"""Tag every series with life themes and write them back into discourses.json.

Series titles are mostly master names and scripture references — 'Ah This',
'Bin Bati Bin Tel' — which tell a newcomer nothing about what the talks are
*about*. The source publishes no topic taxonomy (its topic pages are empty), so
themes have to be assigned here.

Two passes, deliberately in this order:

  1. Keyword matching over the title, in English and in transliterated and
     Devanagari Hindi. Precise where it fires, silent where it does not.
  2. A category fallback, so every series lands in at least one theme rather
     than being unreachable from the topic browser.

This is a first pass and is meant to be edited by hand — TOPICS and KEYWORDS
are the only two things to touch.
"""
import json, re, sys
from collections import Counter

CATALOG = "app/src/main/assets/discourses.json"

# id -> (display name, one-line description)
TOPICS = {
    "start-here":    ("Start Here", "Short, approachable series to begin with"),
    "love":          ("Love & Relationships", "On loving, and on being with others"),
    "fear":          ("Fear & Anxiety", "On dread, insecurity and courage"),
    "death":         ("Death & Dying", "On mortality, grief and letting go"),
    "anger":         ("Anger & Emotions", "On rage, jealousy and feeling fully"),
    "aloneness":     ("Aloneness", "On solitude, and the difference from loneliness"),
    "meditation":    ("Meditation & Silence", "Practical talks on sitting quietly"),
    "freedom":       ("Freedom & Rebellion", "On authority, conditioning and breaking free"),
    "joy":           ("Joy & Celebration", "On bliss, laughter and delight"),
    "mind":          ("Mind & Thought", "On thinking, doubt and the watcher"),
    "self":          ("Who Am I", "On identity, ego and self-knowing"),
    "devotion":      ("Devotion & Surrender", "The path of the heart"),
    "society":       ("Society & Work", "On money, politics, education and the world"),
    "search":        ("The Search", "On seeking, the path, and the journey inward"),
    "awakening":     ("Awakening", "On enlightenment and the moment of seeing"),
}

# Matched case-insensitively against the title. Hindi appears both
# transliterated and in Devanagari, so both forms are listed.
KEYWORDS = {
    "love":       ["love", "prem", "प्रेम", "pyar", "प्यार", "beloved", "heart", "sambandh"],
    "fear":       ["fear", "bhay", "भय", "dar", "डर", "anxiety", "insecurit", "chinta", "चिंता"],
    "death":      ["death", "dying", "mrityu", "मृत्यु", "maran", "मरण", "immortal", "amrit", "अमृत"],
    "anger":      ["anger", "krodh", "क्रोध", "jealous", "violence", "hinsa", "हिंसा"],
    "aloneness":  ["alone", "aloneness", "akela", "अकेला", "ekant", "एकांत", "solitude"],
    "meditation": ["meditation", "dhyan", "ध्यान", "samadhi", "समाधि", "silence", "maun", "मौन",
                   "vipassana", "zazen", "sadhana", "साधना", "yoga", "tantra", "vigyan bhairav"],
    "freedom":    ["freedom", "swatantra", "स्वतंत्र", "rebel", "revolution", "kranti", "क्रांति",
                   "bondage", "liberation", "moksha", "मोक्ष", "mukti", "मुक्ति"],
    "joy":        ["joy", "bliss", "anand", "आनंद", "celebration", "utsav", "उत्सव", "laughter",
                   "ecstas", "khushi"],
    "mind":       ["mind", "man ", "मन", "thought", "vichar", "विचार", "no-mind", "doubt",
                   "consciousness", "chetna", "चेतना", "awareness", "hosh", "होश"],
    "self":       ["who am i", "main kaun", "मैं कौन", "self", "swayam", "स्वयं", "atma", "आत्म",
                   "ego", "ahankar", "अहंकार", "identity"],
    "devotion":   ["bhakti", "भक्ति", "devotion", "surrender", "samarpan", "समर्पण", "prayer",
                   "prarthana", "प्रार्थना", "guru", "गुरु", "kabir", "meera", "nanak", "sufi"],
    "society":    ["society", "samaj", "समाज", "politic", "education", "shiksha", "शिक्षा",
                   "money", "work", "world", "vision", "future", "bharat", "भारत", "press",
                   "interview"],
    "awakening":  ["enlighten", "awaken", "sambodhi", "संबोधि", "buddha", "बुद्ध", "satori",
                   "realiz", "truth", "satya", "सत्य", "nirvana", "निर्वाण", "zen", "जेन",
                   "mahavir", "महावीर", "jin ", "jain", "ashtavakra", "अष्टावक्र"],
}

# A title far more often names a master or a scripture than a subject, so these
# carry most of the coverage.
KEYWORDS["devotion"] += ["geeta", "गीता", "gita", "krishna", "कृष्ण", "narad", "नारद",
                         "bhaj", "भज", "jyotish", "ज्योतिष", "smriti", "स्मृति"]
KEYWORDS["self"] += ["upanishad", "उपनिषद", "kathopanishad", "ishavashya", "vedant", "वेदांत",
                     "gyan", "ज्ञान", "darshan", "दर्शन"]
KEYWORDS["meditation"] += ["sahaj", "सहज", "sutra", "सूत्र", "yog", "योग"]
# Hindi titles lean poetic — life, the search, the door, the journey — which is
# its own theme rather than a poor fit for one of the others.
KEYWORDS["search"] = ["jeevan", "जीवन", "jevan", "khoj", "खोज", "yatra", "यात्रा",
                      "marg", "मार्ग", "path", "dwar", "द्वार", "kadam", "कदम",
                      "pukar", "पुकार", "seeker", "journey", "way", "raah", "राह"]

# Every series should be reachable from at least one theme.
CATEGORY_FALLBACK = {
    "meditation": ["meditation"], "yoga": ["meditation"], "tantra": ["meditation"],
    "zen": ["meditation", "awakening"], "buddha": ["awakening"], "tao": ["freedom"],
    "sufi": ["devotion"], "kabir": ["devotion"], "baul": ["devotion"],
    "upanishad": ["self"], "jesus": ["devotion"], "krishna": ["devotion"],
    "vision": ["society"], "america": ["society"], "press": ["society"],
    "world tour": ["society"], "question": ["mind"], "prashnottar": ["mind"],
    "mystic": ["awakening"], "mantra": ["meditation"], "shiv": ["meditation"],
    "nanad": ["devotion"], "nanak": ["devotion"],
}

# The Hindi sant poets each have their own category; all belong to the same
# devotional stream.
for _sant in ("paltu", "पलटू", "dadu", "दादू", "dharamdas", "धर्मदास", "yari", "यारी",
              "laal", "लाल", "dayabai", "दयाबाई", "malook", "मलूक", "gulal", "गुलाल",
              "rajab", "रजब", "sundardas", "सुंदरदास", "charandas", "चरणदास",
              "bhikha", "भीखा", "raidas", "रैदास", "jagjeevan", "जगजीवन", "wajid", "वाजिद",
              "gorakh", "गोरख", "daria", "दरिया", "dariya", "meera", "मीरा", "farid", "फरीद"):
    CATEGORY_FALLBACK[_sant] = ["devotion"]
CATEGORY_FALLBACK["pravachan"] = ["search"]
CATEGORY_FALLBACK["प्रवचन"] = ["search"]


# ---------------------------------------------------------------------------
# Curated overrides. These REPLACE the heuristic result for a series.
#
# The keyword pass can only see the title, so it mis-files anything whose
# subject is not in its name — "Journey to the Heart: Sufism" landed under
# Love because of the word "heart". These are assigned from what the series is
# actually about, and are the right place to correct anything that reads wrong
# in the app. Unknown ids are reported at the end of a run rather than ignored.
# ---------------------------------------------------------------------------
OVERRIDES = {
    # Zen
    "a-bird-on-the-wing-01-11": ["freedom", "awakening"],
    "a-sudden-clash-of-thunder-01-10": ["awakening"],
    "ah-this-01-08": ["awakening", "meditation"],
    "ancient-music-in-the-pines-01-09": ["meditation", "awakening"],
    "and-the-flowers-showered-01-11": ["awakening", "joy"],
    "dang-dang-doko-dang-01-10": ["awakening"],
    "no-water-no-moon-series": ["awakening", "meditation"],
    "nirvana-the-last-nightmare-series": ["awakening", "freedom"],
    "no-mind-the-flowers-of-eternity-series": ["mind", "meditation"],
    "the-book-of-nothing-hsin-hsin-ming-series": ["mind", "meditation"],
    "bodhidharma-the-greatest-zen-master-01-20": ["awakening"],
    "live-zen-series": ["awakening", "meditation"],
    "god-is-dead-zen-is-the-only-living-truth-series": ["awakening", "freedom"],
    "returning-to-the-source-series": ["search", "awakening"],
    "dogen-the-zen-master-a-search-and-a-fulfilment-01-08": ["search", "awakening"],
    "take-it-easy-series": ["joy", "meditation"],
    "christianity-and-zen-01-08": ["devotion", "awakening"],
    "ma-tzu-the-empty-mirror-series": ["awakening", "self"],
    "nansen-point-of-departure-series": ["awakening", "search"],
    "rinzai-master-of-the-irrational-series": ["awakening", "freedom"],
    "joshu-the-lions-roar-series": ["awakening"],
    "isan-no-footprints-in-the-blue-sky-series": ["awakening", "meditation"],
    "hyakujo-the-everest-of-zen-with-bashos-haikus-series": ["awakening", "meditation"],
    "ta-hui-great-zen-master-series": ["awakening"],
    "one-seed-makes-the-whole-earth-green-series": ["awakening", "search"],
    "communism-and-zen-fire-zen-wind-01-07": ["society", "freedom"],

    # Buddha
    "the-book-of-wisdom-buddha-series": ["meditation", "love"],
    "the-buddha-the-emptiness-of-the-heart-series": ["meditation", "self"],
    "om-mani-padme-hum-series": ["meditation", "devotion"],

    # Tao
    "tao-the-pathless-path-series": ["search", "freedom"],
    "tao-vol-1-absolute-tao-the-three-treasures-series": ["freedom", "self"],
    "tao-vol-2-living-tao-the-three-treasures-series": ["freedom", "self"],
    "tao-vol-3-undone-the-three-treasures-series": ["freedom", "self"],
    "tao-vol-4-talking-the-three-treasures-series": ["freedom", "self"],

    # Sufi and devotional
    "journey-to-the-heart-sufism-series": ["devotion", "search"],
    "sufis-people-of-the-path-series": ["devotion", "search"],
    "just-like-that-series": ["devotion", "joy"],
    "the-beloved-series": ["devotion", "love"],
    "come-come-yet-again-come-01-15": ["devotion"],
    "ecstasy-forgotten-language-01-10": ["joy", "devotion"],
    "fish-in-the-sea-is-not-thirsty-01-15": ["devotion", "search"],

    # Jesus
    "i-say-unto-you-series": ["devotion", "love"],
    "come-follow-to-you-vol-01": ["devotion"],
    "come-follow-to-you-vol-02": ["devotion"],
    "come-follow-to-you-vol-03": ["devotion"],
    "come-follow-to-you-vol-04": ["devotion"],
    "jesus-crucified-again-series": ["society", "devotion"],

    # Tantra and yoga
    "tantra-the-supreme-understanding-series": ["meditation", "freedom"],
    "tantra-vision-vol-1-tantra-experience-series": ["meditation", "love"],
    "tantra-vision-vol-2-tantric-transformation-series": ["meditation", "love"],

    # Upanishads and self-enquiry
    "that-art-thou-series": ["self", "awakening"],
    "i-am-that-series": ["self", "awakening"],
    "osho-upanishad-series": ["self", "search"],
    "sat-chit-anand-series": ["self", "joy"],
    "philosophia-perennisia-series": ["self", "search"],
    "philosophia-ultima-series": ["self", "search"],

    # Death
    "the-art-of-dying-series": ["death", "meditation"],
    "from-death-to-deathlessness-series": ["death", "awakening"],

    # Transformation series
    "from-bondage-to-freedom-01-43": ["freedom"],
    "from-darkness-to-light-series": ["awakening", "search"],
    "from-ignorance-to-innocence-series": ["awakening", "mind"],
    "from-misery-to-enlightenment-series": ["awakening", "fear"],
    "from-personality-to-individuality-series": ["self", "freedom"],
    "from-the-false-to-the-truth-series": ["self", "awakening"],
    "from-unconscious-to-consciousness-series": ["mind", "awakening"],

    # Society and the world
    "rebellious-spirit-series": ["freedom", "society"],
    "socrates-poisoned-again-after-twenty-five-centuries-series": ["society", "freedom"],
    "press-conference-01-11": ["society"],
    "sermons-in-stones-series": ["society", "freedom"],

    # Beginnings
    "light-on-the-path-by-osho-01-38": ["search"],
    "my-way-way-of-white-clouds-series": ["search", "freedom"],
    "guida-spirituale-series": ["search", "devotion"],
    "i-celebrate-myself-series": ["joy", "self"],
    "beyond-psychology-01-44": ["mind", "self"],
    "beyond-enlightenment-01-32": ["awakening"],
    "be-still-and-know-01-10": ["meditation"],
}


def topics_for(series):
    override = OVERRIDES.get(series["id"])
    if override:
        return sorted(set(override))

    haystack = f"{series['title']} {series.get('category','')}".lower()
    found = {t for t, words in KEYWORDS.items() if any(w in haystack for w in words)}

    if not found:
        category = series.get("category", "").lower()
        for key, themes in CATEGORY_FALLBACK.items():
            if key in category:
                found.update(themes)
                break

    return sorted(found)


def pick_start_here(discourses, limit=16):
    """Choose a small opening shelf.

    Short enough to finish, and only series with their own cover — the shared
    fallback photo makes a browsing shelf look broken. Balanced across the two
    languages so neither audience opens the app to a list it cannot use.
    """
    def eligible(s):
        return 4 <= len(s["tracks"]) <= 10 and "no_image" not in s.get("image", "")

    chosen = []
    for language in ("english", "hindi"):
        pool = [s for s in discourses if s.get("language") == language and eligible(s)]
        pool.sort(key=lambda s: -s.get("popularity", 0))
        chosen += pool[: limit // 2]
    return {s["id"] for s in chosen}


def main():
    doc = json.load(open(CATALOG, encoding="utf-8"))
    counts, untagged = Counter(), []
    start_here = pick_start_here(doc["discourses"])

    for series in doc["discourses"]:
        topics = topics_for(series)
        if series["id"] in start_here:
            topics = sorted(set(topics) | {"start-here"})
        series["topics"] = topics
        for t in topics:
            counts[t] += 1
        if not topics:
            untagged.append(series["title"])

    doc["topics"] = [
        {"id": tid, "name": name, "description": desc, "seriesCount": counts[tid]}
        for tid, (name, desc) in TOPICS.items()
        if counts[tid] > 0
    ]

    with open(CATALOG, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)

    known = {d["id"] for d in doc["discourses"]}
    stale = sorted(set(OVERRIDES) - known)

    print(f"{len(doc['discourses'])} series tagged; {len(untagged)} untagged")
    print(f"curated overrides: {len(OVERRIDES)} ({len(stale)} stale)")
    for sid in stale:
        print(f"   STALE OVERRIDE (no such series): {sid}", file=sys.stderr)
    for tid, (name, _) in TOPICS.items():
        print(f"  {counts[tid]:4d}  {name}")
    if untagged:
        print("\nuntagged:", file=sys.stderr)
        for t in untagged[:25]:
            print(f"   {t}", file=sys.stderr)


if __name__ == "__main__":
    main()
