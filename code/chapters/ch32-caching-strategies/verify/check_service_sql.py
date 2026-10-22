"""Runs the service's own SQL and Lua, exactly as written in the Java sources, against PostgreSQL and Redis:
the migration, price writes and fills through PUT_IF_NEWER, the top-restaurants aggregation, and the view
flush (claim, apply, crash and repeat). Uses database c32svc (recreated) and FLUSHES the Redis instance.
  PGHOST=... PGPORT=... PGUSER=postgres REDIS_PORT=... python3 check_service_sql.py"""
import os
import re
import sys

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "foodfleet", "ch32")
failures = 0


def check(label, ok, detail=""):
    global failures
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'} {label}{'' if ok else '  -> ' + str(detail)}")


def blocks(path):
    """static final String X = \"\"\"...\"\"\"; and RedisScript.of(\"\"\"...\"\"\", ...) text blocks, by name."""
    text = open(os.path.join(JAVA, path)).read()
    found = {}
    for name, body in re.findall(r'static final (?:String|RedisScript<\w+>) (\w+) = (?:RedisScript\.of\()?"""\n(.*?)"""', text, re.S):
        lines = body.split("\n")
        indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
        found[name] = "\n".join(l[indent:] for l in lines).rstrip()
    return found


def inline_block(path, needle):
    text = open(os.path.join(JAVA, path)).read()
    for body in re.findall(r'\.sql\("""\n(.*?)"""\)', text, re.S):
        if needle in body:
            lines = body.split("\n")
            indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
            return "\n".join(l[indent:] for l in lines).rstrip()
    raise KeyError(needle)


def sql(statement, **params):
    def literal(v):
        return repr(v) if isinstance(v, int) else "'" + str(v).replace("'", "''") + "'"
    return re.sub(r"(?<!:):([A-Za-z]\w*)", lambda m: literal(params[m.group(1)]), statement)


def main():
    admin = wire.Pg("postgres")
    admin.query("DROP DATABASE IF EXISTS c32svc")
    admin.query("CREATE DATABASE c32svc")
    admin.close()
    pg = wire.Pg("c32svc")
    pg.query(open(os.path.join(SRC, "resources", "db", "migration", "V1__caching.sql")).read())
    check("Flyway migration V1 applies", True)
    r = wire.Redis()
    r("FLUSHALL")

    price = blocks("menu/ItemPriceCache.java")["PUT_IF_NEWER"]
    update = inline_block("menu/ItemPrices.java", "UPDATE items SET price_paise")
    pg.query("INSERT INTO items (item_id, price_paise) VALUES (100, 32000)")

    # a reader misses and loads version 1, then stalls; meanwhile a writer commits version 2 and writes through
    v1 = pg.query("SELECT item_id, version, price_paise FROM items WHERE item_id = 100")[0]
    v2 = pg.query(sql(update, price=34000, id=100))[0]
    check("the price update returns the new version", v2 == ("100", "2", "34000"), v2)
    check("the writer's write-through after commit is stored", r("EVAL", price, 1, "price:100", v2[1], v2[2], 1800000) == 1)
    check("the stalled reader's late fill with version 1 is refused", r("EVAL", price, 1, "price:100", v1[1], v1[2], 1800000) == 0)
    check("the same version again is refused (idempotent)", r("EVAL", price, 1, "price:100", 2, 34000, 1800000) == 0)
    check("the cache holds 2:34000 with a TTL", r("GET", "price:100") == "2:34000" and 0 < r("PTTL", "price:100") <= 1800000)
    check("an empty cache accepts a fill", r("EVAL", price, 1, "price:101", 1, 5000, 1800000) == 1)

    # top restaurants
    top = blocks("top/TopRestaurants.java")["TOP"]
    pg.query("INSERT INTO reviews (restaurant_id, rating) VALUES (1, 5), (1, 4), (2, 3), (2, 3), (3, 5)")
    out = pg.query(sql(top, minReviews=2))[0][0]
    check("TOP returns ranked JSON, restaurants with too few reviews left out",
          out.startswith('[{"restaurant_id":1,"rating":4.50,"reviews":2}') and '"restaurant_id":3' not in out, out)
    check("TOP returns [] when nothing qualifies", pg.query(sql(top, minReviews=100))[0][0] == "[]")

    # the view flush
    flusher = blocks("views/ViewFlusher.java")
    claim, apply = flusher["CLAIM"], flusher["APPLY"]
    keys = ("views", "views:flushing", "views:batch")
    for _ in range(25):
        r("HINCRBY", "views", 100, 1)
    r("HINCRBY", "views", 101, 1)
    got = r("EVAL", claim, 3, *keys, "batch-1")
    check("CLAIM renames the pending hash and returns the batch id first", got[0] == "batch-1" and
          dict(zip(got[1::2], got[2::2])) == {"100": "25", "101": "1"} and r("EXISTS", "views") == 0, got)
    r("HINCRBY", "views", 100, 1)                                 # a view arriving during the flush
    counts = "{" + ",".join(f'"{k}":{v}' for k, v in zip(got[1::2], got[2::2])) + "}"
    pg.query(sql(apply, batch=got[0], counts=counts))
    # the flusher dies here, before deleting views:flushing; the next run claims the same batch again
    again = r("EVAL", claim, 3, *keys, "batch-2")
    check("after a crash, CLAIM returns the unfinished batch with its original id", again == got, again)
    pg.query(sql(apply, batch=again[0], counts=counts))
    check("applying the same batch twice counts it once",
          pg.query("SELECT item_id, views FROM item_views ORDER BY item_id") == [("100", "25"), ("101", "1")])
    r("DEL", "views:flushing", "views:batch")
    nxt = r("EVAL", claim, 3, *keys, "batch-3")
    check("the next flush takes the view that arrived meanwhile, under a new batch id", nxt == ["batch-3", "100", "1"], nxt)
    pg.query(sql(apply, batch=nxt[0], counts='{"100":1}'))
    check("totals are exact", pg.query("SELECT views FROM item_views WHERE item_id = 100") == [("26",)])
    r("DEL", "views:flushing", "views:batch")
    check("CLAIM with nothing pending returns nothing", r("EVAL", claim, 3, *keys, "batch-4") == [])

    pg.close(), r.close()
    print(f"\n{'all checks passed' if failures == 0 else str(failures) + ' check(s) failed'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
