# Plan 8 — Knockout bracket seeding (test simulator)

**Status:** Ready for implementation
**Created:** 2026-05-29
**Design:** [`docs/superpowers/specs/2026-05-29-knockout-bracket-seeding-design.md`](../specs/2026-05-29-knockout-bracket-seeding-design.md)

> **For agentic workers:** REQUIRED SUB-SKILL — use superpowers:subagent-driven-development (preferred) or superpowers:executing-plans. Backend-only. Test-first. Gates from `backend/`: `./mvnw -B verify` (Spotless runs in verify; on a format failure run `./mvnw spotless:apply` then re-verify — never hand-format). Commit locally; do NOT push until the final task.

**Goal:** Make the test-mode simulator drive the knockout phase: when the group round finishes, seed the 32 R32 teams from group standings and wire the `match_parent_*` bracket tree, so the existing `advanceFromRound` carries winners through to the Final.

**Architecture:** All changes live in `AdminTestService` (already admin + test-mode gated). A new `seedKnockoutBracket()` runs once when GROUP completes: compute standings → pick 32 qualifiers → fill R32 slots → wire R16/QF/SF/Final/Third-place parents. The existing winner-advancement logic then works unchanged; one added step fills the third-place match from SF losers. `clean` is extended to reset the simulated bracket. Test-mode only — production fills the bracket from football-data.org.

**Tech Stack:** Spring Boot 4 + Java 25 + Maven + Postgres (Testcontainers ITs). No frontend, no migration.

---

## Conventions

- Single file of production code: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`. Single test file: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`.
- `AdminTestService` already has: `TOURNAMENT_ID = 1L`, `MatchRepository matches`, `RoundRepository rounds`, `JdbcTemplate jdbc`; `Match` entity getters `getId/getTournamentId/getRoundId/getGroupCode/getTeam1Id/getTeam2Id/getScoreT1/getScoreT2/getWinnerId/getPlayed/getMatchParent1Id/getMatchParent2Id`; team + parent writes go via `jdbc.update` (no entity setters for those — matches the existing `advanceFromRound`).
- Rounds: GROUP(seq1), R32(2), R16(3), QF(4), SF(5), THIRD_PLACE(6), FINAL(7). Match counts: 72/16/8/4/2/1/1.
- `matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, roundId)` gives a stable per-round ordering by kickoff.
- `rounds.findByTournamentIdAndCode(TOURNAMENT_ID, code)` → `Optional<Round>` (`Round.getId()`).
- The simulator is reached only via the test-mode-gated public `simulateRound`/`simulateAll`; private helpers don't re-check auth.
- Spotless + Google Java Format runs in `verify`.
- **CRITICAL test-vs-prod divergence:** the test seed `src/test/resources/db/test-migration/V007__seed_test_fixtures.sql` PRE-WIRES `match_parent_*` for R16/QF/SF/FINAL (lines 131-190) and leaves R32 with null teams + null parents. **Production does NOT** — `FootballDataLoader` wires no parents at all. So every new IT in this plan MUST first null the knockout parents to reproduce prod state, otherwise it tests V007's wiring instead of our seeder. Each IT does this with: `UPDATE match m SET team_1_id=NULL, team_2_id=NULL, match_parent_1_id=NULL, match_parent_2_id=NULL WHERE m.tournament_id=1 AND m.round_id <> (SELECT id FROM round WHERE tournament_id=1 AND code='GROUP')`. Do NOT remove that reset "to simplify" — it's what makes the test meaningful. (The IT `@AfterEach` should also restore a clean state; the existing one already resets matches.)

## Progress

- [ ] Task 1: group standings helper + unit-style IT for the tiebreak
- [ ] Task 2: seedKnockoutBracket (fill R32 + wire the tree) hooked into GROUP simulation
- [ ] Task 3: third-place SF-loser advancement + extend clean to reset the bracket
- [ ] Task 4: verify end-to-end (simulate/all → champion) + ship

---

