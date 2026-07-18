import pc from 'picocolors';
import {buildCli} from './step-cli.js';
import {buildCore} from './step-core.js';
import {buildPlugins} from './step-plugins.js';
import type {BuildResult, CliOptions} from '../types.js';

export async function runBuildPhase(opts: CliOptions): Promise<BuildResult> {
    const start = Date.now();

    if (!opts.verbose) {
        console.log(pc.bold(pc.cyan('\n--- Build ---')));
    }

    const steps = [];

    if (!opts.skipCli) {
        steps.push(buildCli(opts));
    } else if (opts.verbose) {
        console.log(pc.dim('  erii-cli (skipped)'));
    }

    if (!opts.skipCore) {
        steps.push(buildCore(opts));
    } else if (opts.verbose) {
        console.log(pc.dim('  erii-core (skipped)'));
    }

    if (!opts.skipPlugins) {
        steps.push(buildPlugins(opts));
    } else if (opts.verbose) {
        console.log(pc.dim('  erii-plugins (skipped)'));
    }

    const success = steps.every(s => s.success);
    const totalMs = Date.now() - start;

    if (!success) {
        console.log(pc.red(`\n  Build failed.`));
        for (const step of steps) {
            if (step.error) {
                console.log(pc.red(`    ✗ ${step.step}: ${step.error}`));
            }
        }
    }

    return {success, steps, totalDurationMs: totalMs};
}
