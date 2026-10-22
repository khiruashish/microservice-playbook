-- Test only: the container's roles get passwords so the tests can log in as them.
ALTER ROLE orders_app PASSWORD 'orders_app';
ALTER ROLE orders_owner_login PASSWORD 'orders_owner';
