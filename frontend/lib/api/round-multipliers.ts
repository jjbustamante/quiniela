import { api } from "./client";

export type RoundMultiplierRow = { code: string; name: string; multiplier: number };

export type RoundMultipliers = { rounds: RoundMultiplierRow[] };

export async function getRoundMultipliers(): Promise<RoundMultipliers> {
  return api<RoundMultipliers>("/api/admin/round-multipliers");
}

export async function updateRoundMultipliers(input: {
  rounds: { code: string; multiplier: number }[];
}): Promise<RoundMultipliers> {
  return api<RoundMultipliers>("/api/admin/round-multipliers", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
