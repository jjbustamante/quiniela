/**
 * Pure decision logic for Paul's user-facing feedback. Server actions and
 * client components stay thin adapters; every branch is decided (and tested)
 * here — same split as lib/ranking-payouts.ts.
 *
 * The *Outcome types mirror the server-action return shapes structurally, so
 * this module never imports from a "use server" file.
 */

/** Return shape of `acceptPaulSuggestionAction`. */
export type AcceptOutcome =
  | { ok: true; scoreT1: number; scoreT2: number; reasoning: string }
  | { ok: false; locked: boolean };

/** Return shape of `paulFillAllAction` (structurally equal to its PaulFillResult). */
export type FillOutcome =
  | { ok: true; created: number }
  | { ok: false; locked: boolean; error: string };

export type SingleFeedback =
  | { kind: "changed"; scoreT1: number; scoreT2: number; reasoning: string }
  | { kind: "kept"; scoreT1: number; scoreT2: number; reasoning: string }
  | { kind: "locked" }
  | { kind: "error" };

export type FillFeedback =
  | { kind: "filled"; count: number }
  | { kind: "nothing" }
  | { kind: "locked" }
  | { kind: "error" };

export function singleMatchFeedback(
  prior: { t1: number | null; t2: number | null },
  outcome: AcceptOutcome,
): SingleFeedback {
  if (!outcome.ok) return outcome.locked ? { kind: "locked" } : { kind: "error" };
  const kept = prior.t1 === outcome.scoreT1 && prior.t2 === outcome.scoreT2;
  return {
    kind: kept ? "kept" : "changed",
    scoreT1: outcome.scoreT1,
    scoreT2: outcome.scoreT2,
    reasoning: outcome.reasoning,
  };
}

export function fillAllFeedback(outcome: FillOutcome): FillFeedback {
  if (!outcome.ok) return outcome.locked ? { kind: "locked" } : { kind: "error" };
  return outcome.created > 0 ? { kind: "filled", count: outcome.created } : { kind: "nothing" };
}

/** i18n key suffixes (relative to the `group` / `lobby` namespaces). */
export const KEPT_HEADER_KEYS = ["paulAgreed1", "paulAgreed2", "paulAgreed3"] as const;
export const FILL_NOTHING_KEYS = ["fillNothing1", "fillNothing2"] as const;

/** Deterministic given `rand` in [0,1). Callers pass Math.random(). */
export function pickKey(keys: readonly string[], rand: number): string {
  const i = Math.min(keys.length - 1, Math.max(0, Math.floor(rand * keys.length)));
  return keys[i];
}
