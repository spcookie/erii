import fs from 'node:fs';
import path from 'node:path';
import pc from 'picocolors';
import {DIST_PLUGINS, DIST_ROOT, GRADLEW, PLUGINS_BUILD} from '../config.js';
import {exec, execOrThrow, verbose} from '../lib/shell.js';
import {copyDirContents, exists} from '../lib/fs.js';
import type {BuildStepResult, CliOptions} from '../types.js';

export function buildPlugins(opts: CliOptions): BuildStepResult {
    const start = Date.now();
    if (opts.verbose) console.log(pc.cyan('\n--- Step 3: erii-plugins ---'));

    try {
        if (opts.dryRun) {
            console.log(pc.yellow(`  erii-plugins  ${pc.dim('(dry-run)')}`));
            return {step: 'erii-plugins', success: true, durationMs: 0};
        }

        if (!opts.verbose) console.log(pc.dim('  erii-plugins ...'));

        // Build
        verbose('  Running gradlew assembleAllPlugins ...', opts);
        execOrThrow(GRADLEW, ['-p', 'erii-plugins', 'assembleAllPlugins'], {cwd: path.dirname(GRADLEW)});

        if (!exists(PLUGINS_BUILD)) {
            throw new Error(`Build output not found: ${PLUGINS_BUILD}`);
        }

        // Copy each plugin directory
        for (const pluginDir of fs.readdirSync(PLUGINS_BUILD, {withFileTypes: true})) {
            if (!pluginDir.isDirectory()) continue;
            const srcDir = path.join(PLUGINS_BUILD, pluginDir.name);
            const destDir = path.join(DIST_PLUGINS, pluginDir.name);
            fs.mkdirSync(destDir, {recursive: true});

            for (const entry of fs.readdirSync(destDir)) {
                if (entry.endsWith('.zip')) fs.rmSync(path.join(destDir, entry));
            }

            copyDirContents(srcDir, destDir);
            verbose(`  ${pluginDir.name} → ${destDir}`, opts);
        }

        // Stage new/modified files in distribution repo
        exec('git', ['add', 'packages/erii-plugins'], {cwd: DIST_ROOT});
        verbose('  git add (erii-plugins)', opts);

        const ms = Date.now() - start;
        console.log(pc.green(`  ✓ erii-plugins ${pc.dim(`(${(ms / 1000).toFixed(1)}s)`)}`));
        return {step: 'erii-plugins', success: true, durationMs: ms};
    } catch (err) {
        console.error(pc.red(`  ✗ erii-plugins failed: ${err}`));
        return {step: 'erii-plugins', success: false, durationMs: Date.now() - start, error: String(err)};
    }
}
