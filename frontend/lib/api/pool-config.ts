import { api } from "./client";

export type PrizeRow = { rank: number; percentage: number };

export type PoolConfig = {
  currency: string;
  entryFeeCents: number;
  houseCutPercentage: number;
  prizeSplit: PrizeRow[];
};

export async function getPoolConfig(): Promise<PoolConfig> {
  return api<PoolConfig>("/api/admin/pool-config");
}

export async function updatePoolConfig(input: {
  entryFeeCents: number;
  houseCutPercentage: number;
  prizeSplit: PrizeRow[];
}): Promise<PoolConfig> {
  return api<PoolConfig>("/api/admin/pool-config", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
