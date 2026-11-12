-- Fixture exclusivo para la base temporal tic_source_test. No usar en un origen real.
CREATE SCHEMA m103_core;
CREATE SCHEMA m103_target;
CREATE SCHEMA m103_cycle;
CREATE SCHEMA m103_empty;
CREATE SCHEMA m103_forbidden;
CREATE SCHEMA m103_types;
CREATE SCHEMA "Ventas raras";

CREATE TABLE m103_target.visible_target(target_id integer PRIMARY KEY);
CREATE TABLE m103_target.hidden_target(secret_target_id integer PRIMARY KEY);
CREATE TABLE m103_core.parent(
    tenant integer NOT NULL, code integer NOT NULL, label varchar(40) NOT NULL,
    CONSTRAINT parent_pk PRIMARY KEY(code,tenant), CONSTRAINT parent_unique UNIQUE(tenant,label)
);
CREATE TABLE m103_core.child(
    id bigint PRIMARY KEY, tenant integer, code integer, amount numeric(10,2),
    happened timestamptz, payload jsonb, tags integer[], flag boolean, birthday date,
    visible_ref integer, hidden_ref integer,
    CONSTRAINT child_parent_fk FOREIGN KEY(code,tenant) REFERENCES m103_core.parent(code,tenant),
    CONSTRAINT child_visible_fk FOREIGN KEY(visible_ref) REFERENCES m103_target.visible_target(target_id),
    CONSTRAINT child_hidden_fk FOREIGN KEY(hidden_ref) REFERENCES m103_target.hidden_target(secret_target_id)
);
INSERT INTO m103_core.parent(tenant,code,label) VALUES(1,1,'NEVER_COPY_ROW_7');
CREATE TABLE m103_cycle.a(id integer PRIMARY KEY, b_id integer);
CREATE TABLE m103_cycle.b(id integer PRIMARY KEY, a_id integer REFERENCES m103_cycle.a(id));
ALTER TABLE m103_cycle.a ADD CONSTRAINT a_b_fk FOREIGN KEY(b_id) REFERENCES m103_cycle.b(id);
CREATE TABLE m103_types.types(id integer, discarded text, opaque bytea, document jsonb, tags integer[], uid uuid);
ALTER TABLE m103_types.types DROP COLUMN discarded;
CREATE TABLE "Ventas raras"."Pedidos. ""2026"""("a.b" integer PRIMARY KEY, "Nombre cliente" text);

GRANT USAGE ON SCHEMA m103_core,m103_target,m103_cycle,m103_empty,m103_types,"Ventas raras" TO tic_origin_reader;
GRANT REFERENCES ON ALL TABLES IN SCHEMA m103_core,m103_cycle,m103_types,"Ventas raras" TO tic_origin_reader;
GRANT REFERENCES ON m103_target.visible_target TO tic_origin_reader;
-- No SELECT sobre filas, ni privilegios sobre hidden_target o m103_forbidden.
