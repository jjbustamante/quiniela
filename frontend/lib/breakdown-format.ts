import type { ScoreBreakdown } from "@/lib/api/scorecard";

export type BreakdownLabels = {
  bdOutcome: string;
  bdTeam1: string;
  bdTeam2: string;
  bdDiff: string;
  multiplier: (n: number) => string;
  pts: (n: number) => string;
};

/** The non-zero point components as display strings, then `×mult` when > 1. */
export function breakdownParts(b: ScoreBreakdown, labels: BreakdownLabels): string[] {
  const parts: string[] = [];
  if (b.outcome) parts.push(`${labels.bdOutcome} ${labels.pts(b.outcome)}`);
  if (b.team1Exact) parts.push(`${labels.bdTeam1} ${labels.pts(b.team1Exact)}`);
  if (b.team2Exact) parts.push(`${labels.bdTeam2} ${labels.pts(b.team2Exact)}`);
  if (b.goalDiff) parts.push(`${labels.bdDiff} ${labels.pts(b.goalDiff)}`);
  // Only show the multiplier when something actually scored — a 0-point knockout
  // would otherwise render a lone, meaningless "×3".
  if (parts.length > 0 && b.multiplier > 1) parts.push(labels.multiplier(b.multiplier));
  return parts;
}
