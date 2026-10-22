#!/usr/bin/env python3
"""Collects the chapters' interview questions (section 9 of every chapter) into the Interview Prep pages: one list per
band (L1-L4) and one per company group, each question linked to its answer in the chapter.

    python3 tools/interview.py        (from the repository root; writes tools/generated/interview/)
"""
import glob
import html
import os
import re
from collections import Counter, defaultdict

import markdown
from markdown.extensions.toc import slugify

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOCS = os.path.join(ROOT, "docs")
OUT = os.path.join(ROOT, "tools", "generated", "interview")
PARTS = [(1, 8, "Foundations"), (9, 20, "Architecture & Patterns"), (21, 28, "Communication"), (29, 34, "Data"),
         (35, 41, "Security"), (42, 46, "Resilience"), (47, 52, "Observability"), (53, 60, "Deployment & Platform"),
         (61, 65, "Testing"), (66, 70, "Performance & Scalability"), (71, 77, "Governance & Organization")]
GROUPS = {
    "amazon": {"Amazon", "AWS-heavy shops"},
    "flipkart": {"Flipkart", "Myntra", "Walmart", "Meesho"},
    "goldman-sachs-jpmorgan": {"Goldman Sachs", "JP Morgan", "banks", "banks with EU operations", "PayPal", "Razorpay", "PhonePe", "Paytm"},
    "sap": {"SAP", "Oracle", "Red Hat"},
    "service-companies": {"TCS", "Infosys", "Cognizant", "Accenture", "Wipro", "Thoughtworks", "service companies", "consulting firms"},
    "food-and-mobility": {"Swiggy", "Zomato", "Uber", "Swiggy-/Zomato-style system design rounds", "Swiggy-style rounds", "Swiggy-style",
                          "Swiggy-/Zomato-style rounds", "Swiggy-/Zomato-style product companies", "Swiggy-style product companies"},
}


def heading_id(md_heading):
    text = re.sub(r"<[^>]+>", "", markdown.markdown(md_heading))
    return slugify(html.unescape(text).replace("\n", " "), "-")


def collect():
    qs = []
    for f in sorted(glob.glob(os.path.join(DOCS, "*", "ch[0-9][0-9]-*.md"))):
        page = os.path.relpath(f, DOCS)
        ch = int(re.search(r"ch(\d\d)", os.path.basename(f)).group(1))
        title = re.search(r'^title: "?(.*?)"?$', open(f, encoding="utf-8").read(), re.M).group(1)
        lines = open(f, encoding="utf-8").read().split("\n")
        for i, line in enumerate(lines):
            m = re.match(r"#### Q(\d+)\. (.*?) · (.*)$", line)
            if not m:
                continue
            levels = sorted(set(re.findall(r"badge-l([1-4])", m.group(3))))
            tags = [t.strip() for t in re.sub(r"<[^>]+>", "", m.group(3)).split("·")[1:] if t.strip()]
            body = []
            for l in lines[i + 1:]:
                if l.startswith(("#", "---")):
                    break
                body.append(l)
            answer = "\n".join(body).strip()
            short = re.sub(r"^\*\*Short answer:\*\*\s*", "", answer.split("\n\n")[0])
            qs.append({"ch": ch, "page": page, "chtitle": re.sub(r"^\d+\.\s*", "", title), "n": int(m.group(1)), "q": m.group(2),
                       "levels": levels, "tags": tags, "short": short, "id": heading_id(line[5:])})
    return qs


def part_of(ch):
    return next(name for lo, hi, name in PARTS if lo <= ch <= hi)


def render(qs, rel="../"):
    out, last_ch, last_part = [], None, None
    for q in qs:
        p = part_of(q["ch"])
        if p != last_part:
            out.append("\n### %s\n" % p)
            last_part = p
        if q["ch"] != last_ch:
            out.append("\n**[%d. %s](%s%s)**\n" % (q["ch"], q["chtitle"], rel, q["page"]))
            last_ch = q["ch"]
        lv = " ".join('<span class="badge badge-l%s">L%s</span>' % (l, l) for l in q["levels"])
        short = re.sub(r"\]\((?!http|\.\./|#)([^)]+)\)", r"](../%s/\1)" % os.path.dirname(q["page"]), q["short"].replace("\n", " "))
        out.append('??? question "%s"' % q["q"].replace('"', "'"))
        out.append("    %s %s" % (lv, " · ".join(q["tags"])))
        out.append("")
        out.append("    %s" % short)
        out.append("")
        out.append("    [Full answer in chapter %d](%s%s#%s)" % (q["ch"], rel, q["page"], q["id"]))
        out.append("")
    return "\n".join(out).strip() + "\n"


PART_DIRS = ["foundations", "architecture-patterns", "communication", "data", "security", "resilience", "observability",
             "deployment", "testing", "performance-scalability", "governance-org"]


def cheat_sheet():
    """every part's 'if your problem is... reach for...' table in one place; Part 1, which has none, from its ADRs"""
    sys_path = os.path.join(ROOT, "code", "foodfleet", "model")
    import sys
    sys.path.insert(0, sys_path)
    import adrs as adrlog
    decisions = {a["ch"]: a for a in adrlog.collect()}
    out = []
    for (lo, hi, name), d in zip(PARTS, PART_DIRS):
        out.append("### %s\n" % name)
        text = open(os.path.join(DOCS, d, "index.md"), encoding="utf-8").read().split("\n")
        rows = []
        for i, line in enumerate(text):
            if line.startswith("| If your problem is"):
                for r in text[i + 2:]:
                    if not r.startswith("|"):
                        break
                    rows.append(re.sub(r"\]\((ch\d\d-[^)]+)\)", r"](../%s/\1)" % d, r))
                break
        if rows:
            out.append("| If your problem is… | Reach for | Ch. | Measured |")
            out.append("|---|---|---|---|")
            out.extend(rows)
        else:
            out.append("| Ch. | FoodFleet's decision |")
            out.append("|---|---|")
            for ch in range(lo, hi + 1):
                a = decisions[ch]
                out.append("| [%d](../%s) | %s |" % (ch, a["page"], adrlog.short(a["title"], 140)))
        out.append("")
    return "\n".join(out)


