# The name pattern from Measure's ReDoS table, in Python's re (no memoisation of failed positions).
import re
import sys
import time

pattern = re.compile(r"^(\w+\s?)*$")
row = "%-58s" % ("Python %d.%d re: ^(\\w+\\s?)*$" % sys.version_info[:2])
for n in (16, 20, 24, 28):
    t = time.perf_counter()
    pattern.match("a" * n + "!")
    ms = (time.perf_counter() - t) * 1000
    row += " %-10s" % ("%.0f ms" % ms if ms >= 10 else "%.2f ms" % ms)
    if ms > 10_000:
        break
print(row)
