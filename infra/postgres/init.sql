-- One Postgres instance, one database per service (database-per-service pattern).
-- In production each would be its own managed instance; locally this keeps memory low.
CREATE DATABASE txn;
CREATE DATABASE cases;
\connect cases
CREATE EXTENSION IF NOT EXISTS vector;
