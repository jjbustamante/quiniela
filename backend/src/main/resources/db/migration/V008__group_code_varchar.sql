-- V008: align group_code column types with JPA entity expectations.
--
-- V004 declared `group_code CHAR(1)` on team + match. Postgres stores CHAR(n)
-- as the `bpchar` type. Hibernate 7's schema validator under Spring Boot 4
-- distinguishes bpchar (Types#CHAR) from varchar(n) (Types#VARCHAR) and
-- rejects the mismatch because our Team/Match entities map `String groupCode`
-- with `@Column(length = 1)` — which Hibernate normalizes to VARCHAR.
--
-- Local Testcontainers tests (postgres:16-alpine) didn't catch this; Cloud
-- SQL's JDBC metadata path does. Converting to VARCHAR(1) on both columns
-- aligns the schema with the entity model and avoids the legacy CHAR padding
-- semantics anyway.

ALTER TABLE team  ALTER COLUMN group_code TYPE VARCHAR(1);
ALTER TABLE match ALTER COLUMN group_code TYPE VARCHAR(1);
