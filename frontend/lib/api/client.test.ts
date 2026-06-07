import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/auth", () => ({ auth: vi.fn().mockResolvedValue(null) }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

import { api, ApiError } from "./client";

/** A fetch Response stub whose `json`/`text` behave like the real thing. */
function mockResponse(status: number, body: string) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: "",
    json: async () => JSON.parse(body), // throws on "" exactly like the browser
    text: async () => body,
  } as Response;
}

afterEach(() => vi.restoreAllMocks());

describe("api", () => {
  it("returns undefined for an empty 200 body (void endpoints)", async () => {
    // The whatsapp-visibility PUT returns 200 with no body; the helper must not
    // choke trying to JSON-parse an empty string.
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(200, "")));
    await expect(api("/x", { authed: false })).resolves.toBeUndefined();
  });

  it("parses a JSON 200 body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(200, JSON.stringify({ a: 1 }))));
    await expect(api("/x", { authed: false })).resolves.toEqual({ a: 1 });
  });

  it("throws ApiError on a non-2xx response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(mockResponse(500, "boom")));
    await expect(api("/x", { authed: false })).rejects.toBeInstanceOf(ApiError);
  });
});
