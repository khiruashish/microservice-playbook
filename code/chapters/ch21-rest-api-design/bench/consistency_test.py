#!/usr/bin/env python3
"""
What a client sees when it pages through a list that changes underneath it.

A client scrolls restaurant 77's orders, newest first, 20 per page, 40 pages, while the list
changes between its requests. Two common situations:
  * new orders arrive:      3 new orders between page requests (a busy feed)
  * orders leave the list:  3 orders the client has already seen are removed (moved to another
                            status tab, purged by retention, cancelled and hidden)
Each runs with OFFSET paging and with keyset (cursor) paging, from the same starting data.

  python3 consistency_test.py "psql -h localhost -U postgres -d ch21"
"""
import random, shlex, subprocess, sys

PSQL = shlex.split(sys.argv[1] if len(sys.argv) > 1 else "psql -X -q -At -d ch21")
RESTAURANT, PAGE, PAGES = 77, 20, 40


def psql(sql):
    out = subprocess.run(PSQL + ["-X", "-q", "-At", "-c", sql], check=True, capture_output=True, text=True)
    return [line.split("|") for line in out.stdout.strip().splitlines() if line]


def run(style, inserts, removals, seed):
    psql(f"drop table if exists snapshot; create table snapshot as "
         f"select * from restaurant_orders where restaurant_id = {RESTAURANT}")
    rnd = random.Random(seed)
    seen, duplicates, removed, cursor = [], 0, set(), None
    base = f"select id, placed_at from restaurant_orders where restaurant_id = {RESTAURANT}"
    for page in range(PAGES):
        if style == "offset":
            rows = psql(base + f" order by placed_at desc, id desc limit {PAGE} offset {page * PAGE}")
        else:
            where = "" if cursor is None else f" and (placed_at, id) < ('{cursor[1]}', {cursor[0]})"
            rows = psql(base + where + f" order by placed_at desc, id desc limit {PAGE}")
        if not rows:
            break
        for r in rows:
            duplicates += r[0] in seen
            seen.append(r[0])
        cursor = rows[-1]
        if inserts:
            psql(f"insert into restaurant_orders (restaurant_id, customer_id, status, total_paise, placed_at) "
                 f"select {RESTAURANT}, -1, 'PLACED', 25000, now() from generate_series(1, {inserts})")
        for victim in rnd.sample(sorted(set(seen) - removed), removals):
            removed.add(victim)
            psql(f"delete from restaurant_orders where id = {victim}")
    # Rows that existed at the start, lie within the range the client scrolled through, were never
    # removed, and were never shown: the client silently missed them.
    oldest = psql(f"select min(placed_at) from snapshot where id in ({','.join(seen)})")[0][0]
    skipped = psql(f"select count(*) from snapshot where placed_at >= '{oldest}' "
                   f"and id not in ({','.join(seen)}) and id not in ({','.join(removed) or '0'})")[0][0]
    psql(f"delete from restaurant_orders where restaurant_id = {RESTAURANT}; "
         f"insert into restaurant_orders select * from snapshot; drop table snapshot;")
    return len(seen), duplicates, int(skipped)


print(f"restaurant {RESTAURANT}: a client reads {PAGES} pages of {PAGE}, newest first, while the list changes\n")
print(f"{'between page requests':<34} {'pagination':<10} {'rows shown':>11} {'duplicates':>11} {'missed':>7}")
for label, inserts, removals in [("3 new orders arrive", 3, 0), ("3 already-seen orders leave", 0, 3)]:
    for style in ["offset", "keyset"]:
        shown, dup, skipped = run(style, inserts, removals, 21)
        print(f"{label:<34} {style:<10} {shown:>11} {dup:>11} {skipped:>7}")
