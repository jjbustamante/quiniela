import type { Metadata } from "next";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import { TestModeBanner } from "@/components/shell/TestModeBanner";
import "./globals.css";

export const metadata: Metadata = {
  title: "Quiniela Panas — Mundial 2026",
  description: "La quiniela de los panas para el Mundial 2026. USA · CAN · MEX.",
};

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const locale = await getLocale();
  const messages = await getMessages();
  return (
    <html lang={locale}>
      <head>
        {/*
          Google Fonts loaded via <link> rather than next/font/google.
          next/font/google currently emits a virtual `target.css` URL that
          Turbopack (Next 16 default) doesn't resolve in every config — using
          a stylesheet link sidesteps that entirely and still gets us the
          three families we need: Archivo (body), Big Shoulders Display
          (headlines), IBM Plex Mono (labels & numerals).
        */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Archivo:wght@400;500;600;700;800;900&family=Big+Shoulders+Display:wght@500;700;800;900&family=IBM+Plex+Mono:wght@400;500;600;700&display=swap"
        />
      </head>
      <body className="min-h-screen bg-[var(--color-bg-primary)] text-[var(--color-text-primary)] antialiased">
        <NextIntlClientProvider locale={locale} messages={messages}>
          <TestModeBanner />
          {children}
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
