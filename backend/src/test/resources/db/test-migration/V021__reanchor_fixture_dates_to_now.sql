-- V021 (test-only): Re-anchor seeded fixture dates relative to NOW().
--
-- The production seed (V006) and test seed (V007) hardcode the real WC2026
-- calendar: the group stage is anchored at '2026-06-11 17:00 UTC', and the
-- integration suite was written assuming the test run happens *before* kickoff
-- (see the comment in MatchesControllerIT). Once real wall-clock time passes
-- that instant the suite breaks two ways:
--   * the group-stage betting window is closed by default -> saveBet / Paul
--     fill return 423 BracketLocked (BracketControllerIT, PaulControllerIT,
--     PaulServiceCachedIT);
--   * seeded matches fall into the past/today buckets instead of upcoming
--     (MatchesControllerIT bucket + past[0] assertions).
--
-- Fix: shift every match kickoff and both tournament deadlines by a single
-- delta so the whole tournament sits ~10 days in the future relative to the
-- test run, preserving the original relative spacing exactly. This reproduces
-- the "before kickoff" world the suite last passed under, independent of the
-- calendar date the suite runs on.
--
-- Test-only: this migration lives under db/test-migration and is NEVER applied
-- to production (prod keeps V006's real absolute dates).
DO $$
DECLARE
  shift INTERVAL := (NOW() + INTERVAL '10 days') - TIMESTAMPTZ '2026-06-11 17:00 UTC';
BEGIN
  UPDATE match SET kickoff_at = kickoff_at + shift WHERE tournament_id = 1;
  UPDATE tournament
     SET group_stage_deadline = group_stage_deadline + shift,
         knockout_deadline    = knockout_deadline + shift
   WHERE id = 1;
END $$;
