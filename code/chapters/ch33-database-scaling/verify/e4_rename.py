"""Experiment 4: renaming customers.address to delivery_address while 4 app instances keep reading and writing it,
deployed one instance at a time (a rolling deploy). Errors are failed statements; a wrong read returns an address
older than one committed before the read started."""
import random
import sys
import threading
import time

import wire

DB = "c33"
# what each app version writes and reads
VERSIONS = {
    "v1": (["address"], "address"),
    "v2-naive": (["delivery_address"], "delivery_address"),
    "v1.5": (["address", "delivery_address"], "address"),             # expand: write both, read old
    "v2": (["address", "delivery_address"], "delivery_address"),      # switch reads, still write both
    "v3": (["delivery_address"], "delivery_address"),                 # stop writing the old column
}


def q(sql):
    c = wire.Pg(DB)
    try:
        return c.query(sql)
    finally:
        c.close()


def run(name, schedule, duration):
    """schedule: [(t, action)], action = ("deploy", instance, version) or ("sql", statement) or ("call", fn)."""
    q("DROP TABLE IF EXISTS customers")
    q("CREATE TABLE customers (id int PRIMARY KEY, address text NOT NULL)")
    q("INSERT INTO customers SELECT c, 'address-0-' || c FROM generate_series(1, 2000) c")
    versions = ["v1"] * 4
    committed = {}                      # customer -> [(time, value)]
    lock = threading.Lock()
    stats = {"ops": 0, "errors": 0, "wrong": 0}
    end = time.monotonic() + duration
    seq = iter(range(1, 10**9))

    def instance(n):
        rng = random.Random(n)
        c = wire.Pg(DB)
        while time.monotonic() < end:
            cols, read_col = VERSIONS[versions[n]]
            cust = rng.randint(1, 2000)
            try:
                if rng.random() < 0.5:
                    with lock:
                        value = f"address-{next(seq)}-{cust}"
                    c.query(f"UPDATE customers SET {', '.join(f'{col} = {chr(39)}{value}{chr(39)}' for col in cols)} WHERE id = {cust}")
                    with lock:
                        committed.setdefault(cust, []).append((time.monotonic(), value))
                else:
                    t0 = time.monotonic()
                    seen = c.query(f"SELECT {read_col} FROM customers WHERE id = {cust}")[0][0]
                    with lock:
                        history = [v for t, v in committed.get(cust, []) if t < t0]
                        later = [v for t, v in committed.get(cust, [])]
                        expected = history[-1] if history else f"address-0-{cust}"
                        # wrong: not the newest value committed before the read, nor anything committed since
                        if seen != expected and seen not in later[len(history):]:
                            stats["wrong"] += 1
            except wire.PgError:
                with lock:
                    stats["errors"] += 1
            with lock:
                stats["ops"] += 1
            time.sleep(0.002)
        c.close()

    ts = [threading.Thread(target=instance, args=(n,)) for n in range(4)]
    t_start = time.monotonic()
    [t.start() for t in ts]
    notes = []
    for at, action in schedule:
        time.sleep(max(0.0, t_start + at - time.monotonic()))
        if action[0] == "deploy":
            versions[action[1]] = action[2]
        elif action[0] == "sql":
            q(action[1])
        else:
            notes.append(action[1]())
    [t.join() for t in ts]
    col = "delivery_address" if q("SELECT 1 FROM information_schema.columns WHERE table_name = 'customers' AND column_name = 'delivery_address'") else "address"
    final_wrong = 0
    for cust, values in committed.items():
        if q(f"SELECT {col} FROM customers WHERE id = {cust}")[0][0] != values[-1][1]:
            final_wrong += 1
    print(f"{name:52}{stats['ops']:>8}{stats['errors']:>9}{stats['wrong']:>13}{final_wrong:>15}   {'; '.join(n for n in notes if n)}")
    sys.stdout.flush()


def rolling(start, version, gap=1.5):
    return [(start + i * gap, ("deploy", i, version)) for i in range(4)]


def backfill(where):
    def go():
        c = wire.Pg(DB)
        n = 0
        while True:
            rows = c.query(f"""WITH b AS (SELECT id FROM customers WHERE {where} ORDER BY id LIMIT 200)
                               UPDATE customers c SET delivery_address = c.address FROM b WHERE c.id = b.id RETURNING 1""")
            n += len(rows)
            if not rows:
                break
        c.close()
        return f"backfilled {n} rows"
    return go


if __name__ == "__main__":
    print("2,000 customers; 4 instances read and write addresses (50/50) every 2 ms; deploys roll one instance every 1.5 s.\n")
    print(f"{'migration':52}{'ops':>8}{'errors':>9}{'wrong reads':>13}{'wrong at end':>15}")
    run("rename the column, then roll out the new version",
        [(4, ("sql", "ALTER TABLE customers RENAME COLUMN address TO delivery_address"))] + rolling(4, "v2-naive"), 14)
    run("roll out the new version, then rename the column",
        rolling(4, "v2-naive") + [(9, ("sql", "ALTER TABLE customers RENAME COLUMN address TO delivery_address"))], 14)
    expand = [(3, ("sql", "ALTER TABLE customers ADD COLUMN delivery_address text"))] + rolling(4, "v1.5")
    tail = rolling(13, "v2") + rolling(20, "v3") + [(27, ("sql", "ALTER TABLE customers DROP COLUMN address"))]
    run("expand/contract, backfill only empty rows",
        expand + [(10, ("call", backfill("delivery_address IS NULL")))] + tail, 30)
    run("expand/contract, backfill rows that differ",
        expand + [(10, ("call", backfill("delivery_address IS DISTINCT FROM address")))] + tail, 30)
