import {confirm, select} from '@inquirer/prompts';
import pc from 'picocolors';
import {bumpVersion} from '../lib/semver.js';
import type {BumpStrategy, GroupInfo, PluginInfo, VersionSelections} from '../types.js';

const STRATEGY_CHOICES = [
    {value: 'auto' as BumpStrategy, name: '', description: ''},
    {value: 'patch' as BumpStrategy, name: '', description: ''},
    {value: 'minor' as BumpStrategy, name: '', description: ''},
    {value: 'major' as BumpStrategy, name: '', description: ''},
    {value: 'skip' as BumpStrategy, name: '', description: ''},
];

function buildChoices(current: string, changed: boolean, versionBumped: boolean) {
    return STRATEGY_CHOICES.map(c => {
        const next = bumpVersion(current, c.value);
        let name: string;
        switch (c.value) {
            case 'auto':
                if (versionBumped) {
                    name = `Auto-detect ${pc.dim('(version already bumped, will skip)')}`;
                } else {
                    name = `Auto-detect ${next ? `→ ${next}` : ''} ${changed ? pc.yellow('(will bump patch)') : pc.dim('(will skip)')}`;
                }
                break;
            case 'patch':
                name = `Patch     x.x.+1  ${pc.dim(`${current} → ${next}`)}`;
                break;
            case 'minor':
                name = `Minor     x.+1.x  ${pc.dim(`${current} → ${next}`)}`;
                break;
            case 'major':
                name = `Major     +1.x.x  ${pc.dim(`${current} → ${next}`)}`;
                break;
            case 'skip':
                name = 'Skip              (no change)';
                break;
        }
        return {value: c.value, name};
    });
}

async function selectGroupStrategy(group: GroupInfo): Promise<BumpStrategy> {
    const choices = buildChoices(group.currentVersion, group.changed, group.versionBumped);
    console.log(pc.bold(`\n  ${group.label}`));
    console.log(pc.dim(`  Version: ${group.currentVersion}`));
    if (group.versionBumped) {
        console.log(pc.yellow(`  Status: version already bumped`));
    } else if (group.changed) {
        console.log(pc.yellow(`  Status: changed`));
    }

    const strategy = await select({
        message: `Select strategy for ${group.label}:`,
        choices,
        default: 'auto',
    });
    return strategy;
}

async function selectPluginStrategy(plugin: PluginInfo): Promise<BumpStrategy> {
    const choices = buildChoices(plugin.currentVersion, plugin.changed, plugin.versionBumped);
    let tag: string;
    if (plugin.versionBumped) {
        tag = pc.yellow('[version bumped]');
    } else if (plugin.changed) {
        tag = pc.yellow('[changed]');
    } else {
        tag = pc.dim('[unchanged]');
    }
    console.log(pc.bold(`\n  ${plugin.displayName} ${tag}`));
    console.log(pc.dim(`  Version: ${plugin.currentVersion}`));

    const strategy = await select({
        message: `Select strategy for ${plugin.displayName}:`,
        choices,
        default: 'auto',
    });
    return strategy;
}

export async function runVersionTui(
    groupDeps: GroupInfo,
    groupCli: GroupInfo,
    groupConfig: GroupInfo,
    groupCore: GroupInfo,
    plugins: PluginInfo[],
    groupMeta: GroupInfo,
): Promise<VersionSelections | null> {
    console.log(pc.dim('\nSelect version bump strategy for each component group.'));

    groupDeps.strategy = await selectGroupStrategy(groupDeps);
    groupCli.strategy = await selectGroupStrategy(groupCli);
    groupConfig.strategy = await selectGroupStrategy(groupConfig);
    groupCore.strategy = await selectGroupStrategy(groupCore);

    const configurePlugins = await confirm({
        message: 'Configure each plugin version individually?',
        default: false,
    });

    if (configurePlugins) {
        for (const plugin of plugins) {
            plugin.strategy = await selectPluginStrategy(plugin);
        }
    } else {
        console.log(pc.dim('\n  All plugins will use auto-detect'));
        for (const plugin of plugins) {
            plugin.strategy = 'auto';
        }
    }

    groupMeta.strategy = await selectGroupStrategy(groupMeta);

    const selections: VersionSelections = {
        groupDeps: computeTarget(groupDeps),
        groupCli: computeTarget(groupCli),
        groupConfig: computeTarget(groupConfig),
        groupCore: computeTarget(groupCore),
        plugins: plugins.map(p => computePluginTarget(p)),
        groupMeta: computeTarget(groupMeta),
    };

    console.log(pc.bold(pc.magenta('\n--- Version Summary ---')));

    printGroupSummary(selections.groupDeps, '[1] deps');
    printGroupSummary(selections.groupCli, '[2] cli');
    printGroupSummary(selections.groupConfig, '[3] config');
    printGroupSummary(selections.groupCore, '[4] core');
    console.log(pc.bold(`\n  [5] plugins:`));
    for (const p of selections.plugins) {
        printPluginSummary(p);
    }
    printGroupSummary(selections.groupMeta, '[6] erii + create-erii');
    console.log('');

    const proceed = await confirm({
        message: 'Confirm version changes?',
        default: true,
    });

    if (!proceed) {
        console.log(pc.yellow('  Cancelled.'));
        return null;
    }

    return selections;
}

