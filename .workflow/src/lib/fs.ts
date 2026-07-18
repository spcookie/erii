import fs from 'node:fs';
import path from 'node:path';

export function copyDir(src: string, dest: string): void {
    fs.mkdirSync(dest, {recursive: true});
    for (const entry of fs.readdirSync(src, {withFileTypes: true})) {
        const srcPath = path.join(src, entry.name);
        const destPath = path.join(dest, entry.name);
        if (entry.isDirectory()) {
            copyDir(srcPath, destPath);
        } else {
            fs.copyFileSync(srcPath, destPath);
        }
    }
}

export function copyDirContents(src: string, dest: string, options?: { exclude?: (name: string) => boolean }): void {
    const exclude = options?.exclude;
    fs.mkdirSync(dest, {recursive: true});
    for (const entry of fs.readdirSync(src, {withFileTypes: true})) {
        if (exclude?.(entry.name)) continue;
        const srcPath = path.join(src, entry.name);
        const destPath = path.join(dest, entry.name);
        if (entry.isDirectory()) {
            copyDirContents(srcPath, destPath, options);
        } else {
            fs.copyFileSync(srcPath, destPath);
        }
    }
}

export function cleanDir(dir: string, predicate?: (name: string) => boolean): void {
    if (!fs.existsSync(dir)) return;
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            fs.rmSync(full, {recursive: true});
        } else if (!predicate || predicate(entry.name)) {
            fs.rmSync(full);
        }
    }
}

export function resetDir(dir: string): void {
    if (fs.existsSync(dir)) {
        fs.rmSync(dir, {recursive: true});
    }
    fs.mkdirSync(dir, {recursive: true});
}

export function moveFile(src: string, dest: string): void {
    fs.mkdirSync(path.dirname(dest), {recursive: true});
    fs.renameSync(src, dest);
}

export function exists(p: string): boolean {
    return fs.existsSync(p);
}

export function readJson<T>(filePath: string): T {
    const raw = fs.readFileSync(filePath, 'utf-8');
    return JSON.parse(raw) as T;
}

export function writeJson(filePath: string, data: unknown): void {
    fs.writeFileSync(filePath, JSON.stringify(data, null, 2) + '\n');
}
