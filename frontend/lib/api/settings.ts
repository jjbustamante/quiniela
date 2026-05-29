import { api } from "./client";

export async function setTimezone(timezone: string): Promise<void> {
  await api("/api/me/timezone", {
    method: "PUT",
    body: JSON.stringify({ timezone }),
  });
}