function computeTarget(group: GroupInfo): GroupInfo {
    const strategy = group.strategy || 'skip';
    const target = bumpVersion(group.currentVersion, strategy);
    return {...group, strategy, targetVersion: target};
}

function computePluginTarget(plugin: PluginInfo): PluginInfo {
    const strategy = plugin.strategy || 'skip';
    const target = bumpVersion(plugin.currentVersion, strategy);
    return {...plugin, strategy, targetVersion: target};
}

function printGroupSummary(g: GroupInfo, label: string): void {
    if (g.targetVersion && g.targetVersion !== g.currentVersion) {
        console.log(pc.green(`  ${label}: ${g.currentVersion} → ${g.targetVersion}`));
    } else if (g.versionBumped) {
        console.log(pc.yellow(`  ${label}: ${g.currentVersion} (version already bumped, skipped)`));
    } else if (g.strategy === 'skip') {
        console.log(pc.dim(`  ${label}: ${g.currentVersion} (skip)`));
    } else {
        console.log(pc.dim(`  ${label}: ${g.currentVersion} (unchanged)`));
    }
}

function printPluginSummary(p: PluginInfo): void {
    if (p.targetVersion && p.targetVersion !== p.currentVersion) {
        console.log(pc.green(`    ${p.displayName}: ${p.currentVersion} → ${p.targetVersion}`));
    } else if (p.versionBumped) {
        console.log(pc.yellow(`    ${p.displayName}: ${p.currentVersion} (version already bumped, skipped)`));
    } else if (p.strategy === 'skip') {
        console.log(pc.dim(`    ${p.displayName}: ${p.currentVersion} (skip)`));
    } else {
        console.log(pc.dim(`    ${p.displayName}: ${p.currentVersion} (unchanged)`));
    }
}

function autoSelect(g: GroupInfo): GroupInfo {
    if (g.versionBumped) {
        return {...g, strategy: 'skip' as BumpStrategy, targetVersion: g.currentVersion};
    }
    const strategy: BumpStrategy = g.changed ? 'auto' : 'skip';
    const target = g.changed ? bumpVersion(g.currentVersion, 'patch') : g.currentVersion;
    return {...g, strategy, targetVersion: target};
}

export function autoFillSelections(
    groupDeps: GroupInfo,
    groupCli: GroupInfo,
    groupConfig: GroupInfo,
    groupCore: GroupInfo,
    plugins: PluginInfo[],
    groupMeta: GroupInfo,
): VersionSelections {
    const selDeps = autoSelect(groupDeps);
    const selCli = autoSelect(groupCli);
    const selConfig = autoSelect(groupConfig);
    const selCore = autoSelect(groupCore);
    const selPlugins = plugins.map(p => {
        if (p.versionBumped) {
            return {...p, strategy: 'skip' as BumpStrategy, targetVersion: p.currentVersion};
        }
        const strategy: BumpStrategy = p.changed ? 'auto' : 'skip';
        const target = p.changed ? bumpVersion(p.currentVersion, 'patch') : p.currentVersion;
        return {...p, strategy, targetVersion: target};
    });

    // erii/create-erii should follow if any depended group is bumped, because
    // sync will update erii/package.json deps to reference the new versions.
    // Plugins are NOT depended by erii/create-erii, so plugin-only bumps
    // should not trigger a meta version bump.
    const anyBumped = selDeps.strategy !== 'skip'
        || selCli.strategy !== 'skip'
        || selConfig.strategy !== 'skip'
        || selCore.strategy !== 'skip';

    let selMeta: GroupInfo;
    if (anyBumped && groupMeta.strategy !== 'skip' && !groupMeta.versionBumped) {
        const target = bumpVersion(groupMeta.currentVersion, 'patch');
        selMeta = {...groupMeta, strategy: 'auto' as BumpStrategy, targetVersion: target};
    } else {
        selMeta = autoSelect(groupMeta);
    }

    return {
        groupDeps: selDeps,
        groupCli: selCli,
        groupConfig: selConfig,
        groupCore: selCore,
        plugins: selPlugins,
        groupMeta: selMeta,
    };
}
