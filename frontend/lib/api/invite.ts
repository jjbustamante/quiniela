import { api, ApiError } from "./client";

export type InviteResolution = {
  valid: boolean;
  inviterDisplayName: string;
  inviterRole: "ADMIN" | "CAPTAIN";
};

export async function resolveInvite(path: string): Promise<InviteResolution | null> {
  try {
    return await api<InviteResolution>(`/api/invite/${encodeURIComponent(path)}`, { authed: false });
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
}
