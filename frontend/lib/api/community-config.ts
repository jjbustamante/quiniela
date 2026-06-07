import { api } from "./client";

export type CommunityConfig = {
  url: string | null;
  enabled: boolean;
};

export async function getCommunityConfig(): Promise<CommunityConfig> {
  return api<CommunityConfig>("/api/admin/community-config");
}

export async function updateCommunityConfig(input: {
  url: string | null;
  enabled: boolean;
}): Promise<CommunityConfig> {
  return api<CommunityConfig>("/api/admin/community-config", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
