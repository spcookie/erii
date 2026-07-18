import fs from 'node:fs';
import path from 'node:path';
import pc from 'picocolors';
import {
    CORE_BUILD_LIB,
    CORE_JAR_NAMES,
    DEPS_SPLIT_SCRIPT,
    DIST_CORE_LIB,
    DIST_ROOT,
    EXCLUDE_JAR_PREFIXES,
    extractJarBaseName,
    GRADLEW,
} from '../config.js';
import {exec, execNode, execOrThrow, verbose} from '../lib/shell.js';
import {exists} from '../lib/fs.js';
import type {BuildStepResult, CliOptions} from '../types.js';

export function buildCore(opts: CliOptions): BuildStepResult {
    const start = Date.now();
    if (opts.verbose) console.log(pc.cyan('\n--- Step 2: erii-core ---'));

    try {
        if (opts.dryRun) {
            console.log(pc.yellow(`  erii-core  ${pc.dim('(dry-run)')}`));
            return {step: 'erii-core', success: true, durationMs: 0};
        }

        if (!opts.verbose) console.log(pc.dim('  erii-core ...'));

        // Build
        verbose('  Running gradlew :erii-core:installDist ...', opts);
        execOrThrow(GRADLEW, [':erii-core:installDist'], {cwd: path.dirname(GRADLEW)});

        if (!exists(CORE_BUILD_LIB)) {
            throw new Error(`Build output not found: ${CORE_BUILD_LIB}`);
        }

        // Copy all jars (excluding driver*) to erii-core/lib
        fs.mkdirSync(DIST_CORE_LIB, {recursive: true});
        const allJars = fs.readdirSync(CORE_BUILD_LIB).filter(f => f.endsWith('.jar'));
        let copied = 0, excluded = 0;

        for (const jar of allJars) {
            if (EXCLUDE_JAR_PREFIXES.some(p => jar.startsWith(p))) {
                excluded++;
                continue;
            }
            fs.copyFileSync(path.join(CORE_BUILD_LIB, jar), path.join(DIST_CORE_LIB, jar));
            copied++;
        }
        verbose(`  ${copied} jars copied, ${excluded} excluded (driver*)`, opts);

        // Move core jars aside so split.mjs only sees dependencies
        const tmpDir = path.join(path.dirname(DIST_CORE_LIB), '.tmp-core-jars');
        fs.mkdirSync(tmpDir, {recursive: true});
        for (const jar of fs.readdirSync(DIST_CORE_LIB)) {
            if (!jar.endsWith('.jar')) continue;
            if (CORE_JAR_NAMES.includes(extractJarBaseName(jar))) {
                fs.renameSync(path.join(DIST_CORE_LIB, jar), path.join(tmpDir, jar));
            }
        }

        // Run split.mjs on dependency jars only
        execNode(DEPS_SPLIT_SCRIPT, [DIST_CORE_LIB]);

        // Clean and restore core jars
        for (const jar of fs.readdirSync(DIST_CORE_LIB)) {
            if (jar.endsWith('.jar')) fs.rmSync(path.join(DIST_CORE_LIB, jar));
        }
        for (const jar of fs.readdirSync(tmpDir)) {
            fs.renameSync(path.join(tmpDir, jar), path.join(DIST_CORE_LIB, jar));
        }
        fs.rmdirSync(tmpDir);

        // Stage new/modified files in distribution repo
        exec('git', ['add', 'packages/erii-core/lib', 'packages/erii-deps'], {cwd: DIST_ROOT});
        verbose('  git add (erii-core/lib, erii-deps)', opts);

        const ms = Date.now() - start;
        console.log(pc.green(`  ✓ erii-core ${pc.dim(`(${(ms / 1000).toFixed(1)}s)`)}`));
        return {step: 'erii-core', success: true, durationMs: ms};
    } catch (err) {
        console.error(pc.red(`  ✗ erii-core failed: ${err}`));
        return {step: 'erii-core', success: false, durationMs: Date.now() - start, error: String(err)};
    }
}
