import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Smoke + a11y test for the landing page. Expand as real pages land.

test.describe('home page', () => {
  test('renders without crashing', async ({ page }) => {
    await page.goto('/');
    // The page emits a real <html lang="es"> with a heading; adapt this assertion
    // as the landing-page content evolves past the scaffold.
    await expect(page.locator('html')).toHaveAttribute('lang', 'es');
  });

  test('has no critical accessibility violations (WCAG 2 AA)', async ({ page }) => {
    await page.goto('/');
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();
    expect(results.violations).toEqual([]);
  });
});
