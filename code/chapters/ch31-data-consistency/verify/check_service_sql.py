"""Runs the service's SQL, exactly as written in the Java sources, against a real primary and streaming replica:
the migration, If-Match updates, the WAL positions the router compares, token-routed reads against a lagging
replica, and the loyalty statements. Uses database c31svc (recreated on the primary, replicated).
  PGHOST=... PRIMARY_PORT=... REPLICA1_PORT=... PGUSER=postgres python3 check_service_sql.py"""
import os
import re
import sys
import time

import wire

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main")
JAVA = os.path.join(SRC, "java", "dev", "foodfleet", "ch31")
HOST = os.environ.get("PGHOST", "localhost")
PRIMARY, REPLICA = os.environ.get("PRIMARY_PORT", "5432"), os.environ.get("REPLICA1_PORT", "5433")
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


def inline(path, needle):
    """A statement written inline in a .sql("...") call."""
    text = open(os.path.join(JAVA, path)).read()
    return re.search(r'\.sql\("([^"]*' + re.escape(needle) + r'[^"]*)"\)', text).group(1)


def sql(statement, **params):
    def literal(v):
        return repr(v) if isinstance(v, int) else "'" + str(v).replace("'", "''") + "'"
    missing = set(re.findall(r"(?<!:):([A-Za-z]\w*)", statement)) - set(params)
    assert not missing, f"unbound parameters {missing}"
    return re.sub(r"(?<!:):([A-Za-z]\w*)", lambda m: literal(params[m.group(1)]), statement)


def lsn(text):
    hi, lo = text.split("/")
    return (int(hi, 16) << 32) | int(lo, 16)


def main():
    admin = wire.Pg("postgres", host=HOST, port=PRIMARY)
    admin.query("DROP DATABASE IF EXISTS c31svc")
    admin.query("CREATE DATABASE c31svc")
    admin.close()
    p = wire.Pg("c31svc", host=HOST, port=PRIMARY)
    p.query(open(os.path.join(SRC, "resources", "db", "migration", "V1__profiles_and_points.sql")).read())
    check("Flyway migration V1 applies", True)
    p.query("INSERT INTO profiles (customer_id, address) VALUES (7, '12 MG Road')")

    profile = constants("profiles/ProfileService.java")
    loyalty = constants("points/LoyaltyPoints.java")
    primary_pos = inline("reads/JdbcPositions.java", "pg_current_wal_lsn")
    replica_pos = inline("reads/JdbcPositions.java", "pg_last_wal_replay_lsn")

    # the positions the router compares
    check("on the primary, the replay position is NULL (the router won't trust it as a replica)",
          p.query(replica_pos) == [(None,)], p.query(replica_pos))
    deadline = time.time() + 10
    while True:
        try:
            r = wire.Pg("c31svc", host=HOST, port=REPLICA)
            break
        except wire.PgError:
            if time.time() > deadline:
                raise
            time.sleep(0.2)                       # the new database reaches the replica a moment later
    check("on the replica, the replay position is a WAL position", "/" in r.query(replica_pos)[0][0])

    # If-Match updates
    row = p.query(sql(profile["UPDATE"], address="4 Brigade Road", customer=7, expected=1))
    check("UPDATE against the current version returns the new version", row == [("7", "4 Brigade Road", "2")], row)
    token = p.query(primary_pos)[0][0]
    check("UPDATE against an old version changes nothing", p.query(sql(profile["UPDATE"], address="stale", customer=7, expected=1)) == [])

    # a lagging replica: the token says when it is safe to read there
    r.query("SELECT 1")
    admin_r = wire.Pg("postgres", host=HOST, port=REPLICA)
    admin_r.query("ALTER SYSTEM SET recovery_min_apply_delay = '300ms'")
    admin_r.query("SELECT pg_reload_conf()")
    time.sleep(1)
    p.query(sql(profile["UPDATE"], address="Koramangala 5th Block", customer=7, expected=2))
    token = p.query(primary_pos)[0][0]
    behind = lsn(r.query(replica_pos)[0][0]) < lsn(token)
    stale = r.query(sql(profile["SELECT"], customer=7))[0][2]
    check("right after the write the replica is behind the token and still shows version 2", behind and stale == "2", (behind, stale))
    waited = 0.0
    while lsn(r.query(replica_pos)[0][0]) < lsn(token):
        time.sleep(0.005)
        waited += 0.005
    fresh = r.query(sql(profile["SELECT"], customer=7))[0][2]
    check(f"once its replay position reaches the token (after ~{waited * 1000:.0f} ms) it shows version 3", fresh == "3", fresh)
    admin_r.query("ALTER SYSTEM RESET recovery_min_apply_delay")
    admin_r.query("SELECT pg_reload_conf()")
    admin_r.close()

    # ConsistencyToken's ordering agrees with PostgreSQL's pg_lsn ordering
    pairs = [("1/FFFFFFFF", "2/0"), ("16/B374D848", "16/B374D849"), ("0/0", "0/1"), ("A/0", "9/FFFFFFFF")]
    ok = all((p.query(f"SELECT '{a}'::pg_lsn >= '{b}'::pg_lsn")[0][0] == "t") == (lsn(a) >= lsn(b)) for a, b in pairs)
    check("hex-pair parsing orders positions as pg_lsn does", ok)

    # loyalty points
    check("ADD creates the row", p.query(sql(loyalty["ADD"], customer=42, points=10)) == [("10",)])
    check("ADD adds to it", p.query(sql(loyalty["ADD"], customer=42, points=15)) == [("25",)])
    check("REDEEM takes points when there are enough", p.query(sql(loyalty["REDEEM"], customer=42, points=20)) == [("5",)])
    check("REDEEM refuses (no row) when there aren't", p.query(sql(loyalty["REDEEM"], customer=42, points=20)) == [])
    check("the balance is untouched by the refusal", p.query("SELECT points FROM loyalty WHERE customer_id = 42") == [("5",)])

    p.close(), r.close()
    print(f"\n{'all checks passed' if failures == 0 else str(failures) + ' check(s) failed'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
