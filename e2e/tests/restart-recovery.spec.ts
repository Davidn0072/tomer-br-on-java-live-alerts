import { expect, test } from '@playwright/test';
import { restartServerContainer } from './support/docker';
import { triggerEmulatorSend } from './support/emulator';

test('the server restarts, and the browser reconnects and receives the next alert', async ({ page }) => {
  test.setTimeout(180_000);

  await page.goto('/');

  const status = page.getByRole('status');
  await expect(status).toHaveText(/live/i, { timeout: 15_000 });

  restartServerContainer();

  await expect(status).toHaveText(/reconnecting/i, { timeout: 20_000 });
  // Spring Boot + MSSQL round trip on restart can take a while under load -- generous timeout.
  await expect(status).toHaveText(/live/i, { timeout: 90_000 });

  const messageCountBefore = await page.locator('.message-item').count();

  // The emulator reconnects to the server independently of the browser's own WS reconnect, on
  // its own ~3s retry cycle, so it may not have reconnected in the same instant the browser's
  // status flips back to "Live". Retrying the trigger absorbs that race without weakening what
  // the test actually proves: no manual step anywhere gets the alert through.
  await expect(async () => {
    await triggerEmulatorSend();
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 30_000, intervals: [2_000] });

  await expect(async () => {
    const count = await page.locator('.message-item').count();
    expect(count).toBeGreaterThan(messageCountBefore);
  }).toPass({ timeout: 10_000 });
});
