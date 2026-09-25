-- Each service owns a database of its own. The official image runs init scripts only on an
-- empty volume, so this is applied on every start by the postgres-init service instead, and
-- creates only what is missing.
SELECT 'CREATE DATABASE check_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'check_db')\gexec

SELECT 'CREATE DATABASE notification_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_db')\gexec
