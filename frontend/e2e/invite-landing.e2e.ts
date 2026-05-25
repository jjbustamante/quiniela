import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// SKIP: requires a running backend with a seeded captain (invite_path = "valid-captain-abc").
// Unskip once a backend test-fixture seeding step is added to the CI pipeline (or
// once Plan 2 wires real tournament data we can use for seeding).
test.describe.skip("invite landing", () => {
  test("invite landing resolves inviter and shows sign-in", async ({ page }) => {
    await page.goto("/join/valid-captain-abc");
    await expect(page.getByText(/Andrés te invitó/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /iniciar sesión con Google/i })).toBeVisible();

    // A11y: no violations on the invite page.
    const a11y = await new AxeBuilder({ page }).analyze();
    expect(a11y.violations).toEqual([]);
  });

  test("invalid invite shows friendly error and no sign-in CTA", async ({ page }) => {
    await page.goto("/join/totally-bogus");
    await expect(page.getByText(/invitación no válida/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /iniciar sesión/i })).toHaveCount(0);
  });
});
