"""Runs the service's own SQL, exactly as written in the Java sources, against PostgreSQL (with
max_prepared_transactions > 0): the Flyway migration, then the statements of placing, relaying and paying for
orders, and of resolving prepared transactions. Uses database c30svc on cluster A (recreated).
  PGHOST_A=... PGPORT_A=... PGUSER=postgres python3 check_service_sql.py"""
import os
import re
import sys

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "foodfleet", "ch30")
A = dict(host=os.environ.get("PGHOST_A", "localhost"), port=os.environ.get("PGPORT_A", "5432"))
failures = 0


def check(label, ok, detail=""):
    global failures
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'} {label}{'' if ok else '  -> ' + str(detail)}")


def constants(path):
    text = open(os.path.join(JAVA, path)).read()
    found = {}
    for name, body in re.findall(r'static final String (\w+) = """\n(.*?)"""', text, re.S):
        lines = body.split("\n")
        indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
        found[name] = "\n".join(l[indent:] for l in lines).rstrip()
    for name, body in re.findall(r'static final String (\w+) = "([^"\n]*)";', text):
        found[name] = body
    return found


def sql(statement, **params):
    def literal(v):
        if v is None:
            return "NULL"
        if isinstance(v, bool):
            return "true" if v else "false"
        if isinstance(v, (int, float)):
            return repr(v)
        return "'" + str(v).replace("'", "''") + "'"
    missing = set(re.findall(r"(?<!:):([A-Za-z]\w*)", statement)) - set(params)
    assert not missing, f"unbound parameters {missing}"
    return re.sub(r"(?<!:):([A-Za-z]\w*)", lambda m: literal(params[m.group(1)]), statement)


def orders_json(rows):
    return "[" + ",".join(f'{{"order_id":{o},"customer_id":{c},"item_id":{i},"amount_paise":{a}}}' for o, c, i, a in rows) + "]"


