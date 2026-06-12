# Frozen per-match points + kickoff lock — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development (or executing-plans). Steps use `- [ ]`. Read the spec alongside this plan: `docs/superpowers/specs/2026-06-12-scorecard-frozen-points-and-kickoff-lock-design.md` — it holds the canonical SQL and message copy.

**Goal:** Make the scorecard show real frozen per-match points (matching the correct leaderboard) with a polite note when a bet was placed/edited after its match, and lock each bet at its match's kickoff so it can't recur.

**Architecture:** New `bet.points` column frozen by the scoring trigger; one-time backfill in the migration with a `SUM(bet.points)=quiniela.points` assertion; `ScorecardService` reads `bet.points` + emits a note; `BracketService.saveBet` rejects post-kickoff bets; frontend renders the localized note. `quiniela.points` (leaderboard) is never written.

**Tech Stack:** Java 21 / Spring Boot 4, Postgres + Flyway, JUnit5 + Testcontainers; Next.js + i18n + Vitest.

**Conventions:** run one backend test class from `backend/`: `./mvnw -q test -Dtest=ClassName` (Docker needed). Migrations are `db/migration/V0xx__*.sql`; next is **V021**. `bet` PK is `(quiniela_id, match_id)`. Scoring fn: `score_match_for_bet(is_knockout, bet_t1, bet_t2, real_t1, real_t2, predicted_winner_id, advanced_team_id, multiplier)`. Trigger: `update_players_score()` / `matches_score_update_trigger` (BEFORE UPDATE OF score_t1,score_t2,advanced_team_id ON match). Two played matches in prod: 537327 (g1), 537328 (g2).

---

## Task 1: V021 migration — `bet.points` column, trigger writes it (pre-kickoff bets only), backfill + assertion

**Files:**
- Create: `backend/src/main/resources/db/migration/V021__bet_points_frozen.sql`
- Test: `backend/src/test/java/io/quiniela/api/support/V021MigrationTest.java`

- [ ] **Step 1: Write the failing migration test** (mirror existing `V0xxMigrationTest` classes; they extend `AbstractIntegrationTest` and assert against a real Postgres). Cover: (a) `bet.points` exists; (b) when a match is scored, a bet placed **before** kickoff gets `bet.points` = its computed score and `quiniela.points` matches; (c) a bet **created after** the match's `kickoff_at` is NOT credited (its `bet.points` stays 0 and `quiniela.points` excludes it) — both on first scoring AND on a re-score (landmine de-armed); (d) the `SUM(bet.points over played) = quiniela.points` invariant holds.

  Seed teams/round/match via JdbcTemplate; insert bets with explicit `created_at` before/after `kickoff_at`; `UPDATE match SET score_t1=.., score_t2=..` to fire the trigger; assert columns.

- [ ] **Step 2: Run it red** — `./mvnw -q test -Dtest=V021MigrationTest` → FAIL (column/behavior missing).

- [ ] **Step 3: Write `V021__bet_points_frozen.sql`** using the spec's §1–§3 SQL verbatim:
  1. `ALTER TABLE bet ADD COLUMN points INT NOT NULL DEFAULT 0;`
  2. `CREATE OR REPLACE FUNCTION update_players_score()` — copy the current V019 body, then: change the bet loop's `WHERE b.match_id = NEW.id` to `WHERE b.match_id = NEW.id AND b.created_at <= NEW.kickoff_at`, and inside the loop after computing `new_points`, add `UPDATE bet SET points = new_points WHERE quiniela_id = bet_row.quiniela_id AND match_id = NEW.id;`. (The trigger binding from V017 stays; only `CREATE OR REPLACE FUNCTION` is needed.)
  3. Backfill (a)+(b)+(c) exactly as in spec §3.
  4. The `DO $$ ... RAISE EXCEPTION` assertion from spec §3.

  ⚠ Read the current `update_players_score()` from `V019__round_points_multiplier.sql` and preserve every line except the two changes above (the advanced_team_id maintenance, the early `RETURN NEW` on no-change, the multiplier lookup, the delta `UPDATE quiniela`).

- [ ] **Step 4: Run it green** — `./mvnw -q test -Dtest=V021MigrationTest` → PASS.

- [ ] **Step 5: Regression** — `./mvnw -q test -Dtest=V019MigrationTest,ScoringTriggerIT,AdvancedTeamScoringIT` → PASS (existing scoring behavior preserved for pre-kickoff bets).

- [ ] **Step 6: Commit** — `git add` the migration + test; `git commit -m "feat(scoring): bet.points frozen column + trigger writes it (pre-kickoff only) + backfill"`.

---

