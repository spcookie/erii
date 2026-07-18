import path from 'node:path';
import pc from 'picocolors';
import {
    CLI_SET_VERSION_SCRIPT,
    DEPS_SET_VERSION_SCRIPT,
    DIST_CONFIG,
    DIST_CORE,
    DIST_CREATE_ERII,
    DIST_ERII,
} from '../config.js';
import {execNode} from '../lib/shell.js';
import {exists, readJson, writeJson} from '../lib/fs.js';
import type {BumpStrategy, CliOptions, VersionBumpEntry, VersionSelections} from '../types.js';

export function executeBumps(selections: VersionSelections, opts: CliOptions): VersionBumpEntry[] {
    const entries: VersionBumpEntry[] = [];

    bumpDeps(selections.groupDeps.targetVersion, selections.groupDeps.strategy!, entries, opts);
    bumpCli(selections.groupCli.targetVersion, selections.groupCli.strategy!, entries, opts);
    bumpPkg('config', path.join(DIST_CONFIG, 'package.json'), selections.groupConfig.targetVersion, selections.groupConfig.strategy!, entries, opts);
    bumpPkg('core', path.join(DIST_CORE, 'package.json'), selections.groupCore.targetVersion, selections.groupCore.strategy!, entries, opts);
    for (const plugin of selections.plugins) {
        bumpPkg(plugin.dir, plugin.packageJsonPath, plugin.targetVersion, plugin.strategy!, entries, opts);
    }
    bumpPkg('erii', path.join(DIST_ERII, 'package.json'), selections.groupMeta.targetVersion, selections.groupMeta.strategy!, entries, opts);
    bumpPkg('create-erii', path.join(DIST_CREATE_ERII, 'package.json'), selections.groupMeta.targetVersion, selections.groupMeta.strategy!, entries, opts);

    // Show skipped summary
    const skipped: string[] = [];
    if (selections.groupDeps.strategy === 'skip') skipped.push('deps');
    if (selections.groupCli.strategy === 'skip') skipped.push('cli');
    if (selections.groupConfig.strategy === 'skip') skipped.push('config');
    if (selections.groupCore.strategy === 'skip') skipped.push('core');
    for (const p of selections.plugins) {
        if (p.strategy === 'skip') skipped.push(p.dir);
    }
    if (selections.groupMeta.strategy === 'skip') skipped.push('erii + create-erii');
    if (skipped.length > 0) {
        console.log(pc.dim(`  (skipped: ${skipped.join(', ')})`));
    }

    return entries;
}

function bumpDeps(targetVersion: string | null, strategy: BumpStrategy, entries: VersionBumpEntry[], opts: CliOptions): void {
    if (strategy === 'skip' || !targetVersion) return;

    if (opts.dryRun) {
        console.log(pc.yellow(`  deps    → ${targetVersion}  ${pc.dim('[dry-run]')}`));
        entries.push({dir: 'erii-deps/deps-*', oldVersion: '-', newVersion: targetVersion});
        return;
    }

    execNode(DEPS_SET_VERSION_SCRIPT, [targetVersion]);
    entries.push({dir: 'erii-deps/deps-*', oldVersion: '-', newVersion: targetVersion});
    console.log(pc.green(`  deps    → ${targetVersion}`));
}

function bumpCli(targetVersion: string | null, strategy: BumpStrategy, entries: VersionBumpEntry[], opts: CliOptions): void {
    if (strategy === 'skip' || !targetVersion) return;

    if (opts.dryRun) {
        console.log(pc.yellow(`  cli     → ${targetVersion}  ${pc.dim('[dry-run]')}`));
        entries.push({dir: 'erii-cli/*', oldVersion: '-', newVersion: targetVersion});
        return;
    }

    execNode(CLI_SET_VERSION_SCRIPT, [targetVersion]);
    entries.push({dir: 'erii-cli/*', oldVersion: '-', newVersion: targetVersion});
    console.log(pc.green(`  cli     → ${targetVersion}`));
}

function bumpPkg(
    dir: string,
    packageJsonPath: string,
    targetVersion: string | null,
    strategy: BumpStrategy,
    entries: VersionBumpEntry[],
    opts: CliOptions,
): void {
    if (strategy === 'skip' || !targetVersion || !exists(packageJsonPath)) return;

    if (opts.dryRun) {
        const json = readJson<{ version: string }>(packageJsonPath);
        console.log(pc.yellow(`  ${dir}  ${json.version} → ${targetVersion}  ${pc.dim('[dry-run]')}`));
        entries.push({dir, oldVersion: json.version, newVersion: targetVersion});
        return;
    }

    const json = readJson<{ version: string }>(packageJsonPath);
    const oldVer = json.version;
    json.version = targetVersion;
    writeJson(packageJsonPath, json);
    entries.push({dir, oldVersion: oldVer, newVersion: targetVersion});
    console.log(pc.green(`  ${dir}  ${oldVer} → ${targetVersion}`));
}
