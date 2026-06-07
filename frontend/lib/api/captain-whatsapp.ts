import { api } from "./client";

export type RosterEntry = {
  userId: number;
  displayName: string;
  visible: boolean;
};

export async function getWhatsappRoster(): Promise<RosterEntry[]> {
  return api<RosterEntry[]>("/api/captain/whatsapp-roster");
}

export async function setWhatsappVisibility(input: {
  userId: number;
  visible: boolean;
}): Promise<void> {
  await api("/api/captain/whatsapp-visibility", {
    method: "PUT",
    body: JSON.stringify(input),
  });
}
