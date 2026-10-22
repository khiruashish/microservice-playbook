"""Runs the service's own SQL and Lua, exactly as written in the Java sources, against PostgreSQL and
Redis: the Flyway migration, then the statements the container test exercises. A way to check them with
nothing but python3, psql-reachable PostgreSQL 16+ and Redis 7. Uses database c29svc (recreated) and
FLUSHES the Redis instance."""
import datetime
import os
import re
import sys

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "foodfleet", "ch29")
failures = 0


def check(label, ok, detail=""):
    global failures
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'} {label}{'' if ok else '  -> ' + str(detail)}")


def constants(path):
    """static final String NAME = \"\"\"...\"\"\"; and RedisScript.of(\"\"\"...\"\"\", ...) text blocks."""
    text = open(os.path.join(JAVA, path)).read()
    found = {}
    for name, body in re.findall(r'static final (?:String|RedisScript<Long>) (\w+) = (?:RedisScript\.of\()?"""\n(.*?)"""', text, re.S):
        lines = body.split("\n")
        indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
        found[name] = "\n".join(l[indent:] for l in lines).rstrip()
    return found


def sql(statement, **params):
    """Named parameters as Spring's NamedParameterJdbcTemplate sees them (':name', not '::type')."""
    def literal(v):
        if isinstance(v, bool):
            return "true" if v else "false"
        if isinstance(v, (int, float)):
            return repr(v)
        return "'" + str(v).replace("'", "''") + "'"
    missing = set(re.findall(r"(?<!:):([A-Za-z]\w*)", statement)) - set(params)
    assert not missing, f"unbound parameters {missing}"
    return re.sub(r"(?<!:):([A-Za-z]\w*)", lambda m: literal(params[m.group(1)]), statement)


