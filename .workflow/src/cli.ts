import {Command} from 'commander';
import pc from 'picocolors';
import {runBuildPhase} from './build/index.js';
import {runVersionPhase} from './version/index.js';
import type {CliOptions} from './types.js';

/**
 * Commander has conflicts when the same option name (--auto) is defined
 * on both the program default action and subcommands. We parse --auto
 * from process.argv directly to work around this.
 */
function hasArg(arg: string): boolean {
    return process.argv.includes(arg);
}

function toOpts(cmd: Command, extra: Partial<CliOptions> = {}): CliOptions {
    const o = cmd.opts() as Record<string, boolean>;
    return {
        auto: hasArg('--auto'),
        dryRun: hasArg('--dry-run') || (o.dryRun ?? false),
        verbose: hasArg('--verbose') || (o.verbose ?? false),
        buildOnly: extra.buildOnly ?? false,
        versionOnly: extra.versionOnly ?? false,
        skipCli: hasArg('--skip-cli') || (o.skipCli ?? false),
        skipCore: hasArg('--skip-core') || (o.skipCore ?? false),
        skipPlugins: hasArg('--skip-plugins') || (o.skipPlugins ?? false),
    };
}

export function createProgram(): Command {
    const program = new Command();

    program
        .name('erii-dist')
        .description('Erii distribution workflow script')
        .version('1.0.0');

    // Default: full workflow
    program
        .option('--auto', 'Auto-detect version changes (no TUI)')
        .option('--dry-run', 'Preview mode, no files written')
        .option('--verbose', 'Verbose output')
        .option('--skip-cli', 'Skip erii-cli build')
        .option('--skip-core', 'Skip erii-core build')
        .option('--skip-plugins', 'Skip erii-plugins build')
        .action(async () => {
            await runFull(toOpts(program));
        });

    // build subcommand
    program
        .command('build')
        .description('Build and copy phase only')
        .option('--dry-run', 'Preview mode')
        .option('--verbose', 'Verbose output')
        .option('--skip-cli', 'Skip erii-cli build')
        .option('--skip-core', 'Skip erii-core build')
        .option('--skip-plugins', 'Skip erii-plugins build')
        .action(async () => {
            const opts = toOpts(program, {buildOnly: true});
            const result = await runBuildPhase(opts);
            process.exit(result.success ? 0 : 1);
        });

    // version subcommand
    program
        .command('version')
        .description('Version bump phase only')
        .option('--auto', 'Auto-detect version changes (no TUI)')
        .option('--dry-run', 'Preview mode, no files written')
        .option('--verbose', 'Verbose output')
        .action(async () => {
            const opts = toOpts(program, {versionOnly: true});
            const result = await runVersionPhase(opts);
            process.exit(result.success ? 0 : 1);
        });

    return program;
}

async function runFull(o: CliOptions) {
    const tags: string[] = [];
    if (o.dryRun) tags.push(pc.yellow('dry-run'));
    if (o.auto) tags.push(pc.cyan('auto'));

    const tagStr = tags.length > 0 ? `  ${tags.join('  ')}` : '';
    console.log(pc.bold(pc.blue(`Erii Distribution Workflow${tagStr}`)));

    const buildResult = await runBuildPhase(o);
    if (!buildResult.success) {
        console.error(pc.red('\nBuild phase failed. Aborting.'));
        process.exit(1);
    }

    const versionResult = await runVersionPhase(o);
    if (!versionResult.success) {
        console.error(pc.red('\nVersion phase failed.'));
        process.exit(1);
    }

    if (o.dryRun) {
        console.log(pc.yellow('\nNo files modified (dry-run).'));
    } else {
        console.log(pc.green('\nDone.'));
    }
    process.exit(0);
}
