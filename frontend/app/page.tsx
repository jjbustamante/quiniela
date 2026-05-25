import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { auth } from "@/lib/auth";
import { AuthButton } from "@/components/AuthButton";

export default async function Home() {
  const session = await auth();
  if (session?.userId) redirect("/home");

  const t = await getTranslations("landing");

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 py-24">
      <div className="flex flex-col items-center gap-3 text-center">
        <h1 className="text-5xl font-semibold tracking-tight text-[var(--color-accent-cyan)] uppercase">
          {t("title")}
        </h1>
        <p className="text-lg text-[var(--color-text-muted)]">{t("subtitle")}</p>
      </div>

      <AuthButton />
    </main>
  );
}
