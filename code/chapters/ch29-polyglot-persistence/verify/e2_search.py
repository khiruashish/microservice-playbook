"""Experiment 2: dish search over 192,000 menu items, five ways, all in PostgreSQL.
Relevance is known: each item was generated from a base dish, and each query names the dishes a
user typing it wants. Reports, per query kind, the share of the top 10 that is relevant (precision)
and the share of queries that found anything relevant (recall of intent), plus median latency."""
import statistics
import time
import wire

QUERIES = [  # (kind, text, relevant base dishes)
    ("exact", "paneer butter masala", {"Paneer Butter Masala"}),
    ("exact", "chicken biryani", {"Chicken Biryani"}),
    ("exact", "masala dosa", {"Masala Dosa"}),
    ("exact", "gulab jamun", {"Gulab Jamun"}),
    ("exact", "veg momos", {"Veg Momos"}),
    ("exact", "dal makhani", {"Dal Makhani"}),
    ("reordered", "biryani chicken", {"Chicken Biryani"}),
    ("reordered", "dosa masala", {"Masala Dosa"}),
    ("reordered", "momos chicken", {"Chicken Momos"}),
    ("reordered", "naan garlic", {"Garlic Naan"}),
    ("reordered", "paneer palak", {"Palak Paneer"}),
    ("plural/stem", "dosas", {"Masala Dosa", "Plain Dosa", "Rava Dosa"}),
    ("plural/stem", "chicken kebabs", {"Chicken Kebab"}),
    ("plural/stem", "momo", {"Veg Momos", "Chicken Momos"}),
    ("plural/stem", "fried fish", {"Fish Fry"}),
    ("typo", "panner butter masala", {"Paneer Butter Masala"}),
    ("typo", "chiken biryani", {"Chicken Biryani"}),
    ("typo", "chicken biriyani", {"Chicken Biryani"}),
    ("typo", "masla dosa", {"Masala Dosa"}),
    ("typo", "gulab jamoon", {"Gulab Jamun"}),
    ("typo", "paner tikka", {"Paneer Tikka"}),
    ("typo", "ras malai", {"Rasmalai"}),
    ("typing", "panee", {d for d in ["Paneer Butter Masala", "Paneer Tikka", "Palak Paneer", "Kadai Paneer", "Shahi Paneer"]}),
    ("typing", "chicken bir", {"Chicken Biryani"}),
    ("typing", "butter n", {"Butter Naan"}),
    ("typing", "chicken biry", {"Chicken Biryani"}),
    ("typing", "gul", {"Gulab Jamun"}),
    ("by ingredient", "cottage cheese", {"Paneer Butter Masala", "Paneer Tikka", "Palak Paneer", "Kadai Paneer", "Shahi Paneer", "Rasmalai"}),
    ("by ingredient", "lentil", {"Dal Makhani", "Dal Tadka", "Idli Sambar", "Medu Vada"}),
]

def lit(s):
    return "'" + s.replace("'", "''") + "'"


def words(q):
    """SearchQuery.words in the Java service: lower-case letters and digits only."""
    import re
    return re.sub(r"[^\w]+|_", " ", q.lower()).split()[:8]


# The service's two statements (DishSearch.BY_WORDS and BY_SIMILAR_NAME), parameters inlined.
def by_words(q):
    w = words(q)
    complete, partial = " & ".join(w[:-1]), w[-1]
    return f"""WITH q AS (
                SELECT CASE WHEN {lit(complete)} = '' THEN p ELSE to_tsquery('english', {lit(complete)}) && p END AS q
                  FROM (SELECT to_tsquery('english', {lit(partial)} || ':*') || to_tsquery('simple', {lit(partial)} || ':*') AS p) last_word)
            SELECT i.base_dish
              FROM menu_items i, q
             WHERE i.search @@ q.q AND i.available
             ORDER BY ts_rank(i.search, q.q) DESC, i.id
             LIMIT 10"""


def by_similar_name(q):
    t = lit(" ".join(words(q)))
    return f"""SELECT i.base_dish
              FROM menu_items i
             WHERE i.available AND {t} <% lower(i.name)
             ORDER BY {t} <<-> lower(i.name), i.id
             LIMIT 10"""


