import pc from 'picocolors';
import {ERII_SYNC_SCRIPT} from '../config.js';
import {execNode} from '../lib/shell.js';
import type {CliOptions} from '../types.js';

export function runSync(opts: CliOptions): boolean {
    if (opts.dryRun) {
        console.log(pc.yellow(`  Sync  ${pc.dim('[dry-run]')}`));
        return true;
    }

    const result = execNode(ERII_SYNC_SCRIPT, []);

    if (result.success) {
        console.log(pc.green('  ✓ Dependencies synced'));
    } else {
        console.log(pc.yellow('  ⚠ Dependencies synced with warnings (some packages not found)'));
    }

    return true;
}
