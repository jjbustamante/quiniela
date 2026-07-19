import { it, expect } from "vitest";
import { computeHomeState } from "./home-phase";
import type { BracketView } from "./api/bracket";
import type { RankingView } from "./api/ranking";
import type { MatchesView } from "./api/matches";
import type { PublicSummary } from "./api/summary";

const T0 = Date.parse("2026-06-11T17:00:00Z"); // group deadline / kickoff
const KO = Date.parse("2026-06-28T17:00:00Z"); // knockout deadline

function bracket(over: Partial<BracketView> = {}): BracketView {
  return {
    quinielaId: 1,
    totalMatches: 104,
    totalBets: 0,
    groupStageDeadline: new Date(T0).toISOString(),
    knockoutDeadline: new Date(KO).toISOString(),
    groups: [{ code: "A", filled: 0, total: 6, locked: false, matches: [] }],
    knockouts: [
      { code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, locked: false, matches: [] },
      { code: "FINAL", name: "Final", filled: 0, total: 1, unlocked: false, locked: false, matches: [] },
    ],
    ...over,
  };
}
function ranking(over: Partial<RankingView> = {}): RankingView {
  return {
    entries: [
      { rank: 1, userId: 9, displayName: "María", points: 0, delta: null, isYou: false, isBot: false },
      { rank: 1, userId: 1, displayName: "Tú", points: 0, delta: null, isYou: true, isBot: false },
    ],
    updatedAt: "2026-06-01T00:00:00Z",
    liveScoring: false,
    ...over,
  };
}
function matches(over: Partial<MatchesView> = {}): MatchesView {
  return { serverTime: new Date(T0 - 5 * 86400000).toISOString(), past: [], today: [], upcoming: [], ...over };
}
function summary(over: Partial<PublicSummary> = {}): PublicSummary {
  return {
    tournament: { slug: "x", name: "WC", startDate: "2026-06-11", endDate: "2026-07-19", hostCountryCodes: [], openingVenue: null, totalGroupStageMatches: 72, totalGroups: 12 },
    pool: { currency: "USD", entryFeeCents: 2000, potCents: 26000, panaCount: 13 },
    prizeSplit: [{ rank: 1, percentage: 80, payoutCents: 20800 }, { rank: 2, percentage: 15, payoutCents: 3900 }, { rank: 3, percentage: 5, payoutCents: 1300 }],
    roundMultipliers: [],
    testMode: false,
    ...over,
  };
}
// A bracket-level match (distinct from the matches-API MatchView). Only `played`
// matters for these tests; the rest is filler to satisfy the type.
function koMatch(played: boolean): BracketView["knockouts"][number]["matches"][number] {
  return {
    id: 1, team1Id: 1, team1Code: "A", team1Name: "A", team1Flag: "🏳",
    team2Id: 2, team2Code: "B", team2Name: "B", team2Flag: "🏳",
    kickoffAt: new Date(T0).toISOString(),
    betScoreT1: null, betScoreT2: null, betPredictedWinnerId: null,
    actualScoreT1: played ? 1 : null, actualScoreT2: played ? 0 : null, played,
  };
}

const nowBefore = T0 - 5 * 86400000; // 5 days before group lock
const nowGroupsLive = T0 + 86400000; // after group lock, before KO

it("FILL_GROUP before the group deadline", () => {
  const s = computeHomeState({ bracket: bracket({ totalBets: 40, groups: [{ code: "A", filled: 4, total: 6, locked: false, matches: [] }] }), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.focus.kind).toBe("fillGroup");
  if (s.focus.kind === "fillGroup") {
    expect(s.focus.full).toBe(false);
    expect(s.focus.href).toBe("/groups");
  }
  // pre-kickoff: no match played yet -> recap is the pot/countdown fallback
  expect(s.recap.kind).toBe("preKickoff");
});

it("FILL_GROUP softens to full when all group bets are in", () => {
  const s = computeHomeState({ bracket: bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: false, matches: [] }] }), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.focus.kind).toBe("fillGroup");
  if (s.focus.kind === "fillGroup") expect(s.focus.full).toBe(true);
});

it("FILL_KNOCKOUT picks the earliest open round once groups are locked", () => {
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [
      { code: "R32", name: "Dieciseisavos", filled: 3, total: 16, unlocked: true, locked: false, matches: [] },
      { code: "R16", name: "Octavos", filled: 0, total: 8, unlocked: false, locked: false, matches: [] },
    ],
  });
  const s = computeHomeState({ bracket: b, ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowGroupsLive });
  expect(s.focus.kind).toBe("fillKnockout");
  if (s.focus.kind === "fillKnockout") {
    expect(s.focus.roundCode).toBe("R32");
    expect(s.focus.href).toBe("/knockout/R32");
  }
});

