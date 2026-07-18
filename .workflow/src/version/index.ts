import pc from 'picocolors';
import {
    detectCliGroup,
    detectConfigGroup,
    detectCoreGroup,
    detectDepsGroup,
    detectMetaGroup,
    detectPlugins,
} from './detector.js';
import {autoFillSelections, runVersionTui} from './tui.js';
import {executeBumps} from './bumper.js';
import {runSync} from './sync.js';
import type {CliOptions, VersionResult} from '../types.js';

export async function runVersionPhase(opts: CliOptions): Promise<VersionResult> {
    const groupDeps = detectDepsGroup();
    const groupCli = detectCliGroup();
    const groupConfig = detectConfigGroup();
    const groupCore = detectCoreGroup();
    const plugins = detectPlugins();
    const groupMeta = detectMetaGroup();

    let selections;
    if (opts.auto) {
        selections = autoFillSelections(groupDeps, groupCli, groupConfig, groupCore, plugins, groupMeta);

        console.log(pc.bold(pc.magenta('\n--- Version ---')));
        printVersionLine('[1] deps      ', selections.groupDeps);
        printVersionLine('[2] cli       ', selections.groupCli);
        printVersionLine('[3] config    ', selections.groupConfig);
        printVersionLine('[4] core      ', selections.groupCore);
        for (const p of selections.plugins) {
            printVersionLine(`    ${p.displayName}`, p);
        }
        printVersionLine('[5] erii+create-erii', selections.groupMeta);
    } else {
        selections = await runVersionTui(groupDeps, groupCli, groupConfig, groupCore, plugins, groupMeta);
        if (!selections) {
            return {success: false, entries: [], errors: ['User cancelled']};
        }
    }

    console.log(pc.dim('\n  Bumping...'));
    const entries = executeBumps(selections, opts);

    runSync(opts);

    return {success: true, entries, errors: []};
}

function printVersionLine(
    label: string,
    info: { currentVersion: string; targetVersion: string | null; versionBumped?: boolean; strategy?: string | null },
): void {
    const {currentVersion: current, targetVersion: target} = info;
    if (target && target !== current) {
        console.log(`  ${label} ${pc.dim(current)} ${pc.green('→ ' + target)}  ${pc.yellow('[changed]')}`);
    } else if (info.versionBumped) {
        console.log(`  ${label} ${pc.dim(current)}  ${pc.yellow('(version already bumped)')}`);
    } else if (info.strategy === 'skip') {
        console.log(`  ${label} ${pc.dim(current + ' (skip)')}`);
    } else {
        console.log(`  ${label} ${pc.dim(current + ' (unchanged)')}`);
    }
}
