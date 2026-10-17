#!/usr/bin/env node
// 把 images.py 的产物增量同步到 staging：/root/gudu/staging-images/dish/{original,thumbnail}/
// （front-nginx 以 /images/ → /var/www/images/ 只读挂载该目录，URL 见 SEED_IMAGE_BASE）
// 用法：node sync-images.mjs --env=staging [--dry-run]
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { sshEnv, parseEnvArg } from './lib/db.mjs';

const argv = process.argv.slice(2);
if (parseEnvArg(argv) !== 'staging') { console.error('拒绝：图片只同步到 --env=staging'); process.exit(2); }
const dry = argv.includes('--dry-run');
const SRC = join(import.meta.dirname, '.cache/images/dish');
const REMOTE = '/root/gudu/staging-images/dish';
if (!existsSync(join(SRC, 'original'))) { console.error('先运行 python3 images.py'); process.exit(2); }

const { host, user, env } = sshEnv();
const ssh = 'ssh -o StrictHostKeyChecking=no -o LogLevel=ERROR';
const run = (args) => spawnSync('sshpass', ['-e', ...args], { env, stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' });

const mk = run(['ssh', '-o', 'StrictHostKeyChecking=no', '-o', 'LogLevel=ERROR', `${user}@${host}`, `mkdir -p ${REMOTE}/original ${REMOTE}/thumbnail`]);
if (mk.status !== 0) { console.error('远端建目录失败：', mk.stderr.trim()); process.exit(1); }
for (const sub of ['original', 'thumbnail']) {
  const r = run(['rsync', '-az', '--stats', ...(dry ? ['-n'] : []), '-e', ssh, `${SRC}/${sub}/`, `${user}@${host}:${REMOTE}/${sub}/`]);
  if (r.status !== 0) { console.error(`rsync ${sub} 失败：`, r.stderr.trim()); process.exit(1); }
  const files = (r.stdout.match(/Number of (?:regular )?files transferred: ([\d,]+)/) || [])[1];
  const total = (r.stdout.match(/Number of files: ([\d,]+)/) || [])[1];
  console.log(`${sub}：传输 ${files ?? '?'} / 共 ${total ?? '?'}${dry ? '（dry-run）' : ''}`);
}