it("FILL_KNOCKOUT skips a played-out round to the next open one (test-mode sim)", () => {
  // Mirrors test mode: R32 is simulated (all matches played) while the single
  // knockout deadline is still open, so R32 stays unlocked && !locked. The current
  // round is R16. The earlier bug picked R32 (first unlocked && !locked) and so
  // rendered "16VOS ●, 8VOS ✓" — a green chip AFTER the yellow one.
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [
      { code: "R32", name: "Dieciseisavos", filled: 16, total: 16, unlocked: true, locked: false, matches: [koMatch(true)] },
      { code: "R16", name: "Octavos", filled: 0, total: 8, unlocked: true, locked: false, matches: [koMatch(false)] },
      { code: "QF", name: "Cuartos", filled: 0, total: 4, unlocked: false, locked: false, matches: [] },
    ],
  });
  const s = computeHomeState({ bracket: b, ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowGroupsLive });
  expect(s.focus.kind).toBe("fillKnockout");
  if (s.focus.kind === "fillKnockout") {
    expect(s.focus.roundCode).toBe("R16");
    expect(s.focus.roundName).toBe("Octavos");
  }
  // Phase rail must read done → done → open → locked (no green after the yellow).
  expect(s.chips.find((c) => c.code === "R32")!.state).toBe("done");
  expect(s.chips.find((c) => c.code === "R16")!.state).toBe("open");
  expect(s.chips.find((c) => c.code === "QF")!.state).toBe("locked");
});

it("LIVE when groups are locked and no round is open to fill", () => {
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [{ code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, locked: false, matches: [] }],
  });
  const m = matches({ past: [{ id: 1, roundCode: "GROUP", groupCode: "A", kickoffAt: new Date(nowGroupsLive - 3600000).toISOString(), team1: { code: "BRA", name: "Brasil", flag: "🇧🇷" }, team2: { code: "GHA", name: "Ghana", flag: "🇬🇭" }, score: { t1: 2, t2: 0 }, played: true, live: false, yourPick: { t1: 2, t2: 0 }, pointsEarned: 7, breakdown: null, pickWinner: null, winner: null }] });
  const r = ranking({ entries: [{ rank: 1, userId: 1, displayName: "Tú", points: 12, delta: null, isYou: true, isBot: false }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: nowGroupsLive });
  expect(s.focus.kind).toBe("live");
  // a match is played -> recap shows results, not the pre-kickoff fallback
  expect(s.recap.kind).toBe("results");
  if (s.recap.kind === "results") expect(s.recap.recent[0].pointsEarned).toBe(7);
});

it("a match that finished TODAY shows in results, not as the upcoming fixture", () => {
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [{ code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, locked: false, matches: [] }],
  });
  // `today` bucket holds one game already played (1-1, +2) and one still to come today.
  const m = matches({
    today: [
      { id: 537333, roundCode: "GROUP", groupCode: "B", kickoffAt: new Date(nowGroupsLive - 3600000).toISOString(), team1: { code: "CAN", name: "Canadá", flag: "🇨🇦" }, team2: { code: "BIH", name: "Bosnia", flag: "🇧🇦" }, score: { t1: 1, t2: 1 }, played: true, live: false, yourPick: { t1: 1, t2: 2 }, pointsEarned: 2, breakdown: null, pickWinner: null, winner: null },
      { id: 537345, roundCode: "GROUP", groupCode: "D", kickoffAt: new Date(nowGroupsLive + 3600000).toISOString(), team1: { code: "USA", name: "USA", flag: "🇺🇸" }, team2: { code: "PAR", name: "Paraguay", flag: "🇵🇾" }, score: null, played: false, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null },
    ],
  });
  const s = computeHomeState({ bracket: b, ranking: ranking(), matches: m, summary: summary(), nowMs: nowGroupsLive });
  expect(s.recap.kind).toBe("results");
  if (s.recap.kind === "results") {
    expect(
      s.recap.recent.some((r) => r.matchId === 537333 && r.s1 === 1 && r.s2 === 1 && r.pointsEarned === 2),
    ).toBe(true);
    // the played-today game is NOT shown as the upcoming fixture — the unplayed one is.
    expect(s.recap.next?.t1Code).toBe("USA");
  }
});

it("CHAMPION when every match is played, with payout for a prize rank", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({
    entries: [
      { rank: 1, userId: 9, displayName: "María", points: 100, delta: null, isYou: false, isBot: false },
      { rank: 2, userId: 1, displayName: "Tú", points: 96, delta: null, isYou: true, isBot: false },
    ],
  });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: Date.parse("2026-07-20T00:00:00Z") });
  expect(s.focus.kind).toBe("champion");
  if (s.focus.kind === "champion") expect(s.focus.payoutCents).toBe(3900);
});

it("CHAMPION on finale day: the played FINAL still sits in today's bucket", () => {
  // Bucket membership is by kickoff date — on July 19 the played FINAL is in `today`,
  // not `past`. CHAMPION must still fire (it must not fall through to LIVE).
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({ entries: [{ rank: 2, userId: 1, displayName: "Tú", points: 96, delta: null, isYou: true, isBot: false }] });
  const m = matches({ today: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: Date.parse("2026-07-19T21:00:00Z") });
  expect(s.focus.kind).toBe("champion");
});

