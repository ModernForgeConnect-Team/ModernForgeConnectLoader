'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const properties = fs.readFileSync(path.join(root, '..', 'gradle.properties'), 'utf8');
const match = /^\s*mod_version\s*=\s*(\S+)\s*$/m.exec(properties);
if (!match || !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.test(match[1])) {
    throw new Error('Root gradle.properties must define a SemVer-compatible mod_version.');
}
const manifestPath = path.join(root, 'package.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
manifest.version = match[1];
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
const executable = process.platform === 'win32' ? 'npx.cmd' : 'npx';
const result = spawnSync(executable, ['--yes', '@vscode/vsce', 'package', '--no-dependencies', '--allow-missing-repository'], {cwd: root, stdio: 'inherit', shell: process.platform === 'win32'});
if (result.error) throw result.error;
process.exitCode = result.status === null ? 1 : result.status;
