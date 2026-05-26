import { api } from "./client";

export type PublicSummary = {
  tournament: {
    slug: string;
    name: string;
    startDate: string;
    endDate: string;
    hostCountryCodes: string[];
    openingVenue: string | null;
    totalGroupStageMatches: number;
    totalGroups: number;
  };
  pool: {
    currency: string;
    entryFeeCents: number;
    potCents: number;
    panaCount: number;
  };
};

export async function getPublicSummary(): Promise<PublicSummary> {
  return api<PublicSummary>("/api/public/summary", { authed: false });
}
