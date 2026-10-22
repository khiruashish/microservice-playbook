#!/usr/bin/env python3
"""Collects every chapter's further-reading list into one page, by part, with each entry once and the chapters that
cite it. Writes tools/generated/reading.md.  python3 tools/reading.py"""
import glob
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOCS = os.path.join(ROOT, "docs")
PARTS = [("foundations", "Foundations"), ("architecture-patterns", "Architecture & Patterns"), ("communication", "Communication"),
         ("data", "Data"), ("security", "Security"), ("resilience", "Resilience"), ("observability", "Observability"),
         ("deployment", "Deployment & Platform"), ("testing", "Testing"), ("performance-scalability", "Performance & Scalability"),
         ("governance-org", "Governance & Organization")]


def key(entry):
    """an entry's identity: its title in italics or its first link text, lower-cased"""
    m = re.search(r"\*([^*]+)\*", entry) or re.search(r"\[([^\]]+)\]", entry)
    return (m.group(1) if m else entry).lower().strip()


def main():
    seen = {}
    out = []
    total = 0
    for d, name in PARTS:
        entries = []
        for f in sorted(glob.glob(os.path.join(DOCS, d, "ch[0-9][0-9]-*.md"))):
            ch = int(re.search(r"ch(\d\d)", os.path.basename(f)).group(1))
            text = open(f, encoding="utf-8").read()
            m = re.search(r"^### Further reading\n(.*?)(?=^#|^---|\Z)", text, re.M | re.S)
            if not m:
                continue
            for line in m.group(1).splitlines():
                if not line.startswith("- "):
                    continue
                entry = re.sub(r"\]\((?!http|\.\./|#)([^)]+)\)", r"](../%s/\1)" % d, line[2:].strip())
                k = key(entry)
                if k in seen:
                    seen[k][1].append(ch)
                    continue
                seen[k] = [entry, [ch]]
                entries.append(k)
        out.append("### %s\n" % name)
        for k in entries:
            entry, chs = seen[k]
            out.append("- %s · <small>ch. %s</small>" % (entry, ", ".join(str(c) for c in sorted(set(chs)))))
            total += 1
        out.append("")
    open(os.path.join(ROOT, "tools", "generated", "reading.md"), "w", encoding="utf-8").write("\n".join(out))
    multi = sorted((v for v in seen.values() if len(set(v[1])) > 1), key=lambda v: -len(set(v[1])))
    rows = ["| Cited by | Work |", "|---|---|"] + ["| %d chapters (%s) | %s |" % (len(set(c)), ", ".join(map(str, sorted(set(c)))), e) for e, c in multi[:15]]
    open(os.path.join(ROOT, "tools", "generated", "reading-most-cited.md"), "w", encoding="utf-8").write("\n".join(rows) + "\n")
    print("%d distinct works, %d cited by more than one chapter" % (total, len(multi)))


if __name__ == "__main__":
    main()
