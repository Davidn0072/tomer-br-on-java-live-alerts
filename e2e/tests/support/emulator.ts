const CONTROL_URL = 'http://localhost:9000/trigger';

/** Calls the emulator's manual-trigger control endpoint — see PLAN.md §2. */
export async function triggerEmulatorSend(): Promise<void> {
  const response = await fetch(CONTROL_URL, { method: 'POST' });
  if (!response.ok) {
    throw new Error(`Failed to trigger emulator send: ${response.status}`);
  }
}
