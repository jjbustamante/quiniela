-- V009: Tournament display metadata for the public landing / home pages.
--
-- host_country_codes and opening_venue are tournament-level facts that
-- belong in the schema (so the v2 UCL edition can supply its own values
-- without code changes). Comma-separated for host_country_codes — the
-- landing page renders them as "USA · CAN · MEX 2026" so order matters.

ALTER TABLE tournament
    ADD COLUMN host_country_codes VARCHAR(64),
    ADD COLUMN opening_venue      VARCHAR(128);

UPDATE tournament
SET host_country_codes = 'USA,CAN,MEX',
    opening_venue      = 'Estadio Azteca'
WHERE slug = 'fifa-wc-2026';