## Task 2: Per-match kickoff lock in `BracketService.saveBet`

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/bracket/BracketService.java`
- Modify/Test: `backend/src/test/java/io/quiniela/api/bracket/BracketControllerIT.java` (or a `BracketServiceIT`)

- [ ] **Step 1: Write the failing test** — saving a bet for a match whose `kickoff_at` is in the past → `BracketLockedException` (HTTP 423 via the controller); saving for a future match before its kickoff still succeeds; (keep an assertion that the round-deadline path still locks). Seed a match with `kickoff_at = now() - interval '1 hour'` and a future match.

- [ ] **Step 2: Run red** — `./mvnw -q test -Dtest=BracketControllerIT` → FAIL (past-kickoff bet currently allowed).

- [ ] **Step 3: Implement** — in `saveBet`, after the match is resolved and before persisting, add (keeping the existing `groupStageDeadline/knockoutDeadline` check):
  ```java
  if (m.kickoffAt() != null && now.isAfter(m.kickoffAt()))
      throw new BracketLockedException("Este partido ya comenzó");
  ```
  (Use whatever accessor the resolved match exposes for kickoff — match the surrounding code; `now` already exists in the method.)

- [ ] **Step 4: Run green** — `./mvnw -q test -Dtest=BracketControllerIT` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(bracket): lock each bet at its match kickoff (keep round deadline too)"`.

---

## Task 3: `ScorecardService` — read frozen `bet.points` + emit note

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/ranking/ScorecardService.java` (+ its `MatchScoreView`/`ScorecardView` records — add `String note`)
- Test: `backend/src/test/java/io/quiniela/api/ranking/ScorecardControllerIT.java` (extend existing)

- [ ] **Step 1: Write the failing test** — seed a played match + 3 bets via the DB: (i) clean pre-kickoff bet with `bet.points` set → scorecard shows that value, `note == null`; (ii) bet `created_at > kickoff` with `bet.points = 0` but a current prediction that *would* recompute > 0 → scorecard shows **0** and `note == "PLACED_AFTER_KICKOFF"`; (iii) bet `created_at <= kickoff`, `updated_at > kickoff`, `bet.points = 4` but current recompute = 7 → shows **4** and `note == "EDITED_AFTER_KICKOFF"`. Also assert the per-round subtotal equals `quiniela.points`.

- [ ] **Step 2: Run red** — `./mvnw -q test -Dtest=ScorecardControllerIT` → FAIL.

- [ ] **Step 3: Implement** — change the scorecard query to also select `b.points AS frozen_points`, `b.created_at`, `b.updated_at`. Per match: the displayed points = `frozen_points` (sum these into the round subtotal). Compute `ScoreBreakdown.of(currentBet…)` only to detect divergence: if `breakdown.total() != frozen_points`, set `note = (created_at > kickoff_at) ? "PLACED_AFTER_KICKOFF" : "EDITED_AFTER_KICKOFF"`, else `note = null`. Add `note` to the match-row record. Keep the header total = `quiniela.points` (unchanged).

- [ ] **Step 4: Run green** — `./mvnw -q test -Dtest=ScorecardControllerIT` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(scorecard): display frozen bet.points + placed/edited-after-kickoff note"`.

---

## Task 4: Frontend — render the note (ES/EN)

**Files:**
- Modify: `frontend/components/ranking/MatchScoreRow.tsx` (+ its test)
- Modify: `frontend/lib/api/scorecard.ts` (add `note?: 'PLACED_AFTER_KICKOFF' | 'EDITED_AFTER_KICKOFF'` to the match-row type)
- Modify: `frontend/i18n` ES + EN message catalogs

- [ ] **Step 1: Write the failing test** — `MatchScoreRow.test.tsx`: given a row with `note: 'PLACED_AFTER_KICKOFF'`, renders the ES/EN string from §6; with `note: 'EDITED_AFTER_KICKOFF'`, renders that string; with no note, renders nothing extra.

- [ ] **Step 2: Run red** — from `frontend/`: `pnpm vitest run MatchScoreRow` → FAIL.

- [ ] **Step 3: Implement** — add the two keys to the i18n ES + EN catalogs (copy from spec §6, no "trampa"/"cheat"), thread `note` through the scorecard API type, and in `MatchScoreRow` render a small muted note line when `note` is set (localized via the existing translation hook).

- [ ] **Step 4: Run green** — `pnpm vitest run MatchScoreRow` and the scorecard API type-check/lint → PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(scorecard-ui): show polite note when a bet was placed/edited after the match"`.

---

## Task 5: Full suites + prod backfill verification

- [ ] **Step 1:** `cd backend && ./mvnw -q test` — feature classes green (note pre-existing date-dependent `MatchesControllerIT`/`BracketControllerIT` flakes are unrelated; confirm no NEW failures). `cd frontend && pnpm test` for the touched suites.
- [ ] **Step 2 (post-deploy, by Juan):** the V021 migration runs via Flyway on deploy; its assertion guards the backfill. After deploy, spot-check `/ranking/19` (Daneff: g1 shows 0 + placed-after note, total 7), `/ranking/36` (Artistic: g1 shows 4 + edited-after note, total 9), `/ranking/5` (Gabriela: no note, total 10).
- [ ] **Step 3:** commit any cleanup; open PR / merge per Juan.

## Self-review notes
- Spec coverage: column+trigger+backfill+assertion (T1), kickoff lock (T2), scorecard frozen+note (T3), frontend note (T4), verification (T5). ✓
- Leaderboard `quiniela.points` is never written by any task. ✓
- Type consistency: `note` is `PLACED_AFTER_KICKOFF | EDITED_AFTER_KICKOFF | null` across backend record, API type, and frontend.