## Task 1: Group standings helper

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`
- Modify: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`

Add a private standings computation + a small record. No behavior change to any
endpoint yet — but we test it through `simulate/round` in a later task; here we
add the helper and a focused IT that exercises the tiebreak via the seeded R32
once Task 2 lands. To keep Task 1 self-contained and test-first, the test here
asserts the helper's ordering indirectly is deferred — instead Task 1 ships the
helper + a direct package-visible entry the IT can call.

- [ ] **Step 1: Add the standings record + helper (package-visible for testing)**

In `AdminTestService.java`, add this record near the other records:
```java
  /** A team's standing within its group, after the group matches are played. */
  record Standing(Long teamId, String groupCode, int points, int goalDiff, int goalsFor) {}
```

Add this helper (package-visible so the IT can call it directly):
```java
  /**
   * Compute standings for every group from played GROUP matches. Returns, per
   * group code, its teams ordered best-first by: points (3/1/0) → goal
   * difference → goals for → team id (deterministic final tiebreak).
   */
  java.util.Map<String, List<Standing>> groupStandings() {
    Long groupRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "GROUP").orElseThrow().getId();
    List<Match> groupMatches =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, groupRoundId);

    // Accumulate points / goals per team, tracking each team's group code.
    record Acc(String group, int pts, int gf, int ga) {}
    java.util.Map<Long, Acc> acc = new java.util.HashMap<>();
    for (Match m : groupMatches) {
      if (!Boolean.TRUE.equals(m.getPlayed())) continue;
      Long t1 = m.getTeam1Id();
      Long t2 = m.getTeam2Id();
      if (t1 == null || t2 == null) continue;
      int s1 = m.getScoreT1() == null ? 0 : m.getScoreT1();
      int s2 = m.getScoreT2() == null ? 0 : m.getScoreT2();
      String g = m.getGroupCode();
      int p1 = s1 > s2 ? 3 : s1 == s2 ? 1 : 0;
      int p2 = s2 > s1 ? 3 : s1 == s2 ? 1 : 0;
      Acc a1 = acc.getOrDefault(t1, new Acc(g, 0, 0, 0));
      Acc a2 = acc.getOrDefault(t2, new Acc(g, 0, 0, 0));
      acc.put(t1, new Acc(g, a1.pts() + p1, a1.gf() + s1, a1.ga() + s2));
      acc.put(t2, new Acc(g, a2.pts() + p2, a2.gf() + s2, a2.ga() + s1));
    }

    java.util.Map<String, List<Standing>> byGroup = new java.util.TreeMap<>();
    acc.forEach(
        (teamId, a) ->
            byGroup
                .computeIfAbsent(a.group(), k -> new java.util.ArrayList<>())
                .add(new Standing(teamId, a.group(), a.pts(), a.gf() - a.ga(), a.gf())));
    for (List<Standing> list : byGroup.values()) {
      list.sort(
          java.util.Comparator.comparingInt(Standing::points)
              .thenComparingInt(Standing::goalDiff)
              .thenComparingInt(Standing::goalsFor)
              .reversed()
              .thenComparingLong(Standing::teamId)); // teamId ASC as final stable key
    }
    return byGroup;
  }
```

> **Comparator note:** `.reversed()` flips the three numeric keys to DESC, but we
> want teamId ASC as the *final* tiebreak. Applying `.reversed()` to the whole
> chain would also reverse teamId. To get points/GD/GF DESC + teamId ASC, build
> it as two parts instead — replace the `list.sort(...)` above with:
> ```java
>       list.sort(
>           java.util.Comparator.comparingInt(Standing::points).reversed()
>               .thenComparing(java.util.Comparator.comparingInt(Standing::goalDiff).reversed())
>               .thenComparing(java.util.Comparator.comparingInt(Standing::goalsFor).reversed())
>               .thenComparingLong(Standing::teamId));
> ```
> Use this second form (it is the correct one); the first is shown only to
> explain why it's wrong.

