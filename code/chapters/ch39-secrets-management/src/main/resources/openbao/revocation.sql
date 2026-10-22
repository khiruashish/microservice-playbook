-- the default revocation drops the role but leaves its sessions connected; end them first (needs pg_signal_backend)
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename = '{{name}}';
REVOKE order_service_rw FROM "{{name}}";
DROP ROLE IF EXISTS "{{name}}";
