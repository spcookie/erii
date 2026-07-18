// --- Version bump strategy ---

export type BumpStrategy = 'auto' | 'patch' | 'minor' | 'major' | 'skip';

// --- Version groups ---

export interface GroupInfo {
    /** Group label, e.g. "deps + cli" */
    label: string;
    /** Current version shared across the group */
    currentVersion: string;
    /** Whether git detected changes in source */
    changed: boolean;
    /** Whether version was already manually bumped (package.json version differs from HEAD) */
    versionBumped: boolean;
    /** User-selected bump strategy */
    strategy: BumpStrategy | null;
    /** Target version after bump (null if skip or undetermined) */
    targetVersion: string | null;
}

export interface PluginInfo {
    /** Plugin directory name */
    dir: string;
    /** Display name */
    displayName: string;
    /** Path to package.json */
    packageJsonPath: string;
    /** Current version */
    currentVersion: string;
    /** Whether git detected changes in source */
    changed: boolean;
    /** Whether version was already manually bumped (package.json version differs from HEAD) */
    versionBumped: boolean;
    /** User-selected bump strategy */
    strategy: BumpStrategy | null;
    /** Target version after bump */
    targetVersion: string | null;
}

export interface VersionSelections {
    groupDeps: GroupInfo;
    groupCli: GroupInfo;
    groupConfig: GroupInfo;
    groupCore: GroupInfo;
    plugins: PluginInfo[];
    groupMeta: GroupInfo;
}

// --- Build phase types ---

export interface BuildStepResult {
    step: string;
    success: boolean;
    durationMs: number;
    error?: string;
}

export interface BuildResult {
    success: boolean;
    steps: BuildStepResult[];
    totalDurationMs: number;
}

// --- Version phase types ---

export interface VersionBumpEntry {
    dir: string;
    oldVersion: string;
    newVersion: string;
}

export interface VersionResult {
    success: boolean;
    entries: VersionBumpEntry[];
    errors: string[];
}

// --- CLI options ---

export interface CliOptions {
    auto: boolean;
    dryRun: boolean;
    verbose: boolean;
    buildOnly: boolean;
    versionOnly: boolean;
    skipCli: boolean;
    skipCore: boolean;
    skipPlugins: boolean;
}