- [ ] **Step 2: Write the failing tiebreak IT**

Add to `AdminTestControllerIT.java`. This needs the service bean — add an
autowired field if not present: `@Autowired io.quiniela.api.admin.AdminTestService adminTestService;`

```java
  @Test
  void groupStandingsRanksByPointsThenGoalDiffThenGoalsForThenId() {
    // Reset, then hand-score Group A so the order is forced & unambiguous.
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    // The 6 Group A matches among its 4 teams. Find them ordered by kickoff.
    var ids =
        jdbc.queryForList(
            "SELECT m.id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.group_code='A' "
                + "ORDER BY m.kickoff_at ASC",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(ids).hasSize(6);
    // Collect the 4 team ids of Group A.
    var teamIds =
        jdbc.queryForList(
            "SELECT DISTINCT t.id FROM team t WHERE t.tournament_id=1 AND t.group_code='A' ORDER BY t.id",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(teamIds).hasSize(4);

    // Score every Group A match 1-0 to the lower team id where it's team_1, so
    // results are deterministic; then assert the helper orders by the rule.
    for (Long id : ids) {
      jdbc.update("UPDATE match SET score_t1=1, score_t2=0, winner_id=team_1_id, played=true WHERE id=?", id);
    }

    var standings = adminTestService.groupStandings();
    org.assertj.core.api.Assertions.assertThat(standings).containsKey("A");
    var groupA = standings.get("A");
    org.assertj.core.api.Assertions.assertThat(groupA).hasSize(4);
    // Every team's points are well-defined; the list is sorted best-first and
    // fully deterministic (no nulls, teamId breaks any remaining tie).
    for (int i = 0; i + 1 < groupA.size(); i++) {
      var hi = groupA.get(i);
      var lo = groupA.get(i + 1);
      boolean ordered =
          hi.points() > lo.points()
              || (hi.points() == lo.points() && hi.goalDiff() > lo.goalDiff())
              || (hi.points() == lo.points()
                  && hi.goalDiff() == lo.goalDiff()
                  && hi.goalsFor() > lo.goalsFor())
              || (hi.points() == lo.points()
                  && hi.goalDiff() == lo.goalDiff()
                  && hi.goalsFor() == lo.goalsFor()
                  && hi.teamId() < lo.teamId());
      org.assertj.core.api.Assertions.assertThat(ordered)
          .as("standings entry %d ordered before %d", i, i + 1)
          .isTrue();
    }
  }
```

- [ ] **Step 3: Run to verify it fails then passes**

`cd backend && ./mvnw verify` — the test fails before the helper exists, passes
after. (If you add helper + test together, run once and confirm green + that no
prior IT regressed.) spotless:apply if format fails.

- [ ] **Step 4: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminTestService.java \
        backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java
