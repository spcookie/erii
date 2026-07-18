import {spawnSync, type SpawnSyncOptions} from 'node:child_process';
import pc from 'picocolors';
import type {CliOptions} from '../types.js';

export interface ExecOptions {
    cwd?: string;
    timeout?: number;
    env?: Record<string, string>;
    /** Suppress stderr on failure (caller handles it) */
    silent?: boolean;
}

export interface ExecResult {
    success: boolean;
    stdout: string;
    stderr: string;
    exitCode: number;
}

export function exec(cmd: string, args: string[], options: ExecOptions = {}): ExecResult {
    const {cwd, timeout, env, silent = false} = options;

    const spawnOpts: SpawnSyncOptions = {
        cwd,
        timeout,
        env: env ? {...process.env, ...env} : process.env,
        encoding: 'utf-8',
        maxBuffer: 10 * 1024 * 1024, // 10MB
    };

    const result = spawnSync(cmd, args, spawnOpts);

    const stdout = (typeof result.stdout === 'string' ? result.stdout : (result.stdout || '').toString()).trim();
    const stderr = (typeof result.stderr === 'string' ? result.stderr : (result.stderr || '').toString()).trim();
    const exitCode = result.status ?? (result.error ? 1 : 0);
    const success = exitCode === 0 && !result.error;

    if (!success && !silent && stderr) {
        console.error(pc.red(`  [stderr] ${stderr}`));
    }
    if (result.error && !silent) {
        console.error(pc.red(`  [error] ${result.error.message}`));
    }

    return {success, stdout, stderr, exitCode};
}

export function execOrThrow(cmd: string, args: string[], options?: ExecOptions): ExecResult {
    const result = exec(cmd, args, options);
    if (!result.success) {
        throw new Error(`Command failed: ${cmd} ${args.join(' ')}\n${result.stderr}`);
    }
    return result;
}

export function execNode(scriptPath: string, args: string[], options?: ExecOptions): ExecResult {
    return exec('node', [scriptPath, ...args], options);
}

export function verbose(msg: string, opts: CliOptions): void {
    if (opts.verbose) {
        console.log(pc.dim(`  [verbose] ${msg}`));
    }
}
