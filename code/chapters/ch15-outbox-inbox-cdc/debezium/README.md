# Log-tailing relay with Debezium

`ordering-outbox-connector.json` is a Kafka Connect configuration for Debezium's PostgreSQL connector
with the **Outbox Event Router** transform. It replaces `OutboxRelay` (the polling publisher):

- Debezium reads INSERTs into `public.outbox` from the write-ahead log (logical replication, `pgoutput`).
- `aggregate_type` picks the topic (`order` → `foodfleet.order.events`), `aggregate_id` is the Kafka key
  (so all events of one order land in one partition, in order), `payload` becomes the message value, and
  `type` and `id` travel as headers (`eventType`, `messageId`; consumers deduplicate on `messageId`).
- With CDC the application can delete outbox rows in the same transaction it inserts them (or a job can
  delete them soon after): the INSERT is already in the WAL. The `published_at` column is then unused.

Database prerequisites (PostgreSQL):

```sql
-- postgresql.conf: wal_level = logical
create role debezium with login replication password '...';
grant select on public.outbox to debezium;
grant create on database ordering to debezium;        -- for the filtered publication
create table debezium_heartbeat (id int primary key, ts timestamptz);
grant insert, update on debezium_heartbeat to debezium;
```

The heartbeat keeps the replication slot advancing when the outbox is quiet but other tables are busy;
without it the slot can retain WAL and fill the disk. Monitor `pg_replication_slots` lag in every case.

Register the connector:

```bash
curl -X POST -H 'Content-Type: application/json' \
     --data @ordering-outbox-connector.json http://connect:8083/connectors
```
