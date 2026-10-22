#!/bin/sh
# What the official postgres image does on first start: initdb, then run the server. Trust auth: tests only.
set -e
D=/tmp/pgdata
if [ ! -s "$D/PG_VERSION" ]; then
  /usr/lib/postgresql/16/bin/initdb -D "$D" -U postgres -A trust -E UTF8 --locale=C >/dev/null
  echo "host all all all trust" >> "$D/pg_hba.conf"
fi
exec /usr/lib/postgresql/16/bin/postgres -D "$D" -k /tmp -c listen_addresses='*' "$@"