def main():
    admin = wire.Pg("postgres")
    admin.query("DROP DATABASE IF EXISTS c29svc")
    admin.query("CREATE DATABASE c29svc")
    admin.close()
    pg = wire.Pg("c29svc")
    pg.query(open(os.path.join(SRC, "resources", "db", "migration", "V1__catalog_and_riders.sql")).read())
    check("Flyway migration V1 applies", True)

    cards = constants("catalog/MenuCards.java")
    catalog = constants("catalog/CatalogService.java")
    search = constants("catalog/DishSearch.java")
    projector = constants("projection/PriceProjector.java")
    cache = constants("projection/PriceCache.java")
    riders = constants("riders/RiderLocations.java")
    history = constants("riders/PositionHistory.java")

    pg.query("""INSERT INTO restaurants VALUES (1, 'Meghana Kitchen', 'Bengaluru');
        INSERT INTO menu_sections VALUES (10, 1, 'Mains', 1), (11, 1, 'Desserts', 2), (12, 1, 'Drinks', 3);
        INSERT INTO menu_items (id, section_id, restaurant_id, name, description, price_paise, is_veg, position) VALUES
          (100, 10, 1, 'Paneer Butter Masala', 'cottage cheese, tomato, butter', 26000, true, 1),
          (101, 10, 1, 'Hyderabadi Chicken Biryani', 'basmati rice, chicken, saffron', 32000, false, 2),
          (102, 11, 1, 'Gulab Jamun', 'milk dumplings in syrup', 9000, true, 1);
        INSERT INTO option_groups VALUES (1000, 101, 'Portion', 1, 1);
        INSERT INTO options VALUES (5000, 1000, 'Regular', 0), (5001, 1000, 'Large', 12000);""")

    v = pg.query(sql(cards["REBUILD"], restaurantId=1))
    check("MenuCards.REBUILD creates the card at version 1", v == [("1",)], v)

    # CatalogService.change: item, outbox, card -- one transaction
    pg.query("BEGIN")
    row = pg.query(sql(catalog["UPDATE_ITEM"], price=34000, available=True, id=101))
    pg.query(sql(catalog["OUTBOX"], item=101, restaurant=int(row[0][0]), price=34000, available=True, version=int(row[0][1])))
    v = pg.query(sql(cards["REBUILD"], restaurantId=int(row[0][0])))
    pg.query("COMMIT")
    check("CatalogService.UPDATE_ITEM returns restaurant and new version", row == [("1", "2")], row)
    check("the card is rebuilt at version 2", v == [("2",)], v)
    card = pg.query("SELECT card::text FROM menu_cards WHERE restaurant_id = 1")[0][0]
    check("the card shows the new price and the options", '"price": 34000' in card and '"Large"' in card, card[:200])
    check("a section without items renders as [] (coalesce), not null", '"name": "Drinks", "items": []' in card, card[-120:])
    missing = pg.query(sql(catalog["UPDATE_ITEM"], price=100, available=True, id=999))
    check("UPDATE_ITEM on an unknown item returns no row", missing == [], missing)

    # PriceProjector.CLAIM inside a transaction, then PriceCache.APPLY_IF_NEWER in Redis
    r = wire.Redis()
    r("FLUSHDB")
    pg.query("BEGIN")
    claimed = pg.query(sql(projector["CLAIM"], batch=500))
    check("PriceProjector.CLAIM returns the outbox row", claimed == [(claimed[0][0], "101", "34000", "t", "2")] if claimed else False, claimed)
    applied = r("EVAL", cache["APPLY_IF_NEWER"], 1, "price:101", 2, 34000, 1)
    pg.query("COMMIT")
    check("APPLY_IF_NEWER applies version 2", applied == 1, applied)
    check("the outbox is empty after commit", pg.query("SELECT count(*) FROM catalog_outbox") == [("0",)])
    check("an older version is refused", r("EVAL", cache["APPLY_IF_NEWER"], 1, "price:101", 1, 1, 1) == 0)
    check("the same version again is refused", r("EVAL", cache["APPLY_IF_NEWER"], 1, "price:101", 2, 1, 1) == 0)
    check("the copy holds v=2 p=34000 a=1", r("HMGET", "price:101", "v", "p", "a") == ["2", "34000", "1"],
          r("HMGET", "price:101", "v", "p", "a"))

    # A claimed batch whose Redis apply failed: rollback leaves the rows for the next poll
    pg.query(sql(catalog["OUTBOX"], item=100, restaurant=1, price=27000, available=True, version=2))
    pg.query("BEGIN")
    pg.query(sql(projector["CLAIM"], batch=500))
    pg.query("ROLLBACK")
    check("rolled-back claims stay in the outbox", pg.query("SELECT count(*) FROM catalog_outbox") == [("1",)])

    # Two projectors at once: SKIP LOCKED gives the second nothing while the first holds the row
    other = wire.Pg("c29svc")
    pg.query("BEGIN")
    first = pg.query(sql(projector["CLAIM"], batch=500))
    other.query("BEGIN")
    second = other.query(sql(projector["CLAIM"], batch=500))
    other.query("COMMIT")
    pg.query("COMMIT")
    check("a concurrent projector skips claimed rows", len(first) == 1 and second == [], (first, second))
    other.close()

    # DishSearch, as DishSearch.search() calls it
    def words(typed):
        return re.sub(r"[^\w]+|_", " ", typed.lower()).split()[:8]

    def by_words(typed):
        w = words(typed)
        return [int(x[0]) for x in pg.query(sql(search["BY_WORDS"], complete=" & ".join(w[:-1]), partial=w[-1], limit=10))]

    def by_similar(typed):
        return [int(x[0]) for x in pg.query(sql(search["BY_SIMILAR_NAME"], text=" ".join(words(typed)), limit=10))]

    for typed, want in [("biryani chicken", [101]), ("chicken biry", [101]), ("chicken bir", [101]),
                        ("cottage cheese", [100]), ("gulab", [102]), ("dumplings", [102])]:
        got = by_words(typed)
        check(f"BY_WORDS {typed!r}", got == want, got)
    stemmed_only = pg.query("SELECT count(*) FROM menu_items WHERE search @@ to_tsquery('english', 'chicken & biry:*')")
    check("(the stemmed prefix alone misses 'chicken biry')", stemmed_only == [("0",)], stemmed_only)
    check("BY_WORDS 'panner buter masala' finds nothing", by_words("panner buter masala") == [])
    got = by_similar("panner buter masala")
    check("BY_SIMILAR_NAME 'panner buter masala' -> Paneer Butter Masala first", got[:1] == [100], got)
    got = by_similar("chicken biriyani")
    check("BY_SIMILAR_NAME 'chicken biriyani' -> the biryani", got[:1] == [101], got)
    got = by_similar("chiken biriyani")
    check("(two typos in two words: word similarity 0.52, below the 0.6 default: not found)", got == [], got)
    # three rows: the planner prefers a scan; forbid it to show the indexes are usable by these statements
    pg.query("SET enable_seqscan = off")
    plan = "\n".join(x[0] for x in pg.query("EXPLAIN " + sql(search["BY_WORDS"], complete="chicken", partial="biry", limit=10)))
    check("BY_WORDS can use the GIN index", "menu_items_search" in plan, plan)
    plan = "\n".join(x[0] for x in pg.query("EXPLAIN " + sql(search["BY_SIMILAR_NAME"], text="panner", limit=10)))
    check("BY_SIMILAR_NAME can use the trigram index", "menu_items_name_trgm" in plan, plan)
    pg.query("RESET enable_seqscan")

    # Rider history: partitions as PartitionPlan creates them, then PositionHistory.INSERT
    today = datetime.datetime.now(datetime.timezone.utc).date()
    for d in range(-6, 3):
        day = today + datetime.timedelta(days=d)
        pg.query(f"CREATE TABLE IF NOT EXISTS rider_pings_{day:%Y%m%d} PARTITION OF rider_pings "
                 f"FOR VALUES FROM ('{day} 00:00:00+00') TO ('{day + datetime.timedelta(days=1)} 00:00:00+00')")
    parts = pg.query("SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid WHERE i.inhparent = 'rider_pings'::regclass")
    check("9 daily partitions (PartitionMaintenance's query)", len(parts) == 9, parts)
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    t0, t1 = now.isoformat().replace("+00:00", "Z"), (now + datetime.timedelta(seconds=4)).isoformat().replace("+00:00", "Z")
    pings = (f'[{{"rider_id":7,"recorded_at":"{t0}","lat":12.930000,"lon":77.620000,"speed_kmh":18.0}},'
             f'{{"rider_id":7,"recorded_at":"{t1}","lat":12.931000,"lon":77.621000,"speed_kmh":21.0}}]')
    pg.query(sql(history["INSERT"], pings=pings))
    n = pg.query("SELECT count(*), min(lat), max(speed_kmh) FROM rider_pings WHERE rider_id = 7")
    check("PositionHistory.INSERT writes the batch", n == [("2", "12.93", "21")], n)

    # RiderLocations scripts and the GEOSEARCH Spring Data Redis sends for nearby()
    t = int(now.timestamp())
    for rider, lat, lon, seen in [(1, 12.9352, 77.6245, t), (2, 12.9279, 77.6271, t), (3, 12.9716, 77.5946, t - 300)]:
        r("EVAL", riders["UPDATE"], 2, "{riders}:live", "{riders}:seen", lon, lat, rider, seen)
    near = r("GEOSEARCH", "{riders}:live", "FROMLONLAT", 77.6260, 12.9300, "BYRADIUS", 3, "km", "WITHDIST", "COUNT", 10, "ASC")
    check("nearest first: riders 2 then 1, MG Road not within 3 km", [x[0] for x in near] == ["2", "1"], near)
    removed = r("EVAL", riders["PRUNE"], 2, "{riders}:live", "{riders}:seen", t - 30)
    check("PRUNE removes the silent rider from both sets", removed == 1 and r("ZCARD", "{riders}:live") == 2
          and r("ZCARD", "{riders}:seen") == 2, removed)

    pg.close()
    r.close()
    print(f"\n{'all checks passed' if failures == 0 else str(failures) + ' check(s) failed'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
