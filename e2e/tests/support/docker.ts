import { execFileSync } from 'node:child_process';
import path from 'node:path';

const repoRoot = path.resolve(process.cwd(), '..');

/** Restarts the `server` container in place, simulating a crash/redeploy mid-run. */
export function restartServerContainer(): void {
  execFileSync('docker', ['compose', 'restart', 'server'], { cwd: repoRoot, stdio: 'inherit' });
}
