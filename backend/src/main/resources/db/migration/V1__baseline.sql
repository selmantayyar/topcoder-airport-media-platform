-- Baseline migration. Proves end-to-end DB connectivity for the skeleton;
-- the real domain schema (flights, screens, campaigns, ...) arrives in ticket 03.
CREATE TABLE schema_baseline (
    id         INTEGER PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_baseline (id) VALUES (1);