def main():
    admin = wire.Pg("postgres", **A)
    admin.query("DROP DATABASE IF EXISTS c30svc")
    admin.query("CREATE DATABASE c30svc")
    admin.close()
    pg = wire.Pg("c30svc", **A)
    pg.query(open(os.path.join(SRC, "resources", "db", "migration", "V1__orders_and_payments.sql")).read())
    check("Flyway migration V1 applies", True)

    svc = constants("orders/OrderService.java")
    relay = constants("orders/OrderRelay.java")
    ledger = constants("payments/PaymentLedger.java")
    monitor = constants("indoubt/InDoubtMonitor.java")

    pg.query("INSERT INTO payments.wallets VALUES (1, 100000), (2, 10000); INSERT INTO payments.stock VALUES (500, 50), (501, 1)")

    # OrderService.place: order + outbox in one transaction
    ids = []
    for c, i in ((1, 500), (2, 500), (1, 501)):
        pg.query("BEGIN")
        oid = int(pg.query(sql(svc["INSERT_ORDER"], customer=c, item=i, amount=25000))[0][0])
        pg.query(sql(svc["INSERT_OUTBOX"], order=oid, customer=c, item=i, amount=25000))
        pg.query("COMMIT")
        ids.append(oid)
    check("INSERT_ORDER returns the new id", ids == [1, 2, 3], ids)
    check("the outbox holds one row per order", pg.query("SELECT count(*) FROM orders.outbox") == [("3",)])

    # OrderRelay.CLAIM, then the ledger's batch path: customer 2 can't pay, so the batch must fail as a whole
    pg.query("BEGIN")
    claimed = sorted((int(r[0]), int(r[1]), int(r[2]), int(r[3])) for r in pg.query(sql(relay["CLAIM"], batch=20)))
    check("CLAIM returns the three orders", [r[0] for r in claimed] == ids, claimed)
    other = wire.Pg("c30svc", **A)
    other.query("BEGIN")
    check("a second relay claims nothing meanwhile (SKIP LOCKED)", other.query(sql(relay["CLAIM"], batch=20)) == [])
    other.query("ROLLBACK")
    other.query("BEGIN")
    fresh = other.query(sql(ledger["INSERT_DEBITS"], orders=orders_json(claimed)))
    check("INSERT_DEBITS inserts all three", len(fresh) == 3, fresh)
    other.query(sql(ledger["TAKE_STOCK"], items='[{"item_id":500,"n":2},{"item_id":501,"n":1}]'))
    try:
        other.query(sql(ledger["DEBIT_WALLETS"], customers='[{"customer_id":1,"amount":50000},{"customer_id":2,"amount":25000}]'))
        check("DEBIT_WALLETS refuses to overdraw wallet 2", False)
    except wire.PgError as e:
        check("DEBIT_WALLETS refuses to overdraw wallet 2 (23514 -> DataIntegrityViolationException)", e.fields.get("C") == "23514", e)
    other.query("ROLLBACK")

    # the one-by-one fallback, as PaymentLedger.applyOne runs it
    def apply_one(o, c, i, a):
        other.query("BEGIN")
        if not other.query(sql(ledger["INSERT_DEBITS"], orders=orders_json([(o, c, i, a)]))):
            other.query("ROLLBACK")
            return "already decided"
        portion = other.query(f"UPDATE payments.stock SET available = available - 1 WHERE item_id = {i} AND available >= 1 RETURNING 1")
        paid = portion and other.query(f"UPDATE payments.wallets SET balance_paise = balance_paise - {a} "
                                       f"WHERE customer_id = {c} AND balance_paise >= {a} RETURNING 1")
        if portion and paid:
            other.query("COMMIT")
            return "DEBITED"
        other.query("ROLLBACK")
        reason = "SOLD_OUT" if not portion else "INSUFFICIENT_FUNDS"
        other.query(f"INSERT INTO payments.debits (order_id, customer_id, item_id, amount_paise, status, reason) "
                    f"VALUES ({o}, {c}, {i}, {a}, 'REJECTED', '{reason}') ON CONFLICT (order_id) DO NOTHING")
        return reason

    outcomes = [apply_one(*r) for r in claimed]
    check("one by one: 1 debited, 2 insufficient funds, 3 debited", outcomes == ["DEBITED", "INSUFFICIENT_FUNDS", "DEBITED"], outcomes)
    decided = sorted(other.query(sql(ledger["DECIDED"], orders=orders_json(claimed))))
    check("DECIDED returns a stored decision for every order", decided ==
          [("1", "DEBITED", None), ("2", "REJECTED", "INSUFFICIENT_FUNDS"), ("3", "DEBITED", None)], decided)
    for o, status, reason in decided:
        pg.query(sql(relay["DECIDE"], status="CONFIRMED" if status == "DEBITED" else "REJECTED", reason=reason, id=int(o)))
    pg.query("COMMIT")
    check("the orders carry the decisions", pg.query("SELECT id, status, reason FROM orders.orders ORDER BY id") ==
          [("1", "CONFIRMED", None), ("2", "REJECTED", "INSUFFICIENT_FUNDS"), ("3", "CONFIRMED", None)])
    check("the outbox is empty", pg.query("SELECT count(*) FROM orders.outbox") == [("0",)])
    check("balances: 1 paid twice, 2 untouched", pg.query("SELECT balance_paise FROM payments.wallets ORDER BY customer_id") == [("50000",), ("10000",)])
    check("stock: 500 down one, 501 sold out", pg.query("SELECT available FROM payments.stock ORDER BY item_id") == [("49",), ("0",)])

    # redelivery: nothing new is inserted, the same decisions come back, DECIDE leaves decided orders alone
    check("a redelivered batch inserts no debit", other.query(sql(ledger["INSERT_DEBITS"], orders=orders_json(claimed))) == [])
    check("a redelivered batch gets the same decisions", sorted(other.query(sql(ledger["DECIDED"], orders=orders_json(claimed)))) == decided)
    pg.query(sql(relay["DECIDE"], status="REJECTED", reason="late duplicate", id=1))
    check("DECIDE never overturns a decided order", pg.query("SELECT status FROM orders.orders WHERE id = 1") == [("CONFIRMED",)])

    # the batch statements lock rows after sorting them, in key order
    plan = "\n".join(r[0] for r in pg.query("EXPLAIN " + sql(ledger["TAKE_STOCK"], items='[{"item_id":500,"n":1}]')))
    check("TAKE_STOCK sorts, then locks (LockRows above Sort)", plan.find("LockRows") < plan.find("Sort") and "LockRows" in plan, plan)

    pg.query("BEGIN")
    pg.query(sql(ledger["TAKE_STOCK"], items='[{"item_id":500,"n":2}]'))
    pg.query(sql(ledger["DEBIT_WALLETS"], customers='[{"customer_id":1,"amount":7000}]'))
    after = pg.query("SELECT (SELECT available FROM payments.stock WHERE item_id = 500), "
                     "(SELECT balance_paise FROM payments.wallets WHERE customer_id = 1)")
    pg.query("ROLLBACK")
    check("TAKE_STOCK / DEBIT_WALLETS apply the totals (49 - 2, 50000 - 7000)", after == [("47", "43000")], after)

    # InDoubtMonitor: prepared transactions and the decision log
    for gid, work in (("order-commit", "UPDATE payments.wallets SET balance_paise = balance_paise - 1000 WHERE customer_id = 1"),
                      ("order-orphan", "UPDATE payments.wallets SET balance_paise = balance_paise - 5000 WHERE customer_id = 2")):
        other.query("BEGIN")
        other.query(work)
        other.query(f"PREPARE TRANSACTION '{gid}'")
    pg.query("INSERT INTO orders.tm_log (gid, decision) VALUES ('order-commit', 'COMMIT')")
    prepared = pg.query(monitor["PREPARED"])
    check("PREPARED lists both, oldest first", [r[0] for r in prepared] == ["order-commit", "order-orphan"], prepared)
    check("DECISIONS reads the coordinator's log", pg.query(monitor["DECISIONS"]) == [("order-commit", "COMMIT")])
    blocked = wire.Pg("c30svc", **A)
    blocked.query("SET lock_timeout = '500ms'")
    try:
        blocked.query("UPDATE payments.wallets SET balance_paise = balance_paise + 1 WHERE customer_id = 2")
        check("a prepared transaction keeps its row locks", False)
    except wire.PgError as e:
        check("a prepared transaction keeps its row locks (lock timeout, 55P03)", e.fields.get("C") == "55P03", e)
    pg.query("COMMIT PREPARED 'order-commit'")
    pg.query("ROLLBACK PREPARED 'order-orphan'")
    check("COMMIT / ROLLBACK PREPARED resolve them", pg.query(monitor["PREPARED"]) == [])
    check("only the committed one took effect", pg.query("SELECT balance_paise FROM payments.wallets ORDER BY customer_id") == [("49000",), ("10000",)])

    blocked.close(), other.close(), pg.close()
    print(f"\n{'all checks passed' if failures == 0 else str(failures) + ' check(s) failed'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
