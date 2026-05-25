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
    const txt = await res.text();
    throw new ApiError(res.status, txt || res.statusText);
  }
  return (await res.json()) as T;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}
