import semver from 'semver';
import type {BumpStrategy} from '../types.js';

export function isValid(version: string): boolean {
    return semver.valid(version) !== null;
}

export function bumpVersion(current: string, strategy: BumpStrategy): string | null {
    if (!isValid(current)) return null;

    switch (strategy) {
        case 'patch':
            return semver.inc(current, 'patch');
        case 'minor':
            return semver.inc(current, 'minor');
        case 'major':
            return semver.inc(current, 'major');
        case 'auto':
            return semver.inc(current, 'patch');
        case 'skip':
            return current;
        default:
            return null;
    }
}

export function strategyLabel(strategy: BumpStrategy, current: string): string {
    const next = bumpVersion(current, strategy);
    switch (strategy) {
        case 'auto':
            return `auto${next ? ` (→ ${next})` : ''}`;
        case 'patch':
            return `patch (${current} → ${next})`;
        case 'minor':
            return `minor (${current} → ${next})`;
        case 'major':
            return `major (${current} → ${next})`;
        case 'skip':
            return 'skip';
        default:
            return strategy;
    }
}
