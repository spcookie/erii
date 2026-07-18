import fs from 'node:fs';
import path from 'node:path';
import {exec} from '../lib/shell.js';
import {exists, readJson} from '../lib/fs.js';
import {DIST_PLUGINS, DIST_ROOT,} from '../config.js';
import type {GroupInfo, PluginInfo} from '../types.js';

/**
 * Check git changes in erii-distribution repo only.
 * All version bumps are driven by changes in the distribution packages.
 */
function hasGitChanges(distRelPaths: string[]): boolean {
    const args = ['status', '--porcelain', '--', ...distRelPaths];
    const result = exec('git', args, {cwd: DIST_ROOT, silent: true});
    return result.stdout.trim().length > 0;
}

/**
 * Check if package.json version in erii-distribution has already been bumped
 * (current working-tree version differs from HEAD).
 */
function isVersionAlreadyBumped(distRelPath: string): boolean {
    const absPath = path.join(DIST_ROOT, distRelPath);
    if (!exists(absPath)) return false;

    const headResult = exec('git', ['show', `HEAD:${distRelPath}`], {cwd: DIST_ROOT, silent: true});
    if (!headResult.success) return false;

    try {
        const headJson = JSON.parse(headResult.stdout) as { version?: string };
        const currentJson = readJson<{ version?: string }>(absPath);
        return headJson.version !== undefined
            && currentJson.version !== undefined
            && headJson.version !== currentJson.version;
    } catch {
        return false;
    }
}

function resolvePkgVersions(
    distRelPaths: string[],
    ...distRelPkgJsonPaths: string[]
): { currentVersion: string; changed: boolean; versionBumped: boolean } {
    const changed = hasGitChanges(distRelPaths);

    let currentVersion = '0.0.0';
    let versionBumped = false;

    for (const rp of distRelPkgJsonPaths) {
        const abs = path.join(DIST_ROOT, rp);
        if (exists(abs)) {
            const json = readJson<{ version: string }>(abs);
            if (json.version) currentVersion = json.version;
        }
        if (isVersionAlreadyBumped(rp)) {
            versionBumped = true;
        }
    }

    return {currentVersion, changed, versionBumped};
}

export function detectDepsGroup(): GroupInfo {
    const {currentVersion, changed, versionBumped} = resolvePkgVersions(
        ['packages/erii-deps'],
        'packages/erii-deps/deps-haiku/package.json',
    );
    return {label: 'deps', currentVersion, changed, versionBumped, strategy: null, targetVersion: null};
}

export function detectCliGroup(): GroupInfo {
    const {currentVersion, changed, versionBumped} = resolvePkgVersions(
        ['packages/erii-cli'],
        'packages/erii-cli/erii-darwin/amd64/package.json',
    );
    return {label: 'cli', currentVersion, changed, versionBumped, strategy: null, targetVersion: null};
}

export function detectConfigGroup(): GroupInfo {
    const {currentVersion, changed, versionBumped} = resolvePkgVersions(
        ['packages/erii-config'],
        'packages/erii-config/package.json',
    );
    return {label: 'config', currentVersion, changed, versionBumped, strategy: null, targetVersion: null};
}

export function detectCoreGroup(): GroupInfo {
    const {currentVersion, changed, versionBumped} = resolvePkgVersions(
        ['packages/erii-core'],
        'packages/erii-core/package.json',
    );
    return {label: 'core', currentVersion, changed, versionBumped, strategy: null, targetVersion: null};
}

export function detectMetaGroup(): GroupInfo {
    const {currentVersion, changed, versionBumped} = resolvePkgVersions(
        ['packages/erii', 'packages/create-erii'],
        'packages/erii/package.json',
        'packages/create-erii/package.json',
    );
    return {label: 'erii + create-erii', currentVersion, changed, versionBumped, strategy: null, targetVersion: null};
}

export function detectPlugins(): PluginInfo[] {
    if (!exists(DIST_PLUGINS)) return [];

    const plugins: PluginInfo[] = [];
    for (const entry of fs.readdirSync(DIST_PLUGINS, {withFileTypes: true})) {
        if (!entry.isDirectory()) continue;
        const pj = path.join(DIST_PLUGINS, entry.name, 'package.json');
        if (!exists(pj)) continue;

        const json = readJson<{ name: string; version: string }>(pj);
        const distRel = `packages/erii-plugins/${entry.name}`;

        const changed = hasGitChanges([distRel]);
        const versionBumped = isVersionAlreadyBumped(distRel + '/package.json');

        plugins.push({
            dir: entry.name,
            displayName: json.name || entry.name,
            packageJsonPath: pj,
            currentVersion: json.version || '0.0.0',
            changed,
            versionBumped,
            strategy: null,
            targetVersion: null,
        });
    }

    return plugins;
}