git commit -m "feat(backend): group standings helper for knockout seeding"
```

---

## Task 2: seedKnockoutBracket — fill R32 + wire the tree

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`
- Modify: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`

- [ ] **Step 1: Write the failing IT**

Add to `AdminTestControllerIT.java`:
```java
  @Test
  void simulatingGroupSeedsR32AndWiresBracket() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    jdbc.update(
        "UPDATE match m SET team_1_id=NULL, team_2_id=NULL, match_parent_1_id=NULL, match_parent_2_id=NULL "
            + "WHERE m.tournament_id=1 AND m.round_id <> "
            + "(SELECT id FROM round WHERE tournament_id=1 AND code='GROUP')");

    // Play the group round.
    mockMvc
        .perform(post("/api/admin/test/simulate/round").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundCode").value("GROUP"));

    // All 16 R32 matches now have both teams.
    Long r32WithoutTeams =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='R32' AND (m.team_1_id IS NULL OR m.team_2_id IS NULL)",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(r32WithoutTeams).isZero();

    Long r32Teams =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='R32'",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(r32Teams).isEqualTo(16L);

    // Every R16/QF/SF/FINAL/THIRD_PLACE match has both parents wired.
    Long childrenWithoutParents =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code IN ('R16','QF','SF','FINAL','THIRD_PLACE') "
                + "AND (m.match_parent_1_id IS NULL OR m.match_parent_2_id IS NULL)",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(childrenWithoutParents).isZero();

    // 32 distinct teams populated across the R32 (no duplicates).
    Long distinctR32Teams =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ("
                + "  SELECT team_1_id AS t FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32' "
                + "  UNION ALL "
                + "  SELECT team_2_id FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32') x",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(distinctR32Teams).isEqualTo(32L);
    Long uniqueR32Teams =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT t) FROM ("
                + "  SELECT team_1_id AS t FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32' "
                + "  UNION ALL "
                + "  SELECT team_2_id FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32') x",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(uniqueR32Teams).isEqualTo(32L);
  }
```

- [ ] **Step 2: Run to verify it fails**

`cd backend && ./mvnw verify` → FAIL (R32 teams stay null; no seeder).

- [ ] **Step 3: Add `seedKnockoutBracket()` + helpers**

In `AdminTestService.java`, add:
```java
  /**
   * After the group round is played, fill the 32 R32 team slots from group
   * standings and wire the parent-pointer tree (R16←R32, QF←R16, SF←QF,
   * FINAL←SF, THIRD_PLACE←SF) so advanceFromRound can carry winners onward.
   * Idempotent: no-op if R32 already has teams. Test-only (caller is gated).
   * NOTE: this is a valid test bracket, NOT FIFA's official slotting matrix.
   */
  private void seedKnockoutBracket() {
    Long r32RoundId = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "R32").orElseThrow().getId();
    List<Match> r32 =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, r32RoundId);
    if (r32.isEmpty()) return;
    if (r32.get(0).getTeam1Id() != null) return; // already seeded

    var byGroup = groupStandings();

    // 1st + 2nd of each group, plus the 8 best 3rd-placed.
    List<Standing> winners = new java.util.ArrayList<>();
    List<Standing> runnersUp = new java.util.ArrayList<>();
    List<Standing> thirds = new java.util.ArrayList<>();
    for (List<Standing> g : byGroup.values()) {
      if (g.size() > 0) winners.add(g.get(0));
      if (g.size() > 1) runnersUp.add(g.get(1));
      if (g.size() > 2) thirds.add(g.get(2));
    }
    // Rank the 12 third-placed by the same rule, take top 8.
    thirds.sort(
        java.util.Comparator.comparingInt(Standing::points).reversed()
            .thenComparing(java.util.Comparator.comparingInt(Standing::goalDiff).reversed())
            .thenComparing(java.util.Comparator.comparingInt(Standing::goalsFor).reversed())
            .thenComparingLong(Standing::teamId));
    List<Standing> bestThirds = thirds.subList(0, Math.min(8, thirds.size()));

    // Seed list of 32: winners, then runners-up, then best thirds.
    List<Long> seeds = new java.util.ArrayList<>();
    winners.forEach(s -> seeds.add(s.teamId()));
    runnersUp.forEach(s -> seeds.add(s.teamId()));
    bestThirds.forEach(s -> seeds.add(s.teamId()));

    // Defensive: if the group data was incomplete we may not have 32; bail
    // rather than wire a broken bracket.
    if (seeds.size() < 32 || r32.size() < 16) return;

    // Fill the 16 R32 matches: slot i = seeds[i] vs seeds[31-i] (stronger vs
    // weaker — a valid, deterministic test pairing).
    for (int i = 0; i < 16; i++) {
      Long home = seeds.get(i);
      Long away = seeds.get(31 - i);
      jdbc.update(
          "UPDATE match SET team_1_id = ?, team_2_id = ?, updated_at = NOW() WHERE id = ?",
          home,
          away,
          r32.get(i).getId());
    }

    // Wire parents for the rounds above R32.
    wireParents("R32", "R16");
    wireParents("R16", "QF");
    wireParents("QF", "SF");
    wireFinalAndThird();
  }

  /**
   * For each child match in childCode (ordered by kickoff), set its two parents
   * to consecutive parent-round matches (also ordered by kickoff): child i ←
   * parents[2i], parents[2i+1].
   */
  private void wireParents(String parentCode, String childCode) {
    Long parentRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, parentCode).orElseThrow().getId();
    Long childRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, childCode).orElseThrow().getId();
    List<Match> parents =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, parentRoundId);
    List<Match> children =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, childRoundId);
    for (int i = 0; i < children.size() && (2 * i + 1) < parents.size(); i++) {
      jdbc.update(
          "UPDATE match SET match_parent_1_id = ?, match_parent_2_id = ?, updated_at = NOW() WHERE id = ?",
          parents.get(2 * i).getId(),
          parents.get(2 * i + 1).getId(),
          children.get(i).getId());
    }
  }

  /** FINAL and THIRD_PLACE both take the two SF matches as parents. */
  private void wireFinalAndThird() {
    Long sfRoundId = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "SF").orElseThrow().getId();
    List<Match> sf =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, sfRoundId);
    if (sf.size() < 2) return;
    Long sf1 = sf.get(0).getId();
    Long sf2 = sf.get(1).getId();
    for (String code : new String[] {"FINAL", "THIRD_PLACE"}) {
      Long roundId = rounds.findByTournamentIdAndCode(TOURNAMENT_ID, code).orElseThrow().getId();
      List<Match> ms =
          matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, roundId);
      if (ms.isEmpty()) continue;
      jdbc.update(
          "UPDATE match SET match_parent_1_id = ?, match_parent_2_id = ?, updated_at = NOW() WHERE id = ?",
          sf1,
          sf2,
          ms.get(0).getId());
    }
  }
