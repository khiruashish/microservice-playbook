#!/usr/bin/env python3
"""Collects the 77 ADRs from the chapters into one log (generated/adr-log.md and generated/adr-dates.md).

    python3 code/foodfleet/model/adrs.py      (from the repository root)
"""
import glob
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
DOCS = os.path.join(ROOT, "docs")
PARTS = [(1, 8, "Foundations"), (9, 20, "Architecture & Patterns"), (21, 28, "Communication"), (29, 34, "Data"),
         (35, 41, "Security"), (42, 46, "Resilience"), (47, 52, "Observability"), (53, 60, "Deployment & Platform"),
         (61, 65, "Testing"), (66, 70, "Performance & Scalability"), (71, 77, "Governance & Organization")]


def collect():
    adrs = []
    for f in sorted(glob.glob(os.path.join(DOCS, "*", "ch[0-9][0-9]-*.md"))):
        page = os.path.relpath(f, DOCS)
        part_dir = os.path.dirname(page)
        lines = open(f, encoding="utf-8").read().split("\n")
        for i, line in enumerate(lines):
            m = re.match(r'!!! decision "ADR-(\d{3}): (.*)"\s*$', line)
            if not m:
                continue
            body = []
            for l in lines[i + 1:]:
                if l.strip() and not l.startswith("    "):
                    break
                body.append(l)
            while body and not body[-1].strip():
                body.pop()
            text = "\n".join(body)

            def fix(link):
                target = link.group(2)
                if target.startswith(("http", "#", "../", "mailto:")):
                    return link.group(0)
                if target.startswith("ch") and ".md" in target:
                    return "%s../%s/%s)" % (link.group(1), part_dir, target)
                return link.group(0)

            text = re.sub(r"(\]\()([^)\s]+)\)", fix, text)
            status = re.search(r"\*\*Date:\*\* (\d{4}-\d{2}-\d{2})", text)
            amended = re.search(r"\*\*Amended:\*\* (\d{4}-\d{2}-\d{2})", text)
            ch = int(re.search(r"ch(\d\d)", os.path.basename(f)).group(1))
            adrs.append({"n": int(m.group(1)), "title": m.group(2), "page": page, "ch": ch, "body": text,
                         "date": status.group(1) if status else None, "amended": amended.group(1) if amended else None})
    return sorted(adrs, key=lambda a: a["n"])


def short(title, n=110):
    return title if len(title) <= n else title[:title.rfind(" ", 0, n)].rstrip(",;") + " …"


def main():
    adrs = collect()
    assert [a["n"] for a in adrs] == list(range(1, len(adrs) + 1)), "ADR numbers must be 1..N with no gaps"
    out = []
    for lo, hi, name in PARTS:
        part = [a for a in adrs if lo <= a["ch"] <= hi]
        out.append("### Part %d · %s" % (PARTS.index((lo, hi, name)) + 1, name))
        out.append("")
        for a in part:
            out.append('<a id="adr-%03d"></a>' % a["n"])
            out.append("")
            out.append('??? decision "ADR-%03d · %s · %s"' % (a["n"], a["date"], short(a["title"]).replace('"', "'")))
            out.append("    **Decision in full:** %s" % a["title"])
            out.append("")
            out.append(a["body"])
            out.append("")
            out.append("    From [chapter %d](../%s)." % (a["ch"], a["page"]))
            out.append("")
    with open(os.path.join(HERE, "generated", "adr-log.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(out).rstrip() + "\n")

    # dates: the log should read in decision order
    rows = ["| ADR | Date | Problem |", "|---|---|---|"]
    best = []  # longest non-decreasing run, as chapter 73's lint does
    dates = [a["date"] for a in adrs]
    import bisect
    tails, idx, prev = [], [], [-1] * len(dates)
    for i, d in enumerate(dates):
        k = bisect.bisect_right([dates[j] for j in tails], d)
        if k > 0:
            prev[i] = tails[k - 1]
        if k == len(tails):
            tails.append(i)
        else:
            tails[k] = i
    keep, j = set(), tails[-1]
    while j != -1:
        keep.add(j)
        j = prev[j]
    for i, a in enumerate(adrs):
        problems = []
        if i not in keep:
            later = [x for x in adrs[i + 1:] if x["date"] < a["date"]]
            earlier = [x for x in adrs[:i] if x["date"] > a["date"]]
            if later:
                first = min(later, key=lambda x: x["date"])
                problems.append("dated after %d later-numbered decisions (the earliest: ADR-%03d, %s)" % (len(later), first["n"], first["date"]))
            if earlier:
                last = max(earlier, key=lambda x: x["date"])
                problems.append("dated before %d earlier-numbered decisions (the latest: ADR-%03d, %s)" % (len(earlier), last["n"], last["date"]))
        if a["amended"]:
            problems.append("amended in place on %s" % a["amended"])
        if problems:
            rows.append("| [ADR-%03d](#adr-%03d) | %s | %s |" % (a["n"], a["n"], a["date"], "; ".join(problems)))
    with open(os.path.join(HERE, "generated", "adr-dates.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(rows) + "\n")
    print("%d ADRs, %s to %s; %d out of order, %d amended in place" % (
        len(adrs), min(dates), max(dates), len(adrs) - len(keep), sum(1 for a in adrs if a["amended"])))


if __name__ == "__main__":
    main()
