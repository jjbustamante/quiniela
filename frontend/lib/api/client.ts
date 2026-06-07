import { redirect } from "next/navigation";
import { auth } from "@/lib/auth";

const base = process.env.API_URL ?? "http://localhost:8080";

export type FetchOptions = Omit<RequestInit, "headers"> & {
  headers?: Record<string, string>;
  authed?: boolean;
};

export async function api<T>(path: string, opts: FetchOptions = {}): Promise<T> {
  const { authed = true, headers = {}, ...rest } = opts;
  if (authed) {
    const session = await auth();
    if (session?.backendToken) headers["Authorization"] = `Bearer ${session.backendToken}`;
  }
  const res = await fetch(`${base}${path}`, {
    cache: "no-store",
    ...rest,
    headers: { "Content-Type": "application/json", ...headers },
  });
  if (!res.ok) {
    // Stale session: cookie still signature-valid but backend rejected. Bounce
    // through a route handler that can actually clear the cookie (RSC can't).
    if (res.status === 401 && authed) redirect("/api/session/expired");
    const txt = await res.text();
    throw new ApiError(res.status, txt || res.statusText);
  }
  // Some endpoints (e.g. a void PUT returning 200 + no body) send an empty
  // body. `res.json()` would throw on that, so read text and only parse when
  // there's something — an empty body resolves to undefined.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}