```

- [ ] **Step 4: Hook the seeder into the GROUP simulation**

In `simulateCurrentRound()`, the block currently is:
```java
    String advancedTo = null;
    if (knockout) {
      advancedTo = advanceFromRound(round.getId());
    }
    return new SimulateRoundResult(roundCode, played, advancedTo);
```
Change it to also seed the bracket when the group round was the one played:
```java
    String advancedTo = null;
    if (knockout) {
      advancedTo = advanceFromRound(round.getId());
    } else if ("GROUP".equals(roundCode) && played > 0) {
      seedKnockoutBracket();
      advancedTo = "R32";
    }
    return new SimulateRoundResult(roundCode, played, advancedTo);
```

- [ ] **Step 5: Run to verify it passes**

`cd backend && ./mvnw verify` → PASS (the seeding IT green; all prior green). spotless:apply if format fails.

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminTestService.java \
        backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java
git commit -m "feat(backend): seed R32 from standings + wire knockout bracket tree"
```

---

## Task 3: third-place SF-loser advancement + clean resets the bracket

**Files:**
- Modify: `backend/src/main/java/io/quiniela/api/admin/AdminTestService.java`
- Modify: `backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java`

The generic `advanceFromRound` fills children from parent **winners** — correct
for FINAL, wrong for THIRD_PLACE (which needs SF **losers**). And `clean` must
reset the simulated bracket so a re-sim starts fresh.

- [ ] **Step 1: Write the failing ITs**

