import { expect, test } from '@playwright/test';
import { restartEmulatorContainer } from './support/docker';
import { triggerEmulatorSend } from './support/emulator';

// Matches EMULATOR_CLEAR_SCREEN_EVERY in docker-compose.yml.
const CLEAR_SCREEN_EVERY = 5;

test('every Nth emulator send is a ClearScreen, and the browser clears its list', async ({ page }) => {
  test.setTimeout(60_000);

  // The "every Nth send" counter lives only in the emulator's memory and counts periodic and
  // manual sends together, so a restart gives this test a known-zero starting point instead of
  // depending on how many sends earlier specs already caused.
  restartEmulatorContainer();

  await page.goto('/');

  // The emulator's control server takes a moment to come back up after the restart.
  await expect(async () => {
    await triggerEmulatorSend();
  }).toPass({ timeout: 30_000, intervals: [2_000] });

  for (let i = 2; i < CLEAR_SCREEN_EVERY; i++) {
    await triggerEmulatorSend();
  }

  const messageCountBeforeClear = await page.locator('.message-item').count();
  expect(messageCountBeforeClear).toBeGreaterThan(0);

  await triggerEmulatorSend(); // the CLEAR_SCREEN_EVERY'th send

  const alert = page.getByRole('alert');
  await expect(alert).toBeVisible({ timeout: 10_000 });
  await expect(alert).toContainText(/screen was cleared/i);

  await expect(page.locator('.message-item')).toHaveCount(0);
  await expect(page.getByText(/no messages yet/i)).toBeVisible();
});
