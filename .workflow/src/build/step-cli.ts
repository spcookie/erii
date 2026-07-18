import path from 'node:path';
import fs from 'node:fs';
import pc from 'picocolors';
import {
    CLI_SRC,
    CLI_SRC_BUILD,
    CLI_SRC_CONF,
    CLI_SRC_CONF_DIR,
    CLI_SRC_OPTS,
    DIST_CLI,
    DIST_CONFIG_CONF,
    DIST_CONFIG_CONF_DIR,
    DIST_CORE_OPTS,
    DIST_ROOT,
} from '../config.js';
import {exec, execOrThrow, verbose} from '../lib/shell.js';
import {copyDirContents, exists, resetDir} from '../lib/fs.js';
import type {BuildStepResult, CliOptions} from '../types.js';

export function buildCli(opts: CliOptions): BuildStepResult {
    const start = Date.now();
    if (opts.verbose) console.log(pc.cyan('\n--- Step 1: erii-cli ---'));

    try {
        if (opts.dryRun) {
            console.log(pc.yellow(`  erii-cli  ${pc.dim('(dry-run)')}`));
            return {step: 'erii-cli', success: true, durationMs: 0};
        }

        if (!opts.verbose) console.log(pc.dim('  erii-cli ...'));

        // Copy erii-cli/.conf -> erii-config/.conf
        resetDir(DIST_CONFIG_CONF);
        if (exists(CLI_SRC_CONF)) {
            copyDirContents(CLI_SRC_CONF, DIST_CONFIG_CONF);
        }
        verbose(`  .conf: ${CLI_SRC_CONF} → ${DIST_CONFIG_CONF}`, opts);

        // Copy erii-cli/conf -> erii-config/conf
        resetDir(DIST_CONFIG_CONF_DIR);
        if (exists(CLI_SRC_CONF_DIR)) {
            copyDirContents(CLI_SRC_CONF_DIR, DIST_CONFIG_CONF_DIR);
        }
        verbose(`  conf/: ${CLI_SRC_CONF_DIR} → ${DIST_CONFIG_CONF_DIR}`, opts);

        // Build erii-cli with mage
        verbose('  Building erii-cli (go run mage) ...', opts);
        execOrThrow('go', ['run', 'github.com/magefile/mage'], {cwd: CLI_SRC});

        // Copy platform binaries to distribution
        if (exists(CLI_SRC_BUILD)) {
            for (const osDir of fs.readdirSync(CLI_SRC_BUILD, {withFileTypes: true})) {
                if (!osDir.isDirectory()) continue;
                const osPath = path.join(CLI_SRC_BUILD, osDir.name);
                for (const archDir of fs.readdirSync(osPath, {withFileTypes: true})) {
                    if (!archDir.isDirectory()) continue;
                    const archPath = path.join(osPath, archDir.name);
                    const destDir = path.join(DIST_CLI, osDir.name, archDir.name);
                    fs.mkdirSync(destDir, {recursive: true});

                    for (const file of fs.readdirSync(archPath)) {
                        if (file === 'erii-cli' || file === 'erii-cli.exe') {
                            const srcFile = path.join(archPath, file);
                            const destFile = path.join(destDir, file);
                            fs.copyFileSync(srcFile, destFile);
                            if (!file.endsWith('.exe')) {
                                fs.chmodSync(destFile, 0o755);
                            }
                        }
                    }
                    verbose(`  ${path.relative(CLI_SRC_BUILD, archPath)} → ${path.relative(DIST_CLI, destDir)}`, opts);
                }
            }
        }

        // Copy erii-cli/opts -> erii-core/opts
        resetDir(DIST_CORE_OPTS);
        if (exists(CLI_SRC_OPTS)) {
            copyDirContents(CLI_SRC_OPTS, DIST_CORE_OPTS);
        }
        verbose(`  opts: ${CLI_SRC_OPTS} → ${DIST_CORE_OPTS}`, opts);

        // Stage new/modified files in distribution repo
        exec('git', ['add',
            'packages/erii-config/.conf',
            'packages/erii-config/conf',
            'packages/erii-cli',
            'packages/erii-core/opts',
        ], {cwd: DIST_ROOT});
        verbose('  git add (erii-config, erii-cli, erii-core/opts)', opts);

        const ms = Date.now() - start;
        console.log(pc.green(`  ✓ erii-cli ${pc.dim(`(${(ms / 1000).toFixed(1)}s)`)}`));
        return {step: 'erii-cli', success: true, durationMs: ms};
    } catch (err) {
        console.error(pc.red(`  ✗ erii-cli failed: ${err}`));
        return {step: 'erii-cli', success: false, durationMs: Date.now() - start, error: String(err)};
    }
}