Add to `AdminTestControllerIT.java`:
```java
  @Test
  void simulateAllReachesAChampionAndThirdPlaceGetsSfLosers() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    jdbc.update(
        "UPDATE match m SET team_1_id=NULL, team_2_id=NULL, match_parent_1_id=NULL, match_parent_2_id=NULL "
            + "WHERE m.tournament_id=1 AND m.round_id <> "
            + "(SELECT id FROM round WHERE tournament_id=1 AND code='GROUP')");

    mockMvc
        .perform(post("/api/admin/test/simulate/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // Final is played with a winner.
    Boolean finalPlayed =
        jdbc.queryForObject(
            "SELECT m.played FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='FINAL'",
            Boolean.class);
    org.assertj.core.api.Assertions.assertThat(finalPlayed).isTrue();
    Long finalWinner =
        jdbc.queryForObject(
            "SELECT m.winner_id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='FINAL'",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(finalWinner).isNotNull();

    // Third-place teams are the two SF losers (each SF's non-winner).
    var sfRows =
        jdbc.queryForList(
            "SELECT m.team_1_id AS t1, m.team_2_id AS t2, m.winner_id AS w "
                + "FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='SF' ORDER BY m.kickoff_at ASC");
    org.assertj.core.api.Assertions.assertThat(sfRows).hasSize(2);
    java.util.Set<Long> expectedLosers = new java.util.HashSet<>();
    for (var row : sfRows) {
      long t1 = ((Number) row.get("t1")).longValue();
      long t2 = ((Number) row.get("t2")).longValue();
      long w = ((Number) row.get("w")).longValue();
      expectedLosers.add(w == t1 ? t2 : t1);
    }
    var thirdTeams =
        jdbc.queryForList(
            "SELECT team_1_id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='THIRD_PLACE' "
                + "UNION ALL "
                + "SELECT team_2_id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='THIRD_PLACE'",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(new java.util.HashSet<>(thirdTeams))
        .isEqualTo(expectedLosers);
  }

  @Test
  void cleanResetsSimulatedBracketButKeepsGroupTeams() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    mockMvc
        .perform(post("/api/admin/test/simulate/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    long groupTeamsBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.team_1_id IS NOT NULL",
            Long.class);

    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // Knockout matches have null teams + null parents again.
    Long koWithTeamsOrParents =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code <> 'GROUP' "
                + "AND (m.team_1_id IS NOT NULL OR m.team_2_id IS NOT NULL "
                + "     OR m.match_parent_1_id IS NOT NULL OR m.match_parent_2_id IS NOT NULL)",
            Long.class);
    org.assertj.core.api.Assertions.assertThat( koWithTeamsOrParents).isZero();

    // Group matches keep their teams.
    long groupTeamsAfter =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.team_1_id IS NOT NULL",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupTeamsAfter).isEqualTo(groupTeamsBefore);
  }
```

- [ ] **Step 2: Run to verify they fail**

`cd backend && ./mvnw verify` → FAIL (third-place empty; clean leaves bracket wired).

- [ ] **Step 3: Fill third-place from SF losers**

In `simulateCurrentRound()`, after the `advanceFromRound` call in the knockout
branch, add a special case: when the round just played is `SF`, fill the
third-place match from the two SF losers. Change the knockout branch to:
```java
    String advancedTo = null;
    if (knockout) {
      advancedTo = advanceFromRound(round.getId());
      if ("SF".equals(roundCode)) {
        fillThirdPlaceFromSfLosers(round.getId());
      }
    } else if ("GROUP".equals(roundCode) && played > 0) {
      seedKnockoutBracket();
      advancedTo = "R32";
    }
    return new SimulateRoundResult(roundCode, played, advancedTo);
```
Add the helper:
```java
  /** After SF is played, set the third-place match's teams to the two SF losers. */
  private void fillThirdPlaceFromSfLosers(Long sfRoundId) {
    List<Match> sf =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, sfRoundId);
    List<Long> losers = new java.util.ArrayList<>();
    for (Match m : sf) {
      if (m.getWinnerId() == null || m.getTeam1Id() == null || m.getTeam2Id() == null) continue;
      losers.add(m.getWinnerId().equals(m.getTeam1Id()) ? m.getTeam2Id() : m.getTeam1Id());
    }
    if (losers.size() < 2) return;
    Long thirdRoundId =
        rounds.findByTournamentIdAndCode(TOURNAMENT_ID, "THIRD_PLACE").orElseThrow().getId();
    List<Match> third =
        matches.findByTournamentIdAndRoundIdOrderByKickoffAtAsc(TOURNAMENT_ID, thirdRoundId);
    if (third.isEmpty()) return;
    jdbc.update(
        "UPDATE match SET team_1_id = ?, team_2_id = ?, updated_at = NOW() WHERE id = ?",
        losers.get(0),
        losers.get(1),
        third.get(0).getId());
  }
```

