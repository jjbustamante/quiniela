/**
 * Display helpers for tournament dates / titles used by the landing and
 * home pages. Centralized so the same string ("USA · CAN · MEX 2026")
 * is built the same way everywhere.
 */

const MONTH_ABBR_ES = [
  "ENE",
  "FEB",
  "MAR",
  "ABR",
  "MAY",
  "JUN",
  "JUL",
  "AGO",
  "SEP",
  "OCT",
  "NOV",
  "DIC",
];

/** "USA · CAN · MEX 2026" — host codes joined with the middle-dot then the year. */
export function hostLine(hostCountryCodes: string[], startDate: string): string {
  const year = parseISODate(startDate).getUTCFullYear();
  return `${hostCountryCodes.join(" · ")} ${year}`;
}

/** Two-digit year glyph used as the landing-page ghost numeral ("26"). */
export function yearGlyph(startDate: string): string {
  const year = parseISODate(startDate).getUTCFullYear();
  return String(year % 100).padStart(2, "0");
}

/** "11.JUN — 19.JUL" — used in the landing-page footer date range. */
export function dateRangeShort(startDate: string, endDate: string): string {
  const s = parseISODate(startDate);
  const e = parseISODate(endDate);
  return `${formatDayMonth(s)} — ${formatDayMonth(e)}`;
}

/** "11.JUN.2026" — single-date long form used in the home page hero. */
export function dateLong(date: string): string {
  const d = parseISODate(date);
  return `${formatDayMonth(d)}.${d.getUTCFullYear()}`;
}

/** Whole days between now (UTC) and the kickoff date. Clamped to >= 0. */
export function daysUntil(startDate: string): number {
  const start = parseISODate(startDate).getTime();
  const ms = start - Date.now();
  return Math.max(0, Math.ceil(ms / (1000 * 60 * 60 * 24)));
}

/** "$24" — pot in the pool's currency, no fractional units. */
export function formatPot(potCents: number, currency: string): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(potCents / 100);
}

function parseISODate(iso: string): Date {
  // Treat "YYYY-MM-DD" as UTC midnight so day/month formatting is locale-stable.
  return new Date(`${iso}T00:00:00Z`);
}

function formatDayMonth(d: Date): string {
  return `${d.getUTCDate()}.${MONTH_ABBR_ES[d.getUTCMonth()]}`;
}
