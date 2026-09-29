import { expect, test } from '@playwright/test';
import { triggerEmulatorSend } from './support/emulator';

test('the emulator sends a message, and the alert appears in the browser', async ({ page }) => {
  await page.goto('/');

  const messageCountBefore = await page.locator('.message-item').count();

  await triggerEmulatorSend();

  const alert = page.getByRole('alert');
  await expect(alert).toBeVisible({ timeout: 10_000 });
  await expect(alert).toContainText('New message from emulator-1');

  // ">=" rather than an exact +1: the emulator's own periodic timer could also land a message
  // in this window, which is fine -- the requirement is that our triggered message shows up.
  await expect(async () => {
    const count = await page.locator('.message-item').count();
    expect(count).toBeGreaterThan(messageCountBefore);
  }).toPass({ timeout: 10_000 });
});