> Ordering note: `advanceFromRound(SF)` runs first and fills FINAL from the SF
> winners (FINAL's `team_1_id` becomes non-null). `fillThirdPlaceFromSfLosers`
> then fills THIRD_PLACE. Both FINAL and THIRD_PLACE are later rounds than SF, so
> on the next `simulateCurrentRound` iteration the current round is FINAL (seq 7)
> — but THIRD_PLACE is seq 6, lower, so it plays first. Either way both have
> teams and get played by `simulateAll`'s loop.

- [ ] **Step 4: Extend `clean` to reset the simulated bracket**

In `clean(...)`, after the existing `UPDATE match SET score_t1=NULL...` and
before the `quiniela` update, add the bracket reset:
```java
    jdbc.update(
        "UPDATE match SET team_1_id=NULL, team_2_id=NULL, "
            + "match_parent_1_id=NULL, match_parent_2_id=NULL, updated_at=NOW() "
            + "WHERE tournament_id = ? AND round_id <> "
            + "(SELECT id FROM round WHERE tournament_id = ? AND code = 'GROUP')",
        TOURNAMENT_ID,
        TOURNAMENT_ID);
```
(Group matches keep their loader-provided teams; every knockout match is reset.)

- [ ] **Step 5: Run to verify they pass**

`cd backend && ./mvnw verify` → PASS (both new ITs green; all prior green). spotless:apply if format fails.

- [ ] **Step 6: Commit**
```bash
git add backend/src/main/java/io/quiniela/api/admin/AdminTestService.java \
        backend/src/test/java/io/quiniela/api/admin/AdminTestControllerIT.java
git commit -m "feat(backend): third-place from SF losers + clean resets the bracket"
```

---

## Task 4: Verify end-to-end + ship

- [ ] **Step 1: Full backend verify**
`cd backend && ./mvnw -B verify` — all ITs green (the 4 new AdminTest cases + every prior test). Report the aggregate "Tests run:" line.

- [ ] **Step 2: Confirm no scope bleed**
`git diff <plan-start>..HEAD --stat` — only `AdminTestService.java` +
`AdminTestControllerIT.java` changed. No migration, no frontend, no other backend
file. (Plan start = the commit before Task 1.)

- [ ] **Step 3: Tick checkboxes, commit, push**
Mark all Progress + verification checkboxes `[x]`, then:
```bash
git add docs/superpowers/plans/2026-05-29-quiniela-plan-8-knockout-seeding.md
git commit -m "docs: Plan 8 complete — knockout bracket seeding"
git push origin master
```

- [ ] **Step 4: Watch CI + smoke prod**
Watch backend CI to green (`gh run watch <id> --exit-status`) — backend-only
change, so only backend CI matters; it deploys to Cloud Run on master. Then:
```bash
curl -sS -o /dev/null -w "%{http_code}\n" https://quiniela-api-ko2t5go6hq-uc.a.run.app/api/admin/test/state
```
Expected: 401 (unauth, route exists). Full knockout simulation is verified by the
ITs; in-app confirmation happens during the manual dry-run (admin → /admin/test →
Simular todo → check /matches + bracket pages show a played knockout through the
Final).

**Verification:**
- [ ] Backend `./mvnw verify` green (4 new knockout-seeding ITs)
- [ ] Only AdminTestService + its IT changed (no migration/frontend)
- [ ] Backend CI green on `master`; `/api/admin/test/state` 401 unauth in prod

---

## Out of scope
FIFA's official 3rd-place matrix + exact crossing pattern; any ResultsSource
interface; frontend changes; the FootballDataLoader re-sync idempotency audit
(separately tracked, pre-launch). Test-mode + admin gated; 409-locked once test
mode is off — never runs in production.
