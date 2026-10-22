"""Runs the service's migrations and SQL, as written in the sources, against PostgreSQL: V1-V5 the way Flyway runs
them (each in its own transaction after SET lock_timeout, V3 outside one), the address steps across phases, the
keyset backfill, and the invalid-index check. Uses database c33svc (recreated).
  PGHOST=... PGPORT=... PGUSER=postgres python3 check_service_sql.py"""
import glob
import os
import re
import sys

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "foodfleet", "ch33")
failures = 0

# The phase SQL, exactly as AddressPhaseTest pins it.
PHASE = {
    "EXPAND": dict(insert="INSERT INTO customers (id, name, address, delivery_address) VALUES (:id, :name, :address, :address)",
                   update="UPDATE customers SET address = :address, delivery_address = :address WHERE id = :id",
                   select="SELECT address FROM customers WHERE id = :id"),
    "READ_NEW": dict(update="UPDATE customers SET address = :address, delivery_address = :address WHERE id = :id",
                     select="SELECT delivery_address FROM customers WHERE id = :id"),
    "CONTRACT": dict(insert="INSERT INTO customers (id, name, delivery_address) VALUES (:id, :name, :address)",
                     update="UPDATE customers SET delivery_address = :address WHERE id = :id",
                     select="SELECT delivery_address FROM customers WHERE id = :id"),
}


def check(label, ok, detail=""):
    global failures
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'} {label}{'' if ok else '  -> ' + str(detail)}")


def block(path, name):
    text = open(os.path.join(JAVA, path)).read()
    body = re.search(r'static final String ' + name + r' = """\n(.*?)"""', text, re.S).group(1)
    lines = body.split("\n")
    indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
    return "\n".join(l[indent:] for l in lines).rstrip()


def sql(statement, **params):
    def literal(v):
        return repr(v) if isinstance(v, int) else "'" + str(v).replace("'", "''") + "'"
    return re.sub(r"(?<!:):([A-Za-z]\w*)", lambda m: literal(params[m.group(1)]), statement)


def flyway_migrate(pg, files):
    """Like Flyway on PostgreSQL: init SQL on the connection, then each script in a transaction unless it holds a
    statement PostgreSQL can't run in one (Flyway's PostgreSQLParser: CREATE/DROP INDEX CONCURRENTLY, VACUUM...)."""
    pg.query("SET lock_timeout = '2s'")
    for f in files:
        script = open(f).read()
        body = "\n".join(l for l in script.splitlines() if not l.startswith("--")).strip()
        non_tx = re.search(r"^(CREATE|DROP)( UNIQUE)? INDEX CONCURRENTLY|^VACUUM", body, re.M | re.I)
        if non_tx:
            check(f"{os.path.basename(f)} holds only the non-transactional statement", body.count(";") == 1, body)
            pg.query(body)
        else:
            pg.query("BEGIN")
            pg.query(body)
            pg.query("COMMIT")
        check(f"{os.path.basename(f)} applies ({'outside a transaction' if non_tx else 'in a transaction'})", True)


def main():
    admin = wire.Pg("postgres")
    admin.query("DROP DATABASE IF EXISTS c33svc")
    admin.query("CREATE DATABASE c33svc")
    admin.close()
    pg = wire.Pg("c33svc")
    flyway_migrate(pg, sorted(glob.glob(os.path.join(SRC, "resources", "db", "migration", "V*.sql")),
                              key=lambda p: int(re.search(r"V(\d+)__", p).group(1))))

    invalid = block("schema/InvalidIndexCheck.java", "INVALID")
    check("no invalid indexes after the migrations", pg.query(invalid) == [])
    check("the CHECK constraint is validated",
          pg.query("SELECT convalidated FROM pg_constraint WHERE conname = 'orders_amount_positive'") == [("t",)])
    try:
        pg.query("INSERT INTO orders (id, customer_id, restaurant_id, amount_paise, status) VALUES (1, 1, 1, 0, 'NEW')")
        check("a zero amount is rejected", False)
    except wire.PgError as e:
        check("a zero amount is rejected", e.fields["C"] == "23514", e)

    # the rename, across phases and instance versions
    e = PHASE["EXPAND"]
    pg.query(sql(e["insert"], id=1, name="Asha", address="12 MG Road"))
    pg.query("INSERT INTO customers (id, name, address) VALUES (2, 'Ravi', '4 Park St')")       # old instance
    pg.query(sql(e["update"], address="7 Brigade Rd", id=1))
    pg.query("UPDATE customers SET address = '9 Church St' WHERE id = 1")                       # old instance, later
    check("EXPAND still reads the old column", pg.query(sql(e["select"], id=1)) == [("9 Church St",)])

    batch = block("address/KeysetBackfill.java", "BATCH")
    up_to = int(pg.query("SELECT max(id) FROM customers")[0][0])
    after, copied, batches = 0, 0, 0
    while after < up_to:
        last, n = pg.query(sql(batch, after=after, upTo=up_to, size=1))[0]
        batches += 1
        if last is None:
            break
        after, copied = int(last), copied + int(n)
    check("the backfill copies the empty row and the stale one", (copied, batches) == (2, 2), (copied, batches))
    last, n = pg.query(sql(batch, after=0, upTo=up_to, size=1000))[0]
    check("a second backfill copies nothing", n == "0", n)
    last, n = pg.query(sql(batch, after=up_to, upTo=up_to, size=1000))[0]
    check("past the end the batch returns NULL last_id (the loop's stop signal)", last is None, last)

    r = PHASE["READ_NEW"]
    check("READ_NEW reads the backfilled values",
          [pg.query(sql(r["select"], id=i))[0][0] for i in (1, 2)] == ["9 Church St", "4 Park St"])
    pg.query(sql(r["update"], address="3 Residency Rd", id=2))
    check("EXPAND instances still running see READ_NEW's writes",
          pg.query(sql(e["select"], id=2)) == [("3 Residency Rd",)])

    c = PHASE["CONTRACT"]
    pg.query(sql(c["insert"], id=3, name="Meera", address="1 Lake Rd"))
    check("CONTRACT inserts without the old column (V2 dropped its NOT NULL)",
          pg.query("SELECT address IS NULL, delivery_address FROM customers WHERE id = 3") == [("t", "1 Lake Rd")])
    pg.query("BEGIN")
    pg.query(open(os.path.join(SRC, "resources", "db", "later", "V6__contract_drop_customer_address.sql")).read())
    pg.query("COMMIT")
    check("after V6 the CONTRACT statements still work",
          pg.query(sql(c["update"], address="2 Lake Rd", id=3)) == [] and
          pg.query(sql(c["select"], id=3)) == [("2 Lake Rd",)])

    # an index left INVALID by a failed concurrent build is reported
    hold = wire.Pg("c33svc")
    hold.query("BEGIN ISOLATION LEVEL REPEATABLE READ")
    hold.query("SELECT 1")
    pg.query("SET lock_timeout = '1s'")
    try:
        pg.query("CREATE INDEX CONCURRENTLY orders_customer ON orders (customer_id)")
    except wire.PgError:
        pass
    hold.query("COMMIT")
    hold.close()
    check("a failed concurrent build is reported by InvalidIndexCheck", pg.query(invalid) == [("orders_customer",)],
          pg.query(invalid))

    pg.close()
    print(f"\n{'all checks passed' if failures == 0 else str(failures) + ' check(s) failed'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
