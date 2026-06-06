import { ApiError } from "./client";

/**
 * Swallow a 423 (round locked) from a bet-save that raced the betting deadline
 * — the page was open, then the deadline passed before the tap, so the backend
 * rejects the save. Returning lets the server action finish cleanly instead of
 * rendering as an HTTP 500. Anything else is re-thrown so genuine failures
 * still surface rather than silently no-op.
 */
export function ignoreLockedRace(e: unknown): void {
  if (e instanceof ApiError && e.status === 423) return;
  throw e;
}
