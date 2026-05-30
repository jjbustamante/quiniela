-- V014: Team strength anchor for Paul's AI predictions.
-- Nullable: playoff-pending slots (TBD_*) stay NULL; the prompt context
-- builder degrades gracefully when a team has no ranking.
ALTER TABLE team ADD COLUMN fifa_ranking INT;

-- Seed FIFA rankings for the known WC2026 teams (men's ranking, approx late-2025).
-- Placeholder teams (TBD_K*/TBD_L*) intentionally left NULL.
UPDATE team SET fifa_ranking = CASE code
    WHEN 'ARG' THEN 1   WHEN 'ESP' THEN 2   WHEN 'FRA' THEN 3   WHEN 'ENG' THEN 4
    WHEN 'BRA' THEN 5   WHEN 'POR' THEN 6   WHEN 'NED' THEN 7   WHEN 'BEL' THEN 8
    WHEN 'GER' THEN 9   WHEN 'CRO' THEN 10  WHEN 'ITA' THEN 11  WHEN 'MAR' THEN 12
    WHEN 'COL' THEN 13  WHEN 'URU' THEN 14  WHEN 'USA' THEN 15  WHEN 'MEX' THEN 16
    WHEN 'SUI' THEN 17  WHEN 'SEN' THEN 18  WHEN 'JPN' THEN 19  WHEN 'KOR' THEN 20
    WHEN 'IRN' THEN 21  WHEN 'AUS' THEN 22  WHEN 'ECU' THEN 23  WHEN 'EGY' THEN 24
    WHEN 'NOR' THEN 25  WHEN 'CIV' THEN 26  WHEN 'NGA' THEN 27  WHEN 'PAN' THEN 28
    WHEN 'CRC' THEN 29  WHEN 'KSA' THEN 30  WHEN 'TUN' THEN 31  WHEN 'CAN' THEN 32
    WHEN 'QAT' THEN 33  WHEN 'CPV' THEN 34  WHEN 'GHA' THEN 35  WHEN 'IRQ' THEN 36
    WHEN 'JAM' THEN 37  WHEN 'NZL' THEN 38  WHEN 'HON' THEN 39  WHEN 'CUR' THEN 40
    ELSE fifa_ranking
END
WHERE tournament_id = 1;
