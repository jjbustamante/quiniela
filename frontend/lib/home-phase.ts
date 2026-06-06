import type { BracketView } from "./api/bracket";
import type { RankingView } from "./api/ranking";
import type { MatchesView, MatchView } from "./api/matches";
import type { PublicSummary } from "./api/summary";

export type ChipState = "done" | "open" | "locked";
export type PhaseChip = { code: string; state: ChipState; href: string };

export type FocusState =
  | { kind: "fillGroup"; filled: number; total: number; full: boolean; deadline: string | null; href: string }
  | { kind: "fillKnockout"; roundCode: string; roundName: string; filled: number; total: number; full: boolean; deadline: string | null; href: string }
  | { kind: "live"; phase: "group" | "knockout"; rank: number | null; points: number | null }
  | { kind: "champion"; rank: number | null; points: number | null; payoutCents: number | null };

export type Standing = { rank: number; points: number; hasScored: boolean } | null;

export type RecapResult = {
  matchId: number;
  t1Code: string | null; t1Flag: string | null;
  t2Code: string | null; t2Flag: string | null;
  s1: number | null; s2: number | null;
  pointsEarned: number | null;
};
export type RecapNext = {
  t1Code: string | null; t1Flag: string | null;
  t2Code: string | null; t2Flag: string | null;
  kickoffAt: string;
} | null;
export type Recap =
  | { kind: "results"; recent: RecapResult[]; next: RecapNext }
  | { kind: "preKickoff"; potCents: number; currency: string; panaCount: number; startDate: string };

export type HomeState = { focus: FocusState; chips: PhaseChip[]; standing: Standing; recap: Recap };

function before(deadlineIso: string | null, nowMs: number): boolean {
  return deadlineIso == null || nowMs < Date.parse(deadlineIso);
}

export function computeHomeState(args: {
  bracket: BracketView;
  ranking: RankingView;
  matches: MatchesView;
  summary: PublicSummary;
  nowMs: number;
}): HomeState {
  const { bracket, ranking, matches, summary, nowMs } = args;

  const me = ranking.entries.find((e) => e.isYou) ?? null;
  const hasScored = ranking.entries.some((e) => e.points > 0);
  const standing: Standing = me ? { rank: me.rank, points: me.points, hasScored } : null;

  const groupOpen = before(bracket.groupStageDeadline, nowMs);
  const groupFilled = bracket.groups.reduce((a, g) => a + g.filled, 0);
  const groupTotal = bracket.groups.reduce((a, g) => a + g.total, 0);

  // First knockout round (by array order = sequence) that is open to bet.
  const openKnockout = bracket.knockouts.find((k) => k.unlocked && !k.locked);
  // "Tournament over" = the bracket has run its course (last knockout round locked)
  // AND there is nothing left scheduled. The locked-final guard is what separates a
  // genuine champion screen from a between-rounds lull where the `past` bucket holds a
  // few finished group matches but no knockout fixtures have been scheduled yet.
  const lastKnockout = bracket.knockouts.at(-1);
  const bracketComplete = lastKnockout != null && lastKnockout.locked;
  const allPlayed = bracketComplete && matches.past.length > 0 && matches.today.length === 0 && matches.upcoming.length === 0;

  // ── Focus (priority order) ────────────────────────────────────────────────
  let focus: FocusState;
  if (groupOpen) {
    focus = { kind: "fillGroup", filled: groupFilled, total: groupTotal, full: groupTotal > 0 && groupFilled >= groupTotal, deadline: bracket.groupStageDeadline, href: "/groups" };
  } else if (openKnockout) {
    focus = { kind: "fillKnockout", roundCode: openKnockout.code, roundName: openKnockout.name, filled: openKnockout.filled, total: openKnockout.total, full: openKnockout.total > 0 && openKnockout.filled >= openKnockout.total, deadline: bracket.knockoutDeadline, href: `/knockout/${openKnockout.code}` };
  } else if (allPlayed) {
    const payoutCents = me && me.rank <= summary.prizeSplit.length ? summary.prizeSplit[me.rank - 1].payoutCents : null;
    focus = { kind: "champion", rank: me?.rank ?? null, points: me?.points ?? null, payoutCents };
  } else {
    const inKnockout = bracket.knockouts.some((k) => k.unlocked);
    focus = { kind: "live", phase: inKnockout ? "knockout" : "group", rank: me?.rank ?? null, points: me?.points ?? null };
  }

  // ── Phase chips ───────────────────────────────────────────────────────────
  const chips: PhaseChip[] = [];
  chips.push({ code: "GROUP", state: groupOpen ? "open" : "done", href: "/groups" });
  for (const k of bracket.knockouts) {
    let state: ChipState;
    if (k.locked || (k.total > 0 && k.filled >= k.total && !openKnockout)) state = "done";
    else if (openKnockout && k.code === openKnockout.code) state = "open";
    else if (k.unlocked) state = "done"; // unlocked, past — treat as reachable/done
    else state = "locked";
    chips.push({ code: k.code, state, href: `/knockout/${k.code}` });
  }

  // ── Recap ─────────────────────────────────────────────────────────────────
  let recap: Recap;
  if (matches.past.length === 0) {
    recap = { kind: "preKickoff", potCents: summary.pool.potCents, currency: summary.pool.currency, panaCount: summary.pool.panaCount, startDate: summary.tournament.startDate };
  } else {
    const recent = [...matches.past]
      .sort((a, b) => Date.parse(b.kickoffAt) - Date.parse(a.kickoffAt))
      .slice(0, 2)
      .map((m: MatchView): RecapResult => ({ matchId: m.id, t1Code: m.team1.code, t1Flag: m.team1.flag, t2Code: m.team2.code, t2Flag: m.team2.flag, s1: m.score?.t1 ?? null, s2: m.score?.t2 ?? null, pointsEarned: m.pointsEarned }));
    const upcoming = [...matches.today, ...matches.upcoming].sort((a, b) => Date.parse(a.kickoffAt) - Date.parse(b.kickoffAt));
    const n = upcoming[0];
    const next: RecapNext = n ? { t1Code: n.team1.code, t1Flag: n.team1.flag, t2Code: n.team2.code, t2Flag: n.team2.flag, kickoffAt: n.kickoffAt } : null;
    recap = { kind: "results", recent, next };
  }

  return { focus, chips, standing, recap };
}
