import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Smoke + a11y test for the landing page. Expand as real pages land.

test.describe('home page', () => {
  test('renders without crashing', async ({ page }) => {
    await page.goto('/');
    // After next-intl scaffolding (Task 7), the layout renders lang from the
    // negotiated locale. Default is es-CO.
    await expect(page.locator('html')).toHaveAttribute('lang', 'es-CO');
  });

  test('has no critical accessibility violations (WCAG 2 AA)', async ({ page }) => {
    await page.goto('/');
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      // Purely decorative elements (the ghost-numeral watermark, host stripe)
      // are hidden from the a11y tree via aria-hidden. axe's color-contrast
      // rule doesn't always respect that, and flagging a 0.06-opacity
      // watermark as low-contrast is the rule misfiring on intent.
      .exclude('[aria-hidden="true"]')
      .analyze();
    expect(results.violations).toEqual([]);
  });

  test('landing page has no nav drawer trigger (unauthenticated)', async ({ page }) => {
    await page.goto('/');
    // The ☰ drawer lives in the TopBar, which only renders for authenticated
    // sessions. The public landing must not expose it.
    await expect(page.getByRole('button', { name: /menú|menu/i })).toHaveCount(0);
  });
});
