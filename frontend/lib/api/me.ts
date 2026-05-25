import { api } from "./client";

export type MeResponse = {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: "ADMIN" | "CAPTAIN" | "PLAYER";
  invitePath: string | null;
  canInvite: boolean;
  invitedByUserId: number | null;
};

export async function getMe(): Promise<MeResponse> {
  return api<MeResponse>("/api/me");
}
