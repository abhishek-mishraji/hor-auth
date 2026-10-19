GRANT CONNECT ON DATABASE auth_db TO :"reader_role";
\c auth_db
GRANT USAGE ON SCHEMA core TO :"reader_role";
GRANT SELECT ON core.stores TO :"reader_role";
