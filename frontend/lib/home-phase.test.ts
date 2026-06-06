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
    testMode: false,
    ...over,
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

it("LIVE when groups are locked and no round is open to fill", () => {
  const b = bracket({
    groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }],
    knockouts: [{ code: "R32", name: "Dieciseisavos", filled: 0, total: 16, unlocked: false, locked: false, matches: [] }],
  });
  const m = matches({ past: [{ id: 1, roundCode: "GROUP", groupCode: "A", kickoffAt: new Date(nowGroupsLive - 3600000).toISOString(), team1: { code: "BRA", name: "Brasil", flag: "🇧🇷" }, team2: { code: "GHA", name: "Ghana", flag: "🇬🇭" }, score: { t1: 2, t2: 0 }, played: true, yourPick: { t1: 2, t2: 0 }, pointsEarned: 7, pickWinner: null, winner: null }] });
  const r = ranking({ entries: [{ rank: 1, userId: 1, displayName: "Tú", points: 12, delta: null, isYou: true, isBot: false }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: nowGroupsLive });
  expect(s.focus.kind).toBe("live");
  // a match is played -> recap shows results, not the pre-kickoff fallback
  expect(s.recap.kind).toBe("results");
  if (s.recap.kind === "results") expect(s.recap.recent[0].pointsEarned).toBe(7);
});

it("CHAMPION when every match is played, with payout for a prize rank", () => {
  const b = bracket({ groups: [{ code: "A", filled: 6, total: 6, locked: true, matches: [] }], knockouts: [{ code: "FINAL", name: "Final", filled: 1, total: 1, unlocked: true, locked: true, matches: [] }] });
  const r = ranking({ entries: [{ rank: 2, userId: 1, displayName: "Tú", points: 96, delta: null, isYou: true, isBot: false }] });
  const m = matches({ past: [{ id: 1, roundCode: "FINAL", groupCode: null, kickoffAt: "2026-07-19T17:00:00Z", team1: { code: "ARG", name: "Argentina", flag: "🇦🇷" }, team2: { code: "FRA", name: "Francia", flag: "🇫🇷" }, score: { t1: 1, t2: 0 }, played: true, yourPick: null, pointsEarned: null, pickWinner: null, winner: null }] });
  const s = computeHomeState({ bracket: b, ranking: r, matches: m, summary: summary(), nowMs: Date.parse("2026-07-20T00:00:00Z") });
  expect(s.focus.kind).toBe("champion");
  if (s.focus.kind === "champion") expect(s.focus.payoutCents).toBe(3900);
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