it("CHAMPION payout uses prize-eligible rank, not raw rank, when a bot tops the leaderboard", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({
    entries: [
      { rank: 1, userId: 99, displayName: "Pulpo Paul 🐙", points: 200, delta: null, isYou: false, isBot: true },
      { rank: 2, userId: 1, displayName: "Tú", points: 190, delta: null, isYou: true, isBot: false },
    ],
  });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: Date.parse("2026-07-20T00:00:00Z") });
  expect(s.focus.kind).toBe("champion");
  // rank 2 raw would give 15% (3900); prize-eligible rank (bot excluded) is 1 -> 80% (20800)
  if (s.focus.kind === "champion") expect(s.focus.payoutCents).toBe(20800);
});

it("winners: overall leaderboard topper can be a bot; prize podium excludes bots and splits ties evenly", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({
    entries: [
      { rank: 1, userId: 99, displayName: "Pulpo Paul 🐙", points: 200, delta: null, isYou: false, isBot: true },
      { rank: 2, userId: 1, displayName: "José", points: 190, delta: null, isYou: false, isBot: false },
      { rank: 3, userId: 2, displayName: "Arturo", points: 180, delta: null, isYou: false, isBot: false },
      { rank: 4, userId: 3, displayName: "Yeison", points: 170, delta: null, isYou: false, isBot: false },
      { rank: 4, userId: 4, displayName: "Ricardo", points: 170, delta: null, isYou: false, isBot: false },
      { rank: 6, userId: 5, displayName: "Eduardo", points: 160, delta: null, isYou: false, isBot: false },
    ],
  });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, live: false, yourPick: null, pointsEarned: null, breakdown: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({
    bracket: b,
    ranking: r,
    matches: m,
    summary: summary({ prizeSplit: [{ rank: 1, percentage: 80, payoutCents: 32000 }, { rank: 2, percentage: 15, payoutCents: 6000 }, { rank: 3, percentage: 5, payoutCents: 2000 }] }),
    nowMs: Date.parse("2026-07-20T00:00:00Z"),
  });
  expect(s.winners).not.toBeNull();
  if (s.winners) {
    expect(s.winners.overall).toEqual({ displayName: "Pulpo Paul 🐙", points: 200, isBot: true });
    expect(s.winners.prizeTop).toEqual([
      { rank: 1, payoutCentsEach: 32000, winners: [{ userId: 1, displayName: "José", points: 190, isYou: false }] },
      { rank: 2, payoutCentsEach: 6000, winners: [{ userId: 2, displayName: "Arturo", points: 180, isYou: false }] },
      {
        rank: 3,
        payoutCentsEach: 1000,
        winners: [
          { userId: 3, displayName: "Yeison", points: 170, isYou: false },
          { userId: 4, displayName: "Ricardo", points: 170, isYou: false },
        ],
      },
    ]);
  }
});

it("winners is null before the tournament is over", () => {
  const s = computeHomeState({ bracket: bracket(), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.winners).toBeNull();
});

it("standing reports your rank and whether anyone has scored", () => {
  const s = computeHomeState({ bracket: bracket(), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  expect(s.standing).not.toBeNull();
  expect(s.standing!.hasScored).toBe(false); // everyone at 0 pts
});

it("phase chips mark groups open / done and knockouts locked", () => {
  const s = computeHomeState({ bracket: bracket({ groups: [{ code: "A", filled: 0, total: 6, locked: false, matches: [] }] }), ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowBefore });
  const group = s.chips.find((c) => c.code === "GROUP")!;
  expect(group.state).toBe("open");
  expect(group.href).toBe("/groups");
  expect(s.chips.find((c) => c.code === "R32")!.state).toBe("locked");
});

it("THIRD_PLACE and FINAL are both open when they unlock together", () => {
  // After the semifinals, the 3rd-place game and the final both receive their
  // teams, so they unlock and become fillable at the same time. The phase rail
  // must show BOTH as open — the bug rendered the FINAL as ✓ done because only a
  // single round was treated as "current".
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [
      { code: "SF", name: "Semifinales", filled: 2, total: 2, unlocked: true, locked: false, matches: [koMatch(true)] },
      { code: "THIRD_PLACE", name: "Tercer puesto", filled: 0, total: 1, unlocked: true, locked: false, matches: [koMatch(false)] },
      { code: "FINAL", name: "Final", filled: 0, total: 1, unlocked: true, locked: false, matches: [koMatch(false)] },
    ],
  });
  const s = computeHomeState({ bracket: b, ranking: ranking(), matches: matches(), summary: summary(), nowMs: nowGroupsLive });
  expect(s.chips.find((c) => c.code === "SF")!.state).toBe("done");
  expect(s.chips.find((c) => c.code === "THIRD_PLACE")!.state).toBe("open");
  expect(s.chips.find((c) => c.code === "FINAL")!.state).toBe("open");
});
