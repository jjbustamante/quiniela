import { defineRouting } from "next-intl/routing";

export const routing = defineRouting({
  locales: ["es-CO", "en"],
  defaultLocale: "es-CO",
  // Keep URLs un-prefixed for the default locale — friends paste WhatsApp links
  // without expecting an /es-CO/ prefix.
  localePrefix: "as-needed",
});

export type Locale = (typeof routing.locales)[number];