class Fallback:
    """DishSearch.search: words first; similar names only when the words find nothing."""
    def __init__(self, first, second):
        self.first, self.second = first, second

STRATEGIES = {
    "LIKE '%q%', no index": (
        "SET enable_bitmapscan = off; SET enable_indexscan = off;",
        lambda q: f"SELECT base_dish FROM menu_items WHERE lower(name) LIKE {lit('%' + q + '%')} LIMIT 10"),
    "LIKE '%q%', trigram index": (
        "RESET enable_bitmapscan; RESET enable_indexscan;",
        lambda q: f"SELECT base_dish FROM menu_items WHERE lower(name) LIKE {lit('%' + q + '%')} LIMIT 10"),
    "full-text, ranked": (
        "",
        lambda q: f"""SELECT base_dish FROM menu_items, websearch_to_tsquery('english', {lit(q)}) tq
                      WHERE search @@ tq ORDER BY ts_rank(search, tq) DESC, id LIMIT 10"""),
    "full-text + prefix, stemmed": (
        "",
        lambda q: f"""SELECT base_dish FROM menu_items, typeahead_query({lit(q)}) tq
                      WHERE search @@ tq ORDER BY ts_rank(search, tq) DESC, id LIMIT 10"""),
    "full-text + prefix, stemmed or as typed": ("", by_words),
    "trigram similarity": (
        "",
        lambda q: f"""SELECT base_dish FROM menu_items WHERE {lit(q)} <% lower(name)
                      ORDER BY {lit(q)} <<-> lower(name), id LIMIT 10"""),
    "the service: words, then similar names": ("", Fallback(by_words, by_similar_name)),
}

def main():
    pg = wire.Pg()
    pg.query(open(__file__.replace("e2_search.py", "search_setup.sql")).read())
    kinds = list(dict.fromkeys(k for k, _, _ in QUERIES))
    print(f"{len(QUERIES)} queries over {pg.query('SELECT count(*) FROM menu_items')[0][0]} items; "
          "precision = relevant share of the top 10; found = queries with at least one relevant result\n")
    header = f"{'strategy':42}" + "".join(f"{k:>16}" for k in kinds) + f"{'all':>12}{'p50 ms':>9}{'max ms':>9}"
    print(header)
    print(f"{'':42}" + "".join(f"{'prec / found':>16}" for _ in kinds))
    latency = []
    for name, (setup, sql) in STRATEGIES.items():
        pg.query("RESET ALL;" + setup)
        per_kind, times, found_all, prec_all = {k: [] for k in kinds}, [], [], []
        kind_ms = {k: [] for k in kinds}
        def ask(q):
            if isinstance(sql, Fallback):
                rows = pg.query(sql.first(q))
                return rows if rows else pg.query(sql.second(q))
            return pg.query(sql(q))

        for kind, q, relevant in QUERIES:
            rows = ask(q)  # warm-up, and the answer
            samples = []
            for _ in range(5):
                t = time.perf_counter()
                ask(q)
                samples.append((time.perf_counter() - t) * 1000)
            times.append(statistics.median(samples))
            kind_ms[kind].append(statistics.median(samples))
            hits = sum(1 for (d,) in rows if d in relevant)
            prec = hits / 10
            per_kind[kind].append((prec, hits > 0))
            prec_all.append(prec)
            found_all.append(hits > 0)
        cells = "".join(f"{statistics.mean(p for p, _ in v):>9.0%} / {sum(f for _, f in v)}/{len(v)}" for v in per_kind.values())
        print(f"{name:42}{cells}{statistics.mean(prec_all):>6.0%} {sum(found_all):>2}/{len(QUERIES)}"
              f"{statistics.median(times):>9.2f}{max(times):>9.2f}")
        latency.append((name, kind_ms))
    print("\nmedian ms by query kind")
    print(f"{'strategy':42}" + "".join(f"{k:>16}" for k in kinds))
    for name, kind_ms in latency:
        print(f"{name:42}" + "".join(f"{statistics.median(v):>16.1f}" for v in kind_ms.values()))
    pg.close()

if __name__ == "__main__":
    main()
