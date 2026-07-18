import path from 'node:path';
import {fileURLToPath} from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const PROJECT_ROOT = path.resolve(__dirname, '..', '..');

// erii-distribution
export const DIST_ROOT = path.join(PROJECT_ROOT, 'erii-distribution');
export const DIST_PACKAGES = path.join(DIST_ROOT, 'packages');

// erii-cli source paths
export const CLI_SRC = path.join(PROJECT_ROOT, 'erii-cli');
export const CLI_SRC_CONF = path.join(CLI_SRC, '.conf');
export const CLI_SRC_CONF_DIR = path.join(CLI_SRC, 'conf');
export const CLI_SRC_OPTS = path.join(CLI_SRC, 'opts');
export const CLI_SRC_BUILD = path.join(CLI_SRC, 'build');

// erii-core source paths
export const CORE_SRC = path.join(PROJECT_ROOT, 'erii-core');
export const CORE_BUILD_LIB = path.join(CORE_SRC, 'build', 'install', 'erii-core', 'lib');

// erii-plugins source paths
export const PLUGINS_SRC = path.join(PROJECT_ROOT, 'erii-plugins');
export const PLUGINS_BUILD = path.join(PLUGINS_SRC, 'build', 'plugins');

// Gradle wrapper
export const GRADLEW = path.join(PROJECT_ROOT, 'gradlew');

// Distribution target paths
export const DIST_CONFIG = path.join(DIST_PACKAGES, 'erii-config');
export const DIST_CONFIG_CONF = path.join(DIST_CONFIG, '.conf');
export const DIST_CONFIG_CONF_DIR = path.join(DIST_CONFIG, 'conf');
export const DIST_CLI = path.join(DIST_PACKAGES, 'erii-cli');
export const DIST_CORE = path.join(DIST_PACKAGES, 'erii-core');
export const DIST_CORE_LIB = path.join(DIST_CORE, 'lib');
export const DIST_CORE_OPTS = path.join(DIST_CORE, 'opts');
export const DIST_DEPS = path.join(DIST_PACKAGES, 'erii-deps');
export const DIST_DEPS_HAIKU = path.join(DIST_DEPS, 'deps-haiku');
export const DIST_PLUGINS = path.join(DIST_PACKAGES, 'erii-plugins');
export const DIST_ERII = path.join(DIST_PACKAGES, 'erii');
export const DIST_CREATE_ERII = path.join(DIST_PACKAGES, 'create-erii');

// erii-deps scripts
export const DEPS_SPLIT_SCRIPT = path.join(DIST_PACKAGES, 'erii-deps', 'split.mjs');
export const DEPS_SET_VERSION_SCRIPT = path.join(DIST_PACKAGES, 'erii-deps', 'set-version.mjs');
export const CLI_SET_VERSION_SCRIPT = path.join(DIST_CLI, 'set-version.mjs');
export const ERII_SYNC_SCRIPT = path.join(DIST_ERII, 'sync-versions.mjs');

// 8 core jar base names (without version), used for matching regardless of version
export const CORE_JAR_NAMES = [
    'erii-common',
    'erii-core',
    'erii-spi-annotation',
    'erii-spi-core',
    'onebot-core',
    'onebot-lib',
    'onebot-mock',
    'onebot-sdk',
];

// Driver jar prefixes to exclude
export const EXCLUDE_JAR_PREFIXES = ['driver-', 'driver-bundle'];

/**
 * Extract base name from jar filename (ignoring version).
 * Example: "erii-common-1.0.0.jar" -> "erii-common"
 *          "kotlin-stdlib-2.0.21.jar" -> "kotlin-stdlib"
 */
export function extractJarBaseName(filename: string): string {
    const name = filename.replace(/\.jar$/, '');
    // Try matching and removing trailing version: digits.digits.digits... pattern
    const versionPattern = /-\d+\.\d+\.\d+.*$/;
    const match = name.match(versionPattern);
    if (match) {
        return name.substring(0, match.index);
    }
    // Fallback: remove trailing segments starting with a digit
    const parts = name.split('-');
    while (parts.length > 1 && /^\d/.test(parts[parts.length - 1])) {
        parts.pop();
    }
    return parts.join('-');
}