def incidents():
    """every chapter's incidents as troubleshooting drills: context and symptoms shown, cause and fix behind a fold"""
    out = []
    count = 0
    for (lo, hi, name), d in zip(PARTS, PART_DIRS):
        out.append("### %s\n" % name)
        for f in sorted(glob.glob(os.path.join(DOCS, d, "ch[0-9][0-9]-*.md"))):
            page = os.path.relpath(f, DOCS)
            ch = int(re.search(r"ch(\d\d)", os.path.basename(f)).group(1))
            text = open(f, encoding="utf-8").read()
            for m in re.finditer(r'!!! pitfall "Incident \d+: (.*?)"\n((?:    .*\n|\n)+?)(?=\S|\Z)', text):
                fields = {}
                for k in ("Context", "Symptoms", "Root cause", "Fix"):
                    fm = re.search(r"\*\*%s\.\*\*\s*(.*?)(?=\n\s*\*\*[A-Z][a-z ]+\.\*\*|\Z)" % k, m.group(2), re.S)
                    if fm:
                        fields[k] = re.sub(r"\s*\n\s*", " ", fm.group(1)).strip()
                if len(fields) < 4:
                    continue
                fields = {k: re.sub(r"\]\((?!http|\.\./|#)([^)]+)\)", r"](../%s/\1)" % d, v) for k, v in fields.items()}
                count += 1
                out.append('??? bug "%d · %s (chapter %d)"' % (count, m.group(1).replace('"', "'"), ch))
                out.append("    **Context.** %s\n" % fields["Context"])
                out.append("    **Symptoms.** %s\n" % fields["Symptoms"])
                out.append('    ??? success "Root cause and fix"')
                out.append("        **Root cause.** %s\n" % fields["Root cause"])
                out.append("        **Fix.** %s\n" % fields["Fix"])
                out.append("        From [chapter %d](../%s#7-anti-patterns-pitfalls).\n" % (ch, page))
        out.append("")
    return "\n".join(out), count


def main():
    qs = collect()
    os.makedirs(OUT, exist_ok=True)
    stats = []
    for b in "1234":
        sel = [q for q in qs if b in q["levels"]]
        open(os.path.join(OUT, "band-l%s.md" % b), "w", encoding="utf-8").write(render(sel))
        stats.append("| L%s | %d |" % (b, len(sel)))
    for g, names in GROUPS.items():
        sel = [q for q in qs if names & set(q["tags"])]
        open(os.path.join(OUT, "%s.md" % g), "w", encoding="utf-8").write(render(sel))
        stats.append("| %s | %d |" % (g, len(sel)))
    # per-part counts by band, for the index
    rows = ["| Part | Questions | L1 | L2 | L3 | L4 |", "|---|---|---|---|---|---|"]
    for lo, hi, name in PARTS:
        sel = [q for q in qs if lo <= q["ch"] <= hi]
        rows.append("| %s | %d | %s |" % (name, len(sel), " | ".join(str(sum(1 for q in sel if b in q["levels"])) for b in "1234")))
    rows.append("| **All** | **%d** | %s |" % (len(qs), " | ".join("**%d**" % sum(1 for q in qs if b in q["levels"]) for b in "1234")))
    open(os.path.join(OUT, "counts.md"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
    tagc = Counter(t for q in qs for t in q["tags"])
    rows = ["| Company or round | Questions tagged |", "|---|---|"] + ["| %s | %d |" % (t, n) for t, n in tagc.most_common(20)]
    open(os.path.join(OUT, "tags.md"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
    open(os.path.join(OUT, "cheat-sheet.md"), "w", encoding="utf-8").write(cheat_sheet())
    drills, n = incidents()
    open(os.path.join(OUT, "incidents.md"), "w", encoding="utf-8").write(drills)
    print("%d incident drills" % n)
    for kind, name in (("Fintech", "fintech"), ("E-commerce", "ecommerce")):
        open(os.path.join(OUT, "analogues-%s.md" % name), "w", encoding="utf-8").write(analogues(kind))
    print("%d questions from %d chapters" % (len(qs), len({q["ch"] for q in qs})))
    print("\n".join(stats))
    return qs



def analogues(kind):
    """section 8's industry analogues of one kind ("Fintech" or "E-commerce"), with a one-sentence summary each"""
    rows = ["| Chapter | Analogue | In one sentence |", "|---|---|---|"]
    for f in sorted(glob.glob(os.path.join(DOCS, "*", "ch[0-9][0-9]-*.md"))):
        page = os.path.relpath(f, DOCS)
        ch = int(re.search(r"ch(\d\d)", os.path.basename(f)).group(1))
        lines = open(f, encoding="utf-8").read().split("\n")
        for i, line in enumerate(lines):
            m = re.match(r"### %s analogue: (.*?)( 🔴)?$" % kind, line)
            if not m:
                continue
            para = next(l for l in lines[i + 1:] if l.strip())
            first = re.split(r"(?<=[.!?])\s", re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", para))[0]
            rows.append("| [%d](../%s) | [%s](../%s#%s) | %s |" % (ch, page, m.group(1), page, heading_id(line[4:]), first.replace("|", "\\|")))
    return "\n".join(rows) + "\n"


if __name__ == "__main__":
    main()
