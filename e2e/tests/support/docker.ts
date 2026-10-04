import { execFileSync } from 'node:child_process';
import path from 'node:path';

const repoRoot = path.resolve(process.cwd(), '..');

/** Restarts the `server` container in place, simulating a crash/redeploy mid-run. */
export function restartServerContainer(): void {
  execFileSync('docker', ['compose', 'restart', 'server'], { cwd: repoRoot, stdio: 'inherit' });
}

/**
 * Restarts the `emulator` container in place. Used by clear-screen.spec.ts to get a fresh,
 * known-zero send counter — the "every Nth send" counter lives only in the emulator process's
 * memory (see CLEAR_SCREEN_FEATURE.md), so a prior spec's manual triggers would otherwise leave
 * it at an unpredictable offset.
 */
export function restartEmulatorContainer(): void {
  execFileSync('docker', ['compose', 'restart', 'emulator'], { cwd: repoRoot, stdio: 'inherit' });
}
